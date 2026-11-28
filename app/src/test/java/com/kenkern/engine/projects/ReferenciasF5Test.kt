package com.kenkern.engine.projects

import com.kenkern.engine.commands.ComandoApagarArquivo
import com.kenkern.engine.commands.ComandoMoverArquivo
import com.kenkern.engine.commands.ComandoRenomearArquivo
import com.kenkern.engine.commands.novoIdOperacao
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F5 (v0.1.31) — referências estáveis: mover/renomear NÃO quebram referências
 * (índice assets.index + repontar os Nicks); apagar com referência quebra e
 * aparece no painel Problemas; desfazer restaura; índice corrompido é
 * reconstruído; parser tolerante devolve linha e motivo dos 3 erros.
 */
class ReferenciasF5Test {

    private fun novaRaiz(): File {
        val raiz = File("/tmp/kenkern-f5-" + System.nanoTime())
        raiz.mkdirs()
        return raiz
    }

    /** cena com um Nick de malha apontando o caminho dado (caminho REAL da UI: addNickOfKind → classe Malha). */
    private fun cenaComMalha(caminho: String): Scene {
        val s = Scene()
        val nick = s.createRootNick("Cubo", kind = "nick.mesh")
        val malha = nick.findClassInstance("Malha") ?: NickClassInstance("Malha").also {
            nick.addClassInstance(it)
        }
        malha.fields["path"] = NickValue.StringValue(caminho)
        return s
    }

    @Test
    fun `índice ida e volta exata com ordem determinística`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/heroi.kern").writeBytes(byteArrayOf(1))
            File(raiz, "assets/textures").mkdirs()
            File(raiz, "assets/textures/pele.kimg").writeBytes(byteArrayOf(2))

            val idx = IndiceAssets.carregar(raiz)
            val id1 = idx.registrar("assets/models/heroi.kern")
            val id2 = idx.registrar("assets/textures/pele.kimg")
            idx.salvar(raiz)

            val idx2 = IndiceAssets.carregar(raiz)
            assertEquals(idx.linhas(), idx2.linhas())
            assertEquals(id1, idx2.idDe("assets/models/heroi.kern"))
            assertEquals(id2, idx2.idDe("assets/textures/pele.kimg"))

            // ordem determinística: alfabética por caminho
            val linhas = idx2.linhas()
            assertEquals(linhas.sortedBy { it.substringAfter('\t') }, linhas)
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `mover e renomear mantêm o MESMO id e repontam os Nicks`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/heroi.kern").writeBytes(byteArrayOf(1))
            val idx = IndiceAssets.carregar(raiz)
            val id = idx.registrar("assets/models/heroi.kern")
            val cena = cenaComMalha("assets/models/heroi.kern")

            // RENOMEAR via Command (com atualização de índice + Nicks)
            val ren = ComandoRenomearArquivo(raiz, "assets/models/heroi.kern", "heroi2.kern") { antigo, novo ->
                idx.mover(antigo, novo)
                ReferenciasAssets.repontar(cena, antigo, novo)
            }
            ren.execute()
            File(raiz, "assets/models/heroi2.kern").let { assertTrue(it.isFile) }
            assertEquals("o ID sobrevive ao renomear", id, idx.idDe("assets/models/heroi2.kern"))
            assertNull(idx.idDe("assets/models/heroi.kern"))
            assertEquals(
                "Nick repontado",
                "assets/models/heroi2.kern",
                ReferenciasAssets.varrer(cena).single().caminho
            )
            assertTrue("referência não quebrada", ReferenciasAssets.problemas(raiz, cena).isEmpty())

            // MOVER para outra pasta via Command
            File(raiz, "assets/textures").mkdirs()
            val mov = ComandoMoverArquivo(raiz, "assets/models/heroi2.kern", "assets/textures") { antigo, novo ->
                idx.mover(antigo, novo)
                ReferenciasAssets.repontar(cena, antigo, novo)
            }
            mov.execute()
            assertEquals(id, idx.idDe("assets/textures/heroi2.kern"))
            assertEquals(
                "assets/textures/heroi2.kern",
                ReferenciasAssets.varrer(cena).single().caminho
            )
            assertTrue(ReferenciasAssets.problemas(raiz, cena).isEmpty())

