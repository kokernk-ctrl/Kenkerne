package com.kenkern.engine.engine

import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import org.luaj.vm2.Globals
import com.kenkern.engine.diag.KenkernLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * C2 (v0.1.26) — NICKS DE ÁREA FUNCIONAIS (ZONA): on_enter/on_exit/on_stay,
 * filtro por tag, uma vez só, area.bodies()/contains(), Signal Nick
 * (signal.emit/on), checkpoint + respawn (queda abaixo do limite) e o
 * template "Coletar moedas" com a classe Area. TUDO SEM GL (runtime real).
 */
class ZonaAreasC2Test {

    @Before
    fun setup() {
        KenkernLog.androidLogging = false
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    private fun attachScript(nick: Nick, fonte: String) {
        val inst = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        inst.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(fonte)
        nick.addClassInstance(inst)
    }

    private fun globalsDe(scripts: NickLuaScripts, nome: String): Globals? {
        val inst = scripts.instanciaDe(nome) ?: return null
        return (inst.runtime as LuaJScriptRuntime).ambiente()
    }

    private fun cenaComChao(): Scene {
        val scene = Scene()
        val mundo = scene.createRootNick("Mundo")
        val chao = scene.createNick("Chão", mundo, kind = "body.static")
        chao.transform.setLocalPosition(0f, -0.5f, 0f)
        chao.transform.scale.set(14f, 1f, 14f)
        val player = scene.createNick("Herói", mundo, kind = "body.player")
        player.transform.setLocalPosition(0f, 0.5f, 0f)
        player.addTag("heroi")
        val spawn = scene.createNick(null, mundo, kind = "area.spawn")
        spawn.transform.setLocalPosition(0f, 1f, 0f)
        return scene
    }

    private fun runtimeComConsultor(scene: Scene): NickPlayRuntime {
        val runtime = NickPlayRuntime(scene)
        val scripts = NickLuaScripts(scene)
        runtime.scripts = scripts
        scripts.consultorArea = consultorDe(runtime)
        scripts.iniciarTodos()
        return runtime
    }

    private fun consultorDe(runtime: NickPlayRuntime): NickLuaScripts.ConsultorArea =
        object : NickLuaScripts.ConsultorArea {
            override fun boundsMundo(nick: Nick, out: FloatArray) = runtime.boundsMundo(nick, out)
            override fun corposCena(): List<Nick> = runtime.corposSimulados
            override fun respawnJogador(): Boolean = runtime.respawnJogador()
            override fun boundsJogador(out: FloatArray) {
                val p = runtime.player ?: return
                runtime.boundsMundo(p, out)
            }
        }

    private fun novaArea(
        scene: Scene,
        nome: String,
        x: Float, y: Float, z: Float,
        filtroTag: String = "",
        umaVezSo: Boolean = false,
        ligado: Boolean = true
    ): Nick {
        val mundo = scene.roots.first()
        val area = scene.createNick(nome, mundo, kind = "area.volume")
        area.transform.setLocalPosition(x, y, z)
        area.transform.scale.set(2f, 2f, 2f)
        val inst = NickClassRegistry.createInstance("Area")!!
        inst.fields["filtroTag"] = NickValue.StringValue(filtroTag)
        inst.fields["umaVezSo"] = NickValue.BoolValue(umaVezSo)
        inst.fields["ligado"] = NickValue.BoolValue(ligado)
        area.addClassInstance(inst)
        return area
    }

    // ---------- C2.1: on_enter / on_exit / on_stay ----------

    @Test
    fun `area dispara enter exit e stay no jogador`() {
        val scene = cenaComChao()
        novaArea(scene, "Porta", 3f, 0.5f, 0f)
        val player = scene.findNickByNameAnywhere("Herói")!!
        attachScript(
            player,
            """
            entrou = 0
            saiu = 0
            ticks_dentro = 0
            on_enter = function(outro) entrou = entrou + 1 end
            on_exit = function(outro) saiu = saiu + 1 end
            on_stay = function(outro, dt) ticks_dentro = ticks_dentro + 1 end
            """.trimIndent()
        )
        val runtime = runtimeComConsultor(scene)
        for (i in 0 until 40) runtime.step(1f / 60f, 1f, 0f)   // anda até dentro
        for (i in 0 until 3) runtime.step(1f / 60f, 0f, 0f)    // fica dentro
        for (i in 0 until 40) runtime.step(1f / 60f, -1f, 0f)  // volta para fora
        val scripts = runtime.scripts!!
        val g = globalsDe(scripts, "Herói")!!
        assertEquals(1, g.get("entrou").toint())
        assertEquals(1, g.get("saiu").toint())
        assertTrue("on_stay rodou dentro", g.get("ticks_dentro").toint() >= 3)
    }

    // ---------- C2.1: filtro por tag ----------

    @Test
    fun `filtro por tag aceita com a tag e bloqueia sem`() {
        val scene = cenaComChao()
        val areaComTag = novaArea(scene, "SóHerói", 3f, 0.5f, 0f, filtroTag = "heroi")
        val areaSemTag = novaArea(scene, "SóInimigo", 3f, 2.5f, 0f, filtroTag = "inimigo")
        attachScript(areaComTag, "entrou = 0\non_enter = function(outro) entrou = entrou + 1 end")
        attachScript(areaSemTag, "entrou = 0\non_enter = function(outro) entrou = entrou + 1 end")
        val runtime = runtimeComConsultor(scene)
        for (i in 0 until 40) runtime.step(1f / 60f, 1f, 0f)
        val scripts = runtime.scripts!!
        assertEquals(1, globalsDe(scripts, "SóHerói")!!.get("entrou").toint())
        assertEquals(0, globalsDe(scripts, "SóInimigo")!!.get("entrou").toint())
    }

    // ---------- C2.1: uma vez só + ligado ----------

    @Test
    fun `uma vez so desarma depois da primeira entrada`() {
        val scene = cenaComChao()
        val area = novaArea(scene, "Tesouro", 3f, 0.5f, 0f, umaVezSo = true)
        attachScript(area, "entrou = 0\non_enter = function(outro) entrou = entrou + 1 end")
        val runtime = runtimeComConsultor(scene)
        for (i in 0 until 40) runtime.step(1f / 60f, 1f, 0f)
        for (i in 0 until 40) runtime.step(1f / 60f, -1f, 0f)
        for (i in 0 until 40) runtime.step(1f / 60f, 1f, 0f)
        assertEquals("uma vez só: só a 1ª entrada dispara", 1, globalsDe(runtime.scripts!!, "Tesouro")!!.get("entrou").toint())
    }

    @Test
    fun `area desligada nao dispara`() {
        val scene = cenaComChao()
        val area = novaArea(scene, "Apagada", 3f, 0.5f, 0f, ligado = false)
        attachScript(area, "entrou = 0\non_enter = function(outro) entrou = entrou + 1 end")
        val runtime = runtimeComConsultor(scene)
        for (i in 0 until 40) runtime.step(1f / 60f, 1f, 0f)
        assertEquals(0, globalsDe(runtime.scripts!!, "Apagada")!!.get("entrou").toint())
    }

    // ---------- C2.1: area.bodies() e area.contains(nick) ----------

    @Test
    fun `bodies e contains veem quem está dentro do volume`() {
        val scene = cenaComChao()
        novaArea(scene, "Zona", 3f, 0.5f, 0f)
        val dentro = scene.createNick("Caixa", scene.roots.first(), kind = "body.rigid")
        dentro.transform.setLocalPosition(3f, 0.5f, 0f)
        val area = scene.findNickByNameAnywhere("Zona")!!
        attachScript(
            area,
            """
            qtd = 0
            tem_jogador = false
            on_start = function()
              local corpos = nick.bodies()
              qtd = #corpos
              local jogador = scene.find("Herói")
              tem_jogador = nick.contains(jogador)
            end
            """.trimIndent()
        )
        runtimeComConsultor(scene)
        val scripts = runtimeScriptsDe(scene)
        val g = globalsDe(scripts, "Zona")!!
        assertTrue("a caixa entra em bodies()", g.get("qtd").toint() >= 1)
        assertFalse("o jogador está FORA da zona", g.get("tem_jogador").toboolean())
    }

    private fun runtimeScriptsDe(scene: Scene): NickLuaScripts {
        val runtime = NickPlayRuntime(scene)
        val scripts = NickLuaScripts(scene)
        runtime.scripts = scripts
        scripts.consultorArea = consultorDe(runtime)
        scripts.iniciarTodos()
        return scripts
    }

    // ---------- C2.3: checkpoint + respawn na queda ----------

    @Test
    fun `queda abaixo do limite reaparece no ultimo checkpoint`() {
        val scene = Scene()
        val mundo = scene.createRootNick("Mundo")
        val chao = scene.createNick("Chão", mundo, kind = "body.static")
        chao.transform.setLocalPosition(0f, -0.5f, 0f)
        chao.transform.scale.set(14f, 1f, 14f) // x ∈ [-7, 7]
        val player = scene.createNick("Herói", mundo, kind = "body.player")
        player.transform.setLocalPosition(0f, 0.5f, 0f)
        val spawn = scene.createNick(null, mundo, kind = "area.spawn")
        spawn.transform.setLocalPosition(0f, 5f, 0f)
        val checkpoint = scene.createNick("Checkpoint 1", mundo, kind = "area.checkpoint")
        checkpoint.transform.setLocalPosition(5f, 0.5f, 0f)
        checkpoint.transform.scale.set(2f, 2f, 2f)

        val runtime = NickPlayRuntime(scene)
        val p = runtime.player!!
        // anda até o checkpoint (x=5±1): ~5 m a 4 m/s ≈ 75 ticks
        for (i in 0 until 80) runtime.step(1f / 60f, 1f, 0f)
        val xNoCheckpoint = p.transform.worldMatrix.m[12]
        assertTrue("passou pelo checkpoint (x=$xNoCheckpoint)", xNoCheckpoint > 4f)
        // sai pela borda do chão (x > 7) e CAI abaixo do limite (0 − 10 = −10 m)
        for (i in 0 until 50) runtime.step(1f / 60f, 1f, 0f)
        var voltou = false
        for (i in 0 until 1200) {
            runtime.step(1f / 60f, 0f, 0f)
            val x = p.transform.worldMatrix.m[12]
            val y = p.transform.worldMatrix.m[13]
            if (x > 4f && x < 6f && y > 0f) voltou = true
        }
        assertTrue("respawn no checkpoint aconteceu", voltou)
    }

    @Test
    fun `player respawn volta ao spawn`() {
        val scene = cenaComChao()
        val player = scene.findNickByNameAnywhere("Herói")!!
        attachScript(
            player,
            """
            on_start = function()
              nick.set_position(5, 0.5, 0)
              nick.respawn()
            end
            """.trimIndent()
        )
        val runtime = runtimeComConsultor(scene)
        val x = player.transform.worldMatrix.m[12]
        assertTrue("voltou ao surgimento (x=$x)", x < 1f)
    }

    // ---------- C2.2: signal emit/on ----------

    @Test
    fun `signal on criado antes do emit recebe`() {
        val scene = Scene()
        val receptor = scene.createRootNick("Receptor")
        val emissor = scene.createRootNick("Emissor")
        attachScript(
            receptor,
            """
            ouviu = false
            valor = 0
            signal.on("abrir_porta", function(d) ouviu = true; valor = d.valor end)
            """.trimIndent()
        )
        attachScript(
            emissor,
            """
            on_start = function()
              signal.emit("abrir_porta", { valor = 7 })
            end
            """.trimIndent()
        )
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        val g = globalsDe(scripts, "Receptor")!!
        assertTrue(g.get("ouviu").toboolean())
        assertEquals(7, g.get("valor").toint())
    }

    // ---------- C2.5: template com classe Area ----------

    @Test
    fun `moedas do template tem a classe Area umaVezSo e script on_enter`() {
        val scene = Scene()
        com.kenkern.engine.projects.ProjectManager.montarTemplateColetarMoedas(scene)
        var comArea = 0
        var comEnter = 0
        for (raiz in scene.roots) {
            for (moeda in raiz.children) {
                if (moeda.name?.startsWith("Moeda") == true) {
                    if (moeda.findClassInstance("Area") != null) comArea++
                    val fonte = moeda.findClassInstance("Script")?.getString("fonte", "") ?: ""
                    if (fonte.contains("on_enter")) comEnter++
                }
            }
        }
        assertEquals(3, comArea)
        assertEquals(3, comEnter)
    }
}
