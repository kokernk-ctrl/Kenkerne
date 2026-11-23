package com.kenkern.engine.engine

import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.script.ScriptRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M1.1 (v0.1.21) — LUA LIGADO AOS NICKS: ponte mínima da LUA_API.md sobre
 * Nicks reais (self/transform/find/destroy/spawn/tempo/log/eventos), classe
 * "Script" com variáveis públicas (params), erro que NUNCA derruba o jogo e
 * LIMITE DE INSTRUÇÕES (while true do end não trava mais o Play).
 */
class NickLuaScriptsTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    private fun nickComScript(
        scene: Scene,
        fonte: String,
        extra: Pair<String, NickValue>? = null,
        nome: String = "ComScript"
    ): com.kenkern.engine.core.Nick {
        val nick = scene.createRootNick(nome)
        nick.kind = "nick.empty"
        val inst = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        inst.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(fonte)
        if (extra != null) inst.fields[extra.first] = extra.second
        nick.addClassInstance(inst)
        return nick
    }

    @Test
    fun `script move o nick pelo transform`() {
        val scene = Scene()
        val nick = nickComScript(
            scene,
            """
            velocidade = params.velocidade or 2.0
            on_update = function(dt)
              local p = nick.position()
              nick.set_position(p.x + velocidade * dt, p.y, p.z)
            end
            """.trimIndent(),
            "velocidade" to NickValue.FloatValue(3f)
        )
        nick.transform.position.set(0f, 1f, 0f)
        scene.updateWorldTransforms()
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        assertEquals("1 script iniciado", 1, scripts.total)
        scripts.atualizar(0.5f)
        assertEquals("x andou velocidade × dt", 1.5f, nick.transform.position.x, 1e-3f)
        assertEquals(1f, nick.transform.position.y, 1e-4f)
    }

    @Test
    fun `scene_find devolve handle e set_active funciona`() {
        val scene = Scene()
        val alvo = scene.createRootNick("Cubo 2")
        alvo.kind = "nick.mesh"
        nickComScript(
            scene,
            """
            local outro = scene.find("Cubo 2")
            if outro then outro.set_active(false) end
            """.trimIndent()
        )
        NickLuaScripts(scene).also { it.iniciarTodos() }
        assertFalse("script desativou o outro nick", alvo.active)
    }

    @Test
    fun `scene_spawn cria o prefab simples e o handle funciona`() {
        val scene = Scene()
        nickComScript(
            scene,
            """
            local m = scene.spawn(2, 3, 4)
            m.set_scale(2, 2, 2)
            """.trimIndent()
        )
        NickLuaScripts(scene).also { it.iniciarTodos() }
        val criados = scene.roots.filter { it.kind == "nick.mesh" && it != scene.roots.firstOrNull() }
        val novo = criados.lastOrNull()
        assertNotNull("cubo spawnado", novo)
        assertEquals(2f, novo!!.transform.position.x, 1e-3f)
        assertEquals(3f, novo.transform.position.y, 1e-3f)
        assertEquals(4f, novo.transform.position.z, 1e-3f)
        assertEquals(2f, novo.transform.scale.x, 1e-3f)
    }

    @Test
    fun `nick_destroy tira o nick da cena de play`() {
        val scene = Scene()
        val alvo = scene.createRootNick("Alvo")
        nickComScript(
            scene,
            """
            local a = scene.find("Alvo")
            if a then a.destroy() end
            """.trimIndent()
        )
        NickLuaScripts(scene).also { it.iniciarTodos() }
        assertNull("nick destruído da cópia de Play", scene.findNick(alvo.id))
    }

    @Test
    fun `engine_time cresce com os ticks`() {
        val scene = Scene()
        nickComScript(
            scene,
            """
            tempos = {}
            on_update = function(dt) table.insert(tempos, engine.time()) end
            """.trimIndent()
        )
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        scripts.atualizar(0.25f)
        scripts.atualizar(0.25f)
        val globals = (scripts.instanciaDe("ComScript")?.runtime as? LuaJScriptRuntime)?.ambiente()
        val tabela = globals?.get("tempos")
        assertNotNull(tabela)
        assertTrue("engine.time() chegou a 0,5 s", tabela!!.get(2).todouble() == 0.5)
    }

    @Test
    fun `erro de runtime desativa o script e o jogo continua`() {
        val scene = Scene()
        val bom = nickComScript(
            scene,
            "ok = 0\non_update = function(dt) ok = ok + 1 end",
            null,
            "Bom"
        )
        val ruim = nickComScript(
            scene,
            "on_update = function(dt) erro_x = nil + 1 end",
            null,
            "Ruim"
        )
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        assertEquals(2, scripts.total)
        scripts.atualizar(0.1f) // o ruim falha e DESATIVA; o bom segue
        val rtRuim = scripts.instanciaDe("Ruim")!!.runtime
        assertTrue("desativado com motivo", rtRuim.ultimoErro() != null)
        val globalsBom = (scripts.instanciaDe("Bom")!!.runtime as LuaJScriptRuntime).ambiente()
        assertEquals(1, globalsBom!!.get("ok").toint())
        assertFalse("script com erro foi DESATIVADO", rtRuim.estado() == ScriptRuntime.Estado.INICIADO)
        assertEquals("nick bom continua na cena", bom.active, true)
        assertEquals(ruim.active, true) // o NICK nunca é tocado por erro de script
    }

    @Test
    fun `limite de instruções para o while true sem travar`() {
        val scene = Scene()
        val nick = nickComScript(scene, "while true do end")
        val scripts = NickLuaScripts(scene)
        val rt = ScriptRuntime.criar("teste", "x = 1") as? LuaJScriptRuntime
        assertNotNull(rt)
        rt!!.definirLimiteInstrucoes(2_000)
        rt.carregar("teste", "while true do end")
        val t0 = System.nanoTime()
        val iniciou = rt.iniciar()
        val duracaoMs = (System.nanoTime() - t0) / 1_000_000L
        assertFalse("script de laço infinito foi DESATIVADO pelo hook", iniciou)
        assertTrue("parou em ms, não travou (${duracaoMs}ms)", duracaoMs < 2000)
        assertTrue(
            "mensagem do LIMITE: ${rt.ultimoErro()}",
            (rt.ultimoErro() ?: "").contains("LIMITE DE INSTRUÇÕES")
        )
        // e o nick/script da cena não precisa nem do runtime (cobre o caminho real)
        scripts.iniciarTodos()
        scripts.atualizar(0.016f)
    }

    @Test
    fun `eventos on_collision e scene_on funcionam`() {
        val scene = Scene()
        nickComScript(
            scene,
            """
            colisoes = 0
            sinais = {}
            scene.on("porta", function(v) table.insert(sinais, v) end)
            on_collision = function(outro) colisoes = colisoes + 1 end
            """.trimIndent()
        )
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        val nick = scripts.instanciaDe("ComScript")!!.nick
        scripts.emitirColisao(nick, "Parede")
        scripts.emitirColisao(nick, "Parede")
        scripts.dispararEvento("porta", LuaJScriptsTestLua.valorTexto("abriu"))
        val globals = (scripts.instanciaDe("ComScript")?.runtime as? LuaJScriptRuntime)?.ambiente()
        assertEquals(2, globals!!.get("colisoes").toint())
        assertEquals("abriu", globals.get("sinais").get(1).tojstring())
    }

    @Test
    fun `variaveis publicas da classe chegam como params`() {
        val scene = Scene()
        nickComScript(
            scene,
            """
            lidos = {}
            function on_start()
              table.insert(lidos, params.vida)
              table.insert(lidos, params.nome)
              table.insert(lidos, params.ativo)
            end
            """.trimIndent(),
            null
        )
        val nick = scene.findNickByNameAnywhere("ComScript")!!
        val inst = nick.findClassInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        inst.fields["vida"] = NickValue.FloatValue(3.5f)
        inst.fields["nome"] = NickValue.StringValue("heroi")
        inst.fields["ativo"] = NickValue.BoolValue(true)
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        val globals = (scripts.instanciaDe("ComScript")?.runtime as? LuaJScriptRuntime)?.ambiente()
        val lidos = globals!!.get("lidos")
        assertEquals(3.5, lidos.get(1).todouble(), 1e-6)
        assertEquals("heroi", lidos.get(2).tojstring())
        assertEquals(true, lidos.get(3).toboolean())
    }
}

/** Helper para os testes criarem valores Lua sem importar LuaJ no bloco de teste. */
object LuaJScriptsTestLua {
    fun valorTexto(texto: String): org.luaj.vm2.LuaValue = org.luaj.vm2.LuaValue.valueOf(texto)
}
