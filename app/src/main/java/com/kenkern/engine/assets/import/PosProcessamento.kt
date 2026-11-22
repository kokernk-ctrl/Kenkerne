package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernGrupoMaterial
import com.kenkern.engine.assets.mesh.KernLod
import com.kenkern.engine.assets.mesh.KernMesh
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.nativecore.MalhaNativa

/**
 * PÓS-PROCESSAMENTO da malha importada (ETAPA B.4/B.5), aplicado pelo
 * ModelImporter DEPOIS do parser e ANTES de gravar o .KERN:
 *
 *  - DEDUP de vértices (B.4): vértices com MESMA posição + normal + UV viram
 *    UM vértice (chave = bits exatos dos 8 floats); índices remapeados; grupos
 *    de material mantidos (só os índices mudam dentro dos mesmos intervalos);
 *  - DEDUP de materiais iguais (B.4) já é feito nos importadores (tabela por
 *    conteúdo — aqui só garantimos os índices de grupo dentro do range);
 *  - LODs (B.5): 3 níveis por AGRUPAMENTO de vértices em GRADE (quadtree de
 *    células sobre a AABB; cada célula colapsa para o 1º vértice dentro dela);
 *    LOD0 = malha inteira; LOD1/LOD2 = células 2×/4× maiores; triângulos
 *    degenerados (2+ cantos na mesma célula) são descartados; tabela de LODs
 *    com AABB própria e offset/count coerentes (KernIntegrity valida).
 *
 * Os índices de LOD1/LOD2 são ANEXADOS ao MESMO buffer (offset crescente) —
 * o formato .KERN guarda um buffer de índices único com a tabela de LODs.
 */
object PosProcessamento {

    /**
     * Aplica dedup + LODs e devolve a malha final pronta para o KernWriter.
     * [cronometro] (B1, v0.1.17): mede dedup e LODs SEPARADAMENTE — a tabela
     * do ImportReport aponta o gargalo com número, não com achismo.
     * [cancelado] (B2d): checado DENTRO dos laços pesados (a cada 8192 vértices
     * no dedup e 4096 triângulos na decimação) — o cancelamento responde em
     * menos de 1 s mesmo em malhas de centenas de milhares de vértices.
     *
     * B6 (v0.1.17): caminho NATIVO (meshoptimizer) quando disponível — dedup
     * por meshopt_generateVertexRemap, cache por meshopt_optimizeVertexCache
     * (POR GRUPO de material) e LODs por meshopt_simplify (com erro reportado
     * no relatório). Falha = motivo no Console + FALLBACK para o código Kotlin
     * atual (abaixo), que continua sendo a referência sem o nativo.
     */
    fun aplicar(
        mesh: KernMesh,
        cronometro: CronometroImport? = null,
        cancelado: () -> Boolean = { false }
    ): KernMesh {
        if (MalhaNativa.disponivel()) {
            val nativa = aplicarViaMeshoptimizer(mesh, cronometro, cancelado)
            if (nativa != null) return nativa
            // Falha do nativo já registrada no Console (MalhaNativa) — segue
            // o caminho Kotlin atual como FALLBACK automático (regra B3b).
        }
        val dedup = cronometro?.medir("dedup de vértices", "${mesh.vertexCount} vértices") {
            deduplicarVertices(mesh, cancelado)
        } ?: deduplicarVertices(mesh, cancelado)
        return cronometro?.medir("LODs (simplificação por grade)", "${dedup.indices.size / 3} triângulos") {
            gerarLods(dedup, cancelado)
        } ?: gerarLods(dedup, cancelado)
    }

    // ------------------------------------------------- caminho NATIVO (B6)

