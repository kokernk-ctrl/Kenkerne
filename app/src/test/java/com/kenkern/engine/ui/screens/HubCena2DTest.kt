package com.kenkern.engine.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T0.5 (v0.1.21): matemática PURA da cena 2.5D do Hub (paralaxe + buraco
 * negro sutil) — 30 FPS, pausa quando invisível (sem laço = sem tempo),
 * "reduzir animações" congela o fundo.
 */
class HubCena2DTest {

    @Test
    fun fpsAlvoEIntervaloDe30() {
        assertEquals(30, HubCena2D.FPS_ALVO)
        assertEquals(33_333_333L, HubCena2D.INTERVALO_NS)
        assertTrue("1/30 s ≈ 33,33 ms", HubCena2D.INTERVALO_NS in 33_000_000L..34_000_000L)
    }

    @Test
    fun paralaxeCamadaProfundaAndaMenos() {
        val a = FloatArray(1); val b = FloatArray(1)
        HubCena2D.deslocamentoCamada(2f, 1f, a)
        HubCena2D.deslocamentoCamada(2f, 0.25f, b)
        assertTrue("camada próxima anda 4× a distante", kotlin.math.abs(a[0]) == 4f * kotlin.math.abs(b[0]))
        assertTrue("deriva para trás (negativa)", a[0] < 0f)
        // t=0 → sem deslocamento (fundo estático no primeiro frame)
        HubCena2D.deslocamentoCamada(0f, 1f, a)
        assertEquals(0f, a[0], 1e-6f)
    }

    @Test
    fun orbitaFicaDentroDoRaioEDiscoAchatado() {
        val out = FloatArray(2)
        var f = 0f
        while (f < HubCena2D.ORBITA_SEGUNDOS * 1.5f) {
            HubCena2D.pontoOrbita(f, 0.7f, 1f, out)
            assertTrue("x dentro do raio (${out[0]})", kotlin.math.abs(out[0]) <= 1f + 1e-3f)
            assertTrue("y achatado ≤ raio (${out[1]})", kotlin.math.abs(out[1]) <= 0.62f + 1e-3f)
            f += 0.5f
        }
        // período: t e t+ORBITA devolvem o mesmo ponto
        HubCena2D.pontoOrbita(0f, 0.7f, 1f, out)
        val x0 = out[0]; val y0 = out[1]
        HubCena2D.pontoOrbita(HubCena2D.ORBITA_SEGUNDOS, 0.7f, 1f, out)
        assertEquals(x0, out[0], 1e-3f)
        assertEquals(y0, out[1], 1e-3f)
    }

    @Test
    fun pulsoDoDiscoESutilENuncaApaga() {
        var f = 0f
        while (f < HubCena2D.ORBITA_SEGUNDOS * 2f) {
            val p = HubCena2D.pulsoAcrecao(f)
            assertTrue("pulso no range 0,85–1,0 ($p)", p in 0.849f..1.0001f)
            f += 0.25f
        }
    }

    @Test
    fun reduzirAnimacoesCongelaFundo() {
        assertTrue("escala 0 = animações reduzidas", HubCena2D.animacoesReduzidas(0f))
        assertTrue("escala 0,005 = reduzidas", HubCena2D.animacoesReduzidas(0.005f))
        assertFalse("escala 1 = animações normais", HubCena2D.animacoesReduzidas(1f))
        assertTrue("NaN é tratado como reduzido (fundo estático)", HubCena2D.animacoesReduzidas(Float.NaN))
    }
}