            // UNDO do mover: referência volta ao caminho antigo
            mov.undo()
            assertEquals(id, idx.idDe("assets/models/heroi2.kern"))
            assertEquals("assets/models/heroi2.kern", ReferenciasAssets.varrer(cena).single().caminho)
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `apagar com referência quebra e o undo restaura`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/heroi.kern").writeBytes(byteArrayOf(1))
            val idx = IndiceAssets.carregar(raiz)
            idx.registrar("assets/models/heroi.kern")
            val cena = cenaComMalha("assets/models/heroi.kern")

            val apagar = ComandoApagarArquivo(raiz, "assets/models/heroi.kern", novoIdOperacao())
            apagar.execute()
            idx.remover("assets/models/heroi.kern")

            val problemas = ReferenciasAssets.problemas(raiz, cena)
            assertEquals(1, problemas.size)
            assertEquals("assets/models/heroi.kern", problemas.single().caminho)
            assertTrue(problemas.single().mensagem.contains("não existe"))

            // undo do apagar restaura o arquivo e a referência deixa de estar quebrada
            apagar.undo()
            assertTrue(File(raiz, "assets/models/heroi.kern").isFile)
            assertTrue(ReferenciasAssets.problemas(raiz, cena).isEmpty())
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `parser tolerante devolve linha e motivo dos erros sem perder o resto`() {
        val raiz = novaRaiz()
        try {
            // 4 problemas (3 motivos diferentes) + 1 linha boa
            File(raiz, IndiceAssets.ARQUIVO).writeText(
                "# comentário\n" +
                    "id-1\tassets/a.kern\n" +
                    "sem-separador-nesse-formato\n" +
                    "\tassets/sem-id.kern\n" +
                    "id-9\t../escapou.kern\n" +
                    "id-2\tassets/a.kern\n" // duplicado com id diferente
            )
            val idx = IndiceAssets.carregar(raiz)
            assertEquals(4, idx.problemas.size)
            assertEquals("sem separador id<TAB>caminho", idx.problemas[0].motivo)
            assertEquals("id vazio", idx.problemas[1].motivo)
            assertTrue(idx.problemas[2].motivo.contains("caminho inválido"))
            assertTrue(idx.problemas[3].motivo.contains("duplicado"))
            // as linhas BOAS sobreviveram
            assertEquals("id-1", idx.idDe("assets/a.kern"))
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `índice corrompido em parte reconstrói preservando os ids legíveis`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/heroi.kern").writeBytes(byteArrayOf(1))
            File(raiz, "assets/models/novo.kern").writeBytes(byteArrayOf(2))
            val idx = IndiceAssets.carregar(raiz)
            val id = idx.registrar("assets/models/heroi.kern")
            idx.salvar(raiz)

            // corrompimento PARCIAL: a linha boa + lixo (parse recupera a boa)
            File(raiz, IndiceAssets.ARQUIVO).writeText(
                "$id\tassets/models/heroi.kern\nLINHA SEM SEPARADOR\n"
            )
            val recon = IndiceAssets.reconstruir(raiz)
            assertEquals("id preservado na reconstrução", id, recon.idDe("assets/models/heroi.kern"))
            assertNotNull("arquivo não listado entra na reconstrução", recon.idDe("assets/models/novo.kern"))
            assertTrue(recon.problemas.isNotEmpty())

            recon.salvar(raiz)
            val recarregado = IndiceAssets.carregar(raiz)
            assertEquals(id, recarregado.idDe("assets/models/heroi.kern"))
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `corrigir e remover referência no painel Problemas`() {
        val raiz = novaRaiz()
        try {
            File(raiz, "assets/models").mkdirs()
            File(raiz, "assets/models/novo.kern").writeBytes(byteArrayOf(1))
            val cena = cenaComMalha("assets/models/velho.kern")

            val problemas = ReferenciasAssets.problemas(raiz, cena)
            assertEquals(1, problemas.size)
            val p = problemas.single()

            // "Corrigir…": escolher o arquivo certo
            assertTrue(ReferenciasAssets.corrigir(cena, p.nickId, "assets/models/novo.kern"))
            assertTrue(ReferenciasAssets.problemas(raiz, cena).isEmpty())

            // "Remover referência": o campo fica vazio (sem quebra, sem crash)
            val cena2 = cenaComMalha("assets/models/sumido.kern")
            val p2 = ReferenciasAssets.problemas(raiz, cena2).single()
            assertTrue(ReferenciasAssets.removerReferencia(cena2, p2.nickId))
            assertTrue("path vazio não é problema", ReferenciasAssets.problemas(raiz, cena2).isEmpty())
            assertNull(ReferenciasAssets.varrer(cena2).singleOrNull())
        } finally {
            raiz.deleteRecursively()
        }
    }
}
