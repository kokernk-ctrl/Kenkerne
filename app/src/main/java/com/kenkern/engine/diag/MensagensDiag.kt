package com.kenkern.engine.diag

import java.util.Locale

/**
 * R6 (v0.1.29) — MENSAGENS do Diagnóstico com FORMATADORES PREENCHIDOS.
 *
 * CAUSA RAIZ (v0.1.28): o `.format()` era aplicado SÓ ao ÚLTIMO pedaço da
 * concatenação — `"%.1f× · " + "… %.4f…".format(args)` formata o segundo
 * literal e deixa o PRIMEIRO com o "%.1f" CRU na mensagem exibida no
 * aparelho ("Malha nativa", "Botão Importar modelo", ".KIMG" e a de toque
 * do slop). Os builders abaixo formatam a concatenação INTEIRA (entre
 * parênteses) e são PUROS — o teste JVM prova que NENHUM '%' sai literal.
 */
object MensagensDiag {

    /** Checagem "malha-nativa" — aceleração × e erro de LOD preenchidos. */
    fun malhaNativa(
        nativoMs: Long,
        kotlinMs: Long,
        aceleracaoX: Double,
        unicos: Int,
        unicosKotlin: Int,
        lodsNativoTris: Int,
        erroLod: Float,
        lodsKotlin: Int
    ): String = (
        "grade 100×100 (20.000 tris) · nativo ${nativoMs} ms × kotlin ${kotlinMs} ms · %.1f× · " +
            "únicos ${unicos} == ${unicosKotlin} · LOD nativo ${lodsNativoTris} tris (erro %.4f) · LODs kotlin ${lodsKotlin}"
        ).format(Locale.US, aceleracaoX, erroLod)

    /** Checagem "importar" — pico de memória e tempo preenchidos. */
    fun importarModelo(picoMemoriaMb: Float, tempoMs: Long): String = (
        "GLB embutido (4 v, integridade OK, pico %.0f MB, %d ms) · nome único + zip-slip bloqueado · " +
            "Command undo/redo com lixeira OK · glTF com .bin externo via .zip OK"
        ).format(Locale.US, picoMemoriaMb, tempoMs)

    /** Checagem "kimg" — PSNRs preenchidos (resumo/bytes entram prontos). */
    fun kimg(psnrRealistaDb: Double, psnrAdversarialDb: Double, resumo: String, bytesRealista: Int): String = (
        "ida e volta OK · PSNR REALISTA (gradiente + ruído leve) %.1f dB · " +
            "PSNR ADVERSARIAL (alfa alternada por pixel, pior caso do EAC) %.1f dB · ${resumo} · " +
            "truncado e magic quebrado = erros claros (${bytesRealista} bytes)"
        ).format(Locale.US, psnrRealistaDb, psnrAdversarialDb)

    /** Checagem "toque" — densidade e ponto do toque preenchidos. */
    fun toqueNaoClassificou(density: Float, tapX: Float, tapY: Float): String = (
        "TouchController NÃO classificou como tap (tap só até 250 ms e dentro do slop de 12 dp×density=%.1f) — " +
            "toque em (%.1f, %.1f) px da view"
        ).format(Locale.US, density, tapX, tapY)
}
