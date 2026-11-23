package com.kenkern.engine.render

import com.kenkern.engine.assets.image.KimgFormato
import com.kenkern.engine.assets.image.KimgImagem
import com.kenkern.engine.assets.image.KimgMip
import com.kenkern.engine.assets.image.KimgWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class TextureLibraryContextLossTest {

    @Test
    fun `cache limpa handle GPU antigo e permite reenviar o mesmo path`() {
        val root = createTempDir(prefix = "texture-context-loss")
        try {
            val relativePath = "assets/textures/one.kimg"
            val file = File(root, relativePath)
            file.parentFile!!.mkdirs()
            file.writeBytes(
                KimgWriter.write(
                    KimgImagem(
                        KimgFormato.RGBA8_RAW,
                        1,
                        1,
                        srgb = true,
                        temAlfa = true,
                        normalMap = false,
                        mips = listOf(KimgMip(1, 1, 0, ByteArray(4)))
                    )
                )
            )

            TextureLibrary.limpar()
            TextureLibrary.baseDir = root
            val firstBackend = Mockito.mock(RenderBackend::class.java)
            Mockito.`when`(firstBackend.createTextureFromKimg(Mockito.any(ByteArray::class.java)))
                .thenReturn(17)
            TextureLibrary.marcarNecessario(relativePath)
            assertEquals(1, TextureLibrary.processarPendentes(firstBackend))
            assertEquals(17, TextureLibrary.handleDe(relativePath))

            // Equivale à invalidação dos handles ao criar um novo contexto EGL.
            TextureLibrary.limpar()
            assertEquals(0, TextureLibrary.handleDe(relativePath))
            val newBackend = Mockito.mock(RenderBackend::class.java)
            Mockito.`when`(newBackend.createTextureFromKimg(Mockito.any(ByteArray::class.java)))
                .thenReturn(29)
            TextureLibrary.marcarNecessario(relativePath)
            assertEquals(1, TextureLibrary.processarPendentes(newBackend))
            assertEquals(29, TextureLibrary.handleDe(relativePath))
            assertNotEquals(
                "o novo contexto deve obter um handle novo",
                17,
                TextureLibrary.handleDe(relativePath)
            )
            Mockito.verify(newBackend).createTextureFromKimg(Mockito.any(ByteArray::class.java))
        } finally {
            TextureLibrary.limpar()
            TextureLibrary.baseDir = null
            root.deleteRecursively()
        }
    }
}