    /**
     * CAMINHO NATIVO (meshoptimizer): dedup + cache por grupo + LODs por
     * simplificação. O registro de dedup é o MESMO dos 8 floats do Kotlin
     * (posição+normal+UV) — a contagem de únicos é idêntica (testado nativo ×
     * Kotlin). Retorna null em falha (fallback Kotlin; motivo no Console).
     */
    private fun aplicarViaMeshoptimizer(mesh: KernMesh, cronometro: CronometroImport?, cancelado: () -> Boolean = { false }): KernMesh? {
        if (mesh.vertexCount <= 0 || mesh.indices.isEmpty() || mesh.indices.size % 3 != 0) return null

        // Registro de 32 bytes por vértice (pos+normal+uv) — a MESMA chave do dedup Kotlin.
        val registros = ByteArray(mesh.vertexCount * MalhaNativa.BYTES_REGISTRO_VERTICE)
        var p = 0
        for (v in 0 until mesh.vertexCount) {
            for (k in 0 until 3) { escreverFloat(registros, p, mesh.positions[v * 3 + k]); p += 4 }
            for (k in 0 until 3) { escreverFloat(registros, p, mesh.normals[v * 3 + k]); p += 4 }
            for (k in 0 until 2) { escreverFloat(registros, p, mesh.uvs[v * 2 + k]); p += 4 }
        }

        // Grupos de material: faixas de índice contíguas (sem grupos = 1 grupo total).
        val grupos = if (mesh.gruposMaterial.isEmpty()) {
            intArrayOf(0, mesh.indices.size)
        } else {
            IntArray(mesh.gruposMaterial.size * 2).also { g ->
                mesh.gruposMaterial.forEachIndexed { i, gm ->
                    g[i * 2] = gm.indexOffset; g[i * 2 + 1] = gm.indexCount
                }
            }
        }

        // ---------- DEDUP + CACHE (nativo, medido no mesmo cronômetro).
        // Cancelamento (B2d): checado antes da chamada nativa — as chamadas do
        // meshoptimizer são uma passe completo cada (limitado a 64 M vértices),
        // dentro da meta de resposta < 1 s.
        if (cancelado()) throw ImportException("pós-processamento cancelado pelo usuário (antes do dedup nativo)")
        val dedup = cronometro?.medir(
            "dedup + cache de vértices (meshoptimizer)",
            "${mesh.vertexCount} vértices · ${mesh.indices.size / 3} triângulos"
        ) {
            MalhaNativa.dedupEOtimizar(registros, mesh.indices, grupos, mesh.vertexCount)
        } ?: MalhaNativa.dedupEOtimizar(registros, mesh.indices, grupos, mesh.vertexCount)
        if (dedup == null) return null

        // ---------- compacta atributos pela tabela remap (como o Kotlin)
        val unicos = dedup.unicos
        val pos = FloatArray(unicos * 3)
        val nor = FloatArray(unicos * 3)
        val uv = FloatArray(unicos * 2)
        val tan = FloatArray(unicos * 3)
        val boneIdx = ByteArray(unicos * 4)
        val boneWgt = ByteArray(unicos * 4)
        for (v in 0 until mesh.vertexCount) {
            val novo = dedup.remap[v]
            if (novo < 0 || novo >= unicos) continue // vértice não referenciado (meshopt descarta)
            System.arraycopy(mesh.positions, v * 3, pos, novo * 3, 3)
            System.arraycopy(mesh.normals, v * 3, nor, novo * 3, 3)
            System.arraycopy(mesh.uvs, v * 2, uv, novo * 2, 2)
            System.arraycopy(mesh.tangents, v * 3, tan, novo * 3, 3)
            System.arraycopy(mesh.boneIndices, v * 4, boneIdx, novo * 4, 4)
            System.arraycopy(mesh.boneWeights, v * 4, boneWgt, novo * 4, 4)
        }
        val novoMin = Vec3()
        val novoMax = Vec3()
        boundsDe(pos, unicos, novoMin, novoMax)

        // ---------- LODs por SIMPLIFICAÇÃO (meshopt_simplify; erro reportado).
        // Uma ÚNICA etapa no cronômetro (como o caminho Kotlin) — o contrato do
        // meshopt_simplify permite PARAR ANTES do alvo por topologia/erro.
        val lods = ArrayList<KernLod>(3)
        val indicesFinais = ArrayList<Int>(mesh.indices.size)
        val avisosErro = ArrayList<String>(2)
        lods.add(KernLod(0, dedup.indices.size, novoMin, novoMax))
        indicesFinais.addAll(dedup.indices.asIterable())
        var total = dedup.indices.size

        val gerarLodsNativos: () -> Unit = {
            val alvoLod1 = multiploDeTres(dedup.indices.size / 4)
            val alvoLod2 = multiploDeTres(dedup.indices.size / 8)
            for ((nivel, alvo) in listOf(1 to alvoLod1, 2 to alvoLod2)) {
                if (alvo < 12) break
                if (cancelado()) throw ImportException("pós-processamento cancelado pelo usuário (antes do LOD $nivel nativo)")
                // LOD1 e LOD2 simplificam do LOD0 (dedup.indices) — como o Kotlin,
                // que decima em cascata a partir da malha deduplicada.
                val lod = MalhaNativa.simplificar(dedup.indices, pos, unicos, alvo)
                if (lod == null) break // falha já no Console; mantém os LODs que deu
                if (lod.indices.size < total && lod.indices.size >= 12) {
                    val mn = Vec3(); val mx = Vec3()
                    boundsDosIndicesPos(pos, lod.indices, mn, mx)
                    lods.add(KernLod(total, lod.indices.size, mn, mx))
                    indicesFinais.addAll(lod.indices.asIterable())
                    total += lod.indices.size
                    avisosErro.add("LOD$nivel: ${lod.indices.size / 3} triângulos · erro de simplificação ${"%.4f".format(java.util.Locale.US, lod.erro)}")
                }
            }
        }
        if (cancelado()) throw ImportException("pós-processamento cancelado pelo usuário (antes do dedup nativo)")
        if (cronometro != null) {
            cronometro.medir("LODs (meshopt_simplify)", "${dedup.indices.size / 3} triângulos") { gerarLodsNativos() }
        } else {
            gerarLodsNativos()
        }
        for (a in avisosErro) {
            com.kenkern.engine.diag.KenkernLog.w("Kenkern/PosProcessamento", "meshoptimizer: $a", null)
        }

        return KernMesh(
            pos, nor, uv, tan, indicesFinais.toIntArray(), novoMin, novoMax,
            lods, boneIdx, boneWgt, mesh.bones, mesh.clusters,
            mesh.gruposMaterial.map { KernGrupoMaterial(it.indexOffset, it.indexCount, it.materialIndex) },
            mesh.materiais
        )
    }

