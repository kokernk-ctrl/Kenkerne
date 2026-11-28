package com.kenkern.engine.projects

import java.io.File

/**
 * F5 (v0.1.31) — REFERÊNCIAS ESTÁVEIS DE ASSETS (PURO, JVM-testado).
 *
 * DECISÃO (registrada em DECISIONS.md #13): UM índice único `assets.index`
 * na RAIZ do projeto (texto, 1 propriedade por linha, ordem determinística,
 * comentários com #) em vez de um `.kmeta` ao lado de cada arquivo — o
 * índice único é reconstruído facilmente, é legível por IA e não suja a
 * árvore de assets com 500 arquivos de metadado.
 *
 * Formato de cada linha: `id<TAB>caminhoRelativo` (o id é ESTÁVEL — sobrevive
 * a mover/renomear porque o mover ATUALIZA o caminho da entrada e reponta os
 * Nicks que referenciam pelo caminho antigo).
 *
 * Parser TOLERANTE: linha inválida vira Problema com LINHA e MOTIVO — sem
 * crash e sem perder o resto do arquivo.
 */
class IndiceAssets private constructor(
    /** caminho relativo → id estável. */
    val entradas: LinkedHashMap<String, String>,
    val problemas: List<ProblemaLinha>
) {

    /** Problema de parse (linha 1-based) — alimenta o painel Problemas. */
    data class ProblemaLinha(val linha: Int, val texto: String, val motivo: String)

    /** id do caminho (null se não registrado). */
    fun idDe(caminhoRelativo: String): String? = entradas[normalizar(caminhoRelativo)]

    /** caminho do id (null se o id não existe). */
    fun caminhoDe(id: String): String? =
        entradas.entries.firstOrNull { it.value == id }?.key

    /** registra (ou devolve o id existente) e devolve o id. */
    fun registrar(caminhoRelativo: String): String {
        val chave = normalizar(caminhoRelativo)
        val atual = entradas[chave]
        if (atual != null) return atual
        val novo = gerarId()
        entradas[chave] = novo
        return novo
    }

    /** mover/renomear: a ENTRADA muda de caminho mantendo o MESMO id. */
    fun mover(caminhoAntigo: String, caminhoNovo: String): Boolean {
        val chave = normalizar(caminhoAntigo)
        val id = entradas.remove(chave) ?: return false
        entradas[normalizar(caminhoNovo)] = id
        return true
    }

    /** remover entrada (arquivo apagado com referência → quebra e aparece em Problemas). */
    fun remover(caminhoRelativo: String): Boolean =
        entradas.remove(normalizar(caminhoRelativo)) != null

    /** linhas do arquivo — ordem DETERMINÍSTICA (alfabética por caminho). */
    fun linhas(): List<String> =
        entradas.entries.sortedBy { it.key }.map { "${it.value}\t${it.key}" }

    /** grava assets.index na raiz (atômico). */
    fun salvar(raiz: File) {
        val conteudo = cabecalho() + linhas().joinToString("\n", postfix = "\n")
        ProjectManager.writeFileAtomic(File(raiz, ARQUIVO), conteudo)
    }

    companion object {
        const val ARQUIVO = "assets.index"

        private fun cabecalho(): String =
            "# KENKERN assets.index — id<TAB>caminhoRelativo (F5)\n" +
                "# id ESTÁVEL: mover/renomear atualiza o caminho da linha, o id não muda.\n"

        private fun normalizar(caminho: String): String =
            caminho.trim().replace('\\', '/').trimStart('/')

        private fun gerarId(): String =
            System.currentTimeMillis().toString(36) + "-" + (contador++).toString(36)

        private var contador = 0

        /** carrega (tolerante): linhas ruins viram ProblemaLinha com linha e motivo. */
        fun carregar(raiz: File): IndiceAssets {
            val f = File(raiz, ARQUIVO)
            val entradas = LinkedHashMap<String, String>()
            val problemas = ArrayList<ProblemaLinha>()
            if (f.isFile) {
                val linhas = f.readText().split('\n')
                for ((i, bruta) in linhas.withIndex()) {
                    val linha = bruta.trimEnd('\r')
                    if (linha.isBlank() || linha.startsWith("#")) continue
                    val sep = linha.indexOf('\t')
                    if (sep < 0) {
                        problemas.add(ProblemaLinha(i + 1, linha, "sem separador id<TAB>caminho"))
                        continue
                    }
                    val id = linha.substring(0, sep).trim()
                    val caminho = normalizar(linha.substring(sep + 1))
                    if (id.isEmpty()) {
                        problemas.add(ProblemaLinha(i + 1, linha, "id vazio"))
                        continue
                    }
                    if (caminho.isEmpty() || caminho.contains("..") || caminho.startsWith("/")) {
                        problemas.add(ProblemaLinha(i + 1, linha, "caminho inválido (vazio, '..' ou absoluto)"))
                        continue
                    }
                    val existente = entradas[caminho]
                    if (existente != null && existente != id) {
                        problemas.add(ProblemaLinha(i + 1, linha, "caminho duplicado (id '$existente' já tem esse caminho)"))
                        continue
                    }
                    entradas[caminho] = id
                }
            }
            return IndiceAssets(entradas, problemas)
        }

        /**
         * RECONSTRUÇÃO (índice ausente/corrompido): varre as pastas de assets
         * do projeto e registra o que existe; ids anteriores que ainda
         * correspondem a caminhos presentes são PRESERVADOS; entradas ÓRFÃS
         * (arquivo apagado para sempre) são descartadas — o id continua
         * estável para o caso apagar→desfazer, que restaura o arquivo antes
         * de qualquer reconstrução.
         */
        fun reconstruir(raiz: File): IndiceAssets {
            val atual = carregar(raiz)
            val novo = IndiceAssets(LinkedHashMap(), atual.problemas)
            for (pasta in listOf("assets", "scenes", "scripts", "ui", "worlds", "dialogues")) {
                val dir = File(raiz, pasta)
                if (!dir.isDirectory) continue
                dir.walkTopDown().filter { it.isFile }
                    .filter { !it.path.contains("/.kenkern/") }
                    .forEach { f ->
                        val rel = OperacoesArquivos.relativo(raiz, f)
                        val idAtual = atual.idDe(rel)
                        novo.entradas[rel] = idAtual ?: novo.registrar(rel)
                    }
            }
            return novo
        }
    }
}
