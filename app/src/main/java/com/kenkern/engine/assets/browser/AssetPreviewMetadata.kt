package com.kenkern.engine.assets.browser

import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.KimgFormat
import com.kenkern.engine.assets.image.KimgFormato
import com.kenkern.engine.assets.image.KimgReader
import com.kenkern.engine.assets.mesh.KernFormat
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

internal class PreviewTooLarge : IOException()
internal class PreviewUnsupported : IOException()

internal data class MeshPreviewMetadata(val version: Int, val vertices: Int, val triangles: Int, val lods: Int)
internal data class TexturePreviewPixels(
    val originalWidth: Int,
    val originalHeight: Int,
    val width: Int,
    val height: Int,
    val argb: IntArray
)

/** Only metadata and (for KIMG) one bounded mip; never loads the whole asset. */
internal object AssetPreviewMetadata {
    const val THUMBNAIL_EDGE = 512
    private const val MAX_DECODE_EDGE = 1024
    private const val MAX_ENCODED_MIP = 8 * 1024 * 1024

    fun mesh(file: SeekableByteChannel): MeshPreviewMetadata {
        val header = file.readAt(0, KernFormat.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        require(header.magic() == KernFormat.MAGIC)
        val version = header.u16()
        if (version != 1 && version != KernFormat.VERSION.toInt()) throw PreviewUnsupported()
        val flags = header.u16()
        val min = FloatArray(3) { header.float }
        repeat(3) { axis ->
            val max = header.float
            require(min[axis].isFinite() && max.isFinite() && min[axis] <= max)
        }
        val vertices = header.int
        val indices = header.int
        val indexSize = when (header.get()) {
            KernFormat.INDEX_TYPE_U16 -> 2
            KernFormat.INDEX_TYPE_U32 -> 4
            else -> throw IllegalArgumentException("Unknown KERN index type")
        }
        val lodCount = header.get().toInt() and 0xff
        val boneCount = header.int
        val vertexBytes = vertices.toLong() * KernFormat.VERTEX_SIZE
        require(vertices > 0 && indices >= 0 && indices % 3 == 0 && boneCount >= 0 && header.int.toLong() == vertexBytes)
        val clusterOffset = header.long
        val clusterBytes = header.long
        val lodOffset = KernFormat.HEADER_SIZE + vertexBytes
        val tableBytes = lodCount * KernFormat.LOD_ENTRY_SIZE
        val minSize = lodOffset + tableBytes + indices.toLong() * indexSize + boneCount.toLong() * 70
        val fileSize = file.size()
        require(minSize <= fileSize)
        var minEnd = minSize
        if ((flags and KernFormat.FLAG_HAS_CLUSTERS.toInt()) != 0) {
            require(clusterOffset in minSize..fileSize && clusterBytes in 0..(fileSize - clusterOffset))
            minEnd += clusterBytes
        }
        if (version >= 2 && (flags and KernFormat.FLAG_HAS_MATERIALS.toInt()) != 0) require(minEnd + 4 <= fileSize)
        val table = file.readAt(lodOffset, tableBytes).order(ByteOrder.LITTLE_ENDIAN)
        var baseIndices = indices
        repeat(lodCount) { lod ->
            val offset = table.int
            val count = table.int
            require(offset in 0..indices && count in 0..(indices - offset) && count % 3 == 0)
            if (lod == 0) baseIndices = count
            table.position(table.position() + 24) // LOD bounds
        }
        return MeshPreviewMetadata(version, vertices, baseIndices / 3, lodCount)
    }

    fun texture(file: SeekableByteChannel): TexturePreviewPixels {
        val header = file.readAt(0, KimgFormat.HEADER_SIZE)
        require(header.magic() == KimgFormat.MAGIC)
        val version = header.u16()
        if (version != KimgFormat.VERSAO.toInt() && version != KimgFormat.VERSAO_DEFLATE.toInt()) throw PreviewUnsupported()
        val format = KimgFormato.de(header.short) ?: throw PreviewUnsupported()
        val width = header.int
        val height = header.int
        val mipCount = header.u16()
        val deflated = (header.u16() and KimgFormat.FLAG_DEFLATE.toInt()) != 0
        require(width in 1..16384 && height in 1..16384 && mipCount in 1..16)
        require(!deflated || version == KimgFormat.VERSAO_DEFLATE.toInt())
        val table = file.readAt(KimgFormat.HEADER_SIZE.toLong(), mipCount * KimgFormat.MIP_ENTRY_SIZE)
        val minOffset = KimgFormat.HEADER_SIZE + table.capacity()
        val fileSize = file.size()
        var best: Mip? = null
        repeat(mipCount) { level ->
            val offset = table.int.toLong()
            val bytes = table.int
            val w = maxOf(1, width shr level)
            val h = maxOf(1, height shr level)
            val expected = KimgReader.tamanhoBloco(format, w, h)
            require(offset >= minOffset && bytes > 0 && offset + bytes <= fileSize && (deflated || bytes == expected))
            val edge = maxOf(w, h)
            if (edge <= MAX_DECODE_EDGE && bytes <= MAX_ENCODED_MIP &&
                (best == null || maxOf(best!!.width, best!!.height) > THUMBNAIL_EDGE && edge <= THUMBNAIL_EDGE)
            ) best = Mip(w, h, offset, bytes, expected)
        }
        if (format !in listOf(KimgFormato.ETC2_RGB, KimgFormato.ETC2_RGBA, KimgFormato.RGBA8_RAW)) throw PreviewUnsupported()
        val mip = best ?: throw PreviewTooLarge()
        val encoded = file.readAt(mip.offset, mip.bytes).array()
        val data = if (deflated) KimgReader.inflar(encoded, 0, encoded.size, mip.expected) else encoded
        require(data.size == mip.expected)
        val rgba = when (format) {
            KimgFormato.ETC2_RGB -> Etc2.decodificarRgb(data, mip.width, mip.height)
            KimgFormato.ETC2_RGBA -> Etc2.decodificarRgba(data, mip.width, mip.height)
            KimgFormato.RGBA8_RAW -> data
            else -> throw PreviewUnsupported()
        }
        val step = (maxOf(mip.width, mip.height) + THUMBNAIL_EDGE - 1) / THUMBNAIL_EDGE
        val w = (mip.width + step - 1) / step
        val h = (mip.height + step - 1) / step
        val argb = IntArray(w * h) { i ->
            val start = ((i / w) * step * mip.width + (i % w) * step) * 4
            ((rgba[start + 3].toInt() and 255) shl 24) or ((rgba[start].toInt() and 255) shl 16) or
                ((rgba[start + 1].toInt() and 255) shl 8) or (rgba[start + 2].toInt() and 255)
        }
        return TexturePreviewPixels(width, height, w, h, argb)
    }

    private data class Mip(val width: Int, val height: Int, val offset: Long, val bytes: Int, val expected: Int)

    private fun SeekableByteChannel.readAt(offset: Long, bytes: Int): ByteBuffer {
        if (offset > size() || bytes > size() - offset) throw EOFException()
        val result = ByteBuffer.allocate(bytes)
        position(offset)
        while (result.hasRemaining()) if (read(result) <= 0) throw EOFException()
        result.flip()
        return result
    }

    private fun ByteBuffer.magic() = String(ByteArray(4).also { get(it) }, Charsets.US_ASCII)
    private fun ByteBuffer.u16() = short.toInt() and 0xffff
}
