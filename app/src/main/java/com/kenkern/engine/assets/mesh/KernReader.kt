package com.kenkern.engine.assets.mesh

import com.kenkern.engine.core.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Leitor do formato .KERN (docs/KERN_SPEC.md).
 * Reconstrói a malha com posições dequantizadas, normais/tangentes decodificadas
 * (octaédrico 8:8) e UVs convertidas de half-float; lê também os blocos
 * reservados de skinning/esqueleto e o bloco de clusters da futura KENA.
 */
object KernReader {

    fun read(bytes: ByteArray): KernMesh {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)
        require(String(magic, Charsets.US_ASCII) == KernFormat.MAGIC) { "Arquivo KERN inválido: magic incorreto" }
        val version = buffer.short
        require(version == KernFormat.VERSION) { "Versão KERN não suportada: $version" }
        val flags = buffer.short

        val boundsMin = Vec3(buffer.float, buffer.float, buffer.float)
        val boundsMax = Vec3(buffer.float, buffer.float, buffer.float)

        val vertexCount = buffer.int
        val indexCount = buffer.int
        val indexType = buffer.get()
        val lodCount = buffer.get().toInt() and 0xFF
        val boneCount = buffer.int
        val vertexBlockSize = buffer.int
        val clusterOffset = buffer.long
        val clusterSize = buffer.long

        require(vertexCount > 0) { "KERN sem vértices" }
        require(vertexBlockSize == vertexCount * KernFormat.VERTEX_SIZE) { "vertexBlockSize inconsistente" }

        // Vértices
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val tangents = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val boneIndices = ByteArray(vertexCount * 4)
        val boneWeights = ByteArray(vertexCount * 4)

        val dirScratch = FloatArray(3)
        for (v in 0 until vertexCount) {
            val px = buffer.short.toInt() and 0xFFFF
            val py = buffer.short.toInt() and 0xFFFF
            val pz = buffer.short.toInt() and 0xFFFF
            positions[v * 3] = KernFormat.dequantizeU16(px, boundsMin.x, boundsMax.x)
            positions[v * 3 + 1] = KernFormat.dequantizeU16(py, boundsMin.y, boundsMax.y)
            positions[v * 3 + 2] = KernFormat.dequantizeU16(pz, boundsMin.z, boundsMax.z)

            val normalPacked = buffer.short.toInt() and 0xFFFF
            KernFormat.decodeOctahedral16(normalPacked, dirScratch)
            normals[v * 3] = dirScratch[0]; normals[v * 3 + 1] = dirScratch[1]; normals[v * 3 + 2] = dirScratch[2]

            val tangentPacked = buffer.short.toInt() and 0xFFFF
            KernFormat.decodeOctahedral16(tangentPacked, dirScratch)
            tangents[v * 3] = dirScratch[0]; tangents[v * 3 + 1] = dirScratch[1]; tangents[v * 3 + 2] = dirScratch[2]

            uvs[v * 2] = halfToFloat(buffer.short)
            uvs[v * 2 + 1] = halfToFloat(buffer.short)

            buffer.get(boneIndices, v * 4, 4)
            buffer.get(boneWeights, v * 4, 4)
            buffer.short // padding
        }

        // Tabela de LODs
        val lods = ArrayList<KernLod>(lodCount)
        for (l in 0 until lodCount) {
            val offset = buffer.int
            val count = buffer.int
            val min = Vec3(buffer.float, buffer.float, buffer.float)
            val max = Vec3(buffer.float, buffer.float, buffer.float)
            lods.add(KernLod(offset, count, min, max))
        }

        // Índices
        val indices = IntArray(indexCount)
        if (indexType == KernFormat.INDEX_TYPE_U16) {
            for (i in 0 until indexCount) indices[i] = buffer.short.toInt() and 0xFFFF
        } else {
            for (i in 0 until indexCount) indices[i] = buffer.int
        }

        // Ossos (reservado)
        val bones = ArrayList<KernBone>(boneCount)
        for (b in 0 until boneCount) {
            val parent = buffer.int
            val nameLen = buffer.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val inverseBind = FloatArray(16)
            for (i in 0 until 16) inverseBind[i] = buffer.float
            bones.add(KernBone(String(nameBytes, Charsets.UTF_8), parent, inverseBind))
        }

        // Clusters (reservado para a KENA)
        var clusters: ByteArray? = null
        val hasClusters = (flags.toInt() and KernFormat.FLAG_HAS_CLUSTERS.toInt()) != 0
        if (hasClusters && clusterSize > 0) {
            clusters = ByteArray(clusterSize.toInt())
            buffer.get(clusters)
        }

        return KernMesh(
            positions, normals, uvs, tangents, indices,
            boundsMin, boundsMax, lods,
            boneIndices, boneWeights, bones, clusters
        )
    }

    /** Leitura e validação rápida (sem dequantizar tudo). */
    fun readHeader(bytes: ByteArray): Triple<Short, Int, Int> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        require(String(magic, Charsets.US_ASCII) == KernFormat.MAGIC) { "Magic KERN inválido" }
        val version = buffer.short
        buffer.short // flags
        buffer.float; buffer.float; buffer.float
        buffer.float; buffer.float; buffer.float
        val vertexCount = buffer.int
        val indexCount = buffer.int
        return Triple(version, vertexCount, indexCount)
    }

    internal fun halfToFloat(half: Short): Float {
        val h = half.toInt() and 0xFFFF
        val sign = (h and 0x8000)
        var exponent = (h ushr 10) and 0x1F
        var mantissa = h and 0x03FF
        if (exponent == 0) {
            if (mantissa == 0) return if (sign != 0) -0f else 0f
            exponent = 1
            while (mantissa and 0x0400 == 0) {
                mantissa = mantissa shl 1
                exponent--
            }
            mantissa = mantissa and 0x03FF
            exponent--
        }
        if (exponent == 0x1F) {
            val infBits = (sign shl 16) or 0x7F800000 or (mantissa shl 13)
            return java.lang.Float.intBitsToFloat(infBits)
        }
        val bits = (sign shl 16) or ((exponent + 127 - 15) shl 23) or (mantissa shl 13)
        return java.lang.Float.intBitsToFloat(bits)
    }
}
