package com.kenkern.engine.projects

import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.DeleteNickCommand
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.engine.NickPlayRuntime
import com.kenkern.engine.engine.copyForPlay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

/** Exercita a cena distribuída, não uma reconstrução programática aproximada dela. */
class ExampleWorkflowTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun exemploEditaJogaSalvaExportaEReabreSemLevarEstadoDoPlay() {
        val a = ProjectHandle(tmp.newFolder("original"), "Teste M1", "")
        for (path in listOf("project.kenkern", "scenes/cena.ck")) {
            val f = File(a.root, path)
            f.parentFile!!.mkdirs()
            javaClass.getResourceAsStream("/teste-m1/$path")!!.use { f.writeBytes(it.readBytes()) }
        }
        val edit = ProjectManager.loadSceneForEditing(a)!!.scene
        val initial = CkFormat.writeToString(edit)
        val player = edit.roots.single { it.kind == "body.player" }
        val history = CommandHistory()
        history.push(DeleteNickCommand(edit, player.id))
        assertNull(edit.findNick(player.id))
        history.undo()
        assertEquals(initial, CkFormat.writeToString(edit))
        val play = NickPlayRuntime(edit.copyForPlay()!!)
        repeat(120) { play.step(1f / 60f, 1f, 0f) }
        assertEquals(1f, play.player!!.transform.position.x, 0.001f)
        assertEquals(0.5f, play.player!!.transform.position.y, 0.001f)
        assertEquals(initial, CkFormat.writeToString(edit))
        assertTrue(ProjectManager.saveSceneAtomic(a, edit))
        val pack = ByteArrayOutputStream().also { Kernpack.empacotar(a.root, it) }.toByteArray()
        val b = ProjectHandle(tmp.newFolder("segunda-instalacao"), "importe", "")
        assertEquals(2, Kernpack.desempacotar(pack.inputStream(), b.root))
        assertEquals(initial, CkFormat.writeToString(ProjectManager.loadSceneForEditing(b)!!.scene))
        assertEquals(initial, a.mainSceneFile.readText())
    }
}
