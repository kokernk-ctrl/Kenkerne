package com.kenkern.engine.diag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6 (v0.1.29) — MENSAGENS DO DIAGNÓSTICO SEM '%' LITERAL. Evidência do
 * aparelho v0.1.28: as mensagens de "Malha nativa", "Botão Importar
 * modelo", ".KIMG" (e a do toque) exibiam "%.1f", "%.0f" e "%d" CRUS —
 * o .format era aplicado SÓ ao último literal da concatenação. Os builders
 * [MensagensDiag] formatam a concatenação INTEIRA; este teste REPROVA
 * qualquer '%' que escape numa mensagem produzida.
 */
class R6MensagensDiagTest {

    private val mensagens: List<String> = listOf(
        MensagensDiag.malhaNativa(
            nativoMs = 12L, kotlinMs = 48L, aceleracaoX = 4.0,
            unicos = 9, unicosKotlin = 9, lodsNativoTris = 30,
            erroLod = 0.0012f, lodsKotlin = 3
        ),
        MensagensDiag.importarModelo(picoMemoriaMb = 3.7f, tempoMs = 210L),
        MensagensDiag.kimg(
            psnrRealistaDb = 41.3, psnrAdversarialDb = 8.9,
            resumo = "resumo do pipeline", bytesRealista = 128
        ),
        MensagensDiag.toqueNaoClassificou(density = 2.75f, tapX = 120f, tapY = 80f)
    )

    @Test
    fun `nenhuma mensagem do Diagnóstico tem '%' literal (formatadores preenchidos)`() {
        for (msg in mensagens) {
            assertFalse(
                "mensagem com '%' literal (formatador não preenchido): $msg",
                msg.contains("%")
            )
        }
    }

    @Test
    fun `os valores REAIS aparecem nas mensagens (não placeholders)`() {
        val malha = mensagens[0]
        assertTrue("aceleração 4.0× formatada", malha.contains("4.0×"))
        assertTrue("erro de LOD 0.0012 formatado", malha.contains("0.0012"))
        val imp = mensagens[1]
        assertTrue("pico 4 MB (arredondado)", imp.contains("4 MB"))
        assertTrue("tempo 210 ms", imp.contains("210 ms"))
        val kimg = mensagens[2]
        assertTrue("PSNR 41.3 dB", kimg.contains("41.3 dB"))
        assertTrue("resumo entra pronto", kimg.contains("resumo do pipeline"))
        val toque = mensagens[3]
        assertTrue("density 2.8 formatada", toque.contains("2.8"))
        assertTrue("toque (120.0, 80.0)", toque.contains("(120.0, 80.0)"))
    }
}
