package com.kenkern.engine.commands

import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Testes JVM da D3 (v0.1.16) — undo/redo da importação com a lixeira:
 *
 *  1. undo recolhe .KERN + origem para assets/.lixeira-import/<importId>/
 *     (MESMO caminho que a checagem "importar" do Diagnóstico agora confere
 *     via cmd.pastaLixeira — antes a checagem olhava a pasta SEM o <importId>
 *     e falhava no aparelho);
 *  2. Files.move falhando (simulação de volume diferente no MESMO ponto do
 *     código) → fallback copiar+apagar roda DE VERDADE e o undo/redo completam;
 *  3. nomes já existentes no destino são substituídos (REPLACE_EXISTING);
 *  4. falha total (lixeira não pode ser criada) = ImportException com os
 *     caminhos reais (origem/destino/existe/canRead/canWrite/mesmoVolume).
 */
class ImportModelCommandTest {

    private fun raizTemporaria(): File =
        File.createTempFile("kenkern-cmd-test", "").let { f ->
            f.delete()
            f.mkdirs()
            f
        }

    private fun classeMalha(rel: String): NickClassInstance {
        val c = NickClassInstance("Malha")
        c.fields["path"] = NickValue.StringValue(rel)
        return c
    }

    private fun comandoComArquivos(raiz: File): Triple<Scene, ImportModelCommand, List<Pair<String, ByteArray>>> {
        val scene = Scene("Cmd-Test")
        val mundo = scene.createRootNick("Mundo")
        val kernRel = "assets/models/probe.KERN"
        val origemRel = "assets/models/origem/probe.glb"
        val kernBytes = ByteArray(64) { it.toByte() }
        val origemBytes = ByteArray(128) { (it * 3).toByte() }
        File(raiz, kernRel).parentFile.mkdirs()
        File(raiz, kernRel).writeBytes(kernBytes)
        File(raiz, origemRel).parentFile.mkdirs()
        File(raiz, origemRel).writeBytes(origemBytes)
        val cmd = ImportModelCommand(scene, mundo.id, "probe", raiz, listOf(kernRel, origemRel), classeMalha(kernRel))
        return Triple(scene, cmd, listOf(kernRel to kernBytes, origemRel to origemBytes))
    }

