package com.kenkern.engine.assets.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.kenkern.engine.assets.import.CronometroImport
import com.kenkern.engine.diag.KenkernLog
import java.io.File

/**
 * CONVERSOR .KIMG do aparelho (ETAPA C): decodifica PNG/JPEG (android.graphics,
 * SEMPRE em background — o chamador roda em Dispatchers.IO/Default) para RGBA,
 * passa pelo [KimgPipeline] (redução proporcional + mipmaps + ETC2) e grava o .KIMG.
 *
 * CACHE: o destino inclui o hash e o perfil de conversão — SE JÁ EXISTE,
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
     * [sha1] é o hash do conteúdo; [perfil] define limite e qualidade ETC2.
     * A camada Deflate do .KIMG é avaliada por arquivo (V2.4 — ADOTADA
     * por padrão: mediu 37–61% menos disco nos modelos reais, carga +8–16 ms
     * por textura 2048² — DECISIONS #22). Retorna o relatório ou LANÇA erro
     * claro (o chamador registra no Console e segue com cor sólida).
     */
    fun converter(
        raizProjeto: File,
        sha1: String,
        bytesOrigem: ByteArray,
        perfil: PerfilTexturaImportacao,
        cancelado: () -> Boolean = { false },
        onProgresso: (Float) -> Unit = {},
        cronometro: CronometroImport? = null,
        rotuloTextura: String = "textura"
    ): Resultado {
        val caminho = perfil.caminho(sha1)
        val destino = File(raizProjeto, caminho)
        val pasta = destino.parentFile!!
        if (!pasta.isDirectory && !pasta.mkdirs() && !pasta.isDirectory) {
            throw KimgException("não criou a pasta assets/textures")
        }
        val arquivo = AtomicFile(destino)
        if (destino.exists()) {
            val kimg = KimgReader.read(arquivo.readFully())
            cronometro?.registrar("$rotuloTextura: conversão", 0, "em cache (${kimg.largura}×${kimg.altura}) — não reconverteu")
            return Resultado(
                "textura $sha1 em cache (${kimg.largura}×${kimg.altura} ${kimg.formato.rotulo}) — não reconverteu",
                caminho
            )
        }

        val decodificada = (if (cronometro != null) {
            cronometro.medir("$rotuloTextura: decodificação de imagem", "${bytesOrigem.size} bytes") {
                decodificar(bytesOrigem, perfil.limite)
            }
        } else {
            decodificar(bytesOrigem, perfil.limite)
        }) ?: throw KimgException(
            "formato de imagem não suportado (não é PNG/JPEG decodificável) — textura vira cor sólida"
        )
        val (bytes, relatorio) = KimgPipeline.converter(
            decodificada.bytes, decodificada.largura, decodificada.altura,
            srgb = true, normalMap = false,
            limiteTextura = perfil.limite,
            comprimirEmDisco = true,
            cancelado = cancelado,
            onProgresso = onProgresso,
            cronometro = cronometro,
            rotuloTextura = rotuloTextura,
            mimeOrigem = decodificada.mime,
            qualidadeEtc2 = perfil.qualidade,
            larguraOrigem = decodificada.larguraOrigem,
            alturaOrigem = decodificada.alturaOrigem
        )
        val gravar = {
            val saida = arquivo.startWrite()
            try {
                saida.write(bytes)
                arquivo.finishWrite(saida)
            } catch (e: Exception) {
                arquivo.failWrite(saida)
                throw e
            }
        }
        if (cronometro != null) {
            cronometro.medir("$rotuloTextura: gravação .KIMG", "${bytes.size} bytes", gravar)
        } else {
            gravar()
        }
        KenkernLog.w(TAG, relatorio.resumo()) // informação útil no console (nível W)
        return Resultado(relatorio.resumo(), caminho)
    }

    private class Decodificada(
        val bytes: ByteArray, val largura: Int, val altura: Int, val mime: String,
        val larguraOrigem: Int, val alturaOrigem: Int
    )

    /** PNG/JPEG → RGBA8888 (android.graphics). Null = formato não decodificável. */
    private fun decodificar(bytes: ByteArray, limite: Int): Decodificada? {
        val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) return null
        val mime = opcoes.outMimeType ?: "" // "image/png" | "image/jpeg"
        val amostra = PerfilTexturaImportacao.amostraDecodificacao(maxOf(opcoes.outWidth, opcoes.outHeight), limite)
        // Estimativa conservadora: bitmap + pixels + RGBA, mips e decodificação
        // ETC2 usada pelo PSNR. Para imagens grandes, falha com aviso em vez de arriscar OOM.
        val texels = ((opcoes.outWidth.toLong() + amostra - 1) / amostra) *
            ((opcoes.outHeight.toLong() + amostra - 1) / amostra)
        val rt = Runtime.getRuntime()
        val heapLivre = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        if (texels > heapLivre / 20) {
            throw KimgException("memória insuficiente para converter textura ${opcoes.outWidth}×${opcoes.outHeight} — escolha um limite menor")
        }
        val opcoes2 = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = amostra
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes2)
            ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        try {
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            bmp.recycle()
        }
        val rgba = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgba[i * 4] = ((p shr 16) and 0xFF).toByte()     // R
            rgba[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()  // G
            rgba[i * 4 + 2] = (p and 0xFF).toByte()          // B
            rgba[i * 4 + 3] = ((p shr 24) and 0xFF).toByte() // A
        }
        return Decodificada(rgba, w, h, mime, opcoes.outWidth, opcoes.outHeight)
    }
}
