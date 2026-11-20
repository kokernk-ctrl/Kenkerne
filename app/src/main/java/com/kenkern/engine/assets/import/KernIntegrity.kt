package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernReader
import kotlin.math.abs
import kotlin.math.max

/**
 * TESTE DE INTEGRIDADE do .KERN gerado pelo importador (BACKLOG F2.7):
 * reabre os bytes com o KernReader REAL da engine e compara com a malha de
 * origem — AABB, contagem de vértices/triângulos, erro máximo de posição
 * pós-dequantização (tolerância da quantização u16) e índices dentro do range.
 * Qualquer divergência vira problema com descrição precisa (nunca silêncio).
 */
object KernIntegrity {

    /** Verifica o roundtrip completo: malha de origem → bytes → KernReader. */
    fun verificar(origem: com.kenkern.engine.assets.mesh.KernMesh, bytes: ByteArray): List<String> {
        val problemas = ArrayList<String>(4)

        val lida = try {
            KernReader.read(bytes)
        } catch (e: Exception) {
            return listOf("KernReader recusou os bytes: ${e.message ?: e.javaClass.simpleName}")
        }

        // 1) Contagens
        if (lida.vertexCount != origem.vertexCount) {
            problemas.add("vértices: origem ${origem.vertexCount} vs .KERN ${lida.vertexCount}")
        }
        if (lida.indices.size != origem.indices.size) {
            problemas.add("índices: origem ${origem.indices.size} vs .KERN ${lida.indices.size}")
        }

        // 2) Índices dentro do range e idênticos
        var idxDiv = 0
        var primeiroIdxDiv = -1
        for (i in 0 until minOf(origem.indices.size, lida.indices.size)) {
            if (origem.indices[i] != lida.indices[i]) {
                idxDiv++
                if (primeiroIdxDiv < 0) primeiroIdxDiv = i
            }
            if (lida.indices[i] < 0 || lida.indices[i] >= lida.vertexCount) {
                problemas.add("índice $i fora do range (${lida.indices[i]} com ${lida.vertexCount} vértices)")
                break
            }
        }
        if (idxDiv > 0) {
            problemas.add(
                "$idxDiv índice(s) divergiram (primeiro na posição $primeiroIdxDiv: " +
                    "origem ${origem.indices[primeiroIdxDiv]} vs .KERN ${lida.indices[primeiroIdxDiv]})"
            )
        }

        // 3) AABB do header coerente (os bounds do .KERN são os usados na quantização)
        val tolBounds = 1e-3f
        val cmp = listOf(
            "min.x" to (origem.boundsMin.x - lida.boundsMin.x),
            "min.y" to (origem.boundsMin.y - lida.boundsMin.y),
            "min.z" to (origem.boundsMin.z - lida.boundsMin.z),
            "max.x" to (origem.boundsMax.x - lida.boundsMax.x),
            "max.y" to (origem.boundsMax.y - lida.boundsMax.y),
            "max.z" to (origem.boundsMax.z - lida.boundsMax.z)
        )
        for ((nome, delta) in cmp) {
            if (abs(delta) > tolBounds) {
                problemas.add("bounds $nome divergiu por ${abs(delta)}")
            }
        }

        // 4) Erro máximo de posição pós-dequantização ≤ passo de quantização por eixo
        val passoX = max((origem.boundsMax.x - origem.boundsMin.x) / 65535f, 1e-9f)
        val passoY = max((origem.boundsMax.y - origem.boundsMin.y) / 65535f, 1e-9f)
        val passoZ = max((origem.boundsMax.z - origem.boundsMin.z) / 65535f, 1e-9f)
        var erroMax = 0f
        var piorVertice = -1
        for (v in 0 until minOf(origem.vertexCount, lida.vertexCount)) {
            val ex = abs(origem.positions[v * 3] - lida.positions[v * 3]) / passoX
            val ey = abs(origem.positions[v * 3 + 1] - lida.positions[v * 3 + 1]) / passoY
            val ez = abs(origem.positions[v * 3 + 2] - lida.positions[v * 3 + 2]) / passoZ
            val pior = max(ex, max(ey, ez))
            if (pior > erroMax) {
                erroMax = pior
                piorVertice = v
            }
        }
        // o passo de quantização é span/65535 → erro máximo esperado ≤ 1 passo
        if (erroMax > 1.0f + 1e-3f) {
            problemas.add(
                "erro de posição pós-quantização = %.2f passos no vértice $piorVertice (esperado ≤ 1)".format(
                    java.util.Locale.US, erroMax
                )
            )
        }

        // 5) Tabela de LODs coerente
        if (lida.lods.isEmpty()) {
            problemas.add("malha sem LODs (toda malha .KERN precisa de ≥ 1)")
        } else {
            var somados = 0
            for (lod in lida.lods) {
                if (lod.indexCount < 0 || lod.indexOffset < 0 ||
                    lod.indexOffset + lod.indexCount > lida.indices.size
                ) {
                    problemas.add(
                        "LOD inválido (offset ${lod.indexOffset}, count ${lod.indexCount}, total ${lida.indices.size})"
                    )
                    break
                }
                somados += lod.indexCount
            }
            if (somados > lida.indices.size) {
                problemas.add("LODs somam $somados índices > total ${lida.indices.size}")
            }
        }

        // 6) Grupos de material coerentes (v2, ETAPA B.3)
        if (lida.gruposMaterial.isNotEmpty()) {
            if (lida.materiais.isEmpty()) {
                problemas.add("grupos de material sem tabela de materiais")
            }
            for ((i, g) in lida.gruposMaterial.withIndex()) {
                if (g.indexOffset < 0 || g.indexCount < 0 ||
                    g.indexOffset + g.indexCount > lida.indices.size
                ) {
                    problemas.add(
                        "grupo de material $i fora do buffer de índices " +
                            "(offset ${g.indexOffset}, count ${g.indexCount}, total ${lida.indices.size})"
                    )
                    break
                }
                if (g.materialIndex < 0 || g.materialIndex >= lida.materiais.size) {
                    problemas.add(
                        "grupo de material $i aponta para material ${g.materialIndex} " +
                            "(tabela tem ${lida.materiais.size})"
                    )
                    break
                }
            }
        }

        return problemas
    }

    /** Resumo do roundtrip para o Diagnóstico: "OK (erro máx 0,73 passos)" etc. */
    fun medirErroMaximo(origem: com.kenkern.engine.assets.mesh.KernMesh, bytes: ByteArray): Float {
        val lida = KernReader.read(bytes)
        val passoX = max((origem.boundsMax.x - origem.boundsMin.x) / 65535f, 1e-9f)
        val passoY = max((origem.boundsMax.y - origem.boundsMin.y) / 65535f, 1e-9f)
        val passoZ = max((origem.boundsMax.z - origem.boundsMin.z) / 65535f, 1e-9f)
        var erroMax = 0f
        for (v in 0 until minOf(origem.vertexCount, lida.vertexCount)) {
            val ex = abs(origem.positions[v * 3] - lida.positions[v * 3]) / passoX
            val ey = abs(origem.positions[v * 3 + 1] - lida.positions[v * 3 + 1]) / passoY
            val ez = abs(origem.positions[v * 3 + 2] - lida.positions[v * 3 + 2]) / passoZ
            erroMax = max(erroMax, max(ex, max(ey, ez)))
        }
        return erroMax
    }
}