    @Test
    fun `undo recolhe para a lixeira com importId e redo devolve`() {
        val raiz = raizTemporaria()
        try {
            val (scene, cmd, arquivos) = comandoComArquivos(raiz)
            cmd.execute()
            assertTrue("Nick criado", scene.findNickByNameAnywhere("probe") != null)

            cmd.undo()
            assertFalse("Nick removido", scene.roots[0].children.isNotEmpty())
            for ((rel, _) in arquivos) {
                assertFalse("arquivo saiu do projeto: $rel", File(raiz, rel).exists())
                val naLixeira = File(cmd.pastaLixeira, rel)
                assertTrue("arquivo está na lixeira COM importId: $rel", naLixeira.exists())
            }
            assertTrue("lixeira fica DENTRO do projeto (mesmo volume)", cmd.pastaLixeira.path.startsWith(raiz.path))

            cmd.execute()
            assertTrue("Nick de volta", scene.findNickByNameAnywhere("probe") != null)
            for ((rel, bytes) in arquivos) {
                val f = File(raiz, rel)
                assertTrue("arquivo de volta: $rel", f.exists())
                assertEquals("conteúdo intacto: $rel", bytes.size.toLong(), f.length())
            }
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }

    @Test
    fun `move falhando simula volume diferente e o fallback copiar e apagar completa`() {
        val raiz = raizTemporaria()
        try {
            val (scene, cmd, arquivos) = comandoComArquivos(raiz)
            cmd.execute()
            // Simula EXDEV exatamente onde Files.move roda; o fallback roda por dentro.
            ImportModelCommand.testeFalhaNoMove =
                { origem, _ -> IOException("Invalid cross-device link (simulado): ${origem.path}") }

            cmd.undo()
            for ((rel, _) in arquivos) {
                assertFalse("origem apagada pelo fallback: $rel", File(raiz, rel).exists())
                val naLixeira = File(cmd.pastaLixeira, rel)
                assertTrue("arquivo na lixeira via copiar+apagar: $rel", naLixeira.exists())
            }

            cmd.execute()
            for ((rel, bytes) in arquivos) {
                val f = File(raiz, rel)
                assertTrue("redo devolveu: $rel", f.exists())
                assertEquals("tamanho intacto: $rel", bytes.size.toLong(), f.length())
            }
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }

    @Test
    fun `nome já existente no destino é substituído sem colisão`() {
        val raiz = raizTemporaria()
        try {
            val (scene, cmd, arquivos) = comandoComArquivos(raiz)
            // Lixo preexistente no MESMO caminho de destino da lixeira.
            val lixo = File(cmd.pastaLixeira, arquivos[0].first)
            lixo.parentFile.mkdirs()
            lixo.writeBytes(ByteArray(9))
            cmd.execute()
            cmd.undo()
            for ((rel, bytes) in arquivos) {
                val naLixeira = File(cmd.pastaLixeira, rel)
                assertEquals(
                    "conteúdo substituído pelo arquivo real: $rel",
                    bytes.size.toLong(),
                    naLixeira.length()
                )
            }
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }

    @Test
    fun `falha total lança ImportException com os caminhos reais`() {
        val raiz = raizTemporaria()
        try {
            val (scene, cmd, _) = comandoComArquivos(raiz)
            val history = CommandHistory()
            history.push(cmd)
            // Bloqueia a lixeira: o caminho dela existe como ARQUIVO (mkdirs falha).
            File(raiz, "assets/.lixeira-import").parentFile.mkdirs()
            File(raiz, "assets/.lixeira-import").writeBytes(ByteArray(1))
            var lancou: ImportException? = null
            try {
                history.undo()
            } catch (e: ImportException) {
                lancou = e
            }
            val msg = lancou?.message ?: ""
            assertTrue("undo deve falhar com erro CLARO (não silêncio)", lancou != null)
            assertTrue("mensagem tem a origem real: $msg", msg.contains("assets/models/probe.KERN") || msg.contains("lixeira"))
            assertTrue("mensagem tem existe=/canRead=/canWrite= (diagnóstico de caminhos): $msg", msg.contains("existe="))
            assertTrue("mensagem tem mesmoVolume: $msg", msg.contains("mesmoVolume"))
            assertTrue("comando continua na pilha undo", history.canUndo)
            assertFalse("comando não foi movido para redo", history.canRedo)
            assertSame("Nick continua na cena", scene.findNickByNameAnywhere("probe"), scene.roots[0].children.single())
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }

    @Test
    fun `falha no segundo movimento reverte o primeiro e mantem cena e historico`() {
        val raiz = raizTemporaria()
        try {
            val scene = Scene("transaction-test")
            val parent = scene.createRootNick("Mundo")
            val kernRel = "assets/models/probe.KERN"
            val sourceRel = "bloqueado/probe.glb"
            val kernBytes = ByteArray(64) { it.toByte() }
            val sourceBytes = ByteArray(128) { (it * 7).toByte() }
            File(raiz, kernRel).apply { parentFile.mkdirs(); writeBytes(kernBytes) }
            File(raiz, sourceRel).apply { parentFile.mkdirs(); writeBytes(sourceBytes) }
            val cmd = ImportModelCommand(
                scene,
                parent.id,
                "probe",
                raiz,
                listOf(kernRel, sourceRel),
                classeMalha(kernRel)
            )
            val history = CommandHistory()
            history.push(cmd)

            // O primeiro asset cabe na lixeira; o segundo falha porque seu
            // diretório de destino é um arquivo. O primeiro movimento deve voltar.
            assertTrue(cmd.pastaLixeira.mkdirs())
            File(cmd.pastaLixeira, "bloqueado").writeBytes(byteArrayOf(1))
            var lancou: ImportException? = null
            try {
                history.undo()
            } catch (e: ImportException) {
                lancou = e
            }

            assertNotNull("undo propaga a falha do segundo arquivo", lancou)
            assertTrue("o comando continua disponível para nova tentativa", history.canUndo)
            assertFalse("não há comando em redo", history.canRedo)
            assertTrue("Nick não foi retirado da cena", scene.findNickByNameAnywhere("probe") != null)
            assertTrue("primeiro asset continua no caminho original", File(raiz, kernRel).exists())
            assertTrue("segundo asset continua no caminho original", File(raiz, sourceRel).exists())
            assertEquals(kernBytes.toList(), File(raiz, kernRel).readBytes().toList())
            assertEquals(sourceBytes.toList(), File(raiz, sourceRel).readBytes().toList())
            assertFalse("primeiro asset foi retirado da lixeira pelo rollback", File(cmd.pastaLixeira, kernRel).exists())
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }

    @Test
    fun `falha parcial no redo reverte arquivos e mantem comando retryable`() {
        val raiz = raizTemporaria()
        try {
            val (scene, cmd, arquivos) = comandoComArquivos(raiz)
            val history = CommandHistory()
            history.push(cmd)
            history.undo()
            assertFalse("Nick retirado antes do redo", scene.findNickByNameAnywhere("probe") != null)

            // O primeiro arquivo restaura; o segundo falha porque o diretório
            // de destino foi substituído por um arquivo.
            val bloqueio = File(raiz, "assets/models/origem")
            if (bloqueio.isDirectory) {
                // `origem` é uma raiz de assets preservada pelo comando no undo.
                assertTrue("remover a raiz vazia para criar o bloqueio", bloqueio.delete())
            }
            assertFalse("caminho do bloqueio não pode continuar sendo diretório", bloqueio.exists())
            bloqueio.writeBytes(byteArrayOf(1))
            var lancou: ImportException? = null
            try {
                history.redo()
            } catch (e: ImportException) {
                lancou = e
            }

            assertNotNull("redo propaga a falha do segundo arquivo", lancou)
            assertTrue("comando continua disponível para redo", history.canRedo)
            assertFalse("comando não foi movido para undo", history.canUndo)
            assertFalse("Nick não foi parcialmente reanexado", scene.findNickByNameAnywhere("probe") != null)
            for ((rel, _) in arquivos) {
                assertFalse("asset não ficou parcialmente no projeto: $rel", File(raiz, rel).exists())
                assertTrue("asset segue na lixeira para retry: $rel", File(cmd.pastaLixeira, rel).exists())
            }

            assertTrue("remover o bloqueio permite nova tentativa", bloqueio.delete())
            history.redo()
            assertTrue("Nick publicado após retry", scene.findNickByNameAnywhere("probe") != null)
            for ((rel, _) in arquivos) {
                assertTrue("asset restaurado após retry: $rel", File(raiz, rel).exists())
            }
        } finally {
            raiz.deleteRecursively()
            ImportModelCommand.testeFalhaNoMove = null
        }
    }
}
