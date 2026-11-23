package com.kenkern.engine.assets.mesh

import com.kenkern.engine.core.math.Vec3
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escritor do formato .KERN (docs/KERN_SPEC.md).
 * Quantiza posições (u16 + bounding box), compacta normais/tangentes (oct 8:8),
 * grava UV em half-float, tabela de LODs e os blocos reservados de skinning/clusters.
 */
object KernWriter {

    /** Entrada de construção da malha (floats; quantização acontece aqui). */
    class Builder {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val tangents = ArrayList<Float>()
        val indices = ArrayList<Int>()
        val lods = ArrayList<KernLod>()
        val gruposMaterial = ArrayList<KernGrupoMaterial>() // v2 (B.3)
        val materiais = ArrayList<KernMaterial>()           // v2 (B.3)
        var boneIndices: ByteArray? = null   // 4 por vértice
        var boneWeights: ByteArray? = null   // 4 por vértice
        var bones: List<KernBone> = emptyList()
        var clusters: ByteArray? = null

        fun vertex(
            px: Float, py: Float, pz: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float,
            tx: Float, ty: Float, tz: Float
        ): Builder {
            positions.add(px); positions.add(py); positions.add(pz)
            normals.add(nx); normals.add(ny); normals.add(nz)
            uvs.add(u); uvs.add(v)
            tangents.add(tx); tangents.add(ty); tangents.add(tz)
            return this
        }

        fun index(i: Int): Builder {
            indices.add(i)
            return this
        }

        fun triangle(a: Int, b: Int, c: Int): Builder {
            indices.add(a); indices.add(b); indices.add(c)
            return this
        }

        fun materialGroup(indexOffset: Int, indexCount: Int, materialIndex: Int): Builder {
            gruposMaterial.add(KernGrupoMaterial(indexOffset, indexCount, materialIndex))
            return this
        }

        fun material(m: KernMaterial): Builder {
            materiais.add(m)
            return this
        }

        fun build(): KernMesh {
            val vertexCount = positions.size / 3
            val min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            var i = 0
            while (i < positions.size) {
                min.x = minOf(min.x, positions[i]); max.x = maxOf(max.x, positions[i])
                min.y = minOf(min.y, positions[i + 1]); max.y = maxOf(max.y, positions[i + 1])
                min.z = minOf(min.z, positions[i + 2]); max.z = maxOf(max.z, positions[i + 2])
                i += 3
            }
            val lodList = if (lods.isEmpty()) {
                listOf(KernLod(0, indices.size, min.copy(), max.copy()))
            } else {
                lods.toList()
            }
            // Material default para grupos sem material explícito: grupos definidos
            // sem nenhum material na tabela referenciam o índice 0 — cria um vazio
            // (cor sólida) para não quebrar o leitor de range.
            val mats = materiais.toList()
            return KernMesh(
                positions.toFloatArray(),
                normals.toFloatArray(),
                uvs.toFloatArray(),
                tangents.toFloatArray(),
                indices.toIntArray(),
                min, max,
                lodList,
                boneIndices ?: ByteArray(vertexCount * 4),
                boneWeights ?: ByteArray(vertexCount * 4),
                bones,
                clusters,
                gruposMaterial.toList(),
                mats
            )
        }
    }

