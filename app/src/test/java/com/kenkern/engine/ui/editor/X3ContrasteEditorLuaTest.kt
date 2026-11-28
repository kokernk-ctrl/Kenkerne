package com.kenkern.engine.ui.editor

import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernTokenApi
import com.kenkern.engine.ui.theme.KenkernTokenComentario
import com.kenkern.engine.ui.theme.KenkernTokenNumero
import com.kenkern.engine.ui.theme.KenkernTokenPalavra
import com.kenkern.engine.ui.theme.KenkernTokenString
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * X3 (v0.1.31) — TODA cor de token do editor de código tem contraste >= 4,5:1
 * contra o fundo do editor.
 *
 * Reprodução do bug: antes da v0.1.31 o EditorLuaScreen mapeava a cor da API
 * para `KenkernSurfaceHigh` (#2D2D30 — 1,21:1, cor de SUPERFÍCIE usada como
 * cor de TEXTO) e comentários para `KenkernTextLow` (#6E6E6E — 3,27:1). Os
 * identificadores `set_position`, `math.cos`, `on_start` ficavam invisíveis.
 *
 * As cores agora moram em Theme.kt (única fonte de hex) e este teste reprova
 * qualquer regressão.
 */
class X3ContrasteEditorLuaTest {

    private fun argb(c: androidx.compose.ui.graphics.Color): Int {
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

    private fun razao(frente: androidx.compose.ui.graphics.Color, fundo: androidx.compose.ui.graphics.Color): Double {
        val lf = luminancia(argb(frente))
        val lb = luminancia(argb(fundo))
        val claro = maxOf(lf, lb)
        val escuro = minOf(lf, lb)
        return (claro + 0.05) / (escuro + 0.05)
    }

    @Test
    fun `toda cor de token tem contraste minimo 4_5 contra o fundo do editor`() {
        val fundo = KenkernBackground
        val pares = mapOf(
            "palavra-chave" to KenkernTokenPalavra,
            "string" to KenkernTokenString,
            "comentario" to KenkernTokenComentario,
            "numero" to KenkernTokenNumero,
            "api" to KenkernTokenApi
        )
        for ((nome, cor) in pares) {
            val r = razao(cor, fundo)
            assertTrue(
                "token '$nome' tem contraste %.2f:1 (minimo 4,5:1)".format(r),
                r >= 4.5
            )
        }
    }

    @Test
    fun `cores de token usadas pelo realce sao as mesmas do Theme`() {
        // corDoToken é a ÚNICA fonte do mapeamento token->cor do editor:
        // o teste garante que o desenho usa os vals de Theme.kt, não outra cor.
        assertTrue(corDoToken(TokenTipo.PALAVRA_CHAVE) == KenkernTokenPalavra)
        assertTrue(corDoToken(TokenTipo.STRING) == KenkernTokenString)
        assertTrue(corDoToken(TokenTipo.COMENTARIO) == KenkernTokenComentario)
        assertTrue(corDoToken(TokenTipo.NUMERO) == KenkernTokenNumero)
        assertTrue(corDoToken(TokenTipo.API) == KenkernTokenApi)
    }
}
