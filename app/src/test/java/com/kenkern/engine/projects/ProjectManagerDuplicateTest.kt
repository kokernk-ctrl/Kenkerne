package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Teste JVM do DUPLICAR PROJETO (F2.5) — sem Android: manipula pastas
 * temporárias diretamente via ProjectHandle.
 *
 *  1. cópia completa (arquivos + subpastas) com nome "X (cópia)";
 *  2. project.kenkern da CÓPIA tem o novo nome; o ORIGINAL fica intocado;
 *  3. duplicar de novo gera "(cópia 2)" (nunca sobrescreve);
 *  4. duplicar projeto inválido (sem project.kenkern) retorna null com erro.
 */
class ProjectManagerDuplicateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun projetoBase(nome: String): ProjectHandle {
        val root = tmp.newFolder(nome)
        tmp.newFolder("$nome/scenes")
        tmp.newFolder("$nome/assets")
        tmp.newFile("$nome/project.kenkern").writeText(
            """
            KENKERN_PROJECT
            format=1
            engine=teste
            name="$nome"
            created="2026-11-20 10:00:00"
            scene=scenes/cena.ck
            """.trimIndent()
        )
        tmp.newFile("$nome/scenes/cena.ck").writeText("CK1\nSCENE name=\"$nome\"\nENDCK\n")
        tmp.newFile("$nome/assets/textura.png").writeBytes(byteArrayOf(1, 2, 3))
        return ProjectHandle(root, nome, "2026-11-20 10:00:00")
    }

    @Test
    fun `duplicar copia tudo renomeia a copia e preserva o original`() {
        val original = projetoBase("Meu Jogo")

        val copia = ProjectManager.duplicateProject(original)

        assertNotNull("duplicação deve funcionar", copia)
        assertEquals("Meu Jogo (cópia)", copia!!.name)
        assertEquals("Meu Jogo (cópia)", copia.root.name)

        assertTrue(java.io.File(copia.root, "project.kenkern").isFile)
        assertTrue(java.io.File(copia.root, "scenes/cena.ck").isFile)
        assertTrue(java.io.File(copia.root, "assets/textura.png").isFile)
        assertEquals(
            byteArrayOf(1, 2, 3).toList(),
            java.io.File(copia.root, "assets/textura.png").readBytes().toList()
        )

        val metaCopia = java.io.File(copia.root, "project.kenkern").readText()
        assertTrue("meta da cópia deve citar o novo nome", metaCopia.contains("name=\"Meu Jogo (cópia)\""))
        val metaOriginal = java.io.File(original.root, "project.kenkern").readText()
        assertTrue("original intocado", metaOriginal.contains("name=\"Meu Jogo\""))
    }

    @Test
    fun `duplicar duas vezes gera copia 2 - nunca sobrescreve`() {
        val original = projetoBase("X")

        val c1 = ProjectManager.duplicateProject(original)
        val c2 = ProjectManager.duplicateProject(original)

        assertNotNull(c1)
        assertNotNull(c2)
        assertEquals("X (cópia)", c1!!.name)
        assertEquals("X (cópia 2)", c2!!.name)
        assertTrue(c1.root.isDirectory)
    }

    @Test
    fun `pasta invalida sem project kenkern retorna null com motivo`() {
        val semMeta = tmp.newFolder("sozinho")
        val handle = ProjectHandle(semMeta, "sozinho", "-")

        val copia = ProjectManager.duplicateProject(handle)

        assertEquals(null, copia)
        assertNotNull("deve reportar o motivo", ProjectManager.lastError)
    }
}
