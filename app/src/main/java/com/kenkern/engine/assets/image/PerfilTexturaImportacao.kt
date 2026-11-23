package com.kenkern.engine.assets.image

import com.kenkern.engine.assets.import.AssetLayout

/** Parâmetros congelados no começo da importação, do material à gravação do .KIMG. */
class PerfilTexturaImportacao private constructor(
    val limite: Int,
    val qualidade: Etc2.Qualidade
) {
    fun caminho(sha1: String): String {
        val modo = if (qualidade == Etc2.Qualidade.ALTA) "alta" else "rapida"
        return "${AssetLayout.DIR_TEXTURAS}/$sha1-r1-$limite-$modo.kimg"
    }

    companion object {
        const val ORIGINAL = 0
        const val PADRAO = 1024
        private const val LIMITE_KIMG = 16384
        private const val FALLBACK_APARELHO = 2048

        fun criar(escolhido: Int, maxAparelho: Int, qualidade: Etc2.Qualidade): PerfilTexturaImportacao {
            val teto = if (maxAparelho > 0) minOf(maxAparelho, LIMITE_KIMG) else FALLBACK_APARELHO
            val limite = if (escolhido == ORIGINAL) teto else minOf(escolhido, teto)
            require(limite > 0)
            return PerfilTexturaImportacao(limite, qualidade)
        }

        /** BitmapFactory usa amostras em potências de dois. Não decodifica abaixo do alvo. */
        fun amostraDecodificacao(maiorDimensao: Int, limite: Int): Int {
            require(limite > 0)
            var amostra = 1
            while (maiorDimensao / amostra / 2 >= limite) amostra *= 2
            return amostra
        }
    }
}
