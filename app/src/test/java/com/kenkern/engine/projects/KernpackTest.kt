package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Teste JVM do .KERNPACK (F2.6): empacotar/desempacotar um projeto inteiro.
 *
 *  1. ida e volta: conteúdo, pastas vazias e ordem de recuperação são preservados;
 *  2. build/ é pulado na exportação (artefato gerado);
 *  3. nomeDoProjeto lê o name= do project.kenkern sem extrair;
 *  4. ZIP-SLIP: entradas hostis (../, /abs) lançam — nunca gravam fora;
 *  5. pacote sem project.kenkern não dá nome.
 */
class KernpackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun projetoBase(): File {
        val root = tmp.newFolder("proj")
        File(root, "scenes").mkdirs()
        File(root, "assets").mkdirs()
        File(root, "assets/vazio/subpasta").mkdirs()
        File(root, "build").mkdirs()
        File(root, "project.kenkern").writeText(
            "KENKERN_PROJECT\nformat=1\nengine=teste\nname=\"Meu Jogo\"\ncreated=\"x\"\nscene=scenes/cena.ck\n"
        )
        File(root, "scenes/cena.ck").writeText("CK1\nSCENE name=\"Cena\"\nENDCK\n")
        File(root, "scenes/cena.autosave.ck").writeText("CK1\nSCENE name=\"Auto\"\nENDCK\n")
        File(root, "assets/binario.bin").writeBytes(byteArrayOf(9, 8, 7, 6))
        File(root, "assets/ ").writeBytes(byteArrayOf())
        File(root, "build/lixo.tmp").writeText("gerado")
        return root
    }

    @Test
    fun `ida e volta do kernpack preserva arquivos e pula build`() {
        val origem = projetoBase()
        val bytes = ByteArrayOutputStream().also {
            assertEquals(9, Kernpack.empacotar(origem, it)) // 5 arquivos + 4 pastas
        }.toByteArray()

        val destino = tmp.newFolder("destino")
        val arquivos = Kernpack.desempacotar(ByteArrayInputStream(bytes), destino)

        assertEquals(5, arquivos)
        assertEquals(
            File(origem, "project.kenkern").readText(),
            File(destino, "project.kenkern").readText()
        )
        assertEquals(
            File(origem, "scenes/cena.ck").readText(),
            File(destino, "scenes/cena.ck").readText()
        )
        assertEquals(
            File(origem, "scenes/cena.autosave.ck").readText(),
            File(destino, "scenes/cena.autosave.ck").readText()
        )
        assertEquals(
            byteArrayOf(9, 8, 7, 6).toList(),
            File(destino, "assets/binario.bin").readBytes().toList()
        )
        assertTrue(File(destino, "assets/ ").isFile)
        assertEquals(0L, File(destino, "assets/ ").length())
        val pastaVazia = File(destino, "assets/vazio/subpasta")
        assertTrue(pastaVazia.isDirectory)
        assertEquals(0, pastaVazia.listFiles()!!.size)
        assertTrue("build/ não deve existir no destino", !File(destino, "build").exists())
    }

    @Test
    fun `recuperacao mantem autosave mais novo mesmo dentro do mesmo segundo`() {
        verificaCronologia(123, 456)
    }

    @Test
    fun `recuperacao mantem cena principal mais nova mesmo dentro do mesmo segundo`() {
        verificaCronologia(456, 123)
    }

    @Test
    fun `recuperacao mantem a cena principal quando ha empate`() {
        verificaCronologia(456, 456)
    }

    private fun verificaCronologia(msCena: Long, msAuto: Long) {
        val origem = projetoBase()
        val pasta = File(origem, "scenes")
        val cena = File(pasta, "cena.ck")
        val auto = File(pasta, "cena.autosave.ck")
        val base = 1_700_000_000_000L
        val mtimePasta = base + 789
        for ((f, mtime) in listOf(cena to base + msCena, auto to base + msAuto, pasta to mtimePasta)) {
            Files.setLastModifiedTime(f.toPath(), FileTime.fromMillis(mtime))
            assertEquals(mtime, f.lastModified())
        }
        val cenaAntes = cena.readText()
        val autoAntes = auto.readText()
        val bytes = ByteArrayOutputStream().also { Kernpack.empacotar(origem, it) }.toByteArray()
        val destino = tmp.newFolder("destino")
        Kernpack.desempacotar(ByteArrayInputStream(bytes), destino)
        val cenaImportada = File(destino, "scenes/cena.ck")
        val autoImportado = File(destino, "scenes/cena.autosave.ck")

        assertEquals(base + msCena, cenaImportada.lastModified())
        assertEquals(base + msAuto, autoImportado.lastModified())
        assertEquals(mtimePasta, File(destino, "scenes").lastModified())
        // Mesmo critério de recuperação usado ao abrir o projeto: só vence se for mais novo.
        val recuperada = if (autoImportado.lastModified() > cenaImportada.lastModified()) {
            autoImportado
        } else {
            cenaImportada
        }
        assertEquals(if (msAuto > msCena) autoAntes else cenaAntes, recuperada.readText())
        assertEquals(cenaAntes, cena.readText())
        assertEquals(autoAntes, auto.readText())
        assertEquals(base + msCena, cena.lastModified())
        assertEquals(base + msAuto, auto.lastModified())
        assertEquals(mtimePasta, pasta.lastModified())
    }

    @Test
    fun `pacote legado usa o horario padrao do ZIP`() {
        val cena = 1_700_000_000_000L
        val auto = cena + 2_000
        val bytes = ByteArrayOutputStream().also {
            ZipOutputStream(it).use { z ->
                for ((nome, hora) in listOf("scenes/cena.ck" to cena, "scenes/cena.autosave.ck" to auto)) {
                    z.putNextEntry(ZipEntry(nome).apply { time = hora })
                    z.write(nome.toByteArray())
                    z.closeEntry()
                }
            }
        }.toByteArray()
        val destino = tmp.newFolder("destino")
        assertEquals(2, Kernpack.desempacotar(ByteArrayInputStream(bytes), destino))
        assertEquals(cena, File(destino, "scenes/cena.ck").lastModified())
        assertEquals(auto, File(destino, "scenes/cena.autosave.ck").lastModified())
    }

    @Test
    fun `conflito na extracao falha sem sobrescrever arquivo existente`() {
        val origem = projetoBase()
        val bytes = ByteArrayOutputStream().also { Kernpack.empacotar(origem, it) }.toByteArray()
        val destino = tmp.newFolder("destino")
        val existente = File(destino, "project.kenkern").apply { writeText("original") }
        val mtime = existente.lastModified()
        assertThrows(IOException::class.java) {
            Kernpack.desempacotar(ByteArrayInputStream(bytes), destino)
        }
        assertEquals("original", existente.readText())
        assertEquals(mtime, existente.lastModified())
    }

    @Test
    fun `exportar pasta inexistente e erro em vez de gerar pacote vazio`() {
        assertThrows(IllegalArgumentException::class.java) {
            Kernpack.empacotar(File(tmp.root, "nao-existe"), ByteArrayOutputStream())
        }
    }

    @Test
    fun `nomeDoProjeto lê o name sem extrair`() {
        val origem = projetoBase()
        val bytes = ByteArrayOutputStream().also { Kernpack.empacotar(origem, it) }.toByteArray()
        assertEquals("Meu Jogo", Kernpack.nomeDoProjeto(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `pacote sem project kenkern devolve nome nulo`() {
        val zip = ByteArrayOutputStream().also {
            ZipOutputStream(it).use { z ->
                z.putNextEntry(ZipEntry("cena.ck"))
                z.write("CK1".toByteArray())
                z.closeEntry()
            }
        }
        assertNull(Kernpack.nomeDoProjeto(ByteArrayInputStream(zip.toByteArray())))
    }

    @Test
    fun `zip-slip - entrada com dotdot lanca e nao grava fora`() {
        val destino = tmp.newFolder("alvo")
        val maldoso = ByteArrayOutputStream().also {
            ZipOutputStream(it).use { z ->
                z.putNextEntry(ZipEntry("../../evil.txt"))
                z.write("lixo".toByteArray())
                z.closeEntry()
            }
        }
        try {
            Kernpack.desempacotar(ByteArrayInputStream(maldoso.toByteArray()), destino)
            fail("deveria lançar IllegalArgumentException")
        } catch (esperado: IllegalArgumentException) {
            assertNotNull(esperado.message)
        }
        assertTrue("nada pode ter sido gravado", destino.listFiles()?.isEmpty() ?: true)
        // e NADA fora do destino
        assertTrue(!File(destino.parentFile, "evil.txt").exists())
    }

    @Test
    fun `zip-slip - entrada absoluta lanca`() {
        val destino = tmp.newFolder("alvo2")
        val maldoso = ByteArrayOutputStream().also {
            ZipOutputStream(it).use { z ->
                z.putNextEntry(ZipEntry("/etc/evil"))
                z.write("x".toByteArray())
                z.closeEntry()
            }
        }
        try {
            Kernpack.desempacotar(ByteArrayInputStream(maldoso.toByteArray()), destino)
            fail("deveria lançar IllegalArgumentException")
        } catch (esperado: IllegalArgumentException) {
        }
    }
}
