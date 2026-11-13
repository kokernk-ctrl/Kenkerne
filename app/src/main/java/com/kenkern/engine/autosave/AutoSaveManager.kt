package com.kenkern.engine.autosave

import com.kenkern.engine.core.Scene
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import kotlinx.coroutines.CoroutineScope
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
                ProjectManager.saveAutosaveAtomic(project, scene)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Salva imediatamente o auto-save e a cena principal (saída da tela / pausa do app). */
    fun saveNow(scene: Scene?, project: ProjectHandle?) {
        if (scene == null || project == null) return
        ProjectManager.saveAutosaveAtomic(project, scene)
        ProjectManager.saveSceneAtomic(project, scene)
        dirty.set(false)
    }
}