    /** Escreve um Float nos bytes [destino] em little-endian (mesma ordem do ByteBuffer nativo). */
    private fun escreverFloat(destino: ByteArray, offset: Int, valor: Float) {
        val bits = java.lang.Float.floatToIntBits(valor)
        destino[offset] = (bits and 0xFF).toByte()
        destino[offset + 1] = ((bits ushr 8) and 0xFF).toByte()
        destino[offset + 2] = ((bits ushr 16) and 0xFF).toByte()
        destino[offset + 3] = ((bits ushr 24) and 0xFF).toByte()
    }

    /** Maior múltiplo de 3 ≤ valor (mínimo 0). */
    private fun multiploDeTres(valor: Int): Int = (valor / 3) * 3

    /** AABB de um subconjunto de índices sobre o buffer de posições compactado. */
    private fun boundsDosIndicesPos(pos: FloatArray, indices: IntArray, min: Vec3, max: Vec3) {
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (idx in indices) {
            min.x = minOf(min.x, pos[idx * 3]); max.x = maxOf(max.x, pos[idx * 3])
            min.y = minOf(min.y, pos[idx * 3 + 1]); max.y = maxOf(max.y, pos[idx * 3 + 1])
            min.z = minOf(min.z, pos[idx * 3 + 2]); max.z = maxOf(max.z, pos[idx * 3 + 2])
        }
    }

    // ---------------------------------------------------------------- dedup

    fun deduplicarVertices(mesh: KernMesh, cancelado: () -> Boolean = { false }): KernMesh {
        val n = mesh.vertexCount
        if (n == 0) return mesh
        // Chave EXATA (8 floats por bits) — sem hash colidível: dois vértices
        // só fundem se posição+normal+UV são idênticos bit a bit.
        // DUAS PASSADAS SEM MUTAÇÃO: a 1ª decide representantes lendo os arrays
        // PRISTINOS (compactar durante a varredura corromperia chaves futuras);
        // a 2ª compacta a partir dos representantes originais.
        val mapa = HashMap<ChaveVertice, Int>(n * 2) // chave → índice do representante
        val remap = IntArray(n)
        var unico = 0
        for (v in 0 until n) {
            if ((v and 8191) == 0 && cancelado()) {
                throw ImportException("pós-processamento cancelado pelo usuário (dedup em $v/$n)")
            }
            val key = ChaveVertice(
                mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2],
                mesh.normals[v * 3], mesh.normals[v * 3 + 1], mesh.normals[v * 3 + 2],
                mesh.uvs[v * 2], mesh.uvs[v * 2 + 1]
            )
            val existente = mapa[key]
            if (existente != null) {
                remap[v] = existente
            } else {
                mapa[key] = v
                remap[v] = v
                unico++
            }
        }
        if (unico == n) return mesh // nada a deduplicar

        // slot compacto de cada representante (ordem de 1ª aparição)
        val slotDoRep = IntArray(n) { -1 }
        var slot = 0
        for (v in 0 until n) {
            if (remap[v] == v) {
                slotDoRep[v] = slot
                slot++
            }
        }

