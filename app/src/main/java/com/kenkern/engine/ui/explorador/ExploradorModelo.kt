package com.kenkern.engine.ui.explorador

import com.kenkern.engine.projects.OperacoesArquivos
import java.io.File

/**
 * F3 (v0.1.31) — MODELO PURO do painel Projeto/explorador (JVM-testado):
 * filtragem por busca, ordenação (nome/data/tamanho/tipo) e a AÇÃO de abrir
 * por tipo de arquivo. O Composable (ExploradorConteudo) consome isto — a
 * lógica fica provada por teste sem Robolectric.
 */
object ExploradorModelo {

    /** Ordenação do painel (F3.1: nome, data, tamanho, tipo). */
    enum class Ordem(val chave: String) {
        NOME("nome"),
        DATA("data"),
        TAMANHO("tamanho"),
        TIPO("tipo");

        fun comparador(): Comparator<File> = when (this) {
            NOME -> compareBy { it.name.lowercase() }
            DATA -> compareByDescending { it.lastModified() }
            TAMANHO -> compareByDescending { it.length() }
            TIPO -> compareBy({ OperacoesArquivos.tipoDe(it.name) }, { it.name.lowercase() })
        }
    }

    /** Filtra por busca (substring case-insensitive no nome; vazio = todos). */
    fun filtrar(lista: List<File>, busca: String): List<File> {
        val q = busca.trim().lowercase()
        if (q.isEmpty()) return lista
        return lista.filter { it.name.lowercase().contains(q) }
    }

    /** Ordena com pastas primeiro (em TODAS as ordens — navegação estável). */
    fun ordenar(lista: List<File>, ordem: Ordem): List<File> =
        lista.sortedWith(
            compareBy<File> { !it.isDirectory }.thenComparing(ordem.comparador())
        )

    /** Ação de ABRIR por tipo (F3.3). */
    enum class Abrir { LUA, KUI, CENA, MODELO, IMAGEM, OUTRO }

    fun abrirDe(nome: String): Abrir = when (OperacoesArquivos.tipoDe(nome)) {
        "script" -> Abrir.LUA
        "ui" -> Abrir.KUI
        "cena" -> Abrir.CENA
        "modelo" -> Abrir.MODELO
        "imagem" -> Abrir.IMAGEM
        else -> Abrir.OUTRO
    }

    /** Conteúdo inicial de cada tipo de NOVO arquivo (F3.2). */
    fun conteudoNovoArquivo(nome: String): String = when (OperacoesArquivos.tipoDe(nome)) {
        "script" -> "-- $nome\n-- script Lua da KENKERN\n\n"
        "ui" -> "KUI1\n"
        "material" -> "# material KENKERN (F3; gramática completa na U5)\nnome: ${nome.substringBeforeLast('.')}\n"
        "dialogo" -> "# diálogo KENKERN (F3; gramática completa na D1)\nnome: ${nome.substringBeforeLast('.')}\n"
        else -> ""
    }

    /** Tipos de arquivo que o "Novo arquivo" oferece (F3.2). */
    val TIPOS_NOVO_ARQUIVO = listOf("lua", "ck", "kui", "kmat", "kdlg")

    /** extensões com MINIATURA real (F3.1: imagens do disco). */
    fun temMiniatura(nome: String): Boolean =
        OperacoesArquivos.tipoDe(nome) == "imagem"
}
