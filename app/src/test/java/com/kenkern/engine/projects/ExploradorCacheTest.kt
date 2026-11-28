package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3/F3 (v0.1.31) — CACHE da listagem do explorador: UMA varredura por estado
 * do diretório. A paginação completa de 5.000 arquivos (25×200) paga UMA
 * listagem + UM sort (a checagem "explorador" do Diagnóstico mede o tempo);
 * criar/apagar arquivo invalida o cache pelo mtime do diretório.
 */
class ExploradorCacheTest {

    @Test
    fun `paginação completa de 5000 reflete e é consistente`() {
        val dir = File("/tmp/kenkern-cache-" + System.nanoTime())
        dir.mkdirs()
        try {
            for (i in 0 until 5000) {
                File(dir, "arquivo_${i.toString().padStart(4, '0')}.lua").writeText("x")
            }
            val paginas = ExploradorArquivos.numeroDePaginas(dir)
            assertEquals(25, paginas)
            val nomes = (0 until paginas).flatMap { p -> ExploradorArquivos.pagina(dir, p).map { it.name } }
            assertEquals(5000, nomes.size)
            // consistência: sem duplicatas e em ordem alfabética
            assertEquals(nomes.toSet().size, nomes.size)
            assertEquals(nomes.sorted(), nomes)
            // primeira página ≤ 200 itens
            assertTrue(ExploradorArquivos.pagina(dir, 0).size <= ExploradorArquivos.PAGINA_PADRAO)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `criar arquivo invalida o cache pelo mtime do diretório`() {
        val dir = File("/tmp/kenkern-cache2-" + System.nanoTime())
        dir.mkdirs()
        try {
            File(dir, "b.lua").writeText("x")
            val antes = ExploradorArquivos.pagina(dir, 0).map { it.name }
            assertEquals(listOf("b.lua"), antes)

            Thread.sleep(15) // garante mtime diferente
            File(dir, "a.lua").writeText("x")
            val depois = ExploradorArquivos.pagina(dir, 0).map { it.name }
            assertEquals("criar arquivo deve aparecer na listagem (cache invalidado)", listOf("a.lua", "b.lua"), depois)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pastas primeiro e ordem A-Z case-insensitive`() {
        val dir = File("/tmp/kenkern-cache3-" + System.nanoTime())
        dir.mkdirs()
        try {
            File(dir, "zeta.lua").writeText("x")
            File(dir, "Alpha.lua").writeText("x")
            val sub = File(dir, "beta")
            sub.mkdirs()
            val nomes = ExploradorArquivos.pagina(dir, 0).map { it.name }
            assertEquals(listOf("beta", "Alpha.lua", "zeta.lua"), nomes)
        } finally {
            dir.deleteRecursively()
        }
    }
}