        // compacta LENDO dos representantes ORIGINAIS (nunca do destino parcial)
        val pos = FloatArray(unico * 3)
        val nor = FloatArray(unico * 3)
        val uv = FloatArray(unico * 2)
        val tan = FloatArray(unico * 3)
        val boneIdx = ByteArray(unico * 4)
        val boneWgt = ByteArray(unico * 4)
        for (v in 0 until n) {
            if (remap[v] != v) continue
            val s = slotDoRep[v]
            pos[s * 3] = mesh.positions[v * 3]
            pos[s * 3 + 1] = mesh.positions[v * 3 + 1]
            pos[s * 3 + 2] = mesh.positions[v * 3 + 2]
            nor[s * 3] = mesh.normals[v * 3]
            nor[s * 3 + 1] = mesh.normals[v * 3 + 1]
            nor[s * 3 + 2] = mesh.normals[v * 3 + 2]
            uv[s * 2] = mesh.uvs[v * 2]
            uv[s * 2 + 1] = mesh.uvs[v * 2 + 1]
            tan[s * 3] = mesh.tangents[v * 3]
            tan[s * 3 + 1] = mesh.tangents[v * 3 + 1]
            tan[s * 3 + 2] = mesh.tangents[v * 3 + 2]
            System.arraycopy(mesh.boneIndices, v * 4, boneIdx, s * 4, 4)
            System.arraycopy(mesh.boneWeights, v * 4, boneWgt, s * 4, 4)
        }

        val indices = IntArray(mesh.indices.size)
        for (i in mesh.indices.indices) {
            indices[i] = slotDoRep[remap[mesh.indices[i]]]
        }

        val novoMin = Vec3()
        val novoMax = Vec3()
        boundsDe(pos, unico, novoMin, novoMax)

