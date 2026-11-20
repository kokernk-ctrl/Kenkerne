package com.kenkern.engine.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste JVM do runtime LUA (F3.1) — LuaJ puro, sem Android.
 *
 *  1. ciclo completo: carregar compila SEM rodar, iniciar roda top-level +
 *     on_start, atualizar chama on_update(dt);
 *  2. erro de SINTAXE no carregar retorna mensagem (não lança);
 *  3. erro de RUNTIME no update DESATIVA o script (jogo continua) com motivo;
 *  4. SANDBOX: os/io NÃO existem no ambiente (script de jogo não toca disco);
 *  5. orçamento: on_update pesada demais é desativada por tempo;
 *  6. script sem on_update não é erro;
 *  7. fábrica criar() devolve null em fonte ruim.
 */
class LuaRuntimeTest {

    private fun runtime(fonte: String, orcamento: Long = 4000): LuaJScriptRuntime {
        val rt = LuaJScriptRuntime("teste", orcamento)
        assertNull("fonte válida não deve dar erro de sintaxe", rt.carregar("teste", fonte))
        return rt
    }

    @Test
    fun `ciclo completo - carregar nao roda nada e iniciar roda top-level e on_start`() {
        val rt = LuaJScriptRuntime("teste", 4000)
        assertNull(rt.carregar("teste", """
            marca_topo = 1
            function on_start()
                marca_start = 1
            end
            function on_update(dt)
                if dt > 0 then somado = (somado or 0) + dt end
            end
        """.trimIndent()))

        // carregar NÃO roda o top-level: nada definido ainda
        assertEquals(ScriptRuntime.Estado.PRONTO, rt.estado())

        assertTrue(rt.iniciar())
        assertEquals(ScriptRuntime.Estado.INICIADO, rt.estado())

        // top-level + on_start rodaram (lê pelo ambiente Lua)
        val env = rt.ambiente()!!
        assertEquals(1.0, env.get("marca_topo").todouble(), 1e-9)
        assertEquals(1.0, env.get("marca_start").todouble(), 1e-9)

        // 3 updates acumulam dt
        assertTrue(rt.atualizar(0.5f))
        assertTrue(rt.atualizar(0.25f))
        assertTrue(rt.atualizar(0.25f))
        assertEquals(1.0, env.get("somado").todouble(), 1e-6)
    }

    @Test
    fun `erro de sintaxe retorna mensagem sem lancar`() {
        val rt = LuaJScriptRuntime("quebrado", 4000)
        val erro = rt.carregar("quebrado", "function on_update(dt) -- falta end")
        assertNotNull("erro de sintaxe deve retornar mensagem", erro)
        assertTrue(erro!!.isNotBlank())
        assertEquals(ScriptRuntime.Estado.ERRO, rt.estado())
    }

    @Test
    fun `erro de runtime no update desativa o script - jogo continua`() {
        val rt = runtime("""
            function on_update(dt)
                error("boom em tempo de execucao")
            end
        """.trimIndent())
        assertTrue(rt.iniciar())

        // o primeiro update falha: retorna false e DESATIVA
        assertFalse(rt.atualizar(0.016f))
        assertEquals(ScriptRuntime.Estado.DESATIVADO, rt.estado())
        assertNotNull("motivo do erro deve estar disponível", rt.ultimoErro())
        assertTrue(rt.ultimoErro()!!.contains("boom"))

        // updates seguintes são no-op (false), nunca lançam
        assertFalse(rt.atualizar(0.016f))
        assertFalse(rt.atualizar(0.016f))
    }

    @Test
    fun `sandbox - os e io NAO existem no ambiente`() {
        val rt = runtime("""
            function on_update(dt)
                tem_os = (os ~= nil)
                tem_io = (io ~= nil)
                tem_math = (math ~= nil)
                tem_string = (string ~= nil)
            end
        """.trimIndent())
        assertTrue(rt.iniciar())
        assertTrue(rt.atualizar(0.016f))
        val env = rt.ambiente()!!
        assertEquals("os não deve existir", false, env.get("tem_os").toboolean())
        assertEquals("io não deve existir", false, env.get("tem_io").toboolean())
        assertEquals("math deve existir", true, env.get("tem_math").toboolean())
        assertEquals("string deve existir", true, env.get("tem_string").toboolean())
    }

    @Test
    fun `acesso a os no script vira erro controlado - nao derruba nada`() {
        val rt = runtime("""
            function on_start()
                os.exit(1)
            end
        """.trimIndent())
        // iniciar roda on_start: erro capturado, script desativado, sem exceção
        assertFalse(rt.iniciar())
        assertEquals(ScriptRuntime.Estado.DESATIVADO, rt.estado())
        assertNotNull(rt.ultimoErro())
    }

    @Test
    fun `orcamento - update pesada e desativada por tempo`() {
        // orçamento 0ms: QUALQUER trabalho medido estoura
        val rt = runtime("""
            function on_update(dt)
                local s = 0
                for i = 1, 200000 do s = s + i end
                soma = s
            end
        """.trimIndent(), orcamento = 0)
        assertTrue(rt.iniciar())
        rt.atualizar(0.016f)
        assertEquals(
            "orçamento estourado deve desativar o script",
            ScriptRuntime.Estado.DESATIVADO, rt.estado()
        )
        assertTrue("motivo deve citar o orçamento", rt.ultimoErro()!!.contains("orçamento"))
    }

    @Test
    fun `script sem on_update nao e erro`() {
        val rt = runtime("x = 10\n")
        assertTrue(rt.iniciar())
        assertTrue("sem on_update: atualizar é ok (true)", rt.atualizar(0.016f))
        assertEquals(ScriptRuntime.Estado.INICIADO, rt.estado())
    }

    @Test
    fun `fabrica criar devolve runtime valido ou null com erro`() {
        val bom = ScriptRuntime.criar("bom", "function on_update(dt) end\n")
        assertNotNull(bom)
        assertTrue(bom!!.iniciar())

        val ruim = ScriptRuntime.criar("ruim", "isto não é lua válida ((")
        assertNull("fonte ruim deve devolver null", ruim)
    }
}
