package com.kenkern.engine.autosave

import com.kenkern.engine.core.Scene
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutoSaveManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun autosaveFuturoNaoSobrepoeUltimoSave() = runBlocking {
        val p = ProjectHandle(tmp.root, "P", "")
        assertTrue(ProjectManager.saveAutosaveAtomic(p, Scene("antiga")))
        assertTrue(p.autosaveFile.setLastModified(System.currentTimeMillis() + 120_000))
        val manager = AutoSaveManager(this)
        assertTrue(manager.saveImmediately(Scene("atual"), p))
        assertEquals("atual", ProjectManager.loadSceneForEditing(p)!!.scene.name)
    }

    @Test fun falhaNaPrincipalGuardaReservaERetornaFalha() = runBlocking {
        val p = ProjectHandle(tmp.root, "P", "")
        assertTrue(p.mainSceneFile.mkdirs()) // destino não substituível por arquivo
        val manager = AutoSaveManager(this)
        assertFalse(manager.saveImmediately(Scene("resgatada"), p))
        assertEquals("resgatada", ProjectManager.loadSceneForEditing(p)!!.scene.name)
    }
}
