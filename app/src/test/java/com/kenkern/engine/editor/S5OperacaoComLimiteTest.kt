package com.kenkern.engine.editor

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S5 (v0.1.24) — INDICADOR GIRANDO INFINITO: toda operação assíncrona do
 * Hub/editor tem limite de 15 s; bloco PENDURADO vira TempoEsgotado com a
 * OPERAÇÃO e a ETAPA corrente como causa (mensagem visível + log), nunca
 * um "Carregando…" eterno com o Hub todo desabilitado.
 *
 * REPRODUÇÃO: antes da correção, executar() rodava SEM timeout — um bloco
 * pendurado devolvia Sucesso (tarde demais) e isLoading ficava true para
 * sempre (HomeScreen "Carregando…", botões desabilitados).
 */
class S5OperacaoComLimiteTest {

    @Test
    fun `bloco pendurado vira TempoEsgotado com operacao e etapa como causa`() = runBlocking {
        val resultado = OperacaoComLimite.executar(
            "abrir projeto", limiteMs = 300, etapa = { "lendo cena" }
        ) {
            delay(60_000) // trava (I/O pendurado)
            "nunca chega"
        }
        assertTrue("deve ser TempoEsgotado (é isto que mata o spinner eterno): $resultado", resultado is OperacaoComLimite.Resultado.TempoEsgotado)
        val t = resultado as OperacaoComLimite.Resultado.TempoEsgotado
        assertEquals("abrir projeto", t.operacao)
        assertEquals("lendo cena", t.etapa)
        assertEquals(300L, t.limiteMs)
    }

    @Test
    fun `bloco normal devolve Sucesso com o valor`() = runBlocking {
        val resultado = OperacaoComLimite.executar("criar projeto", limiteMs = 5_000) {
            ProjectoSimples("Kenkern")
        }
        assertTrue(resultado is OperacaoComLimite.Resultado.Sucesso)
        assertEquals("Kenkern", (resultado as OperacaoComLimite.Resultado.Sucesso<ProjectoSimples>).valor.nome)
    }

    @Test
    fun `excecao devolve Erro com a causa`() = runBlocking {
        val resultado = OperacaoComLimite.executar("abrir projeto", limiteMs = 5_000) {
            throw IllegalStateException("cena corrompida")
        }
        assertTrue(resultado is OperacaoComLimite.Resultado.Erro)
        assertEquals("cena corrompida", (resultado as OperacaoComLimite.Resultado.Erro).causa.message)
    }

    @Test
    fun `bloco que devolve null e Sucesso — nao confundir com timeout`() = runBlocking {
        val resultado: OperacaoComLimite.Resultado<String?> = OperacaoComLimite.executar(
            "listar projetos", limiteMs = 5_000
        ) { null }
        assertTrue("null do bloco é Sucesso(null), NÃO timeout", resultado is OperacaoComLimite.Resultado.Sucesso)
    }

    @Test
    fun `limite da tarefa e 15 s`() {
        assertEquals(15_000L, OperacaoComLimite.LIMITE_MS)
    }

    /** Mini-tipo para o teste de Sucesso (sem Android). */
    private data class ProjectoSimples(val nome: String)
}
