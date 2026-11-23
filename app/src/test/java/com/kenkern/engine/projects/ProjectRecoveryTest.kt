package com.kenkern.engine.projects

import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectRecoveryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun project() = ProjectHandle(tmp.root, "teste", "").also { it.scenesDir.mkdir() }
    private fun save(name: String) = CkFormat.writeToString(Scene(name).apply { createRootNick(name) })

    @Test fun autosaveTruncadoMaisNovoNaoOcultaPrincipal() {
        val p = project()
        p.mainSceneFile.writeText(save("principal"))
        for (damaged in listOf("", "CK2\nSCENE name=x\n", "CK1\nENDCK")) {
            p.autosaveFile.writeText(damaged)
            assertTrue(p.mainSceneFile.setLastModified(1000L))
            val result = ProjectManager.loadSceneForEditing(p)!!
            assertFalse(result.fromAutosave)
            assertEquals("principal", result.scene.roots.single().name)
            assertEquals(damaged, p.autosaveFile.readText())
        }
    }

    @Test fun principalInvalidaRecuperaAutosaveAntigo() {
        val p = project()
        p.autosaveFile.writeText(save("reserva"))
        assertTrue(p.autosaveFile.setLastModified(1000L))
        p.mainSceneFile.writeText("invalido")
        val result = ProjectManager.loadSceneForEditing(p)!!
        assertTrue(result.fromAutosave)
        assertEquals("reserva", result.scene.name)
    }

    @Test fun doisArquivosRuinsNaoAtivamCenaEmBrancoParaEdicao() {
        val p = project()
        p.mainSceneFile.writeText("incompleto")
        assertNull(ProjectManager.loadSceneForEditing(p))
        assertEquals("incompleto", p.mainSceneFile.readText())
        assertFalse(p.autosaveFile.exists())
    }

    @Test fun trocaAtomicaFalhaSemRemoverDestino() {
        val target = tmp.newFolder("destino")
        val sentinela = File(target, "intacto").apply { writeText("original") }
        assertFalse(ProjectManager.writeFileAtomic(target, "novo"))
        assertEquals("original", sentinela.readText())
        assertEquals(listOf("destino"), tmp.root.list()!!.toList())
        assertTrue(ProjectManager.writeFileAtomic(sentinela, "alterado"))
        assertEquals("alterado", sentinela.readText())
    }

    @Test fun nomesReservadosSaoPastasSeparadas() {
        val a = ProjectManager.novaPastaUnica(tmp.root, "jogo")
        val b = ProjectManager.novaPastaUnica(tmp.root, "jogo")
        assertTrue(a.isDirectory && b.isDirectory && a != b)
    }
}
