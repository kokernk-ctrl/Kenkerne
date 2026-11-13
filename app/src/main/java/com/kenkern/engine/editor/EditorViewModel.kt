package com.kenkern.engine.editor

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.autosave.AutoSaveManager
import com.kenkern.engine.commands.Command
import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.commands.DeleteNickCommand
import com.kenkern.engine.commands.RenameCommand
import com.kenkern.engine.commands.ReparentCommand
import com.kenkern.engine.commands.ReorderCommand
import com.kenkern.engine.commands.SetFlagCommand
import com.kenkern.engine.commands.SetClassFieldCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.commands.AddClassCommand
import com.kenkern.engine.commands.RemoveClassCommand
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.render.EngineControls

/**
 * ViewModel do editor: cena, seleção, histórico de comandos (undo/redo),
 * estado de Play e auto-save com debounce.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    val project = mutableStateOf<ProjectHandle?>(null)
    val scene = mutableStateOf<Scene?>(null)
    val selection = mutableStateOf<ReferenceId?>(null)
    val playState = mutableStateOf(PlayState.STOPPED)
    val revision = mutableIntStateOf(0)
    val recoveryNotice = mutableStateOf(false)
    val statusMessage = mutableStateOf<String?>(null)

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

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun enterProject(handle: ProjectHandle) {
        exitProject()
        project.value = handle
        val recovered = ProjectManager.hasAutosave(handle)
        val loaded = ProjectManager.loadScene(handle)
        sceneRef = loaded
        scene.value = loaded
        recoveryNotice.value = recovered
        history.clear()
        selection.value = null
        selectionRef = null
        playState.value = PlayState.STOPPED
        engineControls?.setPlayState(PlayState.STOPPED)
        autosave.start({ sceneRef }, { project.value })
        bump()
    }

    fun exitProject() {
        if (project.value != null && sceneRef != null) {
            autosave.saveNow(sceneRef, project.value)
        }
        autosave.stop()
        engineControls?.setPlayState(PlayState.STOPPED)
        engineControls = null
        sceneRef = null
        selectionRef = null
        scene.value = null
        project.value = null
        selection.value = null
    }

    fun saveNow() {
        autosave.saveNow(sceneRef, project.value)
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

    fun addNick() {
        val s = sceneRef ?: return
        val parent = selection.value?.let { s.findNick(it) }
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
}
