package com.kenkern.engine.ui.telar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4 (v0.1.29) — CANVAS DO TELAR COM LETTERBOX (TelarAjuste, PURO): o canvas
 * INTEIRO cabe no espaço disponível mantendo a PROPORÇÃO ESCOLHIDA
 * (16:9/18:9/20:9). O print v0.1.28 media ~1,25:1 (escala fixa 0,28 sobre
 * 1920×1080dp = 537×302dp CORTADA pelos painéis de 190+230dp: só ~380dp
 * de largura visível — 380/302 = 1,26).
 */
class R4TelarAjusteTest {

    /** Espaço do alvo 800x360dp: 800 − painéis (190+230) = 380dp de largura. */
    private val espacoW = 380f
    private val espacoH = 280f

    @Test
    fun `o canvas 16 por 9 cabe INTEIRO e preserva a proporção (nunca 1,25 para 1)`() {
        val (lw, ah) = TelarAjuste.dimensoesDe("16:9")
        assertEquals(1920 to 1080, lw to ah)
        val esc = TelarAjuste.escalaDeAjuste(espacoW, espacoH, lw.toFloat(), ah.toFloat())
        val (rw, rh) = TelarAjuste.dimensaoRenderizada(esc, lw.toFloat(), ah.toFloat())
        assertTrue("cabe em largura ($rw > $espacoW)", rw <= espacoW + 0.5f)
        assertTrue("cabe em altura ($rh > $espacoH)", rh <= espacoH + 0.5f)
        assertTrue("proporção preservada", TelarAjuste.proporcaoPreservada(esc, lw.toFloat(), ah.toFloat()))
        // a proporção NUNCA é o ~1,25:1 do print v0.1.28
        assertTrue("razão deveria ser 16/9=1,778 (era ~1,25 no print)", kotlin.math.abs(rw / rh - 16f / 9f) < 1e-3f)
    }

    @Test
    fun `as três proporções (16_9 18_9 20_9) preservam a razão escolhida e cabem`() {
        for ((p, razao) in listOf("16:9" to 16f / 9f, "18:9" to 18f / 9f, "20:9" to 20f / 9f)) {
            val (lw, ah) = TelarAjuste.dimensoesDe(p)
            val esc = TelarAjuste.escalaDeAjuste(espacoW, espacoH, lw.toFloat(), ah.toFloat())
            val (rw, rh) = TelarAjuste.dimensaoRenderizada(esc, lw.toFloat(), ah.toFloat())
            assertTrue("$p cabe inteiro (largura)", rw <= espacoW + 0.5f)
            assertTrue("$p cabe inteiro (altura)", rh <= espacoH + 0.5f)
            assertTrue("$p preserva a razão", kotlin.math.abs(rw / rh - razao) < 1e-3f)
        }
    }

    @Test
    fun `espaço inválido devolve escala zero (não desenha nada)`() {
        assertEquals(0f, TelarAjuste.escalaDeAjuste(0f, 100f, 1920f, 1080f), 0f)
        assertEquals(0f, TelarAjuste.escalaDeAjuste(100f, 0f, 1920f, 1080f), 0f)
        assertEquals(0f, TelarAjuste.escalaDeAjuste(380f, 280f, 0f, 1080f), 0f)
    }

    @Test
    fun `letterbox usa o lado MENOR — altura sobrando nas razões largas`() {
        // 16:9 em 380×280: a escala é da ALTURA sobrando? Não — 380/1920=0,198,
        // 280/1080=0,259 → a LARGURA limita (0,198): altura renderizada 213,3
        // de 280 disponíveis — margens (letterbox) em cima/embaixo.
        val esc = TelarAjuste.escalaDeAjuste(380f, 280f, 1920f, 1080f)
        assertEquals(0.1979f, esc, 1e-3f)
        val (rw, rh) = TelarAjuste.dimensaoRenderizada(esc, 1920f, 1080f)
        assertEquals(380f, rw, 0.5f) // largura exata do espaço
        assertTrue("altura com letterbox ($rh)", rh < 280f && rh > 200f)
    }
}
