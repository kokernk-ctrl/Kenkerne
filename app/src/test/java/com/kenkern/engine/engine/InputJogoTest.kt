package com.kenkern.engine.engine

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M1.3 (v0.1.22) — INPUT DE JOGO: normalização, zona morta com
 * re-normalização contínua, curva de resposta, TRAVAR EM 8 DIREÇÕES,
 * MULTITOQUE (funções puras por dedo), borda dos botões de ação e o pulo do
 * player no runtime. M1.5: ui.texto/contador do Lua.
 */
class InputJogoTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    @Test
    fun `normalizacao limita ao circulo unitario`() {
        val out = FloatArray(2)
        InputJogo.aplicar(10f, 0f, 1f, 0f, false, out)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(0f, out[1], 1e-4f)
        InputJogo.aplicar(3f, 4f, 5f, 0f, false, out) // |v| = 1 (na borda)
        assertEquals(0.6f, out[0], 1e-2f)
        assertEquals(0.8f, out[1], 1e-2f)
    }

    @Test
    fun `zona morta zera e re-normaliza sem salto`() {
        val out = FloatArray(2)
        InputJogo.aplicar(0.05f, 0f, 1f, 0.2f, false, out) // DENTRO da zona morta
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(0f, out[1], 1e-6f)
        // na BORDA da zona morta a saída é CONTÍNUA (começa de 0, sem pulo)
        InputJogo.aplicar(0.2f, 0f, 1f, 0.2f, false, out)
        assertEquals(0f, out[0], 0.02f)
        // o MÁXIMO continua 1 na borda externa
        InputJogo.aplicar(1f, 0f, 1f, 0.2f, false, out)
        assertTrue("borda externa ~1 (com curva)", out[0] in 0.95f..1.01f)
    }

    @Test
    fun `curva de resposta e crescente e comeca suave`() {
        val antes = InputJogo.curva(0.1f)
        val meio = InputJogo.curva(0.5f)
        val borda = InputJogo.curva(1f)
        assertTrue(antes < meio && meio < borda)
        assertTrue("suave no centro (v^1,5 < v)", antes < 0.1f)
        assertEquals(1f, borda, 1e-6f)
    }

    @Test
    fun `travar em 8 direcoes ajusta para 45 graus`() {
        val out = FloatArray(2)
        // 30° → trava em 45°: |x| == |y|
        InputJogo.aplicar(1f, 0.577f, 1f, 0f, true, out)
        assertEquals(kotlin.math.abs(out[0]), kotlin.math.abs(out[1]), 0.01f)
        // 0° → anda reto no X
        InputJogo.aplicar(1f, 0.01f, 1f, 0f, true, out)
        assertTrue("y quase zero na direção X", kotlin.math.abs(out[1]) < 0.05f)
        // diagonal exata 45° mantém |x| = |y|
        InputJogo.aplicar(1f, 1f, 1.4142f, 0f, true, out)
        assertEquals(kotlin.math.abs(out[0]), kotlin.math.abs(out[1]), 0.01f)
        // sem trava: 30° mantém o ângulo (proporção preservada)
        InputJogo.aplicar(1f, 0.577f, 1f, 0f, false, out)
        assertEquals(0.577f, out[1] / out[0], 0.05f)
    }

    @Test
    fun `multitoque dois dedos sao independentes`() {
        // funções PURAS: cada dedo aplica separadamente — joystick + botão não
        // interferem (a UI tem áreas de pointerInput separadas).
        val joystick = FloatArray(2)
        InputJogo.aplicar(0.4f, 0f, 1f, 0.1f, false, joystick)
        assertTrue(joystick[0] > 0f)
        val estado = EstadoInputJogo()
        estado.atacarPressionado = true
        estado.fimDoTick()
        assertTrue("atacar na borda", estado.atacarBorda)
        assertEquals("joystick não afetou o botão", 0f, estado.moverX, 1e-6f)
    }

    @Test
    fun `borda de pressao dispara uma vez por pressionada`() {
        val estado = EstadoInputJogo()
        estado.pularPressionado = true
        estado.fimDoTick()
        assertTrue("1º tick: borda", estado.pularBorda)
        estado.fimDoTick()
        assertFalse("segurando NÃO repete", estado.pularBorda)
        estado.pularPressionado = false
        estado.fimDoTick()
        assertFalse(estado.pularBorda)
        estado.pularPressionado = true
        estado.fimDoTick()
        assertTrue("reapertar: nova borda", estado.pularBorda)
    }

    @Test
    fun `pulo do runtime sobe e volta ao chao`() {
        val scene = Scene()
        val chao = scene.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = scene.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10.5f, 0f)
        scene.updateWorldTransforms()
        val runtime = NickPlayRuntime(scene)
        repeat(10) { runtime.step(1f / 60f, 0f, 0f) } // assenta no chão (y=10,3)
        val yChao = player.transform.position.y
        runtime.pular()
        runtime.step(1f / 60f, 0f, 0f)
        val ySubiu = player.transform.position.y
        assertTrue("pulo sobe ($ySubiu > $yChao)", ySubiu > yChao + 0.05f)
        repeat(90) { runtime.step(1f / 60f, 0f, 0f) }
        assertEquals("volta ao chão", yChao, player.transform.position.y, 0.1f)
    }

    @Test
    fun `lua le input joystick e acao`() {
        val scene = Scene()
        val nick = scene.createRootNick("ComScript")
        val inst = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        inst.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            """
            lido = {}
            function on_start()
              local j = input.joystick()
              table.insert(lido, j.x)
              table.insert(lido, input.acao("pular"))
            end
            """.trimIndent()
        )
        nick.addClassInstance(inst)
        val scripts = NickLuaScripts(scene)
        val estado = EstadoInputJogo()
        estado.moverX = 0.5f
        estado.pularPressionado = true
        scripts.estadoInput = estado
        scripts.iniciarTodos()
        val globals = (scripts.instanciaDe("ComScript")?.runtime as? LuaJScriptRuntime)?.ambiente()
        val lido = globals!!.get("lido")
        assertEquals(0.5, lido.get(1).todouble(), 1e-6)
        assertEquals(true, lido.get(2).toboolean())
    }

    @Test
    fun `joystick nick fornece a configuracao`() {
        val scene = Scene()
        val joy = scene.createRootNick("Joy", kind = "ui.joystick")
        joy.transform.position.set(0f, 0.5f, 0f)
        val inst = NickClassRegistry.createInstance("Joystick")!!
        inst.fields["tamanho"] = NickValue.FloatValue(160f)
        inst.fields["zonaMorta"] = NickValue.FloatValue(0.3f)
        inst.fields["oitoDirecoes"] = NickValue.BoolValue(true)
        joy.addClassInstance(inst)
        com.kenkern.engine.ui.screens.JoystickUiConfig.de(scene).let { cfg ->
            assertEquals(160f, cfg.tamanhoDp, 1e-3f)
            assertEquals(0.3f, cfg.zonaMorta, 1e-3f)
            assertTrue(cfg.oitoDirecoes)
            assertEquals("fixo", cfg.tipo)
        }
        // sem joystick na cena: padrão do teste
        com.kenkern.engine.ui.screens.JoystickUiConfig.de(Scene()).let { cfg ->
            assertEquals(112f, cfg.tamanhoDp, 1e-3f)
        }
    }
}
