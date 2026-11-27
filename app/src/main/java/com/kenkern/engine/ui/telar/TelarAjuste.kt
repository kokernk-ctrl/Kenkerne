package com.kenkern.engine.ui.telar

/**
 * R4 (v0.1.29) — AJUSTE DO CANVAS DO TELAR COM LETTERBOX (PURO, testado na
 * JVM; a checagem "layout" usa o MESMO objeto).
 *
 * CAUSA RAIZ do "canvas 16:9 quase quadrado (~1,25:1)" do print v0.1.28: o
 * canvas tinha 1920dp×1080dp com escala FIXA 0,28 (537dp×302dp) — MAIOR que
 * o espaço disponível (~380dp de largura entre os painéis de 190+230dp) — e
 * era CORTADO pelos painéis laterais: o pedaço visível era 380×302 ≈ 1,25:1
 * (quadrado), NUNCA a proporção escolhida. O canvas agora USA a dimensão da
 * PROPORÇÃO ESCOLHIDA (16:9=1920×1080, 18:9=2160×1080, 20:9=2400×1080) e a
 * escala de AJUSTE: min(disponívelW/larguraRef, disponívelH/alturaRef) — o
 * canvas INTEIRO cabe no espaço com letterbox nas margas sobrando.
 */
object TelarAjuste {

    /**
     * Escala de ajuste (letterbox): o maior fator que faz o canvas caber
     * INTEIRO no espaço disponível mantendo a proporção. Zero quando o
     * espaço ou a referência é inválida (não desenha nada).
     */
    fun escalaDeAjuste(
        disponivelW: Float,
        disponivelH: Float,
        larguraRef: Float,
        alturaRef: Float
    ): Float {
        if (disponivelW <= 0f || disponivelH <= 0f) return 0f
        if (larguraRef <= 0f || alturaRef <= 0f) return 0f
        return minOf(disponivelW / larguraRef, disponivelH / alturaRef)
    }

    /**
     * Dimensões renderizadas do canvas com a escala dada (px). Contrato:
     * ambas ≤ disponível (cabe inteiro) e a proporção é a ESCOLHIDA.
     */
    fun dimensaoRenderizada(
        escala: Float,
        larguraRef: Float,
        alturaRef: Float
    ): Pair<Float, Float> = (larguraRef * escala) to (alturaRef * escala)

    /**
     * CONTRATO DE PROPORÇÃO: o canvas renderizado mantém a proporção da
     * referência (larguraRender/alturaRender == larguraRef/alturaRef) —
     * é isto que o print v0.1.28 negava (1,25 ≠ 1,78).
     */
    fun proporcaoPreservada(
        escala: Float,
        larguraRef: Float,
        alturaRef: Float
    ): Boolean {
        if (escala <= 0f) return false
        val (w, h) = dimensaoRenderizada(escala, larguraRef, alturaRef)
        if (w <= 0f || h <= 0f) return false
        return kotlin.math.abs(w / h - larguraRef / alturaRef) < 1e-3f
    }

    /** Dimensões de referência da proporção escolhida ("16:9"/"18:9"/"20:9"). */
    fun dimensoesDe(proporcao: String): Pair<Int, Int> = when (proporcao) {
        "18:9" -> 2160 to 1080
        "20:9" -> 2400 to 1080
        else -> 1920 to 1080
    }
}
