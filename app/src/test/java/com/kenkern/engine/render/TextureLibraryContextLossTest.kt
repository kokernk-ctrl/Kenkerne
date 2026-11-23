package com.kenkern.engine.render

import com.kenkern.engine.assets.image.KimgFormato
import com.kenkern.engine.assets.image.KimgImagem
import com.kenkern.engine.assets.image.KimgMip
import com.kenkern.engine.assets.image.KimgWriter
import com.kenkern.engine.core.Scene
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque

class TextureLibraryContextLossTest {
    private class Backend(private val handle: Int) : RenderBackend by RenderBackendTestDouble() {
        val uploaded = mutableListOf<KimgImagem>()
        val destroyed = mutableListOf<Int>()
        var refuseEtc2 = false
        override fun uploadPreparedTexture(image: KimgImagem): Int {
            uploaded += image
            return if (refuseEtc2 && image.formato == KimgFormato.ETC2_RGB) TEXTURE_RETRY_AS_RGBA else handle
        }
        override fun destroyTexture(handle: Int) { destroyed += handle }
    }
    private class Harness {
        val jobs = ArrayDeque<Runnable>()
        val stream = ProjectAssetStream(jobs::add, { jobs.remove(it) })
        fun frame(scope: ProjectAssetScope?, backend: Backend): Int {
            stream.beginFrame(scope, backend)
            return stream.texture(PATH)
        }
        fun work() = jobs.removeFirst().run()
        fun load(scope: ProjectAssetScope, backend: Backend): Int {
            assertEquals(0, frame(scope, backend)) // discovery, no synchronous upload
            assertEquals(0, frame(scope, backend)) // dispatch
            work()
            return frame(scope, backend)
        }
    }
    @Test fun `context loss drops old handle and reloads in the new backend`() = withRoot { root ->
        val h = Harness()
        val scope = ProjectAssetScope(root)
        write(root, 17)
        val first = Backend(17)
        val second = Backend(29)
        assertEquals(17, h.load(scope, first))
        assertEquals(1, first.uploaded.size)
        h.stream.abandonContext()
        assertEquals(0, h.stream.texture(PATH))
        assertEquals(29, h.load(scope, second))
        assertNotEquals(17, h.stream.texture(PATH))
        assertEquals(1, second.uploaded.size)
        assertTrue("never delete an old EGL name in the new context", second.destroyed.isEmpty())
    }

    @Test fun `stale same-path completion cannot cross a project switch`() = withRoot { first ->
        withRoot { second ->
            write(first, 12); write(second, 34)
            val a = Scene("first"); val b = Scene("second")
            ProjectAssetBindings.attach(a, first); ProjectAssetBindings.attach(b, second)
            assertNull(ProjectAssetBindings.of(Scene("play copy")))
            val h = Harness(); val backend = Backend(9)
            h.frame(ProjectAssetBindings.of(a), backend)
            h.frame(ProjectAssetBindings.of(a), backend)
            h.work() // result from A is ready but has not reached GL.
            ProjectAssetBindings.detach(a)
            assertNull(ProjectAssetBindings.of(a))
            assertEquals(9, h.load(checkNotNull(ProjectAssetBindings.of(b)), backend))
            assertEquals(1, backend.uploaded.size)
            assertEquals(34, backend.uploaded.single().mips.single().dados.first().toInt())
            h.frame(null, backend)
            assertEquals(listOf(9), backend.destroyed)
        }
    }

    @Test fun `path invalidation retries a missing asset and ETC2 fallback decodes on a second job`() = withRoot { root ->
        val h = Harness(); val scope = ProjectAssetScope(root); val backend = Backend(42).apply { refuseEtc2 = true }
        assertEquals(0, h.load(scope, backend)) // file doesn't exist
        write(root, 0, KimgFormato.ETC2_RGB)
        repeat(5) { assertEquals(0, h.frame(scope, backend)) }
        assertTrue(h.jobs.isEmpty())
        scope.invalidate(listOf(PATH))
        assertEquals(0, h.load(scope, backend)) // compressed attempt only
        assertEquals(listOf(KimgFormato.ETC2_RGB), backend.uploaded.map { it.formato })
        h.work()
        assertEquals(42, h.frame(scope, backend))
        assertEquals(listOf(KimgFormato.ETC2_RGB, KimgFormato.RGBA8_RAW), backend.uploaded.map { it.formato })
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("project-stream-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun write(root: File, value: Byte, format: KimgFormato = KimgFormato.RGBA8_RAW) {
        val file = File(root, PATH)
        file.parentFile!!.mkdirs()
        val bytes = ByteArray(if (format == KimgFormato.RGBA8_RAW) 64 else 8) { value }
        file.writeBytes(KimgWriter.write(KimgImagem(format, 4, 4, false, false, false, listOf(KimgMip(4, 4, 0, bytes)))))
    }
    private companion object { const val PATH = "assets/textures/probe.kimg" }
}
