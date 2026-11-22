package com.kenkern.engine.editor

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.kenkern.engine.commands.SetFlagCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.commands.TransformDragCommand
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.render.AssetMeshLibrary
import com.kenkern.engine.render.TextureLibrary
import com.kenkern.engine.render.EngineControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
            val list = ProjectManager.listProjects(getApplication())
            withContext(Dispatchers.Main) { projects.value = list }
        }
    }

    /** Cria um projeto novo (I/O em background) e abre o editor. Nunca crasha. */
    fun createAndEnterProject(rawName: String) {
        if (isLoading.value) return
        // Marcador de progresso: criar também abre o editor — mesma trilha de
        // etapas rastreada para localizar crash nativo (a última etapa gravada
        // é exibida no diálogo de crash do Hub).
        BootTrace.reset()
        BootTrace.mark("openProject início")
        isLoading.value = true
        viewModelScope.launch {
            val handle = withContext(Dispatchers.IO) {
                ProjectManager.createProject(getApplication(), rawName)
            }
            isLoading.value = false
            if (handle == null) {
                statusMessage.value = ProjectManager.lastError ?: "Não foi possível criar o projeto"
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
            val valid = withContext(Dispatchers.IO) {
                ProjectManager.openProject(getApplication(), handle.root)
            }
            isLoading.value = false
            if (valid == null) {
                statusMessage.value = "Projeto inválido ou corrompido"
                refreshProjects()
                return@launch
            }
            loadIntoEditor(valid)
        }
    }

    private fun loadIntoEditor(handle: ProjectHandle) {
        isLoading.value = true
        viewModelScope.launch {
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
            }.onFailure {
                Log.e(TAG, "Falha ao abrir projeto", it)
                KenkernLog.e(TAG, "Falha ao abrir projeto", it)
                statusMessage.value = "Falha ao abrir projeto: ${it.message ?: "erro desconhecido"}"
            }
            isLoading.value = false
        }
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
            val updated = withContext(Dispatchers.IO) {
                ProjectManager.renameProject(handle, newName)
            }
            if (updated == null) {
                statusMessage.value =
                    ProjectManager.lastError ?: "Não foi possível renomear o projeto"
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
            val copia = withContext(Dispatchers.IO) { ProjectManager.duplicateProject(handle) }
            statusMessage.value = if (copia != null) {
                "Cópia criada: ${copia.name}"
            } else {
                ProjectManager.lastError ?: "Não foi possível duplicar o projeto"
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
            val ok = withContext(Dispatchers.IO) {
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
            statusMessage.value = ok.fold(
                onSuccess = { gravou -> if (gravou) "Projeto exportado" else "Projeto vazio — nada a exportar" },
                onFailure = { "Falha ao exportar: ${it.message}" }
            )
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
            val resultado = withContext(Dispatchers.IO) {
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
                        "${handle.name} importado ($arquivos arquivos)"
                    } catch (e: Exception) {
                        pasta.deleteRecursively() // não deixa migalha de pacote ruim
                        throw e
                    }
                }.onFailure {
                    Log.e(TAG, "Falha ao importar .kernpack", it)
                    KenkernLog.e(TAG, "Falha ao importar .kernpack", it)
                }
            }
            statusMessage.value = resultado.fold(
                onSuccess = { it },
                onFailure = { "Falha ao importar: ${it.message ?: it.javaClass.simpleName}" }
            )
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
            val ok = withContext(Dispatchers.IO) { ProjectManager.deleteProject(handle) }
            if (!ok) {
                statusMessage.value =
                    ProjectManager.lastError ?: "Não foi possível apagar o projeto"
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
            statusMessage.value = "Falha ao salvar: ${it.message ?: it.javaClass.simpleName}"
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
     * Cria um Nick como filho do Nick SELECIONADO; sem seleção, filho do
     * "Mundo" (primeiro nick raiz da cena). Nunca fora da raiz.
     */
    fun addNick() {
        val s = sceneRef ?: return
        val parent = selection.value?.let { s.findNick(it) }
            ?: s.roots.firstOrNull()
        val name = s.nextNickName()
        run(CreateNickCommand(s, parent?.id, name))
        sceneRef?.findNickByNameAnywhere(name)?.let { select(it.id) }
    }

    fun renameSelected(newName: String) {
        val id = selection.value ?: return
        run(RenameCommand(sceneRef ?: return, id, newName))
    }

    fun deleteSelected() {
        val id = selection.value ?: return
        run(DeleteNickCommand(sceneRef ?: return, id))
        select(null)
    }

    fun setNickActive(id: ReferenceId, value: Boolean) {
        run(SetFlagCommand(sceneRef ?: return, id, SetFlagCommand.FlagField.ACTIVE, value))
    }

    fun setNickVisible(id: ReferenceId, value: Boolean) {
        run(SetFlagCommand(sceneRef ?: return, id, SetFlagCommand.FlagField.VISIBLE, value))
    }

    fun setTransform(id: ReferenceId, snapshot: TransformSnapshot) {
        run(TransformCommand(sceneRef ?: return, id, snapshot))
    }

    /**
     * Fecha um arrasto do gizmo como UM único Command (estado inicial + final).
     * O transform já foi aplicado ao vivo durante o arrasto; o execute() do
     * comando é idempotente (re-aplica o estado final).
     */
    fun commitGizmoDrag(id: ReferenceId, start: TransformSnapshot, end: TransformSnapshot) {
        val s = sceneRef ?: return
        run(TransformDragCommand(s, id, start, end))
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
    }

    fun setPlayState(state: PlayState) {
        playState.value = state
        engineControls?.setPlayState(state)
        if (state == PlayState.STOPPED) {
            sceneRef?.resetFunctionState()
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
                        ModelImporter.Opcoes(ajustar),
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

                // Texturas → .KIMG (ETAPA C): hash = nome do arquivo (cache por conteúdo);
                // falha de UMA textura não derruba a importação — vira AVISO no relatório.
                // B1: cada textura é medida com rótulo próprio ("textura i/n") — decode,
                // redimensionamento, mipmaps, ETC2 e gravação .KIMG separados.
                if (summary.texturasExtraidas.isNotEmpty()) {
                    importEtapa.value = "convertendo texturas"
                    val limite = maxTexturaDoAparelho()
                    val totalTexturas = summary.texturasExtraidas.size
                    for ((idx, textura) in summary.texturasExtraidas.withIndex()) {
                        if (importCancelado.get()) throw ImportException("importação cancelada pelo usuário")
                        val rotulo = "textura ${idx + 1}/$totalTexturas"
                        try {
                            val sha1 = com.kenkern.engine.assets.import.AssetLayout.sha1Hex(textura.bytes)
                            val resultado = withContext(Dispatchers.IO) {
                                com.kenkern.engine.assets.image.KimgConversor.converter(
                                    raiz, sha1, textura.bytes, limite,
                                    comprimirEmDisco = true, // DECISIONS #22 (V2.4)
                                    cancelado = { importCancelado.get() },
                                    cronometro = cronometro,
                                    rotuloTextura = rotulo
                                )
                            }
                            summary.texturasRelatorio.add("$rotulo (${textura.nomeOrigem}): ${resultado.relatorio}")
                        } catch (e: Exception) {
                            KenkernLog.e(TAG, "conversão de textura falhou (${textura.nomeOrigem})", e)
                            summary.texturasRelatorio.add(
                                "$rotulo: textura '${textura.nomeOrigem}' NÃO convertida: ${e.message ?: e.javaClass.simpleName} — material vira cor sólida"
                            )
                        }
                    }
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
     * GL_MAX_TEXTURE_SIZE real do aparelho (lido pelo backend no initialize do
     * GL). Antes do primeiro frame é 0 → sem limite no pipeline (o upload aplica
     * o limite real e cai para RGBA8 se exceder).
     */
    private fun maxTexturaDoAparelho(): Int = engineControls?.maxTextureSize ?: 0

    /** Caminho relativo da origem copiada (arquivo simples ou pasta inteira). */
    private fun caminhoOrigemRelativo(pasta: Boolean, nomeFonte: String): String {
        return if (pasta) {
            "${AssetLayout.DIR_ORIGEM}/${AssetLayout.sanearNome(nomeFonte.substringBeforeLast('.'))}/$nomeFonte"
        } else {
            "${AssetLayout.DIR_ORIGEM}/$nomeFonte"
        }
    }

    companion object {
        private const val TAG = "Kenkern/EditorVM"
    }
}
