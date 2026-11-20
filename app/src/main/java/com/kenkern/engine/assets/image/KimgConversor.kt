package com.kenkern.engine.assets.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kenkern.engine.diag.KenkernLog
import java.io.File

/**
 * CONVERSOR .KIMG do aparelho (ETAPA C): decodifica PNG/JPEG (android.graphics,
 * SEMPRE em background — o chamador roda em Dispatchers.IO/Default) para RGBA,
 * passa pelo [KimgPipeline] (POT + mipmaps + ETC2) e grava o .KIMG.
 *
 * CACHE (C.5): o arquivo destino é assets/textures/<sha1>.kimg — SE JÁ EXISTE,
 * a conversão é PULADA (reabrir o projeto não reconverte). Os relatórios de
 * conversão vão para o ImportReport (tamanho, razão, PSNR, tempo).
 */
object KimgConversor {

    private const val TAG = "KimgConversor"

    class Resultado(val relatorio: String, val caminhoRelativo: String)

    /**
     * Converte os bytes de origem (PNG/JPEG) para .KIMG em [raizProjeto]/assets/textures/.
     * [sha1] vem do ImportSummary (hash do conteúdo). Retorna o relatório ou
     * LANÇA erro claro (o chamador registra no Console e segue com cor sólida).
     */
    fun converter(
        raizProjeto: File,
        sha1: String,
        bytesOrigem: ByteArray,
        limiteTextura: Int,
        cancelado: () -> Boolean = { false },
        onProgresso: (Float) -> Unit = {}
    ): Resultado {
        val pasta = File(raizProjeto, "assets/textures")
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw KimgException("não criou a pasta assets/textures")
        }
        val destino = File(pasta, "$sha1.kimg")
        if (destino.exists()) {
            // CACHE: hash de conteúdo + versão do formato — já convertido antes.
            val kimg = KimgReader.read(destino.readBytes())
            return Resultado(
                "textura $sha1 em cache (${kimg.largura}×${kimg.altura} ${kimg.formato.rotulo}) — não reconverteu",
                "assets/textures/$sha1.kimg"
            )
        }

        val decodificada = decodificar(bytesOrigem)
            ?: throw KimgException(
                "formato de imagem não suportado (não é PNG/JPEG decodificável) — textura vira cor sólida"
            )
        val (bytes, relatorio) = KimgPipeline.converter(
            decodificada.bytes, decodificada.largura, decodificada.altura,
            srgb = true, normalMap = false,
            limiteTextura = limiteTextura,
            cancelado = cancelado,
            onProgresso = onProgresso
        )
        destino.writeBytes(bytes)
        KenkernLog.w(TAG, relatorio.resumo()) // informação útil no console (nível W)
        return Resultado(relatorio.resumo(), "assets/textures/$sha1.kimg")
    }

    private class Decodificada(val bytes: ByteArray, val largura: Int, val altura: Int)

    /** PNG/JPEG → RGBA8888 (android.graphics). Null = formato não decodificável. */
    private fun decodificar(bytes: ByteArray): Decodificada? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) return null
        val opcoes2 = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes2)
            ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        val rgba = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgba[i * 4] = ((p shr 16) and 0xFF).toByte()     // R
            rgba[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()  // G
            rgba[i * 4 + 2] = (p and 0xFF).toByte()          // B
            rgba[i * 4 + 3] = ((p shr 24) and 0xFF).toByte() // A
        }
        return Decodificada(rgba, w, h)
    }
}
