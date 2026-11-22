package com.kenkern.engine.assets.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kenkern.engine.assets.import.CronometroImport
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
 *
 * B1 (v0.1.17): [cronometro] mede decodificação e gravação .KIMG POR TEXTURA;
 * o mime de origem (PNG/JPEG) vai no relatório junto da resolução original.
 */
object KimgConversor {

    private const val TAG = "KimgConversor"

    class Resultado(val relatorio: String, val caminhoRelativo: String)

    /**
     * Converte os bytes de origem (PNG/JPEG) para .KIMG em [raizProjeto]/assets/textures/.
     * [sha1] vem do ImportSummary (hash do conteúdo). [comprimirEmDisco] liga a
     * camada Deflate do .KIMG (V2.4 — decisão POR ARQUIVO no escritor; ADOTADA
     * por padrão: mediu 37–61% menos disco nos modelos reais, carga +8–16 ms
     * por textura 2048² — DECISIONS #22). Retorna o relatório ou LANÇA erro
     * claro (o chamador registra no Console e segue com cor sólida).
     */
    fun converter(
        raizProjeto: File,
        sha1: String,
        bytesOrigem: ByteArray,
        limiteTextura: Int,
        comprimirEmDisco: Boolean = true,
        cancelado: () -> Boolean = { false },
        onProgresso: (Float) -> Unit = {},
        cronometro: CronometroImport? = null,
        rotuloTextura: String = "textura",
        qualidadeEtc2: Etc2.Qualidade = Etc2.Qualidade.RAPIDA
    ): Resultado {
        val pasta = File(raizProjeto, "assets/textures")
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw KimgException("não criou a pasta assets/textures")
        }
        val destino = File(pasta, "$sha1.kimg")
        if (destino.exists()) {
            // CACHE: hash de conteúdo + versão do formato — já convertido antes.
            val kimg = KimgReader.read(destino.readBytes())
            cronometro?.registrar("$rotuloTextura: conversão", 0, "em cache (${kimg.largura}×${kimg.altura}) — não reconverteu")
            return Resultado(
                "textura $sha1 em cache (${kimg.largura}×${kimg.altura} ${kimg.formato.rotulo}) — não reconverteu",
                "assets/textures/$sha1.kimg"
            )
        }

        val decodificada = (if (cronometro != null) {
            cronometro.medir("$rotuloTextura: decodificação de imagem", "${bytesOrigem.size} bytes") {
                decodificar(bytesOrigem)
            }
        } else {
            decodificar(bytesOrigem)
        }) ?: throw KimgException(
            "formato de imagem não suportado (não é PNG/JPEG decodificável) — textura vira cor sólida"
        )
        val (bytes, relatorio) = KimgPipeline.converter(
            decodificada.bytes, decodificada.largura, decodificada.altura,
            srgb = true, normalMap = false,
            limiteTextura = limiteTextura,
            comprimirEmDisco = comprimirEmDisco,
            cancelado = cancelado,
            onProgresso = onProgresso,
            cronometro = cronometro,
            rotuloTextura = rotuloTextura,
            mimeOrigem = decodificada.mime,
            qualidadeEtc2 = qualidadeEtc2
        )
        if (cronometro != null) {
            cronometro.medir("$rotuloTextura: gravação .KIMG", "${bytes.size} bytes") {
                destino.writeBytes(bytes)
            }
        } else {
            destino.writeBytes(bytes)
        }
        KenkernLog.w(TAG, relatorio.resumo()) // informação útil no console (nível W)
        return Resultado(relatorio.resumo(), "assets/textures/$sha1.kimg")
    }

    private class Decodificada(val bytes: ByteArray, val largura: Int, val altura: Int, val mime: String)

    /** PNG/JPEG → RGBA8888 (android.graphics). Null = formato não decodificável. */
    private fun decodificar(bytes: ByteArray): Decodificada? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) return null
        val mime = opcoes.outMimeType ?: "" // "image/png" | "image/jpeg"
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
        return Decodificada(rgba, w, h, mime)
    }
}
