package com.kenkern.engine.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 (v0.1.18) — Diagnóstico NUNCA PODE TRAVAR: teste JVM da máquina de
 * estados da [Diagnostico.Checagem] (mesmo código usado na tela) e do
 * contrato do timeout por checagem.
 *
 * CAUSA RAIZ do "ETC2 nativo: executando…" no aparelho: Diagnostico.executar
 * era BLOQUEANTE numa única thread de fundo, SEM timeout e SEM botão cancelar;
 * a checagem "etc2-nativo" rodava o ENCODER KOTLIN em 1024² (dezenas de
 * segundos num aparelho médio) + 2 decodificações + PSNR. Agora: executar é
 * SUSPENSE, cada checagem com withTimeout(30 s) + runInterruptible, tempo na
 * tela, botão cancelar e comparação PSNR em 256² (a medição da meta B5
 * permanece em 1024², só no encoder NATIVO).
 */
class DiagnosticoChecagemTest {

    private fun nova(): Diagnostico.Checagem = Diagnostico.Checagem("nativo", "Núcleo nativo C++")

    @Test
    fun checagemNascePendenteSemTempo() {
        val c = nova()
        assertEquals(Diagnostico.Status.PENDENTE, c.status)
        assertEquals("—", c.detalhe)
        assertTrue("tempo começa não medido (-1)", c.tempoMs < 0)
    }

    @Test
    fun cicloRodandoAprovadoRegistraTempo() {
        val c = nova()
        c.rodando()
        assertEquals(Diagnostico.Status.RODANDO, c.status)
        assertEquals("executando…", c.detalhe)
        c.registrarTempo(432)
        c.aprovado("funcionou")
        assertEquals(Diagnostico.Status.APROVADO, c.status)
        assertEquals("funcionou", c.detalhe)
        assertEquals(432L, c.tempoMs)
    }

    @Test
    fun falhaTemDetalheProprioETempo() {
        val c = nova()
        c.rodando()
        c.registrarTempo(30_012)
        c.falhou("tempo limite de 30 s — checagem interrompida")
        assertEquals(Diagnostico.Status.FALHOU, c.status)
        assertTrue(c.detalhe.contains("tempo limite"))
        assertEquals(30_012L, c.tempoMs)
    }

    @Test
    fun canceladaEEstadoDistintoDeFalha() {
        val c = nova()
        c.rodando()
        c.cancelada("cancelada pelo usuário")
        assertEquals(Diagnostico.Status.CANCELADA, c.status)
        // CANCELADA não é FALHOU: a rodada interrompida não conta como erro
        // de engine — o veredito da tela distingue os dois.
        assertTrue(c.status != Diagnostico.Status.FALHOU)
    }

    @Test
    fun timeoutPorChecagemE30SegundosFixado() {
        // Contrato do E2: timeout de 30 s POR CHECAGEM (ninguém pode travar a rodada).
        assertEquals(30_000L, Diagnostico.TIMEOUT_CHECAGEM_MS)
    }
}
