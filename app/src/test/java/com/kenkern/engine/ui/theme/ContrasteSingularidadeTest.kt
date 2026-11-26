package com.kenkern.engine.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * P2.3 (v0.1.25) — CONTRASTE MÍNIMO 4,5:1 do visual "Singularidade".
 *
 * Calcula a razão de contraste WCAG 2.1 (luminância relativa) dos PARES DE
 * TEXTO usados pelos componentes K* e pelas superfícies do editor. Aros e
 * brilhos cobre são DECORAÇÃO (borda 1dp, overlay de pressão) — não são texto.
 * Os tokens vêm de Theme.kt (a ÚNICA fonte de hex do visual).
 */
class ContrasteSingularidadeTest {

    /** ARGB int do Color do Compose (packing de android.graphics.Color: a 56-63, r 48-55, g 40-47, b 32-39). */
    private fun argb(c: Color): Int {
        val v = c.value
        val a = ((v shr 56) and 0xFFu).toInt()
        val r = ((v shr 48) and 0xFFu).toInt()
        val g = ((v shr 40) and 0xFFu).toInt()
        val b = ((v shr 32) and 0xFFu).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun canal(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminancia(argbInt: Int): Double {
        val r = canal((argbInt shr 16) and 0xFF)
        val g = canal((argbInt shr 8) and 0xFF)
        val b = canal(argbInt and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun razao(frente: Color, fundo: Color): Double {
        val lf = luminancia(argb(frente))
        val lb = luminancia(argb(fundo))
        val claro = maxOf(lf, lb)
        val escuro = minOf(lf, lb)
        return (claro + 0.05) / (escuro + 0.05)
    }

    /** Fundo translúcido (token com alfa) sobreposto ao fundo real da tela. */
    private fun sobrepor(translucido: Color, fundo: Color): Color {
        val alfa = (argb(translucido) shr 24) and 0xFF
        val a = alfa / 255.0
        val tr = (argb(translucido) shr 16) and 0xFF
        val tg = (argb(translucido) shr 8) and 0xFF
        val tb = argb(translucido) and 0xFF
        val fr = (argb(fundo) shr 16) and 0xFF
        val fg = (argb(fundo) shr 8) and 0xFF
        val fb = argb(fundo) and 0xFF
        fun mist(cTrans: Int, cFundo: Int) = (cTrans * a + cFundo * (1 - a)).toInt().coerceIn(0, 255)
        return Color(mist(tr, fr), mist(tg, fg), mist(tb, fb))
    }

    @Test
    fun textoAltoSobreBotaoTranslucidoPassa45() {
        // KenkernSingPreto 72% sobre o fundo escuro real (KenkernBackground)
        val fundoBotao = sobrepor(KenkernSingPreto.copy(alpha = 0.72f), KenkernBackground)
        val r = razao(KenkernTextHigh, fundoBotao)
        assertTrue("TextHigh sobre botão translúcido: %.2f".format(r), r >= 4.5)
    }

    @Test
    fun textoAltoSobrePainelOpaPassa45() {
        val r = razao(KenkernTextHigh, KenkernSurface)
        assertTrue("TextHigh sobre KenkernSurface: %.2f".format(r), r >= 4.5)
    }

    @Test
    fun textoEscuroSobreBotaoPrimarioAmbarPassa45() {
        // onPrimary do esquema = 0xFF1E1E1E sobre o âmbar do botão primário
        val r = razao(Color(0xFF1E1E1E), KenkernAmber)
        assertTrue("texto escuro sobre âmbar primário: %.2f".format(r), r >= 4.5)
    }

    @Test
    fun textoMidSobrePainelOpaPassa45() {
        val r = razao(KenkernTextMid, KenkernSurface)
        assertTrue("TextMid sobre KenkernSurface: %.2f".format(r), r >= 4.5)
    }

    @Test
    fun textoAmbarSobreFundoEscuroPassa45() {
        val r = razao(KenkernAmber, KenkernSingPreto)
        assertTrue("âmbar sobre preto quente: %.2f".format(r), r >= 4.5)
    }

    @Test
    fun cobreClaroSobrePretoQuentePassa45() {
        val r = razao(KenkernSingCobreClaro, KenkernSingPreto)
        assertTrue("cobre claro sobre preto quente: %.2f".format(r), r >= 4.5)
    }
}
