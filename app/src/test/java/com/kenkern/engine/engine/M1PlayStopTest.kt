package com.kenkern.engine.engine

import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * M1.6 (v0.1.22) — PLAY E STOP REAIS: o Play inicia SCRIPTS (M1.1), FÍSICA
 * (M1.2) e INPUT (M1.3) numa CÓPIA da cena (snapshot); o STOP restaura a cena
 * EXATAMENTE — inclusive Nicks CRIADOS e DESTRUÍDOS por script, posições
 * movidas pela física e variáveis de estado do Lua. A cena do EDITOR (e o
 * autosave) nunca é tocada durante o Play.
 */
class M1PlayStopTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    @Test
    fun `stop restaura a cena exatamente apos play destrutivo`() {
        // ---------- CENA DO EDITOR ----------
        val editor = Scene()
        val chao = editor.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = editor.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10.5f, 0f)
        val alvo = editor.createRootNick("Alvo")
        alvo.transform.position.set(5f, 0.5f, 0f)
        val script = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        script.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            """
            andou = 0
            on_update = function(dt)
              andou = andou + dt
              local p = nick.position()
              nick.set_position(p.x + 1.0, p.y, p.z)
              if andou >= 0.3 and not destruido then
                destruido = true
                local a = scene.find("Alvo")
                if a then a.destroy() end
                scene.spawn(9, 9, 9)
                ui.texto("aviso", "destrui")
              end
            end
            """.trimIndent()
        )
        player.addClassInstance(script)
        editor.updateWorldTransforms()

        // ---------- SNAPSHOT do editor (antes do Play) ----------
        val snapshotAntes = CkFormat.writeToString(editor)
        val rootsAntes = editor.roots.size
        val playerXAntes = player.transform.position.x

        // ---------- PLAY: cópia + runtime + física + scripts (como no SurfaceView) ----------
        val playScene = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!
        playScene.updateWorldTransforms()
        val runtime = NickPlayRuntime(playScene)
        val scripts = NickLuaScripts(playScene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        // física (anda para a direita) + scripts (movem, destroem, criam)
        repeat(4) { t ->
            runtime.step(0.1f, 1f, 0f)
            scripts.atualizar(0.1f)
        }
        // confirma que o Play DE FATO mexeu na CÓPIA (o teste prova a restauração):
        val playerCopy = playScene.findNickByNameAnywhere("player")!!
        val spawnados = playScene.roots.count { it != playScene.findNickByNameAnywhere("chao") && it != playerCopy && it != playScene.findNickByNameAnywhere("Alvo") }
        assertTrue("a cópia MOVEU o jogador", playerCopy.transform.position.x > 0.5f)
        assertNull("o script DESTRUIU o Alvo na cópia", playScene.findNickByNameAnywhere("Alvo"))
        assertEquals("o script CRIOU 1 nick na cópia", 1, spawnados)

        // ---------- STOP: a cena do EDITOR está EXATAMENTE como antes ----------
        assertEquals("N de Nicks do editor intacto", rootsAntes, editor.roots.size)
        assertNotNull("Alvo NÃO foi destruído no editor", editor.findNickByNameAnywhere("Alvo"))
        assertEquals("posição do player intacta", playerXAntes, player.transform.position.x, 1e-6f)
        assertEquals("serialização idêntica (snapshot completo)", snapshotAntes, CkFormat.writeToString(editor))
        // e um NOVO Play reconstrói tudo do snapshot (jogador no lugar, Alvo vivo)
        val novaCopia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!
        assertNotNull(novaCopia.findNickByNameAnywhere("Alvo"))
        assertEquals(playerXAntes, novaCopia.findNickByNameAnywhere("player")!!.transform.position.x, 1e-6f)
    }

    @Test
    fun `play inicia fisica scripts e input na copia`() {
        val editor = Scene()
        val chao = editor.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = editor.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10.5f, 0f)
        val script = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        script.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            "n = 0\non_update = function(dt) n = n + 1 end"
        )
        player.addClassInstance(script)
        editor.updateWorldTransforms()

        val playScene = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!
        playScene.updateWorldTransforms()
        val runtime = NickPlayRuntime(playScene)
        val scripts = NickLuaScripts(playScene).also { it.iniciarTodos() }
        val estado = EstadoInputJogo().also { it.moverX = 1f }
        scripts.estadoInput = estado
        runtime.scripts = scripts
        // 1 tick de Play: input MOVE (física) + script RODA
        runtime.step(0.1f, estado.moverX, estado.moverY)
        scripts.atualizar(0.1f)
        estado.fimDoTick()
        assertTrue("física moveu pela input", playScene.findNickByNameAnywhere("player")!!.transform.position.x > 0.2f)
        val globals = (scripts.instanciaDe("player")?.runtime as? com.kenkern.engine.core.script.LuaJScriptRuntime)?.ambiente()
        assertEquals("script rodou 1 tick", 1, globals!!.get("n").toint())
    }
}
