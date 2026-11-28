package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F4 (v0.1.31) — importar de QUALQUER pasta: roteamento por extensão para a
 * árvore F2 (modelos → assets/models, texturas → assets/textures, áudio →
 * assets/audio, fontes → assets/fonts, vídeo → assets/video, scripts →
 * scripts), original PRESERVADO, nome duplicado com sufixo " 2", árvore
 * RECURSIVA com contagem e cancelamento, resolvedor local para glTF com
 * .bin/texturas externas (zip-slip bloqueado).
 */
class ImportadorArquivosTest {

    private fun novaRaiz(): File {
        val raiz = File("/tmp/kenkern-f4-" + System.nanoTime())
        raiz.mkdirs()
        return raiz
    }

    @Test
    fun `roteamento por extensão vai para a pasta certa com o original preservado`() {
        val raiz = novaRaiz()
        val origem = File("/tmp/kenkern-f4-origem-" + System.nanoTime())
        origem.mkdirs()
        try {
            File(origem, "heroi.glb").writeBytes(byteArrayOf(1, 2, 3))
            File(origem, "pele.png").writeBytes(byteArrayOf(4, 5))
            File(origem, "passo.ogg").writeBytes(byteArrayOf(6))
            File(origem, "inter.ttf").writeBytes(byteArrayOf(7))
            File(origem, "cena.mp4").writeBytes(byteArrayOf(8))
            File(origem, "porta.lua").writeText("-- lua")

            val r1 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "heroi.glb"))
            assertTrue("glb → assets/models: $r1", r1 is ImportadorArquivos.CopiaResult.Ok && r1.relativo == "assets/models/heroi.glb")
            val r2 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "pele.png"))
            assertTrue("png → assets/textures: $r2", r2 is ImportadorArquivos.CopiaResult.Ok && r2.relativo == "assets/textures/pele.png")
            val r3 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "passo.ogg"))
            assertTrue("ogg → assets/audio: $r3", r3 is ImportadorArquivos.CopiaResult.Ok && r3.relativo == "assets/audio/passo.ogg")
            val r4 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "inter.ttf"))
            assertTrue("ttf → assets/fonts: $r4", r4 is ImportadorArquivos.CopiaResult.Ok && r4.relativo == "assets/fonts/inter.ttf")
            val r5 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "cena.mp4"))
            assertTrue("mp4 → assets/video: $r5", r5 is ImportadorArquivos.CopiaResult.Ok && r5.relativo == "assets/video/cena.mp4")
            val r6 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "porta.lua"))
            assertTrue("lua → scripts: $r6", r6 is ImportadorArquivos.CopiaResult.Ok && r6.relativo == "scripts/porta.lua")

            // original PRESERVADO (a fonte continua; a cópia é byte a byte)
            assertTrue(File(origem, "heroi.glb").isFile)
            assertEquals(byteArrayOf(1, 2, 3).toList(), File(raiz, "assets/models/heroi.glb").readBytes().toList())

            // duplicado ganha sufixo " 2"
            val r7 = ImportadorArquivos.copiarPreservado(raiz, File(origem, "heroi.glb"))
            assertTrue(r7 is ImportadorArquivos.CopiaResult.Ok && r7.relativo == "assets/models/heroi 2.glb")
        } finally {
            raiz.deleteRecursively()
            origem.deleteRecursively()
        }
    }

    @Test
    fun `importar pasta inteira é recursivo com contagem e cancelamento`() {
        val raiz = novaRaiz()
        val origem = File("/tmp/kenkern-f4-arvore-" + System.nanoTime())
        origem.mkdirs()
        try {
            File(origem, "sub").mkdirs()
            File(origem, "a.png").writeBytes(byteArrayOf(1))
            File(origem, "sub/b.ogg").writeBytes(byteArrayOf(2))
            File(origem, "sub/c.ttf").writeBytes(byteArrayOf(3))
            File(origem, "sub/d.lua").writeText("-- d")

            var totalReportado = 0
            val resumo = ImportadorArquivos.importarArvore(raiz, origem, onProgresso = { _, t -> totalReportado = t })
            assertEquals(4, resumo.copiados)
            assertEquals(0, resumo.pulados)
            assertFalse(resumo.cancelado)
            assertEquals(4, totalReportado)
            assertTrue(File(raiz, "assets/textures/a.png").isFile)
            assertTrue(File(raiz, "assets/audio/b.ogg").isFile)
            assertTrue(File(raiz, "assets/fonts/c.ttf").isFile)
            assertTrue(File(raiz, "scripts/d.lua").isFile)
            assertTrue(resumo.texto().contains("4 copiado"))

            // cancelamento ANTES do fim
            val origem2 = File("/tmp/kenkern-f4-arvore2-" + System.nanoTime())
            origem2.mkdirs()
            for (i in 0 until 20) File(origem2, "f$i.png").writeBytes(byteArrayOf(1))
            val cancelado = ImportadorArquivos.importarArvore(raiz, origem2, cancelado = { true })
            assertTrue("cancelado devolve estado", cancelado.cancelado)
            origem2.deleteRecursively()
        } finally {
            raiz.deleteRecursively()
            origem.deleteRecursively()
        }
    }

    @Test
    fun `resolvedor local lê bin e texturas relativas e bloqueia zip-slip`() {
        val dirGltf = File("/tmp/kenkern-f4-gltf-" + System.nanoTime())
        dirGltf.mkdirs()
        try {
            File(dirGltf, "heroi.bin").writeBytes(byteArrayOf(9, 9))
            File(dirGltf, "texturas").mkdirs()
            File(dirGltf, "texturas/pele.png").writeBytes(byteArrayOf(8, 8))

            val resolver = ImportadorArquivos.ResolvedorArquivos(dirGltf)
            assertEquals(byteArrayOf(9, 9).toList(), resolver.bytes("heroi.bin")!!.toList())
            assertEquals(byteArrayOf(8, 8).toList(), resolver.bytes("texturas/pele.png")!!.toList())
            assertEquals(null, resolver.bytes("../fora.png"))
            assertEquals(null, resolver.bytes("inexistente.bin"))
        } finally {
            dirGltf.deleteRecursively()
        }
    }
}
