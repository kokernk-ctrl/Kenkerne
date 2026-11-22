package com.kenkern.engine.nativecore

import com.kenkern.engine.diag.KenkernLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MESHOPTIMIZER NATIVO (B6, v0.1.17): dedup de vértices
 * (meshopt_generateVertexRemap), otimização de cache por grupo de material
 * (meshopt_optimizeVertexCache) e LODs por simplificação (meshopt_simplify)
 * sobre o meshoptimizer (MIT, vendorizado em third_party/meshoptimizer).
 *
 * O [PosProcessamento][com.kenkern.engine.assets.import.PosProcessamento] usa
 * o nativo quando disponível e o código Kotlin atual como FALLBACK — qualquer
 * falha registra o motivo no Console e a importação continua.
 *
 * DEDUP com a MESMA SEMÂNTICA do Kotlin: o registro de vértice passado ao
 * nativo tem exatamente os 8 floats que o [ChaveVertice][com.kenkern.engine.assets.import.PosProcessamento]
 * compara (posição + normal + UV, igualdade binária) — a contagem de vértices
 * únicos sai IDÊNTICA à do encoder Kotlin (garantia testada nativo × Kotlin).
 */
object MalhaNativa {

    private const val TAG = "Kenkern/MalhaNativa"

    /** Bytes do registro de vértice usado no dedup (8 floats: pos+normal+uv). */
    const val BYTES_REGISTRO_VERTICE = 32

    /** Resultado do dedup + otimização de cache. */
    class ResultadoDedup(
        /** Tabela old→new (tamanho = vértices de origem). */
        val remap: IntArray,
        /** Vértices únicos (mesma contagem que o dedup Kotlin). */
        val unicos: Int,
        /** Índices remapeados (mesma ORDEM — grupos de material intactos) com
         *  triângulos reordenados POR GRUPO para o cache da GPU. */
        val indices: IntArray
    )

    /** Resultado de um LOD simplificado. */
    class ResultadoLod(
        /** Índices do LOD (múltiplo de 3, ≤ alvo). */
        val indices: IntArray,
        /** Erro de simplificação reportado pelo meshopt_simplify (relativo). */
        val erro: Float
    )

    /** True quando o núcleo nativo (com o meshoptimizer) está carregado. */
    fun disponivel(): Boolean = NativeCore.carregar()

    /**
     * DEDUP + OTIMIZAÇÃO DE CACHE. [registros] = vértices × 32 bytes
     * (pos+normal+uv por vértice); [grupos] = pares (offset, count) das
     * faixas de índice dos materiais (o cache otimiza DENTRO de cada grupo).
     * Retorna null em falha (motivo no Console; chamador usa o Kotlin).
     */
    fun dedupEOtimizar(
        registros: ByteArray,
        indices: IntArray,
        grupos: IntArray,
        contagemVertices: Int
    ): ResultadoDedup? {
        if (!disponivel()) return null
        if (contagemVertices <= 0 || indices.isEmpty() || indices.size % 3 != 0) return null
        if (registros.size < contagemVertices * BYTES_REGISTRO_VERTICE) return null
        return try {
            val bufRegistros = ByteBuffer.allocateDirect(registros.size).order(ByteOrder.nativeOrder())
            bufRegistros.put(registros)
            bufRegistros.flip()
            val bufIndices = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            for (i in indices) bufIndices.putInt(i)
            bufIndices.flip()
            val bufRemap = ByteBuffer.allocateDirect(contagemVertices * 4).order(ByteOrder.nativeOrder())
            val bufSaida = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            val unicos = dedupEOtimizarNativo(bufRegistros, bufIndices, contagemVertices, grupos, bufRemap, bufSaida)
            if (unicos <= 0) {
                KenkernLog.e(TAG, "meshoptimizer dedup devolveu $unicos — usando o caminho Kotlin")
                return null
            }
            val remap = IntArray(contagemVertices)
            for (i in remap.indices) remap[i] = bufRemap.int
            val saida = IntArray(indices.size)
            for (i in saida.indices) saida[i] = bufSaida.int
            ResultadoDedup(remap, unicos, saida)
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "meshoptimizer dedup/cache falhou (${e.message ?: e.javaClass.simpleName}) — usando o caminho Kotlin", e)
            null
        }
    }

    /**
     * LOD POR SIMPLIFICAÇÃO: reduz [indices] para ~[alvoIndices] preservando a
     * forma ([posicoes] = xyz float por vértice). O ERRO de simplificação
     * vem reportado no resultado (vai para o relatório do PosProcessamento).
     */
    fun simplificar(
        indices: IntArray,
        posicoes: FloatArray,
        contagemVertices: Int,
        alvoIndices: Int
    ): ResultadoLod? {
        if (!disponivel()) return null
        if (contagemVertices <= 0 || indices.size % 3 != 0 || alvoIndices <= 0 || alvoIndices % 3 != 0) return null
        if (posicoes.size < contagemVertices * 3) return null
        return try {
            val bufIndices = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            for (i in indices) bufIndices.putInt(i)
            bufIndices.flip()
            val bufPos = ByteBuffer.allocateDirect(contagemVertices * 12).order(ByteOrder.nativeOrder())
            for (p in posicoes) if (p.isFinite()) bufPos.putFloat(p) else bufPos.putFloat(0f)
            bufPos.flip()
            val bufDestino = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            val erro = floatArrayOf(0f)
            val contagem = simplificarNativo(bufIndices, indices.size, bufPos, contagemVertices, alvoIndices, erro, bufDestino)
            if (contagem <= 0 || contagem > indices.size || contagem % 3 != 0) {
                KenkernLog.e(TAG, "meshopt_simplify devolveu $contagem (alvo $alvoIndices) — LOD nativo descartado")
                return null
            }
            val saida = IntArray(contagem)
            for (i in 0 until contagem) saida[i] = bufDestino.int
            ResultadoLod(saida, erro[0])
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "meshopt_simplify falhou (${e.message ?: e.javaClass.simpleName}) — LOD nativo descartado", e)
            null
        }
    }

    // ------------------------------------------------- externos (JNI)

    private external fun dedupEOtimizarNativo(
        registros: ByteBuffer,
        indices: ByteBuffer,
        contagemVertices: Int,
        grupos: IntArray,
        remapSaida: ByteBuffer,
        indicesSaida: ByteBuffer
    ): Int

    private external fun simplificarNativo(
        indices: ByteBuffer,
        contagemIndices: Int,
        posicoes: ByteBuffer,
        contagemVertices: Int,
        alvoIndices: Int,
        erroSaida: FloatArray,
        destino: ByteBuffer
    ): Int
}
