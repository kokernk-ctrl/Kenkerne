package com.kenkern.engine.ui.jogo

import com.kenkern.engine.engine.PlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 (v0.1.25) — REPRODUÇÃO E REGRESSÃO do toque preso na Janela de Jogo.
 *
 * O teste nasceu VERMELHO contra o comportamento da v0.1.24: a SurfaceView
 * consumia TODO toque em Play (KenkernSurfaceView.kt:541-542
 * `if (requestedPlayState != PlayState.STOPPED) return true`) e o roteamento
 * não existia — Pausar/Sair não recebiam nada e o usuário ficava preso.
 *
 * As regiões abaixo replicam a GEOMETRIA REAL dos botões da janela de jogo
 * (Row no topo central, alvos de toque mínimo 48dp) numa View de 800x360dp:
 * Pausar x=[310,430] y=[8,60]; Sair x=[440,520] y=[8,60]; Forçar saída
 * (watchdog) x=[280,520] y=[70,122] (faixa abaixo, só em alerta).
 */
class RoteadorToqueJogoTest {

    private fun regioesPadrao(): List<RegiaoBotao> = listOf(
        RegiaoBotao(AlvoToque.BOTAO_PAUSAR, 310f, 8f, 430f, 60f),
        RegiaoBotao(AlvoToque.BOTAO_SAIR, 440f, 8f, 520f, 60f)
    )

    private val forcarSaida = RegiaoBotao(AlvoToque.BOTAO_FORCAR, 280f, 70f, 520f, 122f)

    // ---------- REPRODUÇÃO DO BUG (era vermelho antes da correção) ----------

