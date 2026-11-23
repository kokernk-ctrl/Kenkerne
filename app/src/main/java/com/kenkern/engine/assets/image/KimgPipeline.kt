package com.kenkern.engine.assets.image

import com.kenkern.engine.assets.import.CronometroImport
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PIPELINE .KIMG (ETAPA C): RGBA8888 de origem → redução proporcional (se exceder o
 * limite do aparelho) → mipmaps (filtro de caixa) → compressão ETC2 (ou ASTC
 * externo/RGBA8 fallback) → bytes .KIMG + RELATÓRIO de compressão
 * (tamanho origem, tamanho .KIMG, razão, PSNR, tempo).
 *
 * Kotlin puro (sem android.*) — roda na JVM dos testes e no aparelho. A
 * decodificação de PNG/JPEG (android.graphics) fica no conversor Android
 * (KimgConversor), que chama ESTE pipeline em background com cancelamento.
 */
object KimgPipeline {

    /** Relatório de compressão de UMA textura (ETAPA C.6 + B1: origem/formato). */
    class RelatorioTextura(
        val bytesOrigem: Long,
        val bytesKimg: Int,
        val largura: Int,
        val altura: Int,
        val mipCount: Int,
        val formato: KimgFormato,
        val psnrDb: Double,
        val tempoMs: Long,
        val redimensionada: Boolean,
        /** B1: resolução/formato de ORIGEM no relatório (a origem real da textura). */
        val larguraOrigem: Int = 0,
        val alturaOrigem: Int = 0,
        val mimeOrigem: String = ""
    ) {
        val razao: Float get() = if (bytesKimg > 0) bytesOrigem.toFloat() / bytesKimg else 0f

        fun resumo(): String =
            "%dx%d%s %s · %.1f KB → %.1f KB (razão %.2f×) · PSNR %.1f dB · %d mips · %d ms%s".format(
                java.util.Locale.US,
                largura, altura,
                if (larguraOrigem > 0 && (larguraOrigem != largura || alturaOrigem != altura))
                    " (origem ${larguraOrigem}×${alturaOrigem}${if (mimeOrigem.isNotEmpty()) " $mimeOrigem" else ""})"
                else if (mimeOrigem.isNotEmpty()) " ($mimeOrigem)" else "",
                formato.rotulo,
                bytesOrigem / 1024f, bytesKimg / 1024f, razao,
                psnrDb, mipCount, tempoMs,
                if (redimensionada) " · redimensionada" else ""
            )
    }

