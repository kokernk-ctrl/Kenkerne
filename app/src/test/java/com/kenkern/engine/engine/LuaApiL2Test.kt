package com.kenkern.engine.engine

import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.Diagnostico
import com.kenkern.engine.diag.KenkernLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * L2: Testes de JVM de TODAS as funções da API Lua, catálogo offline,
 * persistência em arquivo/tags no .ck e checagem "lua-api" do Diagnóstico.
 */
class LuaApiL2Test {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setup() {
        KenkernLog.androidLogging = false
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    private fun attachScript(nick: Nick, fonte: String = "", arquivo: String = "") {
        val inst = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        inst.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(fonte)
        inst.fields[NickLuaScripts.CAMPO_ARQUIVO] = NickValue.StringValue(arquivo)
        nick.addClassInstance(inst)
    }

    @Test
    fun `eventos multi-ouvinte emit e on com tabela de dados e off`() {
        val scene = Scene()
        val n = scene.createRootNick("HubEventos")
        attachScript(
            n,
            """
            soma = 0
            ultimo_nome = ""
            local fn2 = function(d) soma = soma + d.valor * 2 end
            on_start = function()
              scene.on("pontos", function(d)
                soma = soma + d.valor
                ultimo_nome = d.quem
              end)
              scene.on("pontos", fn2)
              scene.emit("pontos", { valor = 10, quem = "moeda" })
              scene.off("pontos", fn2)
              scene.emit("pontos", { valor = 5, quem = "bau" })
            end
            """.trimIndent()
        )
        val lua = NickLuaScripts(scene).also { it.iniciarTodos() }
        val g = (lua.instanciaDe("HubEventos")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals(35, g.get("soma").toint())
        assertEquals("bau", g.get("ultimo_nome").tojstring())
    }

    @Test
    fun `timers after every e cancel`() {
        val scene = Scene()
        val n = scene.createRootNick("Relogio")
        attachScript(
            n,
            """
            a, e, cancelado = 0, 0, 0
            on_start = function()
              timer.after(0.25, function() a = a + 1 end)
              local idRep = timer.every(0.10, function() e = e + 1 end)
              local idCancel = timer.every(0.10, function() cancelado = cancelado + 1 end)
              timer.cancel(idCancel)
              timer.after(0.50, function() timer.cancel(idRep) end)
            end
            """.trimIndent()
        )
        val lua = NickLuaScripts(scene).also { it.iniciarTodos() }
        for (i in 0 until 8) lua.atualizar(0.1f)
        val g = (lua.instanciaDe("Relogio")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals("after rodou exatamente 1x", 1, g.get("a").toint())
        assertTrue("every rodou entre 4 e 6x antes do cancel (e=${g.get("e").toint()})", g.get("e").toint() in 4..6)
        assertEquals("timer cancelado nunca disparou", 0, g.get("cancelado").toint())
    }

    @Test
    fun `callbacks de area on_enter on_stay e on_exit`() {
        val scene = Scene()
        val floor = scene.createRootNick("Chao", kind = "body.static")
        floor.transform.setLocalPosition(0f, -0.5f, 0f)
        floor.transform.scale.set(20f, 1f, 20f)

        val player = scene.createRootNick("Player", kind = "body.player")
        player.transform.setLocalPosition(-2f, 0.5f, 0f)
        player.addTag("heroi")

        val area = scene.createRootNick("Zona", kind = "area.volume")
        area.transform.setLocalPosition(0f, 0.5f, 0f)
        area.transform.scale.set(2f, 2f, 2f)
        attachScript(
            area,
            """
            entrou, ficou, saiu, era_heroi = 0, 0, 0, false
            on_enter = function(outro)
              entrou = entrou + 1
              era_heroi = outro.has_tag("heroi")
            end
            on_stay = function(outro, dt)
              ficou = ficou + 1
            end
            on_exit = function(outro)
              saiu = saiu + 1
            end
            """.trimIndent()
        )

        val rt = NickPlayRuntime(scene)
        val lua = NickLuaScripts(scene).also { it.iniciarTodos() }
        rt.scripts = lua

        // Move o player de x = -2 para x = +2 (passando por x = 0 onde está a área) e volta
        for (i in 0 until 30) {
            val dir = if (i < 15) 1f else -1f
            rt.step(0.05f, dir, 0f)
            lua.atualizar(0.05f)
        }

        val g = (lua.instanciaDe("Zona")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals(1, g.get("entrou").toint())
        assertTrue("on_stay chamado varias vezes", g.get("ficou").toint() >= 2)
        assertEquals(1, g.get("saiu").toint())
        assertTrue(g.get("era_heroi").toboolean())
    }

    @Test
    fun `busca por tag e nome e roundtrip de tags no ck`() {
        val scene = Scene()
        val m1 = scene.createRootNick("MoedaA", kind = NickKindRegistry.NICK_MESH)
        m1.addTag("moeda")
        m1.addTag("brilhante")
        val m2 = scene.createRootNick("MoedaB", kind = NickKindRegistry.NICK_MESH)
        m2.addTag("moeda")

        val ckText = CkFormat.writeToString(scene)
        val recarregada = CkFormat.parseToNewScene(ckText, "Tags")!!
        assertEquals(2, recarregada.findAllNicksByTag("moeda").size)
        assertEquals("MoedaA", recarregada.findNickByTagAnywhere("brilhante")?.name)

        val ctrl = recarregada.createRootNick("Ctrl")
        attachScript(
            ctrl,
            """
            total_moedas = 0
            primeira = ""
            on_start = function()
              local lista = scene.find_all_by_tag("moeda")
              total_moedas = #lista
              local um = scene.find_by_tag("brilhante")
              if um then
                primeira = um.name()
                um.remove_tag("brilhante")
                um.add_tag("coletada")
              end
            end
            """.trimIndent()
        )
        val lua = NickLuaScripts(recarregada).also { it.iniciarTodos() }
        val g = (lua.instanciaDe("Ctrl")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals(2, g.get("total_moedas").toint())
        assertEquals("MoedaA", g.get("primeira").tojstring())
        val refM1 = recarregada.findNickByNameAnywhere("MoedaA")!!
        assertFalse(refM1.hasTag("brilhante"))
        assertTrue(refM1.hasTag("coletada"))
    }

    @Test
    fun `spawn_prefab de disco e destroy funcionam`() {
        val projDir = tmp.newFolder("projPrefab")
        val prefabsDir = File(projDir, "prefabs").apply { mkdirs() }
        val prefabScene = Scene("MoedaPrefab")
        val pNick = prefabScene.createRootNick("MoedaPrefab", kind = NickKindRegistry.NICK_MESH)
        pNick.addTag("spawned")
        File(prefabsDir, "moeda.ck").writeText(CkFormat.writeToString(prefabScene))

        val scene = Scene()
        val ctrl = scene.createRootNick("Spawner")
        attachScript(
            ctrl,
            """
            nascido_tag = false
            on_start = function()
              local inst = scene.spawn_prefab("moeda", 4, 2, -1)
              if inst then
                nascido_tag = inst.has_tag("spawned")
                inst.set_name("MoedaViva")
              end
            end
            """.trimIndent()
        )
        NickLuaScripts(scene, projectRoot = projDir).also { it.iniciarTodos() }
        val criado = scene.findNickByNameAnywhere("MoedaViva")
        assertNotNull(criado)
        assertEquals(4f, criado!!.transform.position.x, 1e-4f)
        assertEquals(2f, criado.transform.position.y, 1e-4f)
        assertEquals(-1f, criado.transform.position.z, 1e-4f)
        assertTrue(criado.hasTag("spawned"))
    }

    @Test
    fun `ler e escrever propriedades de classes do Nick e script externo em arquivo`() {
        val projDir = tmp.newFolder("projScripts")
        val scriptsDir = File(projDir, "scripts").apply { mkdirs() }
        File(scriptsDir, "girar.lua").writeText(
            """
            vel_lida = 0
            on_start = function()
              vel_lida = nick.get("Spin", "speed")
              nick.set("Spin", "speed", vel_lida + 35)
            end
            """.trimIndent()
        )

        val scene = Scene()
        val cubo = scene.createRootNick("Cubo", kind = NickKindRegistry.NICK_MESH)
        val spin = NickClassRegistry.createInstance("Spin")!!
        spin.fields["speed"] = NickValue.FloatValue(55f)
        cubo.addClassInstance(spin)
        attachScript(cubo, fonte = "", arquivo = "scripts/girar.lua")

        val lua = NickLuaScripts(scene, projectRoot = projDir).also { it.iniciarTodos() }
        val g = (lua.instanciaDe("Cubo")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals(55f, g.get("vel_lida").todouble().toFloat(), 1e-4f)
        assertEquals(90f, (spin.fields["speed"] as NickValue.FloatValue).value, 1e-4f)
    }

    @Test
    fun `input ui storage em arquivo e log funcionam`() {
        val projDir = tmp.newFolder("projSave")
        val saveFile = File(projDir, "saves/gamedata.ck")
        val inputEstado = EstadoInputJogo().apply { definirAcao("interagir", true) }

        val scene = Scene()
        val n = scene.createRootNick("Persist")
        attachScript(
            n,
            """
            interagiu = false
            on_start = function()
              ui.text("fase", "Floresta")
              ui.count("gemas", 4)
              storage.save("perfil", { nome = "Ayla", pontos = ui.get_count("gemas") })
              log("save concluido")
            end
            on_update = function(dt)
              if input.action("interagir") and engine.dt() > 0 then
                interagiu = true
              end
            end
            """.trimIndent()
        )
        val lua = NickLuaScripts(scene, projectRoot = projDir, saveStore = FileGameSaveStore(saveFile)).also {
            it.estadoInput = inputEstado
            it.iniciarTodos()
        }
        lua.atualizar(0.016f)

        assertEquals("Floresta", lua.snapshotUiJogo()["fase"])
        assertEquals("4", lua.snapshotUiJogo()["gemas"])
        assertTrue(lua.logsEmitidos().contains("save concluido"))
        val g = (lua.instanciaDe("Persist")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertTrue(g.get("interagiu").toboolean())

        val scene2 = Scene()
        val n2 = scene2.createRootNick("Loader")
        attachScript(
            n2,
            """
            nome_lido, pts_lido = "", 0
            on_start = function()
              local p = storage.load("perfil", nil)
              if p then
                nome_lido = p.nome
                pts_lido = p.pontos
              end
              storage.delete("perfil")
              existe_apos = storage.has("perfil")
            end
            """.trimIndent()
        )
        val lua2 = NickLuaScripts(scene2, projectRoot = projDir, saveStore = FileGameSaveStore(saveFile)).also {
            it.iniciarTodos()
        }
        val g2 = (lua2.instanciaDe("Loader")?.runtime as LuaJScriptRuntime).ambiente()!!
        assertEquals("Ayla", g2.get("nome_lido").tojstring())
        assertEquals(4, g2.get("pts_lido").toint())
        assertFalse(g2.get("existe_apos").toboolean())
    }

    @Test
    fun `checagem diagnostico lua-api passa na JVM`() {
        val (ok, detalhe) = Diagnostico.avaliarLuaApi()
        assertTrue("checagem lua-api falhou: $detalhe", ok)
    }

    @Test
    fun `catalogo offline cobre todas as categorias e templates`() {
        val categorias = LuaApiCatalog.ENTRIES.map { it.category }.toSet()
        assertTrue(categorias.containsAll(listOf("callback", "nick", "scene", "timer", "engine", "input", "ui", "storage")))
        assertEquals(4, LuaApiCatalog.TEMPLATES.size)
        assertTrue(LuaApiCatalog.buscarAutocompletar("storage").any { it.symbol == "storage.save" })
        assertNull(null)
    }
}
