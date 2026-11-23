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
        val header = parseHeader(bytes)
        val layout = preflight(bytes, header)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(KernFormat.HEADER_SIZE)

        val vertexCount = header.vertexCount
        val indexCount = header.indexCount
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val tangents = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val boneIndices = ByteArray(vertexCount * 4)
        val boneWeights = ByteArray(vertexCount * 4)

        val dirScratch = FloatArray(3)
        for (v in 0 until vertexCount) {
            if (v and 4095 == 0 && Thread.currentThread().isInterrupted) throw InterruptedException()
            val px = buffer.short.toInt() and 0xFFFF
            val py = buffer.short.toInt() and 0xFFFF
            val pz = buffer.short.toInt() and 0xFFFF
            positions[v * 3] = KernFormat.dequantizeU16(px, header.boundsMin.x, header.boundsMax.x)
            positions[v * 3 + 1] = KernFormat.dequantizeU16(py, header.boundsMin.y, header.boundsMax.y)
            positions[v * 3 + 2] = KernFormat.dequantizeU16(pz, header.boundsMin.z, header.boundsMax.z)

            KernFormat.decodeOctahedral16(buffer.short.toInt() and 0xFFFF, dirScratch)
            normals[v * 3] = dirScratch[0]; normals[v * 3 + 1] = dirScratch[1]; normals[v * 3 + 2] = dirScratch[2]
            KernFormat.decodeOctahedral16(buffer.short.toInt() and 0xFFFF, dirScratch)
            tangents[v * 3] = dirScratch[0]; tangents[v * 3 + 1] = dirScratch[1]; tangents[v * 3 + 2] = dirScratch[2]
            uvs[v * 2] = halfToFloat(buffer.short)
            uvs[v * 2 + 1] = halfToFloat(buffer.short)
            buffer.get(boneIndices, v * 4, 4)
            buffer.get(boneWeights, v * 4, 4)
            buffer.short // padding
        }

        buffer.position(buffer.position() + header.lodCount * KernFormat.LOD_ENTRY_SIZE)

        val indices = IntArray(indexCount)
        if (header.indexType == KernFormat.INDEX_TYPE_U16) {
            for (i in 0 until indexCount) indices[i] = buffer.short.toInt() and 0xFFFF
        } else {
            for (i in 0 until indexCount) indices[i] = buffer.int
        }

        val bones = ArrayList<KernBone>(header.boneCount)
        for (b in 0 until header.boneCount) {
            val parent = buffer.int
            val nameLen = buffer.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val inverseBind = FloatArray(16)
            for (i in inverseBind.indices) inverseBind[i] = buffer.float
            bones.add(KernBone(String(nameBytes, Charsets.UTF_8), parent, inverseBind))
        }

        var clusters: ByteArray? = null
        if (header.hasClusters) {
            clusters = ByteArray(header.clusterSize.toInt())
            buffer.get(clusters)
        }

        return KernMesh(
            positions, normals, uvs, tangents, indices,
            header.boundsMin, header.boundsMax, layout.lods,
            boneIndices, boneWeights, bones, clusters, layout.materiais.grupos, layout.materiais.materiais
        )
    }

    private data class Header(
        val version: Short,
        val flags: Short,
        val boundsMin: Vec3,
        val boundsMax: Vec3,
        val vertexCount: Int,
        val indexCount: Int,
        val indexType: Byte,
        val lodCount: Int,
        val boneCount: Int,
        val clusterOffset: Long,
        val clusterSize: Long
    ) {
        val hasClusters: Boolean get() = (flags.toInt() and KernFormat.FLAG_HAS_CLUSTERS.toInt()) != 0
        val hasMaterials: Boolean get() = (flags.toInt() and KernFormat.FLAG_HAS_MATERIALS.toInt()) != 0
    }

    private fun parseHeader(bytes: ByteArray): Header {
        require(bytes.size >= KernFormat.HEADER_SIZE) {
            "Arquivo KERN truncado: ${bytes.size} bytes (header exige ${KernFormat.HEADER_SIZE})"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        require(String(magic, Charsets.US_ASCII) == KernFormat.MAGIC) {
            "Arquivo KERN inválido: magic incorreto"
        }
        val version = buffer.short
        require(version == 1.toShort() || version == KernFormat.VERSION) {
            "Versão KERN não suportada: $version (suportadas: 1, ${KernFormat.VERSION})"
        }
        val flags = buffer.short
        val boundsMin = Vec3(buffer.float, buffer.float, buffer.float)
        val boundsMax = Vec3(buffer.float, buffer.float, buffer.float)
        validarBounds(boundsMin, boundsMax, "global")

        val vertexCount = buffer.int
        val indexCount = buffer.int
        val indexType = buffer.get()
        val lodCount = buffer.get().toInt() and 0xFF
        val boneCount = buffer.int
        val vertexBlockSize = buffer.int
        val clusterOffset = buffer.long
        val clusterSize = buffer.long

        require(vertexCount > 0) { "KERN sem vértices" }
        require(indexCount > 0 && indexCount % 3 == 0) {
            "contagem de índices KERN inválida: $indexCount (esperado triângulos)"
        }
        require(indexType == KernFormat.INDEX_TYPE_U16 || indexType == KernFormat.INDEX_TYPE_U32) {
            "tipo de índice KERN desconhecido: $indexType"
        }
        require(indexType != KernFormat.INDEX_TYPE_U16 || vertexCount <= 65535) {
            "KERN usa índices U16 para $vertexCount vértices"
        }
        require(lodCount > 0) { "KERN sem LODs" }
        require(boneCount >= 0) { "contagem de ossos KERN inválida: $boneCount" }
        val allowedFlags = KernFormat.FLAG_HAS_CLUSTERS.toInt() or
            (if (version >= 2.toShort()) KernFormat.FLAG_HAS_MATERIALS.toInt() else 0)
        require((flags.toInt() and allowedFlags.inv()) == 0) { "flags KERN desconhecidas: $flags" }

        val vertexBytes = vertexCount.toLong() * KernFormat.VERTEX_SIZE
        require(vertexBytes <= Int.MAX_VALUE && vertexBlockSize.toLong() == vertexBytes) {
            "vertexBlockSize inconsistente (declara $vertexBlockSize, esperado $vertexBytes)"
        }
        val indexBytes = indexCount.toLong() *
            (if (indexType == KernFormat.INDEX_TYPE_U16) 2L else 4L)
        val coreEnd = KernFormat.HEADER_SIZE.toLong() + vertexBytes +
            lodCount.toLong() * KernFormat.LOD_ENTRY_SIZE + indexBytes
        require(coreEnd <= bytes.size.toLong()) {
            "Arquivo KERN truncado: contagens exigem $coreEnd bytes antes dos ossos; arquivo tem ${bytes.size}"
        }
        val minBoneBytes = boneCount.toLong() * BONE_MIN_SIZE
        require(coreEnd + minBoneBytes <= bytes.size.toLong()) {
            "Arquivo KERN truncado: $boneCount ossos exigem ao menos $minBoneBytes bytes"
        }
        val hasClusters = (flags.toInt() and KernFormat.FLAG_HAS_CLUSTERS.toInt()) != 0
        if (hasClusters) {
            require(clusterOffset >= 0L && clusterSize > 0L && clusterSize <= Int.MAX_VALUE.toLong()) {
                "intervalo do bloco de clusters inválido (offset=$clusterOffset, tamanho=$clusterSize)"
            }
            require(clusterOffset >= coreEnd + minBoneBytes &&
                clusterOffset <= bytes.size.toLong() &&
                clusterSize <= bytes.size.toLong() - clusterOffset
            ) {
                "bloco de clusters fora do arquivo (offset=$clusterOffset, tamanho=$clusterSize)"
            }
        } else {
            require(clusterOffset == 0L && clusterSize == 0L) {
                "offset/tamanho de clusters definido sem flag HAS_CLUSTERS"
            }
        }

        return Header(
            version, flags, boundsMin, boundsMax, vertexCount, indexCount,
            indexType, lodCount, boneCount, clusterOffset, clusterSize
        )
    }

    private data class TabelaMaterial(
        val grupos: List<KernGrupoMaterial>,
        val materiais: List<KernMaterial>
    )

    private data class LayoutValidado(
        val lods: List<KernLod>,
        val materiais: TabelaMaterial
    )

    /**
     * Passagem de forma sem alocar vetores da malha: valida seções, ranges,
     * índices, ossos, clusters e materiais antes de qualquer FloatArray grande.
     */
    private fun preflight(bytes: ByteArray, header: Header): LayoutValidado {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(KernFormat.HEADER_SIZE)
        repeat(header.vertexCount) { v ->
            buffer.position(buffer.position() + 10) // posição quantizada + normal/tangente
            val u = halfToFloat(buffer.short)
            val vUv = halfToFloat(buffer.short)
            require(u.isFinite() && vUv.isFinite()) { "UV não finita no vértice $v" }
            buffer.position(buffer.position() + 10) // skinning reservado + padding
        }

        val lods = ArrayList<KernLod>(header.lodCount)
        for (l in 0 until header.lodCount) {
            val offset = buffer.int
            val count = buffer.int
            val min = Vec3(buffer.float, buffer.float, buffer.float)
            val max = Vec3(buffer.float, buffer.float, buffer.float)
            require(
                offset >= 0 && count >= 0 && count % 3 == 0 &&
                    offset.toLong() + count <= header.indexCount.toLong()
            ) {
                "LOD $l fora do bloco de índices (offset=$offset, count=$count, índices=${header.indexCount})"
            }
            validarBounds(min, max, "LOD $l")
            lods.add(KernLod(offset, count, min, max))
        }

        for (i in 0 until header.indexCount) {
            val index = if (header.indexType == KernFormat.INDEX_TYPE_U16) {
                buffer.short.toInt() and 0xFFFF
            } else {
                buffer.int
            }
            require(index in 0 until header.vertexCount) {
                "índice $i aponta para vértice $index fora de 0..${header.vertexCount - 1}"
            }
        }

        for (b in 0 until header.boneCount) {
            exigirRestante(buffer, BONE_MIN_SIZE, "cabeçalho do osso $b")
            val parent = buffer.int
            val nameLength = buffer.short.toInt() and 0xFFFF
            exigirRestante(buffer, nameLength.toLong() + 64L, "dados do osso $b")
            require(parent == -1 || parent in 0 until header.boneCount) {
                "osso $b aponta para pai $parent inexistente"
            }
            require(parent != b) { "osso $b não pode ser pai de si mesmo" }
            buffer.position(buffer.position() + nameLength)
            repeat(16) {
                require(buffer.float.isFinite()) { "matriz inverseBind não finita no osso $b" }
            }
        }

        val actualClusterOffset = buffer.position().toLong()
        if (header.hasClusters) {
            exigirRestante(buffer, header.clusterSize, "bloco de clusters")
            buffer.position(buffer.position() + header.clusterSize.toInt())
        }
        val materialStart = buffer.position()
        val materiais = if (header.hasMaterials) lerMateriais(buffer, header.indexCount)
        else TabelaMaterial(emptyList(), emptyList())
        require(!buffer.hasRemaining()) { "arquivo KERN tem ${buffer.remaining()} bytes excedentes" }

        if (header.hasClusters) {
            val tamanhoMateriais = buffer.position().toLong() - materialStart
            val offsetLegadoWriter = actualClusterOffset + tamanhoMateriais
            require(
                header.clusterOffset == actualClusterOffset ||
                    (header.hasMaterials && header.clusterOffset == offsetLegadoWriter)
            ) {
                "clusterBlockOffset inconsistente: declarado ${header.clusterOffset}, " +
                    "real $actualClusterOffset (legado possível $offsetLegadoWriter)"
            }
        }
        return LayoutValidado(lods, materiais)
    }

    private fun lerMateriais(buffer: ByteBuffer, indexCount: Int): TabelaMaterial {
        exigirRestante(buffer, 2, "contagem de grupos de material")
        val groupCount = buffer.short.toInt() and 0xFFFF
        exigirRestante(buffer, groupCount.toLong() * 12L + 2L, "tabela de grupos de material")
        val grupos = ArrayList<KernGrupoMaterial>(groupCount)
        for (g in 0 until groupCount) {
            val offset = buffer.int
            val count = buffer.int
            val materialIndex = buffer.int
            require(
                offset >= 0 && count >= 0 && count % 3 == 0 &&
                    offset.toLong() + count <= indexCount.toLong()
            ) {
                "grupo de material $g fora do bloco de índices (offset=$offset, count=$count)"
            }
            grupos.add(KernGrupoMaterial(offset, count, materialIndex))
        }

        val materialCount = buffer.short.toInt() and 0xFFFF
        exigirRestante(buffer, materialCount.toLong() * 16L, "tabela de materiais")
        val materiais = ArrayList<KernMaterial>(materialCount)
        for (m in 0 until materialCount) {
            exigirRestante(buffer, 1, "nome do material $m")
            val nameLength = buffer.get().toInt() and 0xFF
            exigirRestante(buffer, nameLength.toLong() + 15L, "material $m")
            val name = ByteArray(nameLength)
            buffer.get(name)
            val r = (buffer.get().toInt() and 0xFF) / 255f
            val g = (buffer.get().toInt() and 0xFF) / 255f
            val b = (buffer.get().toInt() and 0xFF) / 255f
            val a = (buffer.get().toInt() and 0xFF) / 255f
            val metallic = buffer.float
            val roughness = buffer.float
            require(metallic.isFinite() && roughness.isFinite()) {
                "material $m tem metallic/roughness não finitos"
            }
            val materialFlags = buffer.get().toInt() and 0xFF
            require((materialFlags and 0xF8) == 0) { "flags desconhecidas no material $m: $materialFlags" }
            val basePathLength = buffer.get().toInt() and 0xFF
            exigirRestante(buffer, basePathLength.toLong() + 1L, "caminhos do material $m")
            val basePath = ByteArray(basePathLength)
            buffer.get(basePath)
            val normalPathLength = buffer.get().toInt() and 0xFF
            exigirRestante(buffer, normalPathLength.toLong(), "caminho normal do material $m")
            val normalPath = ByteArray(normalPathLength)
            buffer.get(normalPath)
            materiais.add(
                KernMaterial(
                    String(name, Charsets.UTF_8), r, g, b, a, metallic, roughness,
                    (materialFlags and 1) != 0, (materialFlags and 2) != 0, (materialFlags and 4) != 0,
                    String(basePath, Charsets.UTF_8), String(normalPath, Charsets.UTF_8)
                )
            )
        }
        require(groupCount == 0 || materialCount > 0) { "grupos de material sem materiais" }
        for ((i, group) in grupos.withIndex()) {
            require(group.materialIndex in materiais.indices) {
                "grupo de material $i aponta para material ${group.materialIndex} inexistente"
            }
        }
        return TabelaMaterial(grupos, materiais)
    }

    private fun exigirRestante(buffer: ByteBuffer, bytes: Long, campo: String) {
        require(bytes >= 0L && bytes <= buffer.remaining().toLong()) {
            "Arquivo KERN truncado em $campo (precisa de $bytes bytes, restam ${buffer.remaining()})"
        }
    }

    private fun validarBounds(min: Vec3, max: Vec3, campo: String) {
        require(
            min.x.isFinite() && min.y.isFinite() && min.z.isFinite() &&
                max.x.isFinite() && max.y.isFinite() && max.z.isFinite() &&
                min.x <= max.x && min.y <= max.y && min.z <= max.z
        ) { "bounds inválidos em $campo" }
    }

    /** Leitura e validação rápida (sem dequantizar tudo). */
    fun readHeader(bytes: ByteArray): Triple<Short, Int, Int> {
        val header = parseHeader(bytes)
        return Triple(header.version, header.vertexCount, header.indexCount)
    }

    private const val BONE_MIN_SIZE = 4L + 2L + 64L

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
