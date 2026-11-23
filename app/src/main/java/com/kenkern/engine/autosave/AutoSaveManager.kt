package com.kenkern.engine.autosave

import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.core.Scene
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AUTO-SAVE da KENKERN: salva em segundo plano com debounce e grava de forma
 * atômica (arquivo temporário + rename). Após um crash, a cena é recuperada
 * do auto-save em ProjectManager.loadScene.
 */
class AutoSaveManager(private val scope: CoroutineScope, private val debounceMs: Long = 1500L) {

    private val dirty = AtomicBoolean(false)
    private var job: Job? = null
    // Uma captura já enviada à fila de I/O nunca pode ganhar de um save/stop posterior.
    private val writer = Any()
    private var generation = 0L

    fun markDirty() {
        dirty.set(true)
    }

    /** Inicia o loop em segundo plano (debounce + gravação atômica). */
    fun start(sceneProvider: () -> Scene?, projectProvider: () -> ProjectHandle?) {
        stop()
        job = scope.launch {
            while (true) {
                delay(200)
                if (!dirty.getAndSet(false)) continue
                // Debounce: espera quietude antes de gravar.
                delay(debounceMs)
                if (dirty.get()) {
                    // Novas mudanças chegaram durante o debounce; grava mesmo assim
                    // na próxima passagem (marca de novo).
                    dirty.set(true)
                    continue
                }
                val project = projectProvider() ?: continue
                val scene = sceneProvider() ?: continue
                val ticket = synchronized(writer) { generation }
                val text = scene.withEditorAccess { CkFormat.writeToString(scene) }
                withContext(Dispatchers.IO) {
                    synchronized(writer) {
                        if (ticket == generation && !ProjectManager.writeFileAtomic(project.autosaveFile, text)) {
                            dirty.set(true)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synchronized(writer) { generation++; dirty.set(false) }
    }

    /** Salva imediatamente a cena principal (saída da tela / pausa do app). */
    fun saveNow(scene: Scene?, project: ProjectHandle?) {
        if (scene == null || project == null) return
        saveImmediately(scene, project)
    }

    fun saveImmediately(scene: Scene, project: ProjectHandle): Boolean {
        val text = scene.withEditorAccess { CkFormat.writeToString(scene) }
        return synchronized(writer) {
            generation++
            val salvou = ProjectManager.writeFileAtomic(project.mainSceneFile, text)
            if (!salvou || ProjectManager.hasAutosave(project)) {
                // Falha na principal ou autosave antigo com relógio futuro: guardar o estado correto.
                val reserva = ProjectManager.writeFileAtomic(project.autosaveFile, text)
                dirty.set(!salvou || !reserva)
                salvou && reserva
            } else {
                dirty.set(false)
                true
            }
        }
    }
}