        return KernMesh(
            pos, nor, uv, tan, indices, novoMin, novoMax,
            mesh.lods.map { KernLod(it.indexOffset, it.indexCount, it.boundsMin, it.boundsMax) },
            boneIdx, boneWgt, mesh.bones, mesh.clusters,
            mesh.gruposMaterial.map { KernGrupoMaterial(it.indexOffset, it.indexCount, it.materialIndex) },
            mesh.materiais
        )
    }

    /** Chave EXATA de dedup (B.4): 8 floats comparados por bits (equals/hashCode gerados). */
    private data class ChaveVertice(
        val px: Float, val py: Float, val pz: Float,
        val nx: Float, val ny: Float, val nz: Float,
        val u: Float, val v: Float
    )

    // ---------------------------------------------------------------- LODs

    /** Segmentos da grade na maior extensão da AABB (LOD0 = célula de 1 vértice). */
    private const val SEGMENTOS_BASE = 64

    fun gerarLods(mesh: KernMesh, cancelado: () -> Boolean = { false }): KernMesh {
        if (mesh.indices.isEmpty()) return mesh
        val lod0 = mesh.lods.firstOrNull() ?: KernLod(0, mesh.indices.size, mesh.boundsMin, mesh.boundsMax)
        val indicesLod0 = mesh.indices.copyOfRange(lod0.indexOffset, lod0.indexOffset + lod0.indexCount)

        val lod1Idx = decimar(mesh, indicesLod0, 4, cancelado)
        val lod2Idx = decimar(mesh, lod1Idx, 8, cancelado)

        // só anexa o LOD se ele ficou MENOR (malhas pequenas já são o seu próprio LOD)
        val lods = ArrayList<KernLod>(3)
        lods.add(KernLod(0, indicesLod0.size, mesh.boundsMin, mesh.boundsMax))
        var total = indicesLod0.size
        val indicesFinais = ArrayList<Int>(indicesLod0.size + lod1Idx.size + lod2Idx.size)
        indicesFinais.addAll(indicesLod0.asIterable())

        if (lod1Idx.size < indicesLod0.size * 3 / 4 && lod1Idx.size >= 12) {
            val min1 = Vec3(); val max1 = Vec3()
            boundsDosIndices(mesh, lod1Idx, min1, max1)
            lods.add(KernLod(total, lod1Idx.size, min1, max1))
            indicesFinais.addAll(lod1Idx.asIterable())
            total += lod1Idx.size
        }
        if (lod2Idx.size < indicesLod0.size / 2 && lod2Idx.size >= 12) {
            val min2 = Vec3(); val max2 = Vec3()
            boundsDosIndices(mesh, lod2Idx, min2, max2)
            lods.add(KernLod(total, lod2Idx.size, min2, max2))
            indicesFinais.addAll(lod2Idx.asIterable())
            total += lod2Idx.size
        }

        return KernMesh(
            mesh.positions, mesh.normals, mesh.uvs, mesh.tangents,
            indicesFinais.toIntArray(), mesh.boundsMin, mesh.boundsMax,
            lods, mesh.boneIndices, mesh.boneWeights, mesh.bones, mesh.clusters,
            mesh.gruposMaterial, mesh.materiais
        )
    }

    /**
     * Decimação por AGRUPAMENTO EM GRADE (B.5): vértices referenciados pelos
     * índices recebem célula = floor((p − min) / célula); cada célula aponta
     * para o PRIMEIRO vértice que caiu nela; triângulo com 2+ cantos na mesma
     * célula é degenerado e cai fora. célula = maiorExtensão / (SEGMENTOS/fator).
     */
    private fun decimar(mesh: KernMesh, indices: IntArray, fator: Int, cancelado: () -> Boolean): IntArray {
        val dx = mesh.boundsMax.x - mesh.boundsMin.x
        val dy = mesh.boundsMax.y - mesh.boundsMin.y
        val dz = mesh.boundsMax.z - mesh.boundsMin.z
        val maior = maxOf(dx, dy, dz).coerceAtLeast(1e-6f)
        val celula = maior / (SEGMENTOS_BASE / fator.toFloat())

        val celulas = HashMap<Int, Int>(1024) // chave da célula → vértice-representante
        val saida = ArrayList<Int>(indices.size / 4)
        var i = 0
        while (i + 2 < indices.size) {
            if ((i and 12287) == 0 && cancelado()) {
                throw ImportException("pós-processamento cancelado pelo usuário (LODs em ${i / 3}/${indices.size / 3} triângulos)")
            }
            val a = representante(mesh, celulas, celula, indices[i])
            val b = representante(mesh, celulas, celula, indices[i + 1])
            val c = representante(mesh, celulas, celula, indices[i + 2])
            if (a != b && b != c && a != c) {
                saida.add(a); saida.add(b); saida.add(c)
            }
            i += 3
        }
        return saida.toIntArray()
    }

    /** Vértice-representante da célula (O(1): mapa célula → 1º vértice que caiu nela). */
    private fun representante(
        mesh: KernMesh,
        celulas: HashMap<Int, Int>,
        celula: Float,
        vertice: Int
    ): Int {
        val chave = chaveDoVertice(mesh, celula, vertice)
        val dono = celulas[chave]
        if (dono != null) return dono
        celulas[chave] = vertice
        return vertice
    }

    private fun chaveDoVertice(mesh: KernMesh, celula: Float, v: Int): Int {
        val px = mesh.positions[v * 3]
        val py = mesh.positions[v * 3 + 1]
        val pz = mesh.positions[v * 3 + 2]
        val gx = ((px - mesh.boundsMin.x) / celula).toInt().coerceIn(0, SEGMENTOS_BASE)
        val gy = ((py - mesh.boundsMin.y) / celula).toInt().coerceIn(0, SEGMENTOS_BASE)
        val gz = ((pz - mesh.boundsMin.z) / celula).toInt().coerceIn(0, SEGMENTOS_BASE)
        return (gx shl 12) or (gy shl 6) or gz
    }

    private fun boundsDe(pos: FloatArray, vertices: Int, min: Vec3, max: Vec3) {
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (v in 0 until vertices) {
            min.x = minOf(min.x, pos[v * 3]); max.x = maxOf(max.x, pos[v * 3])
            min.y = minOf(min.y, pos[v * 3 + 1]); max.y = maxOf(max.y, pos[v * 3 + 1])
            min.z = minOf(min.z, pos[v * 3 + 2]); max.z = maxOf(max.z, pos[v * 3 + 2])
        }
    }

    private fun boundsDosIndices(mesh: KernMesh, indices: IntArray, min: Vec3, max: Vec3) {
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (idx in indices) {
            min.x = minOf(min.x, mesh.positions[idx * 3]); max.x = maxOf(max.x, mesh.positions[idx * 3])
            min.y = minOf(min.y, mesh.positions[idx * 3 + 1]); max.y = maxOf(max.y, mesh.positions[idx * 3 + 1])
            min.z = minOf(min.z, mesh.positions[idx * 3 + 2]); max.z = maxOf(max.z, mesh.positions[idx * 3 + 2])
        }
    }
}
