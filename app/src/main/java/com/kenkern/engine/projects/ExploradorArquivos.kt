package com.kenkern.engine.projects

import java.io.File

/**
 * F6 (v0.1.30) — EXPLORADOR DE ARQUIVOS: listagem PAGINADA (F3: uma pasta
 * com 5.000 arquivos não trava — a página lê SÓ a janela pedida). PURO
 * (java.io.File), JVM-testado. Ordem estável: pastas primeiro, depois A→Z.
 */
object ExploradorArquivos {

    /** Tamanho de página padrão (a lista virtualizada pede por janela). */
    const val PAGINA_PADRAO = 200

    /**
     * UMA PÁGINA da listagem de [dir]: [indice] 0-based, [tamanho] itens.
     * Pastas primeiro, A→Z (case-insensitive). Vazio se o dir não existe.
     */
    fun pagina(dir: File, indice: Int, tamanho: Int = PAGINA_PADRAO): List<File> {
        if (!dir.isDirectory) return emptyList()
        val todos = dir.listFiles() ?: return emptyList()
        if (todos.isEmpty()) return emptyList()
        val ordenados = todos.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
