package com.kenkern.engine.editor

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.autosave.AutoSaveManager
import com.kenkern.engine.commands.AddClassCommand
import com.kenkern.engine.commands.Command
import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.commands.DeleteNickCommand
import com.kenkern.engine.commands.RemoveClassCommand
import com.kenkern.engine.commands.RenameCommand
import com.kenkern.engine.commands.ReorderCommand
import com.kenkern.engine.commands.ReparentCommand
import com.kenkern.engine.commands.SetClassFieldCommand
import com.kenkern.engine.commands.SetFlagCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.render.EngineControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                // Encerra o estado anterior DEPOIS que a cena nova carregou com sucesso.
                detachEngineState()
                project.value = handle
                activeHandleAtEnter = handle
                sceneRef = loaded
                scene.value = loaded
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
    }

    fun saveNow() {
        val handle = project.value ?: activeHandleAtEnter ?: return
        val currentScene = sceneRef ?: return
        runCatching { autosave.saveNow(currentScene, handle) }
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

    companion object {
        private const val TAG = "Kenkern/EditorVM"
    }
}