    fun write(mesh: KernMesh): ByteArray {
        val indexType =
            if (mesh.vertexCount <= 65535) KernFormat.INDEX_TYPE_U16 else KernFormat.INDEX_TYPE_U32

        val temMateriais = mesh.materiais.isNotEmpty() || mesh.gruposMaterial.isNotEmpty()
        // T0.4: v3 SÓ quando algum material tem extensão PBR (v1/v2 byte-idênticos).
        val versao = if (temMateriais && mesh.materiais.any { it.temExtensaoPbr })
            KernFormat.VERSION else KernFormat.VERSION_V2
        val flags: Short =
            ((if (mesh.clusters != null && mesh.clusters!!.isNotEmpty()) KernFormat.FLAG_HAS_CLUSTERS else 0) +
                (if (temMateriais) KernFormat.FLAG_HAS_MATERIALS else 0)).toShort()

        // v2 (B.3): grupos/materiais definidos UMA vez (tamanho + escrita usam os mesmos)
        val gruposOut = if (temMateriais) {
            mesh.gruposMaterial.ifEmpty {
                if (mesh.materiais.isNotEmpty()) {
                    listOf(KernGrupoMaterial(0, mesh.indices.size, 0))
                } else emptyList()
            }
        } else emptyList()
        val matsOut = if (temMateriais) mesh.materiais.ifEmpty { listOf(materiaVazia()) } else emptyList()

        var materialBlockSize = 0
        if (temMateriais) {
            materialBlockSize += 2 + gruposOut.size * 12
            materialBlockSize += 2
            for (m in matsOut) {
                materialBlockSize += 1 + m.nome.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                materialBlockSize += 4 // baseColor RGBA u8
                materialBlockSize += 4 + 4 // metallic/roughness f32
                materialBlockSize += 1 // flags
                materialBlockSize += 1 + m.texturaBasePath.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                materialBlockSize += 1 + m.texturaNormalPath.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                if (versao >= KernFormat.VERSION) {
                    // v3 (T0.4): PBR completo por material
                    materialBlockSize += 1 + m.texturaMrPath.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                    materialBlockSize += 1 + m.texturaOclusaoPath.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                    materialBlockSize += 1 + m.texturaEmissivaPath.toByteArray(Charsets.UTF_8).size.coerceAtMost(255)
                    materialBlockSize += 4 * 6 // emissive RGB + intensidade, normalScale, oclusão strength
                }
            }
        }

        val vertexBlockSize = mesh.vertexCount * KernFormat.VERTEX_SIZE
        val lodTableSize = mesh.lods.size * KernFormat.LOD_ENTRY_SIZE
        val indexBlockSize = mesh.indices.size * if (indexType == KernFormat.INDEX_TYPE_U16) 2 else 4

        val boneCount = mesh.bones.size
        var boneBlockSize = 0
        for (bone in mesh.bones) {
            val nameBytes = bone.name.toByteArray(Charsets.UTF_8)
            boneBlockSize += 4 + 2 + nameBytes.size + 64
        }

        val clusterSize: Long = mesh.clusters?.size?.toLong() ?: 0L
        val staticSize: Long =
            (KernFormat.HEADER_SIZE + vertexBlockSize + lodTableSize + indexBlockSize + boneBlockSize + materialBlockSize).toLong()
        val clusterOffset: Long = if (clusterSize > 0) staticSize else 0L
        val totalSize: Long = if (clusterSize > 0) staticSize + clusterSize else staticSize

        val buffer = ByteBuffer.allocate(totalSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)

        // Header (80 bytes)
        buffer.put(KernFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putShort(versao)
        buffer.putShort(flags)
        buffer.putFloat(mesh.boundsMin.x); buffer.putFloat(mesh.boundsMin.y); buffer.putFloat(mesh.boundsMin.z)
        buffer.putFloat(mesh.boundsMax.x); buffer.putFloat(mesh.boundsMax.y); buffer.putFloat(mesh.boundsMax.z)
        buffer.putInt(mesh.vertexCount)
        buffer.putInt(mesh.indices.size)
        buffer.put(indexType)
        buffer.put(mesh.lods.size.toByte())
        buffer.putInt(boneCount)
        buffer.putInt(vertexBlockSize)
        buffer.putLong(clusterOffset)
        buffer.putLong(clusterSize)
        while (buffer.position() < KernFormat.HEADER_SIZE) buffer.put(0)

        // Vértices (24 bytes cada)
        val normalScratch = FloatArray(3)
        val tangentScratch = FloatArray(3)
        for (v in 0 until mesh.vertexCount) {
            buffer.putShort(
                KernFormat.quantizeU16(mesh.positions[v * 3], mesh.boundsMin.x, mesh.boundsMax.x).toShort()
            )
            buffer.putShort(
                KernFormat.quantizeU16(mesh.positions[v * 3 + 1], mesh.boundsMin.y, mesh.boundsMax.y).toShort()
            )
            buffer.putShort(
                KernFormat.quantizeU16(mesh.positions[v * 3 + 2], mesh.boundsMin.z, mesh.boundsMax.z).toShort()
            )
            normalScratch[0] = mesh.normals[v * 3]
            normalScratch[1] = mesh.normals[v * 3 + 1]
            normalScratch[2] = mesh.normals[v * 3 + 2]
            buffer.putShort(KernFormat.encodeOctahedral16(normalScratch[0], normalScratch[1], normalScratch[2]).toShort())
            tangentScratch[0] = mesh.tangents[v * 3]
            tangentScratch[1] = mesh.tangents[v * 3 + 1]
            tangentScratch[2] = mesh.tangents[v * 3 + 2]
            buffer.putShort(KernFormat.encodeOctahedral16(tangentScratch[0], tangentScratch[1], tangentScratch[2]).toShort())
            buffer.putShort(halfFromFloat(mesh.uvs[v * 2]))
            buffer.putShort(halfFromFloat(mesh.uvs[v * 2 + 1]))
            for (b in 0 until 4) {
                buffer.put(if (mesh.boneIndices.size > v * 4 + b) mesh.boneIndices[v * 4 + b] else 0)
            }
            for (b in 0 until 4) {
                buffer.put(if (mesh.boneWeights.size > v * 4 + b) mesh.boneWeights[v * 4 + b] else 0)
            }
            buffer.putShort(0) // padding de alinhamento
        }

        // Tabela de LODs
        for (lod in mesh.lods) {
            buffer.putInt(lod.indexOffset)
            buffer.putInt(lod.indexCount)
            buffer.putFloat(lod.boundsMin.x); buffer.putFloat(lod.boundsMin.y); buffer.putFloat(lod.boundsMin.z)
            buffer.putFloat(lod.boundsMax.x); buffer.putFloat(lod.boundsMax.y); buffer.putFloat(lod.boundsMax.z)
        }

        // Índices
        if (indexType == KernFormat.INDEX_TYPE_U16) {
            for (idx in mesh.indices) buffer.putShort(idx.toShort())
        } else {
            for (idx in mesh.indices) buffer.putInt(idx)
        }

        // Ossos (reservado; 0 nesta fase — implementado para compatibilidade futura)
        for (bone in mesh.bones) {
            buffer.putInt(bone.parentIndex)
            val nameBytes = bone.name.toByteArray(Charsets.UTF_8)
            buffer.putShort(nameBytes.size.toShort())
            buffer.put(nameBytes)
            for (f in bone.inverseBind) buffer.putFloat(f)
        }

        // Clusters (reservado para a KENA)
        mesh.clusters?.let { clusters ->
            if (clusterSize > 0) buffer.put(clusters)
        }

        // v2 (ETAPA B.3): grupos de material por submalha + tabela de materiais
        if (temMateriais) {
            buffer.putShort(gruposOut.size.toShort())
            for (g in gruposOut) {
                buffer.putInt(g.indexOffset)
                buffer.putInt(g.indexCount)
                buffer.putInt(g.materialIndex)
            }
            buffer.putShort(matsOut.size.toShort())
            for (m in matsOut) {
                val nomeBytes = m.nome.toByteArray(Charsets.UTF_8)
                buffer.put(nomeBytes.size.coerceAtMost(255).toByte())
                buffer.put(nomeBytes.take(255).toByteArray())
                buffer.put((m.baseColorR.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buffer.put((m.baseColorG.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buffer.put((m.baseColorB.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buffer.put((m.baseColorA.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buffer.putFloat(m.metallic)
                buffer.putFloat(m.roughness)
                var fl = 0
                if (m.temTexturaBase) fl = fl or 1
                if (m.temTexturaNormal) fl = fl or 2
                if (m.doubleSided) fl = fl or 4
                buffer.put(fl.toByte())
                val tb = m.texturaBasePath.toByteArray(Charsets.UTF_8)
                buffer.put(tb.size.coerceAtMost(255).toByte())
                buffer.put(tb.take(255).toByteArray())
                val tn = m.texturaNormalPath.toByteArray(Charsets.UTF_8)
                buffer.put(tn.size.coerceAtMost(255).toByte())
                buffer.put(tn.take(255).toByteArray())
                if (versao >= KernFormat.VERSION) {
                    // v3 (T0.4): extensão PBR — 3 caminhos + 6 floats
                    val tmr = m.texturaMrPath.toByteArray(Charsets.UTF_8)
                    buffer.put(tmr.size.coerceAtMost(255).toByte())
                    buffer.put(tmr.take(255).toByteArray())
                    val toc = m.texturaOclusaoPath.toByteArray(Charsets.UTF_8)
                    buffer.put(toc.size.coerceAtMost(255).toByte())
                    buffer.put(toc.take(255).toByteArray())
                    val tem = m.texturaEmissivaPath.toByteArray(Charsets.UTF_8)
                    buffer.put(tem.size.coerceAtMost(255).toByte())
                    buffer.put(tem.take(255).toByteArray())
                    buffer.putFloat(m.emissiveR)
                    buffer.putFloat(m.emissiveG)
                    buffer.putFloat(m.emissiveB)
                    buffer.putFloat(m.emissiveIntensity)
                    buffer.putFloat(m.normalScale)
                    buffer.putFloat(m.oclusaoStrength)
                }
            }
        }

        return buffer.array()
    }

    private fun materiaVazia(): KernMaterial = KernMaterial(
        "", 1f, 1f, 1f, 1f, 0f, 1f, false, false, false, "", ""
    )

    internal fun halfFromFloat(value: Float): Short {
        // Conversão float -> half (binary16), sem depender de android.util.Half.
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        var exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        var mantissa = bits and 0x007FFFFF
        if (exponent >= 0x1F) {
            return (sign or 0x7C00).toShort() // infinito
        }
        if (exponent <= 0) {
            if (exponent < -10) return sign.toShort() // zero subnormal
            mantissa = (mantissa or 0x800000) ushr (1 - exponent)
            return (sign or ((mantissa + 0x00001000) ushr 13)).toShort()
        }
        exponent = exponent shl 10
        return (sign or exponent or ((mantissa + 0x00001000) ushr 13)).toShort()
    }
}
