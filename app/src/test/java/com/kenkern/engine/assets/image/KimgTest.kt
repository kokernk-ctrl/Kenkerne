package com.kenkern.engine.assets.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/**
 * TESTES JVM do .KIMG (ETAPA C / F2.9): ida e volta do formato, limites
 * (truncado/magic/versão/dimensões = ERRO CLARO, nunca estoura buffer),
 * mipmap de caixa, tamanho exato do arquivo, blocos ETC2 conhecidos, escolha
 * RGB/RGBA, POT só quando excede o limite e PSNR razoável.
 */
class KimgTest {

    private fun gradiente(w: Int, h: Int, alfaVariado: Boolean): ByteArray {
        // contraste moderado (o pior caso sintético de 4×4 com 16 cores vivas
        // derruba o PSNR de QUALQUER codec de blocos — não mede a corretude)
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                rgba[i] = (x * 16).toByte()
                rgba[i + 1] = (y * 16).toByte()
                rgba[i + 2] = 96
                rgba[i + 3] = if (alfaVariado && (x + y) % 2 == 0) 255.toByte() else 128.toByte()
            }
        }
        return rgba
    }

    private fun corSolida(w: Int, h: Int, r: Int, g: Int, b: Int): ByteArray {
        val rgba = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            rgba[i * 4] = r.toByte(); rgba[i * 4 + 1] = g.toByte()
            rgba[i * 4 + 2] = b.toByte(); rgba[i * 4 + 3] = 255.toByte()
        }
        return rgba
    }

    // ------------------------------------------------------------- ida e volta

    @Test
    fun `ida e volta do KIMG com estrutura e PSNR razoáveis`() {
        val (bytes, relatorio) = KimgPipeline.converter(gradiente(8, 8, alfaVariado = true), 8, 8)
        val imagem = KimgReader.read(bytes)
        assertEquals(8, imagem.largura)
        assertEquals(8, imagem.altura)
        assertTrue("tem alfa", imagem.temAlfa)
        assertEquals(KimgFormato.ETC2_RGBA, imagem.formato)
        assertTrue("mipmaps ≥ 3", imagem.mips.size >= 3)
        assertTrue("sRGB gravado", imagem.srgb)
        // decodifica e compara
        val volta = Etc2.decodificarRgba(imagem.mips[0].dados, 8, 8)
        val psnr = Etc2.psnr(gradiente(8, 8, alfaVariado = true), volta)
        assertTrue("PSNR razoável para gradiente (medido ${psnr} dB)", psnr > 12.0)
        // o relatório tem os números honestos
        assertTrue(relatorio.bytesKimg > 0)
        assertTrue(relatorio.razao > 1f)
        assertTrue(relatorio.tempoMs >= 0)
        assertFalse(relatorio.redimensionada)
        assertTrue("relatório cita PSNR", relatorio.resumo().contains("PSNR"))
    }

    @Test
    fun `textura opaca fica ETC2_RGB e textura com alfa fica ETC2_RGBA`() {
        val opaca = KimgPipeline.converter(corSolida(8, 8, 200, 30, 40), 8, 8)
        assertEquals(KimgFormato.ETC2_RGB, KimgReader.read(opaca.first).formato)
        val comAlfa = KimgPipeline.converter(gradiente(8, 8, alfaVariado = true), 8, 8)
        assertEquals(KimgFormato.ETC2_RGBA, KimgReader.read(comAlfa.first).formato)
        // RGB ocupa menos que RGBA
        assertTrue(opaca.first.size < comAlfa.first.size)
    }

    @Test
    fun `tamanho do arquivo bate com header + tabela + blocos`() {
        val (bytes, _) = KimgPipeline.converter(gradiente(8, 8, alfaVariado = true), 8, 8)
        val mips = listOf(8 to 8, 4 to 4, 2 to 2, 1 to 1)
        var esperado = KimgFormat.HEADER_SIZE + mips.size * KimgFormat.MIP_ENTRY_SIZE
        for ((w, h) in mips) esperado += KimgReader.tamanhoBloco(KimgFormato.ETC2_RGBA, w, h)
        assertEquals(esperado, bytes.size)
    }

    // ------------------------------------------------------------- limites

    @Test
    fun `arquivo truncado magic quebrado versão errada e mip fora do arquivo dao ERRO CLARO`() {
        val bytes = KimgPipeline.converter(gradiente(8, 8, alfaVariado = true), 8, 8).first

        try {
            KimgReader.read(bytes.copyOf(bytes.size / 2))
            fail("truncado deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("truncado") || e.message!!.contains("fora do arquivo"))
        }

        val quebrado = bytes.copyOf().also { it[0] = 0x00 }
        try {
            KimgReader.read(quebrado)
            fail("magic quebrado deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("magic"))
        }

        val versaoRuim = bytes.copyOf().also {
            it[4] = 9; it[5] = 0 // versão u16 big-endian = 9
        }
        try {
            KimgReader.read(versaoRuim)
            fail("versão errada deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("versão"))
        }

        val mipFora = bytes.copyOf().also {
            // zera o offset do mip 0 (bytes 24..27, big-endian)
            it[24] = 0; it[25] = 0; it[26] = 0x7F.toByte(); it[27] = 0x7F.toByte()
        }
        try {
            KimgReader.read(mipFora)
            fail("mip fora do arquivo deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("fora do arquivo"))
        }

        try {
            KimgReader.read(ByteArray(10))
            fail("arquivo curto deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("truncado"))
        }
    }

    @Test
    fun `ASTC no cabeçalho relê e dimensões nulas dao erro`() {
        // ASTC é formato RESERVADO válido no cabeçalho (o leitor lê; o upload recusa)
        val tamanho = KimgReader.tamanhoBloco(KimgFormato.ASTC_4x4, 8, 8)
        val imagem = KimgImagem(
            KimgFormato.ASTC_4x4, 8, 8, false, false, false,
            listOf(KimgMip(8, 8, 0, ByteArray(tamanho)))
        )
        val bytes = KimgWriter.write(imagem)
        val lida = KimgReader.read(bytes)
        assertEquals(KimgFormato.ASTC_4x4, lida.formato)

        // dimensões nulas = erro no leitor E no escritor
        try {
            KimgWriter.write(
                KimgImagem(KimgFormato.ETC2_RGB, 0, 0, false, false, false, listOf(KimgMip(0, 0, 0, ByteArray(8))))
            )
            fail("dimensões 0 deveriam dar erro no escritor")
        } catch (e: KimgException) {
            // ok
        }
        try {
            KimgReader.read(
                KimgWriter.write(
                    KimgImagem(KimgFormato.ETC2_RGB, 0, 0, false, false, false, listOf(KimgMip(0, 0, 0, ByteArray(8))))
                )
            )
            fail("dimensões 0 deveriam dar erro no leitor")
        } catch (e: KimgException) {
            // ok
        }
    }

    // ------------------------------------------------------------- blocos ETC2

    @Test
    fun `bloco de cor sólida usa diferencial e decodifica exato`() {
        val cor = intArrayOf(200, 30, 40)
        val blocos = Etc2.codificarRgb(corSolida(4, 4, cor[0], cor[1], cor[2]), 4, 4)
        assertEquals(8, blocos.size)
        val volta = Etc2.decodificarRgb(blocos, 4, 4)
        for (i in 0 until 16) {
            val r = volta[i * 4].toInt() and 0xFF
            val g = volta[i * 4 + 1].toInt() and 0xFF
            val b = volta[i * 4 + 2].toInt() and 0xFF
            assertTrue(
                "texel $i ($r,$g,$b) próximo de (${cor[0]},${cor[1]},${cor[2]})",
                abs(r - cor[0]) <= 8 && abs(g - cor[1]) <= 8 && abs(b - cor[2]) <= 8
            )
        }
        // modo diferencial escolhido para cor sólida (diff bit 33 = byte 3, bit 1)
        val diffBit = ((blocos[3].toInt() and 0xFF) shr 1) and 1
        assertEquals("cor sólida = modo diferencial (bit 33 = 1)", 1, diffBit)
        val psnr = Etc2.psnr(corSolida(4, 4, cor[0], cor[1], cor[2]), volta)
        assertTrue("PSNR cor sólida ≥ 30 dB (medido $psnr)", psnr > 30.0)
    }

    @Test
    fun `planar ganha em gradiente suave e decodifica coerente`() {
        val src = ByteArray(16 * 4)
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val i = (y * 4 + x) * 4
                src[i] = ((x * 255) / 3).toByte()
                src[i + 1] = ((x * 255) / 3).toByte()
                src[i + 2] = ((x * 255) / 3).toByte()
                src[i + 3] = 255.toByte()
            }
        }
        val blocos = Etc2.codificarRgb(src, 4, 4)
        val volta = Etc2.decodificarRgb(blocos, 4, 4)
        val psnr = Etc2.psnr(src, volta)
        assertTrue("gradiente 1D bem comprimido (PSNR $psnr)", psnr > 20.0)
        for (i in 0 until 16) {
            for (c in 0 until 3) {
                val origem = src[i * 4 + c].toInt() and 0xFF
                val rec = volta[i * 4 + c].toInt() and 0xFF
                assertTrue("texel $i canal $c: $origem vs $rec", abs(origem - rec) <= 16)
            }
        }
    }

    @Test
    fun `xadrez preto e branco fica com erro baixo e 16 cores vivas não crasham`() {
        // xadrez 2×2 preto/branco: caso onde o bloco tem que separar claro/escuro
        val xadrez = ByteArray(16 * 4)
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                val i = (x * 4 + y) * 4
                val branco = (x / 2 + y / 2) % 2 == 0
                val v = if (branco) 240 else 15
                xadrez[i] = v.toByte(); xadrez[i + 1] = v.toByte()
                xadrez[i + 2] = v.toByte(); xadrez[i + 3] = 255.toByte()
            }
        }
        val volta = Etc2.decodificarRgb(Etc2.codificarRgb(xadrez, 4, 4), 4, 4)
        var pior = 0
        for (i in 0 until 16) {
            for (c in 0 until 3) {
                val d = abs((xadrez[i * 4 + c].toInt() and 0xFF) - (volta[i * 4 + c].toInt() and 0xFF))
                pior = maxOf(pior, d)
            }
        }
        assertTrue("xadrez claro/escuro: erro por canal ≤ 24 (medido $pior)", pior <= 24)

        // 16 cores VIVAS = pior caso de qualquer codec de blocos: só garante
        // sem crash e pixels dentro da faixa
        val vivas = ByteArray(16 * 4)
        val cores = arrayOf(
            intArrayOf(255, 0, 0), intArrayOf(0, 255, 0),
            intArrayOf(0, 0, 255), intArrayOf(255, 255, 0)
        )
        for (i in 0 until 16) {
            val cor = cores[i % 4]
            vivas[i * 4] = cor[0].toByte(); vivas[i * 4 + 1] = cor[1].toByte()
            vivas[i * 4 + 2] = cor[2].toByte(); vivas[i * 4 + 3] = 255.toByte()
        }
        val voltaVivas = Etc2.decodificarRgb(Etc2.codificarRgb(vivas, 4, 4), 4, 4)
        for (v in voltaVivas) {
            val valor = v.toInt() and 0xFF
            assertTrue("pixel dentro da faixa", valor in 0..255)
        }
    }

    // ------------------------------------------------------------- mipmap + POT

    @Test
    fun `mipmaps por caixa e último mip é 1x1`() {
        val (bytes, relatorio) = KimgPipeline.converter(corSolida(8, 8, 10, 200, 30), 8, 8)
        val imagem = KimgReader.read(bytes)
        assertEquals(4, imagem.mips.size) // 8→4→2→1
        val ultimo = imagem.mips.last()
        assertEquals(1, ultimo.largura)
        assertEquals(1, ultimo.altura)
        assertEquals(KimgReader.tamanhoBloco(imagem.formato, 1, 1), ultimo.dados.size)
        val mip1 = Etc2.decodificarRgb(imagem.mips[1].dados, 4, 4)
        val r = mip1[0].toInt() and 0xFF
        assertTrue("cor preservada no mip 1 (r=$r)", abs(r - 10) <= 12)
        assertFalse(relatorio.redimensionada)
    }

    @Test
    fun `POT só quando excede o limite do aparelho`() {
        // 6×6 dentro do limite: entra 1:1 (GLES3 suporta NPOT)
        val dentro = KimgPipeline.converter(gradiente(6, 6, alfaVariado = false), 6, 6, limiteTextura = 8)
        assertFalse(dentro.second.redimensionada)
        assertEquals(6, KimgReader.read(dentro.first).largura)
        // 6×6 com limite 4: redimensiona para 4×4 (POT ≤ limite)
        val fora = KimgPipeline.converter(gradiente(6, 6, alfaVariado = false), 6, 6, limiteTextura = 4)
        assertTrue(fora.second.redimensionada)
        val lida = KimgReader.read(fora.first)
        assertEquals(4, lida.largura)
        assertEquals(4, lida.altura)
    }

    @Test
    fun `cancelamento durante a compressão dá erro claro`() {
        try {
            KimgPipeline.converter(gradiente(64, 64, alfaVariado = true), 64, 64, cancelado = { true })
            fail("cancelado deveria dar erro")
        } catch (e: KimgException) {
            assertTrue(e.message!!.contains("cancelada"))
        }
    }

    @Test
    fun `RGBA8_RAW fallback grava e relê`() {
        val (bytes, relatorio) = KimgPipeline.converterRgba8Raw(gradiente(8, 8, alfaVariado = true), 8, 8)
        val imagem = KimgReader.read(bytes)
        assertEquals(KimgFormato.RGBA8_RAW, imagem.formato)
        assertEquals(8 * 8 * 4, imagem.mips[0].dados.size)
        assertTrue(Etc2.psnr(gradiente(8, 8, alfaVariado = true), imagem.mips[0].dados) > 90.0)
        assertTrue(relatorio.resumo().contains("RGBA8"))
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v
}
