package com.kenkern.engine.editor

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.R
import com.kenkern.engine.projects.Kernpack
import com.kenkern.engine.projects.SafFolderResolver
import com.kenkern.engine.autosave.AutoSaveManager
import com.kenkern.engine.assets.import.AssetLayout
import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.assets.import.ImportSummary
import com.kenkern.engine.assets.import.ModelImporter
import com.kenkern.engine.commands.AddClassCommand
import com.kenkern.engine.commands.Command
import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.commands.DeleteNickCommand
import com.kenkern.engine.commands.ImportModelCommand
import com.kenkern.engine.commands.RemoveClassCommand
import com.kenkern.engine.commands.RenameCommand
import com.kenkern.engine.commands.ReorderCommand
import com.kenkern.engine.commands.ReparentCommand
import com.kenkern.engine.commands.SetClassFieldCommand
import com.kenkern.engine.commands.SetColliderShapeCommand
import com.kenkern.engine.commands.SetFlagCommand
import com.kenkern.engine.commands.SetLentePrincipalCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.commands.TransformDragCommand
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.engine.EspelhoNick
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.render.AssetMeshLibrary
import com.kenkern.engine.render.TextureLibrary
import com.kenkern.engine.render.EngineControls
import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.PerfilTexturaImportacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel do editor: cena, seleção, histórico de comandos (undo/redo),
 * estado de Play e auto-save com debounce.
 *
 * ANTI-CRASH: toda operação de arquivo (criar/abrir/listar projeto, carregar cena)
 * roda em Dispatchers.IO e é protegida por runCatching — falhas viram mensagens
 * de status visíveis ([statusMessage]), nunca exceções não tratadas.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    val project = mutableStateOf<ProjectHandle?>(null)
    val scene = mutableStateOf<Scene?>(null)
    val selection = mutableStateOf<ReferenceId?>(null)
    val playState = mutableStateOf(PlayState.STOPPED)
    val revision = mutableIntStateOf(0)
    val recoveryNotice = mutableStateOf(false)
    val statusMessage = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val projects = mutableStateOf<List<ProjectHandle>>(emptyList())

    // ---------- Importação de modelo (ETAPA A) ----------
    /** Etapa corrente da importação ("lendo", "triangulando"…) ou null = ociosa. */
    val importEtapa = mutableStateOf<String?>(null)
    /** Relatório concluído (diálogo com "Copiar relatório"). */
    val importRelatorio = mutableStateOf<ImportSummary?>(null)
    /** Erro CLARO de importação (diálogo: nome do arquivo, causa, o que fazer). */
    val importErro = mutableStateOf<String?>(null)
    /** Confirmação de arquivo grande (> 200 MB — regra A.6). */
    val importPendenteGrande = mutableStateOf<PendenteGrande?>(null)
    /** Opção "Ajustar ao tamanho" escolhida no diálogo de opções (lembrada na sessão). */
    val importAjustarTamanho = mutableStateOf(false)

    /**
     * Limite de resolução na importação: Original ou 512/1024/2048 (padrão 1024).
     * O valor efetivo respeita o limite informado pelo backend GL.
     */
    val importLimiteTextura = mutableIntStateOf(LIMITE_TEXTURA_PADRAO)

    /**
     * B2b (v0.1.17): qualidade do encoder ETC2 — RAPIDA por padrão
     * (individual + diferencial + planar por bloco, paralelo em N threads);
     * ALTA acrescenta os modos T/H ("Alta qualidade" no diálogo).
     */
    val importQualidadeEtc2 = mutableStateOf(Etc2.Qualidade.RAPIDA)

    private val importCancelado = AtomicBoolean(false)
    private var importJob: Job? = null

    /** Pedido de arquivo grande aguardando confirmação do usuário. */
    data class PendenteGrande(val nome: String, val tamanhoMb: Float, val uri: Uri, val pasta: Boolean)

    /** Referência volátil lida pelo thread de render. */
    @Volatile
    var sceneRef: Scene? = null
        private set

    @Volatile
    var selectionRef: ReferenceId? = null
        private set

    var engineControls: EngineControls? = null

    val history = CommandHistory()
    val panelStore = PanelStateStore(application)

    private val autosave = AutoSaveManager(viewModelScope)

    /** Guarda o handle cujo editor está aberto nesta composição (para o save do onDispose). */
    @Volatile
    private var activeHandleAtEnter: ProjectHandle? = null

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val r = OperacaoComLimite.executar("listar projetos") {
                ProjectManager.listProjects(getApplication())
            }
            withContext(Dispatchers.Main) {
                when (r) {
                    is OperacaoComLimite.Resultado.Sucesso -> projects.value = r.valor
                    is OperacaoComLimite.Resultado.Erro -> {
                        KenkernLog.e(TAG, "listar projetos falhou", r.causa)
                        projects.value = emptyList()
                    }
                    is OperacaoComLimite.Resultado.TempoEsgotado -> registrarTimeout(r)
                }
            }
        }
    }

    /** Cria um projeto novo (I/O em background) e abre o editor. Nunca crasha. */
    /**
     * T0.5 (v0.1.21): atalhos Modelar/Animar/Texturizar do Hub — abrem o
     * projeto "Sandbox"; se ele NÃO existe, é criado automaticamente.
     */
    fun abrirOuCriarSandbox() {
        val existente = projects.value.firstOrNull { it.name == "Sandbox" }
        if (existente != null) openProject(existente) else createAndEnterProject("Sandbox")
    }

    fun createAndEnterProject(rawName: String, template: String? = null) {
        if (isLoading.value) return
        // Marcador de progresso: criar também abre o editor — mesma trilha de
        // etapas rastreada para localizar crash nativo (a última etapa gravada
        // é exibida no diálogo de crash do Hub).
        BootTrace.reset()
        BootTrace.mark("openProject início")
        isLoading.value = true
        viewModelScope.launch {
            // S5 (v0.1.24): limite de 15 s — I/O pendurado vira estado de ERRO
            // visível (não spinner eterno com o Hub travado).
            val r = OperacaoComLimite.executar("criar projeto '$rawName'") {
                withContext(Dispatchers.IO) {
                    ProjectManager.createProject(getApplication(), rawName, template)
                }
            }
            isLoading.value = false
            val handle = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "criar projeto falhou", r.causa)
                    statusMessage.value = ProjectManager.lastError
                        ?: getApplication<Application>().getString(R.string.vm_create_fail)
                    return@launch
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    return@launch
                }
            }
            if (handle == null) {
                statusMessage.value =
                    ProjectManager.lastError ?: getApplication<Application>().getString(R.string.vm_create_fail)
                return@launch
            }
            loadIntoEditor(handle)
        }
    }

    /** Abre um projeto existente (I/O em background). Nunca crasha. */
    fun openProject(handle: ProjectHandle) {
        if (isLoading.value) return
        // Marcador de progresso: nova tentativa de abertura — zera o rastreio
        // e grava a primeira etapa com flush (localiza crash nativo no Hub).
        BootTrace.reset()
        BootTrace.mark("openProject início")
        isLoading.value = true
        viewModelScope.launch {
            // S5 (v0.1.24): limite de 15 s — I/O pendurado vira estado de ERRO
            // visível (não spinner eterno com o Hub travado).
            val r = OperacaoComLimite.executar("abrir projeto '${handle.name}'") {
                withContext(Dispatchers.IO) {
                    ProjectManager.openProject(getApplication(), handle.root)
                }
            }
            isLoading.value = false
            val valid = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "abrir projeto falhou", r.causa)
                    statusMessage.value = getApplication<Application>().getString(R.string.vm_invalid)
                    refreshProjects()
                    return@launch
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    refreshProjects()
                    return@launch
                }
            }
            if (valid == null) {
                statusMessage.value = getApplication<Application>().getString(R.string.vm_invalid)
                refreshProjects()
                return@launch
            }
            loadIntoEditor(valid)
        }
    }

    private fun loadIntoEditor(handle: ProjectHandle) {
        isLoading.value = true
        viewModelScope.launch {
            // S5 (v0.1.24): limite de 15 s — carregar a cena pendurada vira
            // estado de ERRO visível (não spinner eterno).
            val r = OperacaoComLimite.executar("carregar cena do projeto '${handle.name}'") {
                runCatching {
                    val recovered = withContext(Dispatchers.IO) { ProjectManager.hasAutosave(handle) }
                    val loaded = withContext(Dispatchers.IO) { ProjectManager.loadScene(handle) }
                    BootTrace.mark("cena carregada") // loadScene retornou sem exceção
                    // Encerra o estado anterior DEPOIS que a cena nova carregou com sucesso.
                    detachEngineState()
                    project.value = handle
                    activeHandleAtEnter = handle
                    sceneRef = loaded
                    scene.value = loaded
                    // Malhas importadas apontam para a raiz DESTE projeto (a cache de GPU
                    // é limpa junto — caminhos relativos resolvem só contra a raiz certa).
                    AssetMeshLibrary.limpar()
                    AssetMeshLibrary.baseDir = handle.root
                    TextureLibrary.limpar()
                    TextureLibrary.baseDir = handle.root
                    recoveryNotice.value = recovered
                    history.clear()
                    selection.value = null
                    selectionRef = null
                    playState.value = PlayState.STOPPED
                    autosave.start({ sceneRef }, { project.value })
                    refreshProjects()
                    bump()
                }
            }
            isLoading.value = false
            when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> {
                    r.valor.onFailure {
                        Log.e(TAG, "Falha ao abrir projeto", it)
                        KenkernLog.e(TAG, "Falha ao abrir projeto", it)
                        statusMessage.value = getApplication<Application>()
                            .getString(R.string.vm_open_fail, it.message ?: "erro desconhecido")
                    }
                }
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "Falha ao abrir projeto", r.causa)
                    statusMessage.value = getApplication<Application>()
                        .getString(R.string.vm_open_fail, r.causa.message ?: "erro desconhecido")
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> registrarTimeout(r)
            }
        }
    }

    /**
     * S5 (v0.1.24): estado de ERRO VISÍVEL para operação que estourou o limite
     * de 15 s — mensagem com a OPERAÇÃO e a ETAPA como causa, registro no
     * Console (KenkernLog) e no logcat; isLoading volta a false (o Hub nunca
     * fica travado em "Carregando…" para sempre).
     */
    private fun registrarTimeout(t: OperacaoComLimite.Resultado.TempoEsgotado) {
        val segundos = t.limiteMs / 1000
        val etapa = t.etapa ?: "—"
        KenkernLog.e(
            TAG,
            "operação '${t.operacao}' não terminou em $segundos s (etapa: $etapa) — I/O pendurado; registrado no Console"
        )
        statusMessage.value = getApplication<Application>()
            .getString(R.string.vm_timeout, t.operacao, segundos, etapa)
    }

    fun exitProject() {
        val handle = project.value
        val currentScene = sceneRef
        if (handle != null && currentScene != null) {
            autosave.saveNow(currentScene, handle)
        }
        detachEngineState()
        autosave.stop()
        refreshProjects()
    }

    /** Sai do editor somente se o projeto aberto for o mesmo que foi apagado no Hub. */
    fun exitProjectIf(handle: ProjectHandle) {
        if (project.value?.root?.absolutePath == handle.root.absolutePath) {
            exitProject()
        }
    }

    /** Renomeia um projeto do Hub (I/O em background). Nunca crasha. */
    fun renameProject(handle: ProjectHandle, newName: String) {
        viewModelScope.launch {
            // S5: limite de 15 s também no renomear.
            val r = OperacaoComLimite.executar("renomear projeto '${handle.name}'") {
                withContext(Dispatchers.IO) { ProjectManager.renameProject(handle, newName) }
            }
            when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> if (r.valor == null) {
                    statusMessage.value =
                        ProjectManager.lastError ?: getApplication<Application>().getString(R.string.vm_rename_fail)
                }
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "renomear projeto falhou", r.causa)
                    statusMessage.value =
                        getApplication<Application>().getString(R.string.vm_rename_fail)
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> registrarTimeout(r)
            }
            refreshProjects()
        }
    }

    /**
     * DUPLICA um projeto ("Salvar como", F2.5): cópia completa da pasta em
     * background; o original não é tocado. Falhas viram mensagem de status.
     */
    fun duplicateProject(handle: ProjectHandle) {
        if (isLoading.value) return
        viewModelScope.launch {
            // S5: limite de 15 s — duplicar pendurado vira erro visível.
            val r = OperacaoComLimite.executar("duplicar projeto '${handle.name}'") {
                withContext(Dispatchers.IO) { ProjectManager.duplicateProject(handle) }
            }
            val copia = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "duplicar projeto falhou", r.causa)
                    null
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    return@launch
                }
            }
            statusMessage.value = if (copia != null) {
                getApplication<Application>().getString(R.string.vm_duplicate_ok, copia.name)
            } else {
                ProjectManager.lastError ?: getApplication<Application>().getString(R.string.vm_duplicate_fail)
            }
            refreshProjects()
        }
    }

    /**
     * EXPORTA o projeto como .kernpack (SAF CREATE_DOCUMENT): o usuário escolhe
     * onde salvar; o zip é escrito direto no Uri. Nunca crasha.
     */
    fun exportarProjetoPara(handle: ProjectHandle, uri: Uri) {
        viewModelScope.launch {
            // S5: limite de 15 s — SAF pendurado vira erro visível.
            val r = OperacaoComLimite.executar("exportar .kernpack de '${handle.name}'") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val app = getApplication<Application>()
                        app.contentResolver.openOutputStream(uri, "w")?.use { saida ->
                            Kernpack.empacotar(handle.root, saida) > 0
                        } ?: error("não foi possível abrir o destino")
                    }.onFailure {
                        Log.e(TAG, "Falha ao exportar .kernpack", it)
                        KenkernLog.e(TAG, "Falha ao exportar .kernpack", it)
                    }
                }
            }
            statusMessage.value = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor.fold(
                    onSuccess = { gravou ->
                        val app = getApplication<Application>()
                        if (gravou) app.getString(R.string.vm_export_ok) else app.getString(R.string.vm_export_empty)
                    },
                    onFailure = { getApplication<Application>().getString(R.string.vm_export_fail, it.message ?: "") }
                )
                is OperacaoComLimite.Resultado.Erro -> getApplication<Application>()
                    .getString(R.string.vm_export_fail, r.causa.message ?: "")
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    return@launch
                }
            }
        }
    }

    /**
     * IMPORTA um .kernpack (SAF OPEN_DOCUMENT): extrai para uma pasta nova no
     * armazenamento do app (nome do projeto de dentro do pacote) e registra
     * na lista. Pacote hostil/inválido não grava nada (pasta é apagada).
     */
    fun importarKernpack(uri: Uri) {
        if (isLoading.value) return
        viewModelScope.launch {
            // S5: limite de 15 s — pacote pendurado vira erro visível.
            val r = OperacaoComLimite.executar("importar .kernpack") {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val app = getApplication<Application>()
                        val nomeLido = app.contentResolver.openInputStream(uri)?.use { Kernpack.nomeDoProjeto(it) }
                        val base = ProjectManager.sanitize(nomeLido ?: "Importado").ifBlank { "Importado" }
                        val pasta = ProjectManager.novaPastaUnica(ProjectManager.projectsRoot(app), base)
                        try {
                            val arquivos = app.contentResolver.openInputStream(uri)?.use {
                                Kernpack.desempacotar(it, pasta)
                            } ?: error("não foi possível abrir o pacote")
                            val handle = ProjectManager.openProject(app, pasta)
                                ?: error("pacote sem project.kenkern válido")
                            getApplication<Application>().getString(R.string.vm_import_ok, handle.name, arquivos)
                        } catch (e: Exception) {
                            pasta.deleteRecursively() // não deixa migalha de pacote ruim
                            throw e
                        }
                    }.onFailure {
                        Log.e(TAG, "Falha ao importar .kernpack", it)
                        KenkernLog.e(TAG, "Falha ao importar .kernpack", it)
                    }
                }
            }
            statusMessage.value = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor.fold(
                    onSuccess = { it },
                    onFailure = {
                        getApplication<Application>().getString(
                            R.string.vm_import_fail, it.message ?: it.javaClass.simpleName
                        )
                    }
                )
                is OperacaoComLimite.Resultado.Erro -> getApplication<Application>().getString(
                    R.string.vm_import_fail, r.causa.message ?: r.causa.javaClass.simpleName
                )
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    return@launch
                }
            }
            refreshProjects()
        }
    }

    /**
     * APAGA um projeto do disco (com o editor fechado antes, sem gravar nada —
     * salvar depois de apagar recriaria os arquivos). Nunca crasha: falhas
     * viram mensagem de status.
     */
    fun deleteProject(handle: ProjectHandle) {
        if (isLoading.value) return
        viewModelScope.launch {
            val wasOpen = project.value?.root?.absolutePath == handle.root.absolutePath
            if (wasOpen) {
                // Fecha SEM salvar: o projeto está sendo removido do disco.
                autosave.stop()
                detachEngineState()
            }
            // S5: limite de 15 s — apagar pendurado vira erro visível.
            val r = OperacaoComLimite.executar("apagar projeto '${handle.name}'") {
                withContext(Dispatchers.IO) { ProjectManager.deleteProject(handle) }
            }
            val ok = when (r) {
                is OperacaoComLimite.Resultado.Sucesso -> r.valor
                is OperacaoComLimite.Resultado.Erro -> {
                    KenkernLog.e(TAG, "apagar projeto falhou", r.causa)
                    false
                }
                is OperacaoComLimite.Resultado.TempoEsgotado -> {
                    registrarTimeout(r)
                    return@launch
                }
            }
            if (!ok) {
                statusMessage.value =
                    ProjectManager.lastError ?: getApplication<Application>().getString(R.string.vm_delete_fail)
            }
            refreshProjects()
        }
    }

    private fun detachEngineState() {
        engineControls?.setPlayState(PlayState.STOPPED)
        engineControls = null
        activeHandleAtEnter = null
        sceneRef = null
        selectionRef = null
        scene.value = null
        project.value = null
        selection.value = null
        history.clear()
        AssetMeshLibrary.limpar()
        AssetMeshLibrary.baseDir = null
        TextureLibrary.limpar()
        TextureLibrary.baseDir = null
    }

    fun saveNow() {
        val handle = project.value ?: activeHandleAtEnter ?: return
        val currentScene = sceneRef ?: return
        runCatching { autosave.saveNow(currentScene, handle) }.onFailure {
            // SAVE silencioso era a pior perda possível (regra nova: registrar sempre).
            Log.e(TAG, "saveNow falhou — cena pode não ter sido salva", it)
            KenkernLog.e(TAG, "Falha ao salvar o projeto (saveNow)", it)
            statusMessage.value = getApplication<Application>()
                .getString(R.string.vm_save_fail, it.message ?: it.javaClass.simpleName)
        }
    }

    fun select(id: ReferenceId?) {
        selection.value = id
        selectionRef = id
    }

    fun run(command: Command) {
        history.push(command)
        autosave.markDirty()
        bump()
    }

    fun undo() {
        history.undo()
        autosave.markDirty()
        bump()
    }

    fun redo() {
        history.redo()
        autosave.markDirty()
        bump()
    }

    fun bump() {
        revision.intValue++
    }

    // ---------- Ações de alto nível ----------

    /**
     * Cria um Nick VAZIO como filho do Nick selecionado; sem seleção, filho do
     * primeiro nick raiz da cena. T0.2: nasce com kind=nick.empty e name NULO
     * (a Hierarquia exibe o rótulo do tipo no idioma — DECISIONS #26). A
     * janela "Adicionar Nick" com o catálogo de tipos entra na T0.3.
     */
    fun addNick() {
        val s = sceneRef ?: return
        val parent = selection.value?.let { s.findNick(it) }
            ?: s.roots.firstOrNull()
        val cmd = CreateNickCommand(s, parent?.id, NickKindRegistry.NICK_EMPTY)
        run(cmd)
        // O novo nick é o último filho do pai (ou última raiz): seleciona direto.
        val criado = cmd.createdNickId()?.let { s.findNick(it) }
        if (criado != null) select(criado.id)
    }

    /**
     * T0.3 (adendo v4, seção 3): cria um Nick do TIPO escolhido na janela
     * "Adicionar Nick" — UM Command (desfazer remove), classes padrão do tipo
     * aplicadas, nasce SELECIONADO na posição do centro da tela projetada no
     * chão. [spawnXz] traz x/z do ponto projetado (null = origem).
     */
    fun addNickOfKind(kind: String, comoFilhoDoSelecionado: Boolean, spawnXz: FloatArray?) {
        val s = sceneRef ?: return
        val def = NickKindRegistry.find(kind)
        // P3 (v0.1.25): REGRA DE NICK COMPLETO — kinds PARCIAIS/SEM_FUNCAO não
        // são criáveis (a janela já mostra "Em breve" desabilitado; o guard
        // aqui é a segunda barreira, SEM engolir: erro no Console).
        if (def != null && !def.criavel()) {
            com.kenkern.engine.diag.KenkernLog.e(
                TAG,
                "addNickOfKind recusado: '$kind' tem status " + def.status +
                    " — só Nicks COMPLETOS são criáveis (NICKS.md, P3)"
            )
            return
        }
        val parent = if (comoFilhoDoSelecionado) {
            selection.value?.let { s.findNick(it) } ?: s.roots.firstOrNull()
        } else {
            null
        }
        val cmd = CreateNickCommand(
            s, parent?.id, kind,
            classesPadrao = def?.defaultClasses ?: emptyList()
        )
        run(cmd)
        val criado = cmd.createdNickId()?.let { s.findNick(it) }
        if (criado != null) {
            if (spawnXz != null) {
                criado.transform.setLocalPosition(spawnXz[0], 0f, spawnXz[1])
            }
            select(criado.id)
            bump()
        }
    }

    /** T0.3: troca a FORMA do colisor do Nick selecionado (UM Command; ajusta à AABB). */
    fun setColliderShape(id: ReferenceId, forma: String) {
        run(SetColliderShapeCommand(sceneRef ?: return, id, forma))
        espelharSeEmPlay(id)
    }

    fun renameSelected(newName: String) {
        val id = selection.value ?: return
        run(RenameCommand(sceneRef ?: return, id, newName))
        espelharSeEmPlay(id)
    }

    fun deleteSelected() {
        val id = selection.value ?: return
        run(DeleteNickCommand(sceneRef ?: return, id))
        select(null)
    }

    fun setNickActive(id: ReferenceId, value: Boolean) {
        run(SetFlagCommand(sceneRef ?: return, id, SetFlagCommand.FlagField.ACTIVE, value))
        espelharSeEmPlay(id)
    }

    fun setNickVisible(id: ReferenceId, value: Boolean) {
        run(SetFlagCommand(sceneRef ?: return, id, SetFlagCommand.FlagField.VISIBLE, value))
        espelharSeEmPlay(id)
    }

    fun setTransform(id: ReferenceId, snapshot: TransformSnapshot) {
        run(TransformCommand(sceneRef ?: return, id, snapshot))
        espelharSeEmPlay(id)
    }

    /**
     * Fecha um arrasto do gizmo como UM único Command (estado inicial + final).
     * O transform já foi aplicado ao vivo durante o arrasto; o execute() do
     * comando é idempotente (re-aplica o estado final).
     */
    fun commitGizmoDrag(id: ReferenceId, start: TransformSnapshot, end: TransformSnapshot) {
        val s = sceneRef ?: return
        run(TransformDragCommand(s, id, start, end))
        espelharSeEmPlay(id)
    }

    fun reparentSelected(newParentId: ReferenceId?) {
        val id = selection.value ?: return
        val s = sceneRef ?: return
        val nick = s.findNick(id) ?: return
        val target = newParentId?.let { s.findNick(it) } ?: return run(ReparentCommand(s, id, null))
        if (target === nick || target.isAncestorOf(nick)) return
        run(ReparentCommand(s, id, newParentId))
    }

    fun reorderSelected(delta: Int) {
        val id = selection.value ?: return
        run(ReorderCommand(sceneRef ?: return, id, delta))
    }

    fun addClassToSelected(typeName: String) {
        val id = selection.value ?: return
        run(AddClassCommand(sceneRef ?: return, id, typeName))
    }

    fun removeClassFromSelected(typeName: String) {
        val id = selection.value ?: return
        run(RemoveClassCommand(sceneRef ?: return, id, typeName))
    }

    fun setClassField(id: ReferenceId, typeName: String, fieldName: String, value: NickValue) {
        run(SetClassFieldCommand(sceneRef ?: return, id, typeName, fieldName, value))
        espelharSeEmPlay(id)
    }

    /**
     * H3 (v0.1.20): "Câmera principal — SÓ UMA por cena" em UM Command
     * (SetLentePrincipalCommand): marca o Nick escolhido e desmarca os
     * demais camera.*; undo devolve TODOS os flags de uma vez.
     */
    fun setLentePrincipal(id: ReferenceId, principal: Boolean) {
        val s = sceneRef ?: return
        run(SetLentePrincipalCommand(s, id, principal))
        // S3: a regra "só UMA principal" envolve VÁRIOS nicks — espelha TODAS
        // as câmeras da cena para a cópia de Play receber o mesmo conjunto.
        espelharCamarasSeEmPlay(s)
    }

    /**
     * S3 (v0.1.24): INSPETOR AO VIVO EM PLAY — depois de cada comando de
     * campo/transform, o estado editável do Nick (transform + classes +
     * flags + nome) é ESPELHADO na cópia de Play que o viewport renderiza,
     * no MESMO frame (EngineControls.espelharNickNoPlay → queueEvent do GL
     * thread). Sem sessão de Play a chamada é ignorada pelo renderer.
     * Estrutura criada/excluída durante o Play não é espelhada (a cópia
     * nasce no Play — campo/transform sim, estrutura não).
     */
    private fun espelharSeEmPlay(id: ReferenceId?) {
        if (id == null) return
        val s = sceneRef ?: return
        val nick = s.findNick(id) ?: return
        engineControls?.espelharNickNoPlay(EspelhoNick.de(nick))
    }

    /** S3: espelha todas as câmeras (a regra da principal desmarca as outras). */
    private fun espelharCamarasSeEmPlay(s: Scene) {
        val camaras = ArrayList<com.kenkern.engine.core.Nick>(4)
        val pilha = ArrayDeque<com.kenkern.engine.core.Nick>()
        for (raiz in s.roots) pilha.addLast(raiz)
        while (!pilha.isEmpty()) {
            val nick = pilha.removeLast()
            if (nick.kind.startsWith("camera.")) camaras.add(nick)
            for (filho in nick.children) pilha.addLast(filho)
        }
        for (camera in camaras) engineControls?.espelharNickNoPlay(EspelhoNick.de(camera))
    }

    fun setPlayState(state: PlayState) {
        playState.value = state
        engineControls?.setPlayState(state)
        if (state == PlayState.STOPPED) {
            bump()
        }
    }

    fun consumeStatus() {
        statusMessage.value = null
    }

    // ---------- Importação de modelo (ETAPA A) ----------

    /**
     * PONTO DE ENTRADA do botão "Importar modelo": valida o ambiente, consulta
     * nome/tamanho no SAF e, se for arquivo grande (> 200 MB), pede confirmação
     * antes de executar (regra A.6). NUNCA roda I/O na thread principal.
     */
    fun prepararImportacao(uri: Uri, pasta: Boolean) {
        if (importEtapa.value != null) return // uma importação por vez
        if (project.value == null || sceneRef == null) {
            importErro.value =
                "Abra (ou crie) um projeto antes de importar — o modelo é gravado nos assets do projeto atual"
            return
        }
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                SafFolderResolver.nomeETamanho(getApplication(), uri)
            }
            val nome = meta?.first ?: "modelo"
            val tamanho = meta?.second ?: -1L
            if (!pasta && tamanho > AssetLayout.AVISO_GRANDE_BYTES) {
                importPendenteGrande.value =
                    PendenteGrande(nome, tamanho / 1048576f, uri, pasta)
                return@launch
            }
            executarImportacao(uri, pasta)
        }
    }

    fun confirmarImportacaoGrande() {
        val pendente = importPendenteGrande.value ?: return
        importPendenteGrande.value = null
        viewModelScope.launch { executarImportacao(pendente.uri, pendente.pasta) }
    }

    fun recusarImportacaoGrande() {
        importPendenteGrande.value = null
    }

    /** Cancelamento cooperativo: o flag é checado entre etapas e na cópia em blocos. */
    fun cancelarImportacao() {
        importCancelado.set(true)
        importJob?.cancel()
    }

    fun limparErroImportacao() {
        importErro.value = null
    }

    fun limparRelatorioImportacao() {
        importRelatorio.value = null
    }

    /**
     * FLUXO COMPLETO (A.2/A.3): lendo (cópia em streaming para origem/) →
     * triangulando/gravando/validando (ModelImporter em Dispatchers.Default) →
     * gravação do .KERN → UM Command (Nick + assets no undo/redo) → seleção +
     * relatório. Cancelamento limpa os parciais ANTES do Command ser criado;
     * depois do Command, o desfazer apaga tudo (é o undo dele).
     */
    private fun executarImportacao(uri: Uri, pasta: Boolean) {
        if (importEtapa.value != null) return
        val scene = sceneRef ?: return
        val raiz = project.value?.root ?: return
        val ajustar = importAjustarTamanho.value
        val resolucao = importLimiteTextura.intValue
        val perfilTextura = PerfilTexturaImportacao.criar(
            resolucao, engineControls?.maxTextureSize ?: 0, importQualidadeEtc2.value
        )
        importCancelado.set(false)
        importJob = viewModelScope.launch {
            var origemRel: String? = null
            var kernRel: String? = null
            // B1 (v0.1.17): cronômetro da importação INTEIRA — toda etapa pesada é
            // medida no ponto exato onde roda; a tabela (ms + % do total) vai para
            // o ImportReport e para o Console (KenkernLog).
            val cronometro = com.kenkern.engine.assets.import.CronometroImport()
            try {
                // ---------------- FASE 1: ler (cópia em streaming, sem carregar 2×)
                importEtapa.value = "lendo"
                val (nomeFonte, bytesModelo, resolver) = withContext(Dispatchers.IO) {
                    cronometro.medir("leitura do arquivo (cópia + leitura)", if (pasta) "pasta SAF" else "arquivo") {
                        if (pasta) {
                        // PASTA SAF (glTF com .bin + texturas): copia a árvore inteira
                        // para origem/<nome>/ e resolve externos contra a pasta SAF.
                        val resolverSaf = SafFolderResolver(getApplication(), uri)
                        val nomePasta = AssetLayout.sanearNome(
                            SafFolderResolver.nomeETamanho(getApplication(), uri)?.first ?: "pasta"
                        )
                        val dirDestino = File(File(raiz, AssetLayout.DIR_ORIGEM), nomePasta)
                        var totalCopiado = 0L
                        for ((relativo, arquivoUri) in resolverSaf.arquivos) {
                            val destino = File(dirDestino, relativo)
                            getApplication<Application>().contentResolver
                                .openInputStream(arquivoUri)?.use { entrada ->
                                    totalCopiado += AssetLayout.copiarStreaming(
                                        entrada, destino, cancelado = { importCancelado.get() }
                                    )
                                } ?: Log.w(TAG, "pasta: não abriu '$relativo' (SAF)")
                        }
                        if (totalCopiado == 0L) {
                            throw ImportException("a pasta escolhida está vazia ou não pôde ser lida")
                        }
                        val nomeModelo = resolverSaf.arquivos.keys.firstOrNull {
                            it.endsWith(".gltf", true) || it.endsWith(".glb", true)
                        } ?: throw ImportException(
                            "a pasta escolhida não tem nenhum .gltf/.glb — escolha a pasta que contém o modelo e os arquivos dele"
                        )
                        val bytes = resolverSaf.bytes(nomeModelo)
                            ?: throw ImportException("não foi possível ler '$nomeModelo' da pasta")
                        Triple(nomeModelo.substringAfterLast('/'), bytes, resolverSaf as com.kenkern.engine.assets.import.ResolvedorExterno?)
                    } else {
                        // ARQUIVO único: copia em streaming e lê UMA VEZ para os parsers.
                        val meta = SafFolderResolver.nomeETamanho(getApplication(), uri)
                        val nomeOriginal = meta?.first ?: "modelo"
                        val ext = nomeOriginal.substringAfterLast('.', "bin").lowercase()
                        val base = AssetLayout.sanearNome(nomeOriginal.substringBeforeLast('.')).ifBlank { "Modelo" }
                        val dirOrigem = File(raiz, AssetLayout.DIR_ORIGEM)
                        val nomeUnico = AssetLayout.nomeUnico(dirOrigem, base, ext)
                        val destino = File(dirOrigem, nomeUnico)
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { entrada ->
                            AssetLayout.copiarStreaming(entrada, destino, cancelado = { importCancelado.get() })
                        } ?: throw ImportException("não foi possível abrir o arquivo escolhido (o SAF recusou)")
                        Triple(nomeUnico, destino.readBytes(), null as com.kenkern.engine.assets.import.ResolvedorExterno?)
                    }
                    }
                }.also { origemRel = caminhoOrigemRelativo(pasta, it.first) }

                if (importCancelado.get()) throw ImportException("importação cancelada pelo usuário")

                // ---------------- FASE 2–4: importar (background, progresso por etapa)
                val summary = withContext(Dispatchers.Default) {
                    ModelImporter.importarModelo(
                        nomeFonte, bytesModelo,
                        ModelImporter.Opcoes(ajustar, perfilTextura = perfilTextura),
                        onEtapa = { etapa -> importEtapa.value = etapa },
                        cancelado = { importCancelado.get() },
                        resolutorExterno = resolver,
                        cronometro = cronometro
                    )
                }

                // ---------------- Grava o .KERN nos assets do projeto
                importEtapa.value = "gravando .KERN"
                withContext(Dispatchers.IO) {
                    val base = nomeFonte.substringBeforeLast('.')
                    val nomeKern = AssetLayout.nomeUnico(File(raiz, AssetLayout.DIR_MODELOS), base, "KERN")
                    cronometro.medir("gravação .KERN em disco", "${summary.bytesKernData.size} bytes") {
                        File(File(raiz, AssetLayout.DIR_MODELOS), nomeKern).writeBytes(summary.bytesKernData)
                    }
                    kernRel = "${AssetLayout.DIR_MODELOS}/$nomeKern"
                }
                summary.caminhoKernRelativo = kernRel!!
                summary.caminhoOrigemRelativo = origemRel ?: ""

                // Texturas → .KIMG: cache por conteúdo e perfil de conversão;
                // falha de UMA textura não derruba a importação — vira AVISO no relatório.
                // B1: cada textura é medida com rótulo próprio ("textura i/n") — decode,
                // redimensionamento, mipmaps, ETC2 e gravação .KIMG separados.
                // Progresso por textura + estimativa de tempo restante; relatórios em ordem.
                if (summary.texturasExtraidas.isNotEmpty()) {
                    importEtapa.value = "convertendo texturas"

                    val totalTexturas = summary.texturasExtraidas.size
                    val inicioTexturas = System.nanoTime()
                    val concluidas = java.util.concurrent.atomic.AtomicInteger(0)
                    val relatorios = arrayOfNulls<String>(totalTexturas)
                    // Original pode ocupar mais memória. ETC2 ainda paraleliza os blocos.
                    val despachante = Dispatchers.IO.limitedParallelism(
                        if (resolucao == PerfilTexturaImportacao.ORIGINAL) 1 else Etc2.threadsEncoder
                    )
                    coroutineScope {
                        for ((idx, textura) in summary.texturasExtraidas.withIndex()) {
                            launch(despachante) {
                                if (importCancelado.get()) {
                                    throw ImportException("importação cancelada pelo usuário")
                                }
                                val rotulo = "textura ${idx + 1}/$totalTexturas"
                                try {
                                    val resultado = com.kenkern.engine.assets.image.KimgConversor.converter(
                                        raiz, textura.sha1, textura.bytes, perfilTextura,
                                        cancelado = { importCancelado.get() },
                                        onProgresso = { frac ->
                                            // B2e: "textura i/n · P%" + tempo restante estimado
                                            importEtapa.value = "$rotulo · ${(frac * 100).toInt()}%" +
                                                estimativaRestante(inicioTexturas, concluidas.get(), totalTexturas)
                                        },
                                        cronometro = cronometro,
                                        rotuloTextura = rotulo
                                    )
                                    relatorios[idx] = "$rotulo (${textura.nomeOrigem}): ${resultado.relatorio}"
                                } catch (e: Exception) {
                                    if (importCancelado.get()) throw e
                                    KenkernLog.e(TAG, "conversão de textura falhou (${textura.nomeOrigem})", e)
                                    relatorios[idx] = "$rotulo: textura '${textura.nomeOrigem}' NÃO convertida: " +
                                        "${e.message ?: e.javaClass.simpleName} — material vira cor sólida"
                                } finally {
                                    concluidas.incrementAndGet()
                                }
                            }
                        }
                    }
                    for (linha in relatorios) if (linha != null) summary.texturasRelatorio.add(linha)
                }

                if (importCancelado.get()) throw ImportException("importação cancelada pelo usuário")

                // ---------------- UM Command: Nick + assets (undo remove os dois)
                val classe = ModelImporter.criarClasseMalha(
                    kernRel!!, summary.aabbMin, summary.aabbMax,
                    if (ajustar) summary.escalaSugerida else 1f
                )
                val parent = selection.value?.let { scene.findNick(it) } ?: scene.roots.firstOrNull()
                val nomeNick = nomeFonte.substringBeforeLast('.')
                cronometro.medir("criação do Nick (Command)", nomeNick) {
                    run(ImportModelCommand(scene, parent?.id, nomeNick, raiz, listOf(kernRel!!, origemRel!!), classe))
                }

                // Seleciona o novo Nick e aplica a escala de enquadramento (opção).
                val criado = scene.findNickByNameAnywhere(nomeNick)
                if (criado != null) {
                    if (ajustar && summary.escalaSugerida != 1f) {
                        criado.transform.scale.set(
                            summary.escalaSugerida, summary.escalaSugerida, summary.escalaSugerida
                        )
                    }
                    select(criado.id)
                }
                bump()
                // B1: tabela de tempo por etapa (ms + % do total) no ImportReport E no
                // Console — sem medição, nenhuma afirmação sobre o gargalo.
                val tabela = cronometro.tabela()
                summary.tabelaTempo = tabela
                val nomeResolucao = if (resolucao == PerfilTexturaImportacao.ORIGINAL) "Original" else "$resolucao px"
                summary.opcoesImport = "textura $nomeResolucao (limite ${perfilTextura.limite} px) · ETC2 " +
                    if (perfilTextura.qualidade == Etc2.Qualidade.ALTA) "alta (T/H)" else "rápida"
                KenkernLog.w(TAG, "ImportReport de '$nomeFonte' — tempo por etapa:\n${tabela.trimEnd()}")
                val gargalo = cronometro.gargalo()
                if (gargalo != null && gargalo.ms > 0) {
                    KenkernLog.w(TAG, "gargalo da importação: ${gargalo.nome} (${gargalo.ms} ms)")
                }
                importRelatorio.value = summary
            } catch (e: ImportException) {
                // Erro CLARO: nome + causa + o que fazer (A.6) — nunca crash, nunca silêncio.
                KenkernLog.e(TAG, "importação de modelo falhou", e)
                importErro.value = e.message ?: e.javaClass.simpleName
            } catch (e: Exception) {
                KenkernLog.e(TAG, "importação de modelo falhou (inesperado)", e)
                importErro.value = "Falha inesperada: ${e.message ?: e.javaClass.simpleName} — " +
                    "se persistir, confira o arquivo e o espaço livre do aparelho"
            } finally {
                // Cancelamento/falha ANTES do Command: apaga os parciais (a origem
                // completa também — ela só pertence ao projeto com o .KERN ao lado).
                val cancelouOuFalhou = importCancelado.get() || importErro.value != null
                if (cancelouOuFalhou && kernRel == null) {
                    if (origemRel != null) File(raiz, origemRel!!).let { f ->
                        if (f.isFile) f.delete() else f.deleteRecursively()
                    }
                }
                importEtapa.value = null
                importCancelado.set(false)
            }
        }
    }

    /**
     * B2e: estimativa de tempo restante das texturas ("· ~12s") a partir do
     * tempo médio por textura concluída. String vazia enquanto não há amostra.
     */
    private fun estimativaRestante(inicioNanos: Long, concluidas: Int, total: Int): String {
        if (concluidas <= 0 || concluidas >= total) return ""
        val decorridoMs = (System.nanoTime() - inicioNanos) / 1_000_000L
        if (decorridoMs <= 0) return ""
        val restanteMs = (decorridoMs / concluidas) * (total - concluidas)
        val seg = (restanteMs + 999) / 1000
        return if (seg >= 60) " · ~${seg / 60}min${seg % 60}s" else " · ~${seg}s"
    }

    /** Caminho relativo da origem copiada (arquivo simples ou pasta inteira). */
    private fun caminhoOrigemRelativo(pasta: Boolean, nomeFonte: String): String {
        return if (pasta) {
            "${AssetLayout.DIR_ORIGEM}/${AssetLayout.sanearNome(nomeFonte.substringBeforeLast('.'))}/$nomeFonte"
        } else {
            "${AssetLayout.DIR_ORIGEM}/$nomeFonte"
        }
    }

    // ---------- C1 (v0.1.26): EDITOR DE CÓDIGO LUA ----------

    /** C1: arquivo de script <projeto>/scripts/[nome]. */
    fun scriptFile(nome: String): java.io.File? {
        val raiz = project.value?.root ?: return null
        return java.io.File(raiz, "scripts/$nome")
    }

    /** C1.2: lista dos arquivos .lua de scripts/ (ordenados). */
    fun scriptsDoProjeto(): List<String> {
        val raiz = project.value?.root ?: return emptyList()
        val dir = java.io.File(raiz, "scripts")
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".lua") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * C1 + R3 (v0.1.29): cria scripts/<nome> com o conteúdo do MODELO —
     * grava ANTES de abrir (CriadorScript, PURO, testado na JVM). Nome
     * PADRÃO "script.lua"; sufixo "_2" SÓ em colisão (nunca mais o sufixo
     * aleatório "script_movimento_34934.lua" da v0.1.28).
     */
    fun criarScriptModelo(modelo: String): String =
        criarScriptComNome(com.kenkern.engine.ui.editor.CriadorScript.NOME_PADRAO, modelo)

    /** R3: cria com o NOME perguntado no diálogo (padrão script.lua). */
    fun criarScriptComNome(nomeDesejado: String, modelo: String): String {
        val raiz = project.value?.root ?: run {
            com.kenkern.engine.diag.KenkernLog.e(TAG, "criarScript: projeto sem raiz — nada criado")
            return ""
        }
        return try {
            val nome = com.kenkern.engine.ui.editor.CriadorScript.criar(
                java.io.File(raiz, "scripts"), nomeDesejado, modelo
            )
            if (nome == null) {
                com.kenkern.engine.diag.KenkernLog.e(TAG, "criarScript: nome inválido '$nomeDesejado'")
                ""
            } else {
                com.kenkern.engine.diag.KenkernLog.w(TAG, "script novo: scripts/$nome (modelo $modelo)")
                nome
            }
        } catch (e: Exception) {
            com.kenkern.engine.diag.KenkernLog.e(TAG, "criarScript falhou ($nomeDesejado)", e)
            ""
        }
    }

    /** C1: fonte INLINE da classe Script de um Nick (null = Nick sem Script). */
    fun fonteInline(nickId: com.kenkern.engine.core.ReferenceId): String? {
        val s = sceneRef ?: return null
        val nick = s.findNick(nickId) ?: return null
        val inst = nick.findClassInstance("Script") ?: return null
        return (inst.fields["fonte"] as? com.kenkern.engine.core.NickValue.StringValue)?.value
    }

    /** C1: rótulo do alvo inline (nome do Nick com Script). */
    fun fonteInlineNome(nickId: com.kenkern.engine.core.ReferenceId): String {
        val s = sceneRef ?: return "fonte"
        val nick = s.findNick(nickId) ?: return "fonte"
        return "${nick.displayName("pt")}.lua"
    }

    /** C1: salva a fonte inline na classe Script (UM Command + espelho em Play). */
    fun salvarFonteInline(nickId: com.kenkern.engine.core.ReferenceId, fonte: String) {
        val s = sceneRef ?: return
        val nick = s.findNick(nickId) ?: return
        val inst = nick.findClassInstance("Script") ?: return
        val atual = (inst.fields["fonte"] as? com.kenkern.engine.core.NickValue.StringValue)?.value ?: ""
        if (atual == fonte) return
        run(
            SetClassFieldCommand(
                s, nickId, "Script", "fonte", com.kenkern.engine.core.NickValue.StringValue(fonte)
            )
        )
        espelharSeEmPlay(nickId)
    }

    // ---------- U1 (v0.1.27): TELAR — documento de UI por projeto ----------

    /** U1: carrega o .kui do projeto (ui/coletar-moedas.kui ou ui/ui.kui). */
    fun carregarUiDoc(): com.kenkern.engine.ui.telar.DocumentoUi? {
        val raiz = project.value?.root ?: return null
        for (nome in listOf("coletar-moedas.kui", "ui.kui")) {
            val f = java.io.File(raiz, "ui/$nome")
            if (f.exists()) {
                try {
                    val doc = com.kenkern.engine.ui.telar.KuiFormat.carregar(f.readText())
                    if (doc != null) return doc
                } catch (e: Exception) {
                    com.kenkern.engine.diag.KenkernLog.e(TAG, "carregarUiDoc falhou ($nome)", e)
                }
            }
        }
        // U1.4: MIGRAÇÃO — ui.joystick da árvore 3D vira elemento de UI
        val s = sceneRef ?: return null
        val joysticks = ArrayList<com.kenkern.engine.core.Nick>(2)
        val pilha = ArrayDeque<com.kenkern.engine.core.Nick>()
        for (raizN in s.roots) pilha.addLast(raizN)
        while (!pilha.isEmpty()) {
            val n = pilha.removeLast()
            if (n.kind == "ui.joystick") joysticks.add(n)
            for (filho in n.children) pilha.addLast(filho)
        }
        if (joysticks.isEmpty()) return null
        val doc = com.kenkern.engine.ui.telar.DocumentoUi("ui")
        for (j in joysticks) {
            val inst = j.findClassInstance("Joystick")
            doc.executar(
                com.kenkern.engine.ui.telar.ComandoAdicionar(
                    com.kenkern.engine.ui.telar.MigracaoUi.elementoDeJoystick(
                        doc.proximoId(), j.displayName("pt"),
                        inst?.getString("tipo", "fixo") ?: "fixo",
                        (inst?.getFloat("tamanho", 112f) ?: 112f).toInt(),
                        inst?.getFloat("opacidade", 0.9f) ?: 0.9f,
                        inst?.getFloat("zonaMorta", 0.15f) ?: 0.15f,
                        inst?.getBool("oitoDirecoes", false) ?: false,
                        inst?.getString("controla", "") ?: "",
                        inst?.getBool("seguirNick", false) ?: false
                    )
                )
            )
        }
        com.kenkern.engine.diag.KenkernLog.w(TAG, "TELAR: " + joysticks.size + " Joystick Nick migrado(s) para a UI sem perder dados")
        return doc
    }

    /** U1.5: grava o documento de UI em ui/ui.kui. */
    fun salvarUiDoc(doc: com.kenkern.engine.ui.telar.DocumentoUi) {
        val raiz = project.value?.root ?: return
        try {
            val dir = java.io.File(raiz, "ui")
            dir.mkdirs()
            java.io.File(dir, doc.nome + ".kui").writeText(com.kenkern.engine.ui.telar.KuiFormat.salvar(doc))
        } catch (e: Exception) {
            com.kenkern.engine.diag.KenkernLog.e(TAG, "salvarUiDoc falhou", e)
        }
    }

    companion object {
        private const val TAG = "Kenkern/EditorVM"

        const val LIMITE_TEXTURA_PADRAO = PerfilTexturaImportacao.PADRAO

        /** Opções de limite de textura oferecidas no diálogo de importação (B2a). */
        val OPCOES_LIMITE_TEXTURA = intArrayOf(PerfilTexturaImportacao.ORIGINAL, 512, 1024, 2048)

        /**
         * Onda 0 (v0.1.28, P3): alvo PADRÃO da aba Código — o PRIMEIRO script
         * do projeto (ordem alfabética, a mesma da lista de abas do editor de
         * código); null = projeto sem script (a UI cria um do modelo "vazio"
         * para o navegador de arquivos nascer com conteúdo). Puro — JVM prova.
         */
        fun alvoCodigoPadrao(scripts: List<String>): String? = scripts.firstOrNull()
    }
}
