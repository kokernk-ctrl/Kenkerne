package com.kenkern.engine.projects

import com.kenkern.engine.commands.ComandoApagarArquivo
import com.kenkern.engine.commands.ComandoCriarArquivo
import com.kenkern.engine.commands.ComandoCriarPasta
import com.kenkern.engine.commands.ComandoDuplicarArquivo
import com.kenkern.engine.commands.ComandoMoverArquivo
import com.kenkern.engine.commands.ComandoRenomearArquivo
import com.kenkern.engine.commands.CommandHistory
import com.kenkern.engine.commands.novoIdOperacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F3 (v0.1.31) — operações de arquivo do explorador: criar, renomear, mover,
 * apagar (LIXEIRA .kenkern/lixeira) e desfazer; guardas de caminho (sem "..",
 * dentro da raiz); nomes únicos com sufixo " 2"; "esvaziar lixeira".
 * Tudo PURO (java.io.File) — roda na JVM.
 */
class OperacoesArquivosTest {

    private fun novaRaiz(): File {
        val raiz = File("/tmp/kenkern-f3-" + System.nanoTime())
        raiz.mkdirs()
        return raiz
    }

    @Test
    fun `criar pasta e arquivo com nome único e guardas`() {
        val raiz = novaRaiz()
        try {
            val r = OperacoesArquivos.criarPasta(raiz, raiz, "scripts")
            assertTrue(r.ok)
            assertTrue(File(raiz, "scripts").isDirectory)

            val r2 = OperacoesArquivos.criarArquivo(raiz, File(raiz, "scripts"), "a.lua", "-- ola")
            assertTrue(r2.ok)
            assertEquals("scripts/a.lua", r2.caminho)
            assertEquals("-- ola", File(raiz, "scripts/a.lua").readText())

            // nome duplicado ganha sufixo " 2"
            val r3 = OperacoesArquivos.criarArquivo(raiz, File(raiz, "scripts"), "a.lua", "-- outro")
            assertTrue(r3.ok)
            assertEquals("scripts/a 2.lua", r3.caminho)
            assertEquals("-- outro", File(raiz, "scripts/a 2.lua").readText())

            // guardas: ".." recusado
            val escapado = OperacoesArquivos.criarArquivo(raiz, File(raiz, "scripts"), "../fora.lua", "x")
            assertFalse("caminho fora da raiz deve ser recusado", escapado.ok)
            assertFalse(File(raiz.parentFile, "fora.lua").exists())

            // guarda: nome inválido (ponto/..)
            assertFalse(OperacoesArquivos.criarPasta(raiz, raiz, "..").ok)
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `renomear e duplicar arquivo e pasta`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "scripts").mkdirs()
            File(raiz, "scripts/a.lua").writeText("-- a")
            val r = OperacoesArquivos.renomear(raiz, File(raiz, "scripts/a.lua"), "b.lua")
            assertTrue(r.ok)
            assertEquals("scripts/b.lua", r.caminho)
            assertFalse(File(raiz, "scripts/a.lua").exists())

            val dup = OperacoesArquivos.duplicar(raiz, File(raiz, "scripts/b.lua"))
            assertTrue(dup.ok)
            assertEquals("scripts/b 2.lua", dup.caminho)
            assertEquals("-- a", File(raiz, "scripts/b 2.lua").readText())

            // pasta inteira duplicada (recursivo)
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/m.kern").writeBytes(byteArrayOf(1, 2, 3))
            val dupPasta = OperacoesArquivos.duplicar(raiz, File(raiz, "assets/models"))
            assertTrue(dupPasta.ok)
            assertEquals("assets/models 2", dupPasta.caminho)
            assertTrue(File(raiz, "assets/models 2/m.kern").isFile)
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `mover respeita guardas e recusa pasta dentro de si`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "scripts").mkdirs()
            File(raiz, "assets/models").mkdirs()
            File(raiz, "scripts/a.lua").writeText("-- a")
            val r = OperacoesArquivos.mover(raiz, File(raiz, "scripts/a.lua"), File(raiz, "assets/models"))
            assertTrue(r.ok)
            assertEquals("assets/models/a.lua", r.caminho)
            assertFalse(File(raiz, "scripts/a.lua").exists())

