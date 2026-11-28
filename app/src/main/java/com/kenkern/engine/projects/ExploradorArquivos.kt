package com.kenkern.engine.projects

import java.io.File

/**
 * F6 (v0.1.30) — EXPLORADOR DE ARQUIVOS: listagem PAGINADA (F3: uma pasta
 * com 5.000 arquivos não trava — a página lê SÓ a janela pedida). PURO
 * (java.io.File), JVM-testado. Ordem estável: pastas primeiro, depois A→Z.
 *
 * P3 (antecipado da v0.1.33): UMA varredura por estado do diretório — o cache
 * da listagem ORDENADA é invalidado quando o mtime do diretório muda (criar/
 * apagar/renomear altera o mtime no Linux); as 25 páginas de 5.000 arquivos
 * pagam UMA listagem + UM sort em vez de 25.
 */
object ExploradorArquivos {

    /** Tamanho de página padrão (a lista virtualizada pede por janela). */
    const val PAGINA_PADRAO = 200

    /** Cache da listagem ordenada (1 por diretório; invalidado pelo mtime). */
    private var cache: CacheListagem? = null

    private class CacheListagem(
        val caminho: String,
        val mtime: Long,
        val ordenados: List<File>
    )

    /** listagem ordenada COM CACHE (pastas primeiro, A→Z case-insensitive). */
    @Synchronized
    private fun ordenados(dir: File): List<File> {
        val mtime = dir.lastModified()
        val atual = cache
        if (atual != null && atual.caminho == dir.path && atual.mtime == mtime) {
            return atual.ordenados
        }
        val todos: Array<File> = dir.listFiles() ?: emptyArray()
        val ordenados: List<File> = todos.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        cache = CacheListagem(dir.path, mtime, ordenados)
        return ordenados
    }

    /**
     * UMA PÁGINA da listagem de [dir]: [indice] 0-based, [tamanho] itens.
     * Pastas primeiro, A→Z (case-insensitive). Vazio se o dir não existe.
     */
    fun pagina(dir: File, indice: Int, tamanho: Int = PAGINA_PADRAO): List<File> {
        if (!dir.isDirectory) return emptyList()
        val ordenados = ordenados(dir)
        if (ordenados.isEmpty()) return emptyList()
        val ini = (indice * tamanho).coerceIn(0, ordenados.size)
        val fim = (ini + tamanho).coerceAtMost(ordenados.size)
        return ordenados.subList(ini, fim)
    }

    /** Total de entradas do diretório (para o pager calcular as páginas). */
    fun total(dir: File): Int = (dir.listFiles()?.size ?: 0)

    /** Quantidade de páginas com o tamanho dado. */
    fun numeroDePaginas(dir: File, tamanho: Int = PAGINA_PADRAO): Int {
        val t = total(dir)
        return if (t == 0) 0 else (t + tamanho - 1) / tamanho
    }
}
