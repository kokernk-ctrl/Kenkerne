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
        // FIX ETAPA B: v1 (sem materiais) e v2 (com materiais) são lidos;
        // T0.4 (v0.1.20): v3 estende CADA material com o PBR completo.
        // versão 4+ = erro claro (não silêncio).
        require(version == KernFormat.VERSION || version == KernFormat.VERSION_V2 || version == KernFormat.VERSION_V1) {
            "Versão KERN não suportada: $version (suportadas: 1, 2, ${KernFormat.VERSION})"
        }
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
        // FIX F2.2: o escritor preenche o header com zeros até HEADER_SIZE (80);
        // o leitor lia só os 66 bytes de campos e começava os vértices 14 bytes
        // ANTES de onde eles estão — tudo desalinhado (UVs/índices lixo). Bug
        // empilhado sob o LOD_ENTRY_SIZE e engolido pelo runCatching desde a v0.1.0.
        buffer.position(KernFormat.HEADER_SIZE)

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

        // v2 (ETAPA B.3): grupos de material + tabela de materiais (opcionais)
        var gruposMaterial = emptyList<KernGrupoMaterial>()
        var materiais = emptyList<KernMaterial>()
        val hasMaterials = version >= 2 &&
            (flags.toInt() and KernFormat.FLAG_HAS_MATERIALS.toInt()) != 0
        if (hasMaterials && buffer.remaining() >= 4) {
            val gCount = buffer.short.toInt() and 0xFFFF
            require(gCount <= 65535) { "tabela de grupos de material implausível ($gCount)" }
            val gs = ArrayList<KernGrupoMaterial>(gCount)
            for (g in 0 until gCount) {
                gs.add(KernGrupoMaterial(buffer.int, buffer.int, buffer.int))
            }
            val mCount = buffer.short.toInt() and 0xFFFF
            val ms = ArrayList<KernMaterial>(mCount)
            val ehV3 = version >= KernFormat.VERSION
            for (m in 0 until mCount) {
                val nomeLen = buffer.get().toInt() and 0xFF
                val nomeBytes = ByteArray(nomeLen)
                buffer.get(nomeBytes)
                val r = (buffer.get().toInt() and 0xFF) / 255f
                val gg = (buffer.get().toInt() and 0xFF) / 255f
                val b = (buffer.get().toInt() and 0xFF) / 255f
                val a = (buffer.get().toInt() and 0xFF) / 255f
                val metal = buffer.float
                val rough = buffer.float
                val fl = buffer.get().toInt()
                val tbLen = buffer.get().toInt() and 0xFF
                val tbBytes = ByteArray(tbLen); buffer.get(tbBytes)
                val tnLen = buffer.get().toInt() and 0xFF
                val tnBytes = ByteArray(tnLen); buffer.get(tnBytes)
                var tmrPath = ""
                var tocPath = ""
                var temPath = ""
                var emR = 0f; var emG = 0f; var emB = 0f
                var emInt = 1f; var nScale = 1f; var ocStr = 1f
                if (ehV3) {
                    // v3 (T0.4): extensão PBR — 3 caminhos + 6 floats
                    val tmrLen = buffer.get().toInt() and 0xFF
                    val tmrBytes = ByteArray(tmrLen); buffer.get(tmrBytes)
                    tmrPath = String(tmrBytes, Charsets.UTF_8)
                    val tocLen = buffer.get().toInt() and 0xFF
                    val tocBytes = ByteArray(tocLen); buffer.get(tocBytes)
                    tocPath = String(tocBytes, Charsets.UTF_8)
                    val temLen = buffer.get().toInt() and 0xFF
                    val temBytes = ByteArray(temLen); buffer.get(temBytes)
                    temPath = String(temBytes, Charsets.UTF_8)
                    emR = buffer.float; emG = buffer.float; emB = buffer.float
                    emInt = buffer.float; nScale = buffer.float; ocStr = buffer.float
                }
                ms.add(
                    KernMaterial(
                        String(nomeBytes, Charsets.UTF_8), r, gg, b, a, metal, rough,
                        (fl and 1) != 0, (fl and 2) != 0, (fl and 4) != 0,
                        String(tbBytes, Charsets.UTF_8), String(tnBytes, Charsets.UTF_8),
                        tmrPath, tocPath, temPath,
                        emR, emG, emB, emInt, nScale, ocStr
                    )
                )
            }
            gruposMaterial = gs
            materiais = ms
        }

        return KernMesh(
            positions, normals, uvs, tangents, indices,
            boundsMin, boundsMax, lods,
            boneIndices, boneWeights, bones, clusters,
            gruposMaterial, materiais
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