    /**
     * Converte RGBA8888 → .KIMG (ETC2 RGB8 ou ETC2 RGBA8 quando há alfa não
     * opaco). [limiteTextura] = GL_MAX_TEXTURE_SIZE do aparelho (0 = sem limite).
     * [cronometro]/[rotuloTextura] (B1): redimensionamento, mipmaps e ETC2 medidos
     * SEPARADAMENTE por textura — a tabela do ImportReport mostra ms e % do total.
     * [qualidadeEtc2] (B2b): RAPIDA por padrão (individual + diferencial + planar;
     * T/H só em ALTA). [onProgresso] recebe 0..1 da etapa ETC2 (B2e).
     */
    fun converter(
        rgba: ByteArray,
        largura: Int,
        altura: Int,
        srgb: Boolean = true,
        normalMap: Boolean = false,
        limiteTextura: Int = 0,
        comprimirEmDisco: Boolean = false,
        cancelado: () -> Boolean = { false },
        onProgresso: (Float) -> Unit = {},
        cronometro: CronometroImport? = null,
        rotuloTextura: String = "textura",
        mimeOrigem: String = "",
        qualidadeEtc2: Etc2.Qualidade = Etc2.Qualidade.RAPIDA,
        larguraOrigem: Int = largura,
        alturaOrigem: Int = altura
    ): Pair<ByteArray, RelatorioTextura> {
        val t0 = System.nanoTime()
        if (largura <= 0 || altura <= 0) {
            throw KimgException("textura com dimensões inválidas: ${largura}×$altura")
        }
        if (rgba.size != largura * altura * 4) {
            throw KimgException(
                "buffer RGBA de ${rgba.size} bytes não bate com ${largura}×${altura}×4"
            )
        }

        // GLES3 suporta NPOT com mipmap. Calcula a proporção usando a imagem
        // original, mesmo quando o Android já subamostrou durante o decode.
        val w = dimensaoLimitada(larguraOrigem, alturaOrigem, limiteTextura)
        val h = dimensaoLimitada(alturaOrigem, larguraOrigem, limiteTextura)
        val dados = if (w != largura || h != altura) {
            cronometro?.medir("$rotuloTextura: redimensionamento", "$largura×$altura → ${w}×${h}") {
                reescalar(rgba, largura, altura, w, h)
            } ?: reescalar(rgba, largura, altura, w, h)
        } else rgba

        // detecta alfa de fato (economiza 50% quando a textura é opaca)
        var alfaUtil = false
        if (!normalMap) {
            for (i in 3 until dados.size step 4) {
                if ((dados[i].toInt() and 0xFF) != 255) { alfaUtil = true; break }
            }
        }

        // mipmaps (filtro de caixa)
        val niveis = ArrayList<Pair<Int, ByteArray>>(8)
        var nivel = 0
        var cw = w
        var ch = h
        val gerarNiveis: () -> Unit = {
            while (true) {
                if (cancelado()) throw KimgException("conversão .KIMG cancelada pelo usuário")
                niveis.add(cw to dadosNivel(niveis, nivel, dados, larguraRedim = w, alturaRedim = h))
                if (cw == 1 && ch == 1) break
                cw = max(1, cw shr 1)
                ch = max(1, ch shr 1)
                nivel++
            }
        }
        if (cronometro != null) {
            cronometro.medir("$rotuloTextura: mipmaps (filtro de caixa)", "${niveisPadrao(w, h)} níveis") { gerarNiveis() }
        } else {
            gerarNiveis()
        }

        // compressão por mip (ETC2)
        val formato = if (alfaUtil) KimgFormato.ETC2_RGBA else KimgFormato.ETC2_RGB
        val mips = ArrayList<KimgMip>(niveis.size)
        val totalNiveis = niveis.size
        val comprimir: () -> Unit = {
            for ((i, par) in niveis.withIndex()) {
                val (mw, dadosNivel) = par
                val mh = max(1, h shr i)
                val bytes = if (formato == KimgFormato.ETC2_RGBA) {
                    Etc2.codificarRgba(dadosNivel, mw, mh, cancelado, qualidadeEtc2)
                } else {
                    Etc2.codificarRgb(dadosNivel, mw, mh, cancelado, qualidadeEtc2)
                }
                mips.add(KimgMip(mw, mh, 0, bytes))
                onProgresso((i + 1f) / totalNiveis)
            }
        }
        if (cronometro != null) {
            cronometro.medir(
                "$rotuloTextura: ETC2 ${if (alfaUtil) "RGBA8" else "RGB8"}",
                "$totalNiveis mips · ${w}×${h}"
            ) { comprimir() }
        } else {
            comprimir()
        }

        // PSNR medido no mip 0 (ida e volta encoder→decoder)
        val decodificado = if (formato == KimgFormato.ETC2_RGBA) {
            Etc2.decodificarRgba(mips[0].dados, w, h)
        } else {
            Etc2.decodificarRgb(mips[0].dados, w, h)
        }
        val psnr = Etc2.psnr(dados, decodificado)

        val imagem = KimgImagem(
            formato, w, h, srgb, alfaUtil, normalMap, mips
        )
        // comprimirEmDisco (V2.4): camada Deflate OPCIONAL — o escritor decide
        // por arquivo (só grava V2 se o total ficar menor); bytesKimg do
        // relatório é o tamanho FINAL em disco (com ou sem camada).
        val bytes = KimgWriter.write(imagem, comprimirEmDisco = comprimirEmDisco)
        val tempoMs = (System.nanoTime() - t0) / 1_000_000L
        return bytes to RelatorioTextura(
            bytesOrigem = larguraOrigem.toLong() * alturaOrigem * 4,
            bytesKimg = bytes.size,
            largura = w,
            altura = h,
            mipCount = mips.size,
            formato = formato,
            psnrDb = psnr,
            tempoMs = tempoMs,
            redimensionada = w != larguraOrigem || h != alturaOrigem,
            larguraOrigem = larguraOrigem,
            alturaOrigem = alturaOrigem,
            mimeOrigem = mimeOrigem
        )
    }

