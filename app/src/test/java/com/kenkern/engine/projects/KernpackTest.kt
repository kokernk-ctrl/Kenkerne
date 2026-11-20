package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Teste JVM do .KERNPACK (F2.6): empacotar/desempacotar um projeto inteiro.
 *
 *  1. ida e volta: arquivos e subpastas voltam com o MESMO conteúdo;
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
        File(root, "build").mkdirs()
        File(root, "project.kenkern").writeText(
            "KENKERN_PROJECT\nformat=1\nengine=teste\nname=\"Meu Jogo\"\ncreated=\"x\"\nscene=scenes/cena.ck\n"
        )
        File(root, "scenes/cena.ck").writeText("CK1\nSCENE name=\"Cena\"\nENDCK\n")
        File(root, "scenes/cena.autosave.ck").writeText("CK1\nSCENE name=\"Auto\"\nENDCK\n")
        File(root, "assets/binario.bin").writeBytes(byteArrayOf(9, 8, 7, 6))
        File(root, "build/lixo.tmp").writeText("gerado")
        return root
    }

    @Test
    fun `ida e volta do kernpack preserva arquivos e pula build`() {
        val origem = projetoBase()
        val bytes = ByteArrayOutputStream().also { Kernpack.empacotar(origem, it) }.toByteArray()

        val destino = tmp.newFolder("destino")
        val arquivos = Kernpack.desempacotar(ByteArrayInputStream(bytes), destino)

        assertEquals(4, arquivos) // project.kenkern + 2 cenas + binario.bin (build NÃO vai)
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
        assertTrue("build/ não deve existir no destino", !File(destino, "build").exists())
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