    @Test
    fun toqueSobrePausarEmPlayVaiParaOBotaoENaoParaCena() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 370f, 30f, regioesPadrao(), false)
        assertEquals(AlvoToque.BOTAO_PAUSAR, d.alvo)
        assertFalse("a cena NUNCA consome toque sobre os botões (P1.2)", d.cenaConsome)
    }

    @Test
    fun toqueSobreSairEmPlayVaiParaOBotao() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 480f, 30f, regioesPadrao(), false)
        assertEquals(AlvoToque.BOTAO_SAIR, d.alvo)
        assertFalse(d.cenaConsome)
    }

    @Test
    fun toqueForaDosBotoesVaiParaACena() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 100f, 200f, regioesPadrao(), false)
        assertEquals(AlvoToque.CENA, d.alvo)
        assertTrue(d.cenaConsome)
    }

    @Test
    fun pausadoOmesmoBotaoContinuaRecebendo() {
        val d = RoteadorToqueJogo.decidir(PlayState.PAUSED, 370f, 30f, regioesPadrao(), false)
        assertEquals(AlvoToque.BOTAO_PAUSAR, d.alvo)
        assertFalse(d.cenaConsome)
    }

    // ---------- WATCHDOG (P1.3) ----------

    @Test
    fun watchdogColocaForcarSaidaNaFrente() {
        val d = RoteadorToqueJogo.decidir(
            PlayState.PLAYING, 400f, 100f,
            regioesPadrao() + forcarSaida, watchdogEmAlerta = true
        )
        assertEquals(AlvoToque.BOTAO_FORCAR, d.alvo)
        assertFalse(d.cenaConsome)
    }

    @Test
    fun semWatchdogARegiaoDeForcarSaidaNaoExiste() {
        // O botão "Forçar saída" só existe em alerta; sem alerta o toque onde
        // ele estaria vai para a cena.
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 400f, 100f, regioesPadrao(), false)
        assertEquals(AlvoToque.CENA, d.alvo)
        assertTrue(d.cenaConsome)
    }

    @Test
    fun limiarDoWatchdogE3Segundos() {
        assertEquals(3000L, RoteadorToqueJogo.WATCHDOG_MS)
    }

    // ---------- BACK (P1.3) — saída garantida ----------

    @Test
    fun backDePlaySempreSai() {
        assertEquals(PlayState.STOPPED, RoteadorToqueJogo.acaoBack(PlayState.PLAYING))
        assertEquals(PlayState.STOPPED, RoteadorToqueJogo.acaoBack(PlayState.PAUSED))
    }

    // ---------- REGIÕES — alvo de toque mínimo 48dp (P1.2) ----------

    @Test
    fun regioesDosBotoesTemPeloMenos48dpDeAltura() {
        // 800x360dp: as regiões do teste simulam 52dp de altura (>= 48dp).
        for (r in regioesPadrao()) {
            val alturaDp = r.baixo - r.topo
            assertTrue("região ${r.alvo} tem ${alturaDp}dp < 48dp", alturaDp >= 48f)
        }
    }

    // ---------- CONTAGEM DE ÚNICOS — zero duplicidade de decisão ----------

    @Test
    fun decisaoParaForaERaUnicaEMotivoNaoVazio() {
        val d1 = RoteadorToqueJogo.decidir(PlayState.PLAYING, 50f, 50f, regioesPadrao(), false)
        val d2 = RoteadorToqueJogo.decidir(PlayState.PLAYING, 50f, 50f, regioesPadrao(), false)
        assertEquals(d1.alvo, d2.alvo)
        assertEquals(d1.cenaConsome, d2.cenaConsome)
        assertFalse(d1.motivo.isEmpty())
    }

    // ---------- L2 (v0.1.28): AÇÃO aplicada — pausa/continua/sai NO TESTE ----------

    @Test
    fun tocarPausarPausaOJogoNoTeste() {
        // Decisão (toque sobre Pausar em PLAYING) + ação = PAUSED: é o fluxo
        // completo do toque simulado da checagem "play-sair".
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 370f, 30f, regioesPadrao(), false)
        assertEquals(PlayState.PAUSED, RoteadorToqueJogo.acao(d.alvo, PlayState.PLAYING))
    }

    @Test
    fun tocarPausarEmPausadoContinuaOJogoNoTeste() {
        val d = RoteadorToqueJogo.decidir(PlayState.PAUSED, 370f, 30f, regioesPadrao(), false)
        assertEquals(PlayState.PLAYING, RoteadorToqueJogo.acao(d.alvo, PlayState.PAUSED))
    }

    @Test
    fun tocarSairSaiOJogoNoTeste() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 480f, 30f, regioesPadrao(), false)
        assertEquals(PlayState.STOPPED, RoteadorToqueJogo.acao(d.alvo, PlayState.PLAYING))
    }

    @Test
    fun forcarSaidaDoWatchdogSaiNoTeste() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 400f, 100f, regioesPadrao() + forcarSaida, true)
        assertEquals(PlayState.STOPPED, RoteadorToqueJogo.acao(d.alvo, PlayState.PLAYING))
    }

    @Test
    fun toqueNaCenaNaoMudaOEstado() {
        val d = RoteadorToqueJogo.decidir(PlayState.PLAYING, 100f, 200f, regioesPadrao(), false)
        assertEquals(PlayState.PLAYING, RoteadorToqueJogo.acao(d.alvo, PlayState.PLAYING))
    }

    @Test
    fun nenhumBotaoDoEditorEAlvoPossivel() {
        // Onda 0: Focar/Grade (botões do EDITOR) não podem interceptar o toque
        // da janela de jogo — o enum só expressa controles do jogo; a UI os
        // compõe FORA do jogo (por estado).
        assertEquals(
            setOf(AlvoToque.BOTAO_PAUSAR, AlvoToque.BOTAO_SAIR, AlvoToque.BOTAO_FORCAR, AlvoToque.CENA),
            AlvoToque.values().toSet()
        )
    }

    // ---------- Onda 0 (v0.1.28): overlay de toque por 3 s (ETAPA A) ----------

    @Test
    fun overlayDeToqueAparecePor3sAposOCadaToque() {
        val t = 100_000L
        assertTrue("no instante do toque: visível", RoteadorToqueJogo.overlayDeToqueVisivel(t, t))
        assertTrue("aos 2999 ms: visível", RoteadorToqueJogo.overlayDeToqueVisivel(t, t + 2999))
        assertFalse("aos 3001 ms: some", RoteadorToqueJogo.overlayDeToqueVisivel(t, t + 3001))
    }

    @Test
    fun overlayDeToqueNuncaApareceSemToque() {
        assertFalse("antes do 1º toque (0) o overlay não existe", RoteadorToqueJogo.overlayDeToqueVisivel(0L, 50_000L))
    }

    @Test
    fun janelaDoOverlayE3Segundos() {
        assertEquals(3000L, RoteadorToqueJogo.JANELA_OVERLAY_MS)
    }
}
