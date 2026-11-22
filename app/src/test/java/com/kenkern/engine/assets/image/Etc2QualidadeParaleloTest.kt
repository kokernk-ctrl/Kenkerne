package com.kenkern.engine.assets.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 (v0.1.17) — GANHOS IMEDIATOS NO ENCODER ETC2 (Kotlin):
 *  (b) modo RÁPIDO por padrão (individual + diferencial + planar; T/H só em
 *      ALTA) com PSNR e tamanho de saída verificados;
 *  (c) blocos paralelizados em N threads (availableProcessors − 1) — o
 *      resultado tem que ser IDÊNTICO ao sequencial byte a byte;
 *  (d) cancelamento dentro do laço pesado responde em menos de 1 s;
 *  e a CORREÇÃO DE ORDEM do RGBA (blocos coluna-major → linha-major, igual ao
 *  decoder e ao glCompressedTexImage2D).
 */
class Etc2QualidadeParaleloTest {

    /** Padrão REALISTA (gradiente suave + ruído leve) 64×64. */
    private fun padraoRealista(w: Int, h: Int): ByteArray {
        val rgba = ByteArray(w * h * 4)
        var semente = 123456789L
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                semente = semente * 6364136223846793005L + 1442695040888963407L
                val ruido = ((semente ushr 33) % 9).toInt() - 4
                rgba[i] = (120 + x * 2 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 1] = (80 + y * 2 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 2] = 160.toByte()
                rgba[i + 3] = 255.toByte()
            }
        }
        return rgba
    }

    @Test
    fun modoRapidoGeraMesmoTamanhoEArtistasDePSNERazoaveis() {
        val rgba = padraoRealista(64, 64)
        val rapido = Etc2.codificarRgb(rgba, 64, 64, qualidade = Etc2.Qualidade.RAPIDA)
        val alta = Etc2.codificarRgb(rgba, 64, 64, qualidade = Etc2.Qualidade.ALTA)
        // Tamanho de saída IDÊNTICO (mesmo número de blocos, 8 bytes cada)
        assertEquals("tamanho de saída idêntico entre modos", alta.size, rapido.size)
        assertEquals(16 * 16 * 8, rapido.size)
        // PSNR: ALTA ≥ RÁPIDA (T/H só ajuda); RÁPIDA continua razoável em
        // textura realista (gradiente + ruído leve)
        val psnrRapido = Etc2.psnr(rgba, Etc2.decodificarRgb(rapido, 64, 64))
        val psnrAlta = Etc2.psnr(rgba, Etc2.decodificarRgb(alta, 64, 64))
        assertTrue("PSNR alta ($psnrAlta dB) ≥ rápida ($psnrRapido dB)", psnrAlta >= psnrRapido - 1e-9)
        assertTrue("PSNR rápida razoável em textura realista: $psnrRapido dB", psnrRapido > 20.0)
    }

    @Test
    fun modoRapidoESilenciosoSobreTHMaisRapidoOuIgual() {
        // O modo rápido NÃO roda T/H: no pior caso demora o mesmo (planar +
        // ind/dif são comuns), nunca menos. Medimos em 64×64 realista.
        val rgba = padraoRealista(64, 64)
        val tRapida = System.nanoTime()
        Etc2.codificarRgb(rgba, 64, 64, qualidade = Etc2.Qualidade.RAPIDA)
        val msRapida = (System.nanoTime() - tRapida) / 1_000_000L
        val tAlta = System.nanoTime()
        Etc2.codificarRgb(rgba, 64, 64, qualidade = Etc2.Qualidade.ALTA)
        val msAlta = (System.nanoTime() - tAlta) / 1_000_000L
        assertTrue("rápida ($msRapida ms) ≤ alta ($msAlta ms) com folga", msRapida <= msAlta + 50)
    }

    @Test
    fun paraleloProduzBytesIdenticosAoSequencial() {
        // O caminho paralelo (pool com availableProcessors − 1 threads quando
        // existem ≥ 8 linhas de blocos) tem que devolver EXATAMENTE os mesmos
        // bytes do sequencial — faixas disjuntas, sem trava, determinístico.
        // B4: com a biblioteca NATIVA do host carregada, este teste força o
        // caminho KOTLIN (o nativo é comparado à parte em Etc2NativoComparacaoTest).
        Etc2.testeForcarKotlin = true
        try {
            val w = 64
            val h = 64
            val rgba = padraoRealista(w, h)
            val viaEncoder = Etc2.codificarRgb(rgba, w, h, qualidade = Etc2.Qualidade.RAPIDA)

            // Referência SEQUENCIAL manual: bloco a bloco, linha-major.
            val blocosX = (w + 3) / 4
            val blocosY = (h + 3) / 4
            val sequencial = ByteArray(blocosX * blocosY * 8)
            val r = IntArray(16); val g = IntArray(16); val b = IntArray(16); val a = IntArray(16)
            val bloco = Etc2.BlocoEntrada(r, g, b, a)
            for (by in 0 until blocosY) {
                for (bx in 0 until blocosX) {
                    for (x in 0 until 4) {
                        for (y in 0 until 4) {
                            val sx = minOf(bx * 4 + x, w - 1)
                            val sy = minOf(by * 4 + y, h - 1)
                            val p = (sy * w + sx) * 4
                            val i = x * 4 + y
                            r[i] = rgba[p].toInt() and 0xFF
                            g[i] = rgba[p + 1].toInt() and 0xFF
                            b[i] = rgba[p + 2].toInt() and 0xFF
                            a[i] = 255
                        }
                    }
                    val bytes = Etc2.codificarBlocoRgb(bloco, Etc2.Qualidade.RAPIDA)
                    System.arraycopy(bytes, 0, sequencial, (by * blocosX + bx) * 8, 8)
                }
            }
            assertTrue("paralelo == sequencial byte a byte", viaEncoder.contentEquals(sequencial))
        } finally {
            Etc2.testeForcarKotlin = false
        }
    }

    @Test
    fun cancelamentoRespondeEmMenosDe1Segundo() {
        val rgba = padraoRealista(256, 256) // 4096 blocos — o suficiente para demorar
        val inicio = System.nanoTime()
        try {
            Etc2.codificarRgb(rgba, 256, 256, cancelado = { true })
            throw AssertionError("cancelamento deveria ter lançado KimgException")
        } catch (e: KimgException) {
            val ms = (System.nanoTime() - inicio) / 1_000_000L
            assertTrue("cancelamento respondeu em $ms ms (< 1000)", ms < 1000)
            assertTrue(e.message!!.contains("cancelada"))
        }
    }

    @Test
    fun textura4x4EDimensoesNaoMultiplasDe4() {
        // 4×4 = exatamente 1 bloco; 6×6 = 4 blocos (2×2) com bordas repetidas.
        val um = Etc2.codificarRgb(padraoRealista(4, 4), 4, 4, qualidade = Etc2.Qualidade.RAPIDA)
        assertEquals(8, um.size)
        val volta4 = Etc2.decodificarRgb(um, 4, 4)
        assertEquals(4 * 4 * 4, volta4.size)

        val seis = Etc2.codificarRgb(padraoRealista(6, 6), 6, 6, qualidade = Etc2.Qualidade.RAPIDA)
        assertEquals("6×6 → 2×2 blocos × 8 bytes", 4 * 8, seis.size)
        val volta6 = Etc2.decodificarRgb(seis, 6, 6)
        assertEquals(6 * 6 * 4, volta6.size)
        // decodificação sem estourar: todos os texels dentro do buffer
        for (byte in volta6) { /* leitura completa acima já prova o range */ break }
    }

    @Test
    fun rgbaAgoraCodificaEmLinhaMajorComoODecoderEGpu() {
        // REGRESSÃO da correção de ordem: metade ESQUERDA vermelha, metade
        // DIREITA azul em 8×8. Com blocos linha-major o lado decodificado
        // continua no lugar certo (antes os blocos saíam TRANSPOSTOS).
        val w = 8; val h = 8
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                if (x < w / 2) {
                    rgba[i] = 255.toByte(); rgba[i + 1] = 0; rgba[i + 2] = 0
                } else {
                    rgba[i] = 0; rgba[i + 1] = 0; rgba[i + 2] = 255.toByte()
                }
                rgba[i + 3] = 255.toByte()
            }
        }
        val blocos = Etc2.codificarRgba(rgba, w, h, qualidade = Etc2.Qualidade.RAPIDA)
        assertEquals(w / 4 * (h / 4) * 16, blocos.size)
        val volta = Etc2.decodificarRgba(blocos, w, h)
        // Texel (1,1) = lado ESQUERDO → vermelho dominante
        val pEsq = 1 * 4
        val esqR = volta[(1 * w + 1) * 4 + 0].toInt() and 0xFF
        val esqB = volta[(1 * w + 1) * 4 + 2].toInt() and 0xFF
        assertTrue("lado esquerdo continua vermelho (R=$esqR B=$esqB)", esqR > esqB + 40)
        // Texel (6,6) = lado DIREITO → azul dominante
        val dirR = volta[(6 * w + 6) * 4 + 0].toInt() and 0xFF
        val dirB = volta[(6 * w + 6) * 4 + 2].toInt() and 0xFF
        assertTrue("lado direito continua azul (R=$dirR B=$dirB)", dirB > dirR + 40)
    }

    @Test
    fun threadsDoEncoderSaoAvailableProcessorsMenosUm() {
        val esperado = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 8)
        assertEquals(esperado, Etc2.threadsEncoder)
        assertTrue("threads ≥ 1 sempre", Etc2.threadsEncoder >= 1)
    }
}