            // mover pasta para dentro de si mesma é recusado
            val auto = OperacoesArquivos.mover(raiz, File(raiz, "assets/models"), File(raiz, "assets/models"))
            assertFalse(auto.ok)
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `apagar vai para a lixeira e o undo restaura`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "scripts").mkdirs()
            File(raiz, "scripts/a.lua").writeText("-- a")
            val id = "teste1"
            val r = OperacoesArquivos.apagarParaLixeira(raiz, File(raiz, "scripts/a.lua"), id)
            assertTrue(r.ok)
            assertFalse("apagado da pasta visível", File(raiz, "scripts/a.lua").exists())
            assertTrue("está NA lixeira: ${r.caminho}", File(raiz, r.caminho!!).isFile)
            assertTrue("caminho na lixeira", r.caminho!!.startsWith(OperacoesArquivos.LIXEIRA))

            // conteúdo na lixeira listado
            val lixeira = OperacoesArquivos.lixeira(raiz)
            assertEquals(listOf("a.lua"), lixeira[id])

            // restaurar (undo)
            val volta = OperacoesArquivos.restaurarDaLixeira(raiz, r.caminho!!, File(raiz, "scripts/a.lua"))
            assertTrue(volta.ok)
            assertEquals("-- a", File(raiz, "scripts/a.lua").readText())

            // esvaziar de novo (apaga definitivo)
            OperacoesArquivos.apagarParaLixeira(raiz, File(raiz, "scripts/a.lua"), "teste2")
            val esv = OperacoesArquivos.esvaziarLixeira(raiz)
            assertTrue(esv.ok)
            assertTrue(File(raiz, OperacoesArquivos.LIXEIRA).listFiles()?.isEmpty() ?: true)
            assertFalse(File(raiz, "scripts/a.lua").exists())
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `commands de arquivo executam e desfazem no histórico`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "scripts").mkdirs()
            val hist = CommandHistory()

            val criar = ComandoCriarArquivo(raiz, "scripts", "novo.lua", "-- novo")
            hist.push(criar)
            assertTrue(File(raiz, "scripts/novo.lua").isFile)
            hist.undo()
            assertFalse("undo do criar tira o arquivo (lixeira)", File(raiz, "scripts/novo.lua").exists())
            hist.redo()
            assertTrue(File(raiz, "scripts/novo.lua").isFile)

            val ren = ComandoRenomearArquivo(raiz, "scripts/novo.lua", "renomeado.lua")
            hist.push(ren)
            assertTrue(File(raiz, "scripts/renomeado.lua").isFile)
            hist.undo()
            assertTrue(File(raiz, "scripts/novo.lua").isFile)
            hist.redo()
            assertTrue(File(raiz, "scripts/renomeado.lua").isFile)

            // mover para a MESMA pasta é recusado (execute lança erro claro)
            val mover = ComandoMoverArquivo(raiz, "scripts/renomeado.lua", "scripts")
            var recusou = false
            try {
                mover.execute()
            } catch (e: IllegalStateException) {
                recusou = true
            }
            assertTrue("mover para a mesma pasta deve ser recusado", recusou)
            // mover para uma SUBPASTA real
            File(raiz, "scripts/sub").mkdirs()
            val mover2 = ComandoMoverArquivo(raiz, "scripts/renomeado.lua", "scripts/sub")
            hist.push(mover2)
            assertTrue(File(raiz, "scripts/sub/renomeado.lua").isFile)
            hist.undo()
            assertTrue("undo devolve à pasta original", File(raiz, "scripts/renomeado.lua").isFile)

            val apagar = ComandoApagarArquivo(raiz, "scripts/renomeado.lua", novoIdOperacao())
            hist.push(apagar)
            assertFalse(File(raiz, "scripts/renomeado.lua").exists())
            hist.undo()
            assertTrue("undo do apagar restaura o conteúdo", File(raiz, "scripts/renomeado.lua").isFile)
            assertEquals("-- novo", File(raiz, "scripts/renomeado.lua").readText())

            val pasta = ComandoCriarPasta(raiz, "scripts", "sub2")
            hist.push(pasta)
            assertTrue(File(raiz, "scripts/sub2").isDirectory)
            hist.undo()
            assertFalse(File(raiz, "scripts/sub2").exists())

            val dup = ComandoDuplicarArquivo(raiz, "scripts/renomeado.lua")
            hist.push(dup)
            assertTrue(File(raiz, "scripts/renomeado 2.lua").isFile)
            hist.undo()
            assertFalse(File(raiz, "scripts/renomeado 2.lua").exists())
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `tipo por extensão e pastaDe do F4 mantêm o roteamento`() {
        assertEquals("script", OperacoesArquivos.tipoDe("a.lua"))
        assertEquals("cena", OperacoesArquivos.tipoDe("cena.ck"))
        assertEquals("ui", OperacoesArquivos.tipoDe("tela.kui"))
        assertEquals("imagem", OperacoesArquivos.tipoDe("foto.PNG"))
        assertEquals("modelo", OperacoesArquivos.tipoDe("heroi.glb"))
        assertEquals("material", OperacoesArquivos.tipoDe("metal.kmat"))
        assertEquals("dialogo", OperacoesArquivos.tipoDe("intro.kdlg"))
        assertEquals("audio", OperacoesArquivos.tipoDe("passo.ogg"))
        assertEquals("fonte", OperacoesArquivos.tipoDe("inter.ttf"))
        assertEquals("arquivo", OperacoesArquivos.tipoDe("leiame.md"))
    }
}
