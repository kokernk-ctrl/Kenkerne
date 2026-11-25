package com.kenkern.engine.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matemática e contrato do fundo do Hub:
 *  - 30 FPS, pausa quando invisível, "reduzir animações" congela em frame estático;
 *  - C1: resolução de 50% para o FBO + upscale, qualidade Alta/Baixa e shader
 *    com lente gravitacional, disco inclinado com Doppler, anel de fótons e paralaxe.
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

    @Test
    fun resolucaoCinquentaPorCentoZeroAlocacao() {
        val out = IntArray(2)
        HubCena2D.resolucaoFundo(1600, 720, out)
        assertEquals(800, out[0])
        assertEquals(360, out[1])

        // Mínimo seguro em telas minúsculas/transição
        HubCena2D.resolucaoFundo(40, 20, out)
        assertEquals(HubCena2D.TAMANHO_MINIMO_FBO, out[0])
        assertEquals(HubCena2D.TAMANHO_MINIMO_FBO, out[1])
    }

    @Test
    fun qualidadeAltaBaixaEShadersContrato() {
        assertEquals(QualidadeFundoHub.ALTA, QualidadeFundoHub.fromCodigo(null))
        assertEquals(QualidadeFundoHub.ALTA, QualidadeFundoHub.fromCodigo("invalido"))
        assertEquals(QualidadeFundoHub.BAIXA, QualidadeFundoHub.fromCodigo("baixa"))
        assertEquals(QualidadeFundoHub.BAIXA, QualidadeFundoHub.ALTA.alternar())
        assertEquals(QualidadeFundoHub.ALTA, QualidadeFundoHub.BAIXA.alternar())
        assertTrue(QualidadeFundoHub.ALTA.passosGeodesicos > QualidadeFundoHub.BAIXA.passosGeodesicos)
        assertTrue(QualidadeFundoHub.ALTA.camadasEstrelas > QualidadeFundoHub.BAIXA.camadasEstrelas)

        val fs = HubBlackHoleShaders.BLACK_HOLE_FRAGMENT_SHADER
        assertTrue("GLSL ES 3.00", fs.contains("#version 300 es"))
        assertTrue("uniform uResolution", fs.contains("uniform vec2 uResolution"))
        assertTrue("uniform uTime", fs.contains("uniform float uTime"))
        assertTrue("uniform uQuality", fs.contains("uniform float uQuality"))
        assertTrue("lente gravitacional geodésica", fs.contains("cross(pos, vel)") && fs.contains("accel"))
        assertTrue("anel de fótons", fs.contains("rPhoton") && fs.contains("photonRing"))
        assertTrue("Doppler assimétrico no disco", fs.contains("doppler") && fs.contains("beaming"))
        assertTrue("upscale bilinear usa uTexture", HubBlackHoleShaders.UPSCALE_FRAGMENT_SHADER.contains("uniform sampler2D uTexture"))
    }
}
