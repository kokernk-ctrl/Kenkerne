package com.kenkern.engine.assets.browser

import com.kenkern.engine.assets.image.KimgFormat
import com.kenkern.engine.assets.image.KimgFormato
import com.kenkern.engine.assets.image.KimgImagem
import com.kenkern.engine.assets.image.KimgMip
import com.kenkern.engine.assets.image.KimgWriter
import com.kenkern.engine.assets.mesh.KernLod
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files

class AssetPreviewMetadataTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `KERN preview reports base triangles rather than counting all LODs together`() {
        val builder = KernWriter.Builder().apply {
            vertex(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
            vertex(1f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 0f)
            vertex(0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f)
            triangle(0, 1, 2)
            triangle(0, 1, 2)
            lods.add(KernLod(0, 3, Vec3(), Vec3(1f, 1f, 0f)))
            lods.add(KernLod(3, 3, Vec3(), Vec3(1f, 1f, 0f)))
        }
        val bytes = KernWriter.write(builder.build())
        assertEquals(MeshPreviewMetadata(2, 3, 1, 2), read(bytes, AssetPreviewMetadata::mesh))
        assertThrows(IllegalArgumentException::class.java) { read(bytes.copyOf(bytes.size - 1), AssetPreviewMetadata::mesh) }
        assertThrows(EOFException::class.java) { read(bytes.copyOf(32), AssetPreviewMetadata::mesh) }
    }

    @Test fun `base ETC2 RGB KIMG produces opaque pixels`() {
        val image = KimgImagem(KimgFormato.ETC2_RGB, 4, 4, true, false, false, listOf(KimgMip(4, 4, 0, ByteArray(8))))
        val pixels = read(KimgWriter.write(image), AssetPreviewMetadata::texture)
        assertEquals(4, pixels.width)
        assertEquals(4, pixels.height)
        assertEquals(16, pixels.argb.size)
        assertTrue(pixels.argb.all { it ushr 24 == 255 })
    }

    @Test fun `Deflate KIMG uses a small mip without losing original dimensions or alpha`() {
        fun rgba(w: Int, h: Int, red: Int) = ByteArray(w * h * 4) { i ->
            when (i % 4) { 0 -> red.toByte(); 3 -> 128.toByte(); else -> 0 }
        }
        val image = KimgImagem(KimgFormato.RGBA8_RAW, 1024, 16, false, true, false, listOf(
            KimgMip(1024, 16, 0, rgba(1024, 16, 40)), KimgMip(512, 8, 0, rgba(512, 8, 65))
        ))
        val bytes = KimgWriter.write(image, comprimirEmDisco = true)
        assertEquals(KimgFormat.VERSAO_DEFLATE, ByteBuffer.wrap(bytes).getShort(4))
        val pixels = read(bytes, AssetPreviewMetadata::texture)
        assertEquals(1024, pixels.originalWidth)
        assertEquals(16, pixels.originalHeight)
        assertEquals(512, pixels.width)
        assertEquals(8, pixels.height)
        assertTrue(pixels.argb.all { it == 0x80410000.toInt() })
        assertThrows(IllegalArgumentException::class.java) { read(bytes.copyOf(bytes.size - 1), AssetPreviewMetadata::texture) }
    }

    private fun <T> read(bytes: ByteArray, preview: (SeekableByteChannel) -> T): T {
        val file = temp.newFile().apply { writeBytes(bytes) }
        return Files.newByteChannel(file.toPath()).use(preview)
    }
}
