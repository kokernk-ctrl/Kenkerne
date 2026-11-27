package com.kenkern.engine.ui.screens

import com.kenkern.engine.ui.editor.TrechosVerificar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 (v0.1.29) — "Verificar" NÃO ACEITA código quebrado (REPRODUZIR ANTES DE
 * CORRIGIR). Evidência do aparelho (checagem "editor"): "verificar: código
 * quebrado passou". CONTRATO: [verificarLua] usa o COMPILADOR do LuaJ
 * (LuaC.instance.compile — nada executa), devolve (linha, mensagem) para CADA
 * trecho quebrado e null para os VÁLIDOS. Fonte única dos trechos:
 * [TrechosVerificar] (a checagem "editor" lê do MESMO objeto — sem cópia).
 */
class R2VerificarLuaTest {

    @Test
    fun `cada trecho QUEBRADO devolve linha e mensagem de erro`() {
        for ((nome, codigo) in TrechosVerificar.QUEBRADOS) {
            val erro = verificarLua(codigo)
            assertNotNull("'$nome' passou no Verificar — código quebrado ACEITO", erro)
            assertTrue("'$nome': linha inválida (${erro?.first})", (erro?.first ?: 0) >= 1)
            assertTrue("'$nome': mensagem vazia", !erro?.second.isNullOrBlank())
        }
    }

    @Test
    fun `cada trecho VÁLIDO devolve null (compila sem erros)`() {
        for ((nome, codigo) in TrechosVerificar.VALIDOS) {
            val erro = verificarLua(codigo)
            assertNull("'$nome' foi rejeitado com ${erro?.first}: ${erro?.second}", erro)
        }
    }

    @Test
    fun `linha do erro aponta a linha REAL do defeito`() {
        // string aberta na linha 2 (não na 1): o compilador para na linha 2.
        val erro = verificarLua("local ok = 1\nlocal s = \"sem fechar\nlocal fim = 2\n")
        assertNotNull(erro)
        assertTrue("linha do erro (${erro?.first}) deve ser ≥ 2", (erro?.first ?: 0) >= 2)
    }

    @Test
    fun `a tarefa exige 5 quebrados e 3 válidos (contrato TrechosVerificar)`() {
        assertEquals(5, TrechosVerificar.QUEBRADOS.size)
        assertEquals(3, TrechosVerificar.VALIDOS.size)
    }
}
