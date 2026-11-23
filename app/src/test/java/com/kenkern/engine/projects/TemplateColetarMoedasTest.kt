package com.kenkern.engine.projects

import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M1.7 (v0.1.23) — TEMPLATE "COLETAR MOEDAS": o projeto criado já nasce
 * JOGÁVEL — chão estático, Corpo de Jogador, Ponto de Surgimento, Câmera
 * Seguidora, 3 moedas (áreas-gatilho com script de coleta) e contador via
 * ui.contagem. O roundtrip .ck preserva tudo (a cena abre igual no editor).
 */
class TemplateColetarMoedasTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    private fun montar(): Scene {
        val scene = Scene("Coletar moedas")
        ProjectManager.montarTemplateColetarMoedas(scene)
        scene.updateWorldTransforms()
        return scene
    }

    @Test
    fun `template tem chao jogador spawn camera e 3 moedas com script`() {
        val scene = montar()
        assertNotNull(scene.findNickByNameAnywhere("Chão"))
        assertNotNull(scene.findNickByNameAnywhere("Herói"))
        assertNotNull(scene.findNickByNameAnywhere("Moeda 1"))
        assertNotNull(scene.findNickByNameAnywhere("Moeda 2"))
        assertNotNull(scene.findNickByNameAnywhere("Moeda 3"))
        val jogador = scene.findNickByNameAnywhere("Herói")!!
        assertEquals("body.player", jogador.kind)
        val chao = scene.findNickByNameAnywhere("Chão")!!
        assertEquals("body.static", chao.kind)
        val moeda = scene.findNickByNameAnywhere("Moeda 1")!!
        assertEquals("area.volume", moeda.kind)
        val inst = moeda.findClassInstance("Script")
        assertNotNull("moeda com classe Script", inst)
        val fonte = inst!!.getString("fonte", "")
        assertTrue("script coleta + contador", fonte.contains("ui.contagem") && fonte.contains("nick.destroy"))
        // câmera seguidora e surgimento existem
        assertTrue(scene.roots.any { it.kind == "camera.follow" || it.children.any { c -> c.kind == "camera.follow" } })
        assertTrue(scene.roots.any { it.kind == "area.spawn" || it.children.any { c -> c.kind == "area.spawn" } })
    }

    @Test
    fun `coleta soma no contador compartilhado e destrói a moeda`() {
        val scene = montar()
        // PLAY: runtime + scripts (duas moedas coletadas pelo trigger do jogador)
        scene.updateWorldTransforms()
        val runtime = com.kenkern.engine.engine.NickPlayRuntime(scene)
        val scripts = com.kenkern.engine.engine.NickLuaScripts(scene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        // jogador ENTRA nas moedas 1 e 2 (o evento dispara o script da ÁREA —
        // self = moeda — e o do jogador)
        val moeda1 = scene.findNickByNameAnywhere("Moeda 1")!!
        val moeda2 = scene.findNickByNameAnywhere("Moeda 2")!!
        scripts.emitirGatilho(moeda1, "Herói")
        scripts.emitirGatilho(moeda2, "Herói")
        // on_trigger dispara nos scripts do JOGADOR e da ÁREA — a moeda coleta
        // em SEU script (self): a contagem acumula por ui.contagem compartilhada
        val ui = scripts.snapshotUiJogo()
        assertTrue("contador 'moedas' existe no snapshot: $ui", ui.containsKey("moedas"))
        assertEquals(2, ui["moedas"]!!.toInt())
        // as moedas coletadas se destruíram (nick.destroy() no self)
        assertNull(scene.findNickByNameAnywhere("Moeda 1"))
        assertNull(scene.findNickByNameAnywhere("Moeda 2"))
        assertNotNull(scene.findNickByNameAnywhere("Moeda 3"))
    }
}
