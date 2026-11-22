package com.kenkern.engine.assets.image

import com.kenkern.engine.nativecore.Etc2Nativo
import com.kenkern.engine.nativecore.NativeCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * B4 (v0.1.17) — ENCODER ETC2 NATIVO (etcpak) × ENCODER KOTLIN.
 *
 * A MESMA fonte C++ do APK (kenkern_etc2.cpp + etcpak vendorizado) é compilada
 * para o HOST (g++, task compilarNativoHost) e carregada pelo teste via
 * -Dkenkern.native.hostpath. Assim a comparação nativo × Kotlin roda NO CI
 * (ubuntu, g++ presente) e no dev — o mesmo código que roda no arm64 do
 * aparelho (onde a checagem "etc2 nativo" do Diagnóstico mede os ms/MP/s).
 *
 * Sem g++/biblioteca (ex.: máquina exótica), os testes que EXIGEM o nativo são
 * ASSUMIDOS-SKIP; os de validação/fallback rodam sempre.
 */
class Etc2NativoComparacaoTest {

    private fun padraoRealista(w: Int, h: Int, alfa: Boolean = false): ByteArray {
        val rgba = ByteArray(w * h * 4)
        var semente = 987654321L
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                semente = semente * 6364136223846793005L + 1442695040888963407L
                val ruido = ((semente ushr 33) % 11).toInt() - 5
                rgba[i] = (110 + x * 2 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 1] = (90 + y * 2 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 2] = (150 + (x + y) / 2).coerceIn(0, 255).toByte()
                rgba[i + 3] = (if (alfa) (if ((x + y) % 3 == 0) 255 else 180) else 255).toByte()
            }
        }
        return rgba
    }

    /** Referência KOTLIN (encoder atual, modo rápido): bloco a bloco, linha-major. */
    private fun kotlinRgb(rgba: ByteArray, w: Int, h: Int): ByteArray {
        val blocosX = (w + 3) / 4
        val blocosY = (h + 3) / 4
        val saida = ByteArray(blocosX * blocosY * 8)
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
                System.arraycopy(Etc2.codificarBlocoRgb(bloco, Etc2.Qualidade.RAPIDA), 0, saida, (by * blocosX + bx) * 8, 8)
            }
        }
        return saida
    }

    @Test
    fun nativoDisponivelNoHostOuSkipExplicito() {
        val ok = NativeCore.carregar()
        if (!ok) {
            // Máquina sem g++/biblioteca host: os outros testes assumem-skip.
            assumeTrue("libkenkern.so do HOST não carregou (g++ ausente?) — nativo×Kotlin só roda com a biblioteca", false)
        }
        assertTrue(NativeCore.isAvailable)
        assertEquals("1.0.0", NativeCore.versao)
    }

    @Test
    fun psnrDoNativoNaoFicaUmDBAbaixoDoKotlinEmTexturaRealista() {
        assumeTrue(NativeCore.carregar())
        val w = 128
        val h = 128
        val rgba = padraoRealista(w, h)
        val nativo = Etc2Nativo.codificar(rgba, w, h, temAlfa = false, qualidadeRapida = true)
        assertNotNull("encoder nativo devolveu null (ver Console)", nativo)
        val kotlin = kotlinRgb(rgba, w, h)
        // Tamanho de saída IDÊNTICO
        assertEquals("tamanho de saída nativo × kotlin", kotlin.size, nativo!!.size)
        // PSNR: nativo (etcpak) ≥ kotlin (rápido) − 1 dB — o etcpak não pode ser pior
        val psnrNativo = Etc2.psnr(rgba, Etc2.decodificarRgb(nativo, w, h))
        val psnrKotlin = Etc2.psnr(rgba, Etc2.decodificarRgb(kotlin, w, h))
        assertTrue(
            "PSNR nativo ($psnrNativo dB) ≥ kotlin ($psnrKotlin dB) − 1 dB",
            psnrNativo >= psnrKotlin - 1.0
        )
        // e o nativo decodifica pelo DECODER da engine (convenção de blocos idêntica)
        assertTrue("PSNR nativo absoluto razoável: $psnrNativo dB", psnrNativo > 18.0)
    }

    @Test
    fun textura4x4NoNativoTemUmBlocoExato() {
        assumeTrue(NativeCore.carregar())
        val rgba = padraoRealista(4, 4)
        val rgb = Etc2Nativo.codificar(rgba, 4, 4, temAlfa = false, qualidadeRapida = true)
        assertNotNull(rgb)
        assertEquals("4×4 RGB = 1 bloco × 8 bytes", 8, rgb!!.size)
        val rgbaOut = Etc2Nativo.codificar(rgba, 4, 4, temAlfa = true, qualidadeRapida = true)
        assertNotNull(rgbaOut)
        assertEquals("4×4 RGBA = 1 bloco × 16 bytes", 16, rgbaOut!!.size)
        // decodifica de volta pelo decoder da engine sem estourar
        assertEquals(4 * 4 * 4, Etc2.decodificarRgb(rgb, 4, 4).size)
        assertEquals(4 * 4 * 4, Etc2.decodificarRgba(rgbaOut, 4, 4).size)
    }

    @Test
    fun dimensoesNaoMultiplasDe4CodificamComPaddingDeBorda() {
        assumeTrue(NativeCore.carregar())
        val w = 6
        val h = 6
        val rgba = padraoRealista(w, h)
        val nativo = Etc2Nativo.codificar(rgba, w, h, temAlfa = false, qualidadeRapida = true)
        assertNotNull(nativo)
        // 6×6 → 2×2 blocos = 4 blocos × 8 bytes (mesma contagem do Kotlin/GPU)
        assertEquals("6×6 RGB = 4 blocos × 8 bytes", 4 * 8, nativo!!.size)
        val psnr = Etc2.psnr(rgba, Etc2.decodificarRgb(nativo, w, h))
        assertTrue("PSNR 6×6 com padding de borda: $psnr dB", psnr > 15.0)
        // 7×3 (nenhum lado múltiplo de 4)
        val estranha = Etc2Nativo.codificar(padraoRealista(7, 3), 7, 3, temAlfa = true, qualidadeRapida = true)
        assertNotNull(estranha)
        assertEquals("7×3 RGBA = 2×1 blocos × 16 bytes", 2 * 16, estranha!!.size)
    }

    @Test
    fun rgbaNativoTemLayoutAlfaDepoisRgbCompativelComODecoder() {
        assumeTrue(NativeCore.carregar())
        val w = 64
        val h = 64
        val rgba = padraoRealista(w, h, alfa = true)
        val nativo = Etc2Nativo.codificar(rgba, w, h, temAlfa = true, qualidadeRapida = true)
        assertNotNull(nativo)
        assertEquals(16 * 16 * 16, nativo!!.size)
        val volta = Etc2.decodificarRgba(nativo, w, h)
        val psnr = Etc2.psnr(rgba, volta)
        // RGBA: o PSNR inclui o canal alfa — layout [alfa EAC][RGB] idêntico ao
        // decoder da engine tem que dar número saudável.
        assertTrue("PSNR RGBA nativo pelo decoder da engine: $psnr dB", psnr > 18.0)
    }

    @Test
    fun nativoEDeterministicoMesmoComMultithread() {
        assumeTrue(NativeCore.carregar())
        val w = 128
        val h = 128
        val rgba = padraoRealista(w, h)
        val a = Etc2Nativo.codificar(rgba, w, h, temAlfa = false, qualidadeRapida = true)
        val b = Etc2Nativo.codificar(rgba, w, h, temAlfa = false, qualidadeRapida = true)
        assertNotNull(a)
        assertNotNull(b)
        assertTrue("duas codificações idênticas (faixas disjuntas, sem corrida)", a!!.contentEquals(b))
    }

    @Test
    fun entradasInvalidadasRecebemNullSemCrash() {
        assumeTrue(NativeCore.carregar())
        val rgba = padraoRealista(8, 8)
        assertNull("largura 0", Etc2Nativo.codificar(rgba, 0, 8, false, true))
        assertNull("altura negativa", Etc2Nativo.codificar(rgba, 8, -1, false, true))
        assertNull("buffer pequeno", Etc2Nativo.codificar(ByteArray(10), 8, 8, false, true))
        assertNull("dimensão gigante (limite)", Etc2Nativo.codificar(ByteArray(16), 20000, 20000, false, true))
    }

    @Test
    fun fallbackKotlinQuandoNativoIndisponivelOuFalha() {
        // Sem a biblioteca host, Etc2Nativo.codificar devolve null e o Etc2
        // CODIFICA PELO KOTLIN (o dispatch real da pipeline) — o caminho que o
        // aparelho usa quando o nativo falha.
        val w = 16
        val h = 16
        val rgba = padraoRealista(w, h)
        val saida = Etc2.codificarRgb(rgba, w, h, qualidade = Etc2.Qualidade.RAPIDA)
        assertEquals(4 * 4 * 8, saida.size)
        val psnr = Etc2.psnr(rgba, Etc2.decodificarRgb(saida, w, h))
        assertTrue("pipeline funciona nativo OU Kotlin (PSNR $psnr dB)", psnr > 15.0)
        // entrada inválida no dispatch nativo → null SEM lançar (fallback)
        assertNull(Etc2Nativo.codificar(rgba, 0, 0, false, true))
    }
}