    /** fallback RGBA8: recodifica como RGBA8_RAW (sem compressão; GPU recusou ETC2). */
    fun converterRgba8Raw(
        rgba: ByteArray, largura: Int, altura: Int,
        srgb: Boolean = true, normalMap: Boolean = false,
        comprimirEmDisco: Boolean = false
    ): Pair<ByteArray, RelatorioTextura> {
        val t0 = System.nanoTime()
        val w = Integer.highestOneBit(largura.coerceAtLeast(1))
        val h = Integer.highestOneBit(altura.coerceAtLeast(1))
        val dados = if (w != largura || h != altura) reescalar(rgba, largura, altura, w, h) else rgba
        val mips = ArrayList<KimgMip>(1)
        mips.add(KimgMip(w, h, 0, dados.copyOf()))
        val imagem = KimgImagem(KimgFormato.RGBA8_RAW, w, h, srgb, true, normalMap, mips)
        val bytes = KimgWriter.write(imagem, comprimirEmDisco = comprimirEmDisco)
        val tempoMs = (System.nanoTime() - t0) / 1_000_000L
        return bytes to RelatorioTextura(
            bytesOrigem = largura.toLong() * altura * 4,
            bytesKimg = bytes.size,
            largura = w,
            altura = h,
            mipCount = 1,
            formato = KimgFormato.RGBA8_RAW,
            psnrDb = 99.0,
            tempoMs = tempoMs,
            redimensionada = w != largura || h != altura,
            larguraOrigem = largura,
            alturaOrigem = altura
        )
    }

    // ------------------------------------------------------------- helpers

    /** Número de níveis de mipmap de uma textura WxH (mesma conta do laço). */
    private fun niveisPadrao(w: Int, h: Int): Int {
        var n = 1
        var cw = w
        var ch = h
        while (!(cw == 1 && ch == 1)) {
            cw = max(1, cw shr 1)
            ch = max(1, ch shr 1)
            n++
        }
        return n
    }

    /** Limita o maior lado sem alterar a proporção, salvo arredondamento de pixels. */
    internal fun dimensaoLimitada(lado: Int, outroLado: Int, limite: Int): Int {
        val maior = max(lado, outroLado)
        if (limite <= 0 || maior <= limite) return lado
        return ((lado.toLong() * limite + maior / 2) / maior).toInt().coerceAtLeast(1)
    }

    private fun dadosNivel(
        niveis: List<Pair<Int, ByteArray>>,
        nivel: Int,
        base: ByteArray,
        larguraRedim: Int,
        alturaRedim: Int
    ): ByteArray {
        if (nivel == 0) return base
        // filtro de CAIXA 2×2 sobre o nível anterior (larguras ímpares: última coluna/linha repete)
        val (pw, _) = niveis[nivel - 1]
        val ph = max(1, alturaRedim shr (nivel - 1))
        val w = max(1, larguraRedim shr nivel)
        val h = max(1, alturaRedim shr nivel)
        val prev = niveis[nivel - 1].second
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x0 = min(x * 2, pw - 1)
                val x1 = min(x0 + 1, pw - 1)
                val y0 = min(y * 2, ph - 1)
                val y1 = min(y0 + 1, ph - 1)
                for (c in 0 until 4) {
                    val a = prev[(y0 * pw + x0) * 4 + c].toInt() and 0xFF
                    val b = prev[(y0 * pw + x1) * 4 + c].toInt() and 0xFF
                    val d = prev[(y1 * pw + x0) * 4 + c].toInt() and 0xFF
                    val e = prev[(y1 * pw + x1) * 4 + c].toInt() and 0xFF
                    out[(y * w + x) * 4 + c] = ((a + b + d + e) / 4).toByte()
                }
            }
        }
        return out
    }

    /** Reescala bilinear simples quando a textura excede o limite. */
    fun reescalar(src: ByteArray, sw: Int, sh: Int, dw: Int, dh: Int): ByteArray {
        val out = ByteArray(dw * dh * 4)
        for (y in 0 until dh) {
            val fy = (y.toFloat() * sh / dh - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt().coerceIn(0, sh - 1)
            val y1 = min(y0 + 1, sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = (x.toFloat() * sw / dw - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt().coerceIn(0, sw - 1)
                val x1 = min(x0 + 1, sw - 1)
                val tx = fx - x0
                for (c in 0 until 4) {
                    val v00 = src[(y0 * sw + x0) * 4 + c].toInt() and 0xFF
                    val v10 = src[(y0 * sw + x1) * 4 + c].toInt() and 0xFF
                    val v01 = src[(y1 * sw + x0) * 4 + c].toInt() and 0xFF
                    val v11 = src[(y1 * sw + x1) * 4 + c].toInt() and 0xFF
                    val v = (v00 * (1 - tx) * (1 - ty) + v10 * tx * (1 - ty) +
                        v01 * (1 - tx) * ty + v11 * tx * ty).roundToInt()
                    out[(y * dw + x) * 4 + c] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return out
    }
}
