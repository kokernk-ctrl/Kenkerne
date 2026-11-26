package com.kenkern.engine.ui.editor

import com.kenkern.engine.engine.LuaApiCatalog

/**
 * C1 (v0.1.26) — MODELO PURO DO EDITOR DE CÓDIGO LUA (sem Android/Compose —
 * JVM-testável e usado pela checagem "editor" do Diagnóstico).
 *
 * O editor na tela é uma LISTA DE LINHAS VIRTUALIZADA (LazyColumn — realce só
 * nas linhas visíveis; NUNCA um TextField gigante): este modelo guarda as
 * linhas, o cursor (linha/coluna) e o histórico undo/redo (snapshots,
 * máx. 100) e decide TODO o comportamento de texto — a tela só mapeia toques
 * e teclas para operações.
 */

// ------------------------------------------------------------------ TOKENIZADOR

/** Tipos de token para o realce (cores resolvidas na tela, tokens aqui). */
enum class TokenTipo { NORMAL, PALAVRA_CHAVE, STRING, COMENTARIO, NUMERO, API }

/** Um trecho marcado da linha [ini, fim) no tipo [tipo]. */
class SpanLua(val tipo: TokenTipo, val ini: Int, val fim: Int)

/**
 * Tokenizador LINHA A LINHA de Lua: o estado que atravessa a linha
 * (comentário de bloco `--[[ ]]` e string longa `[[ ]]`) entra e sai —
 * a tela guarda o estado da ÚLTIMA linha visível e marca as seguintes
 * (realce correto sem ler o arquivo inteiro).
 */
object TokenizadorLua {

    val PALAVRAS = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true",
        "until", "while"
    )

    /** Estado que atravessa linhas (comentário de bloco/string longa aberta). */
    class Estado(var emBloco: Boolean = false)

    /** Marca UMA linha; devolve o estado para a linha seguinte. */
    fun marcarLinha(texto: String, estado: Estado, apis: Set<String> = simbolosApi): List<SpanLua> {
        val spans = ArrayList<SpanLua>(8)
        var i = 0
        while (i < texto.length) {
            if (estado.emBloco) {
                val fim = texto.indexOf("]]", i)
                if (fim < 0) {
                    spans.add(SpanLua(TokenTipo.COMENTARIO, i, texto.length))
                    return spans // continua em bloco na próxima linha
                }
                spans.add(SpanLua(TokenTipo.COMENTARIO, i, fim + 2))
                i = fim + 2
                estado.emBloco = false
                continue
            }
            val c = texto[i]
            when {
                // comentário de linha ou bloco
                texto.startsWith("--", i) -> {
                    val abreBloco = texto.startsWith("--[[", i)
                    if (abreBloco) {
                        val fim = texto.indexOf("]]", i + 4)
                        if (fim < 0) {
                            spans.add(SpanLua(TokenTipo.COMENTARIO, i, texto.length))
                            estado.emBloco = true
                            return spans
                        }
                        spans.add(SpanLua(TokenTipo.COMENTARIO, i, fim + 2))
                        i = fim + 2
                    } else {
                        spans.add(SpanLua(TokenTipo.COMENTARIO, i, texto.length))
                        return spans
                    }
                }
                // strings com aspas simples/duplas
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < texto.length) {
                        if (texto[j] == '\\') { j += 2; continue }
                        if (texto[j] == c) { j++; break }
                        j++
                    }
                    spans.add(SpanLua(TokenTipo.STRING, i, j.coerceAtMost(texto.length)))
                    i = j.coerceAtMost(texto.length)
                }
                // string longa [[ ]]
                texto.startsWith("[[", i) -> {
                    val fim = texto.indexOf("]]", i + 2)
                    if (fim < 0) {
                        spans.add(SpanLua(TokenTipo.STRING, i, texto.length))
                        estado.emBloco = true // string longa não terminada (tratada como bloco)
                        return spans
                    }
                    spans.add(SpanLua(TokenTipo.STRING, i, fim + 2))
                    i = fim + 2
                }
                // números
                c.isDigit() -> {
                    var j = i
                    while (j < texto.length && (texto[j].isDigit() || texto[j] == '.' ||
                                (texto[j].lowercaseChar() == 'x' && j == i + 1 && texto[i] == '0') ||
                                (texto[j].lowercaseChar() in "abcdef" && j > i && texto[i] == '0' && texto[i + 1].lowercaseChar() == 'x'))
                    ) j++
                    spans.add(SpanLua(TokenTipo.NUMERO, i, j))
                    i = j
                }
                // identificadores (palavras-chave e API)
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < texto.length && (texto[j].isLetterOrDigit() || texto[j] == '_')) j++
                    val palavra = texto.substring(i, j)
                    val tipo = when {
                        palavra in PALAVRAS -> TokenTipo.PALAVRA_CHAVE
                        palavra in apis -> TokenTipo.API
                        else -> TokenTipo.NORMAL
                    }
                    spans.add(SpanLua(tipo, i, j))
                    i = j
                }
                else -> i++
            }
        }
        return spans
    }

    /** Símbolos da API para o realce (nomes simples após o ponto — ex.: "set_position"). */
    val simbolosApi: Set<String> by lazy {
        LuaApiCatalog.ENTRIES.map { it.symbol.substringAfterLast('.') }.toSet()
    }
}

/** Balanço de blocos Lua de um trecho (+1 abre, −1 fecha — aproximação padrão). */
fun nivelBlocos(texto: String): Int {
    val palavras = Regex("[A-Za-z_]+").findAll(texto).map { it.value }.toList()
    var nivel = 0
    var i = 0
    while (i < palavras.size) {
        when (palavras[i]) {
            "function", "do", "repeat", "then" -> nivel++
            "end", "until" -> nivel--
            // 'do' de 'for/while' já entra; 'elseif'/'else' neutros
        }
        i++
    }
    return nivel
}

// ------------------------------------------------------------------ DOCUMENTO

/**
 * Documento de linhas com cursor e undo/redo (snapshots — simples, PROVÁVEL
 * e sem limite de tamanho para projetos reais; máx. 100 níveis).
 */
class DocumentoLua(conteudo: String = "") {

    val linhas = ArrayList<String>()
    var linhaCursor: Int = 0
        private set
    var colunaCursor: Int = 0
        private set

    private val desfazer = ArrayList<String>(101)
    private val refazer = ArrayList<String>(101)

    init {
        carregar(conteudo)
    }

    /** Carrega um conteúdo inteiro (abertura de arquivo). */
    fun carregar(conteudo: String) {
        val semCr = conteudo.replace("\r\n", "\n").replace("\r", "\n")
        linhas.clear()
        linhas.addAll(semCr.split('\n'))
        if (linhas.isEmpty()) linhas.add("")
        linhaCursor = 0
        colunaCursor = 0
        desfazer.clear()
        refazer.clear()
    }

    fun conteudo(): String = linhas.joinToString("\n")

    private fun snapshot() {
        desfazer.add(conteudo())
        if (desfazer.size > 100) desfazer.removeAt(0)
        refazer.clear()
    }

    /** Desfaz a última edição (true se havia algo). */
    fun desfazerEdicao(): Boolean {
        val anterior = desfazer.removeLastOrNull() ?: return false
        refazer.add(conteudo())
        carregarMantendoHistorico(anterior)
        return true
    }

    /** Refaz a última edição desfeita (true se havia algo). */
    fun refazerEdicao(): Boolean {
        val seguinte = refazer.removeLastOrNull() ?: return false
        desfazer.add(conteudo())
        carregarMantendoHistorico(seguinte)
        return true
    }

    private fun carregarMantendoHistorico(conteudo: String) {
        val d = desfazer.toList()
        val r = refazer.toList()
        carregar(conteudo)
        desfazer.addAll(d)
        refazer.addAll(r)
    }

    /** Move o cursor (sem editar) — toque em outra linha/coluna. */
    fun moverCursor(linha: Int, coluna: Int) {
        linhaCursor = linha.coerceIn(0, linhas.size - 1)
        colunaCursor = coluna.coerceIn(0, linhas[linhaCursor].length)
    }

    /** Insere texto na posição do cursor (teclas de apoio e digitação). */
    fun inserirTexto(texto: String) {
        snapshot()
        val l = linhas[linhaCursor]
        val col = colunaCursor.coerceIn(0, l.length)
        linhas[linhaCursor] = l.substring(0, col) + texto + l.substring(col)
        colunaCursor = col + texto.length
    }

    /**
     * ENTER com INDENTAÇÃO AUTOMÁTICA (C1.2): herda o recuo da linha atual;
     * +1 nível se a linha termina em then/do/function/repeat/else/{;
     * −1 nível se a NOVA linha começa com end/until/else/elseif/}.
     */
    fun quebrarLinha() {
        snapshot()
        val l = linhas[linhaCursor]
        val col = colunaCursor.coerceIn(0, l.length)
        val recuo = l.substring(0, col).takeWhile { it == ' ' || it == '\t' }
        val recuoAtual = l.takeWhile { it == ' ' || it == '\t' }
        val antes = l.substring(0, col).trim()
        val depois = l.substring(col).trim()
        // C1.2: INDENTAÇÃO POR BALANÇO de blocos — abre (+1): function/do/
        // repeat/then; fecha (−1): end/until (else/elseif são neutros).
        val balance = nivelBlocos(antes)
        val fechaBloco = Regex("^(end|until|else|elseif|\\})\\b").containsMatchIn(depois)
        var novoRecuo = recuoAtual
        if (balance > 0 && !fechaBloco) novoRecuo = recuoAtual + "    "
        else if (balance <= 0 && fechaBloco) novoRecuo = recuoAtual.dropLast(4.coerceAtMost(recuoAtual.length))
        val novaLinha = novoRecuo + depois
        linhas[linhaCursor] = antes.let { recuoAtual + it } // recuo preservado
        linhas.add(linhaCursor + 1, novaLinha)
        linhaCursor += 1
        colunaCursor = novoRecuo.length
    }

    /** Backspace: junta com a linha anterior quando no início, senão apaga 1 char. */
    fun apagarAntes(): Boolean {
        val l = linhas[linhaCursor]
        if (colunaCursor > 0) {
            snapshot()
            val col = colunaCursor.coerceIn(1, l.length)
            linhas[linhaCursor] = l.substring(0, col - 1) + l.substring(col)
            colunaCursor = col - 1
            return true
        }
        if (linhaCursor > 0) {
            snapshot()
            val anterior = linhas[linhaCursor - 1]
            linhas[linhaCursor - 1] = anterior + l
            linhas.removeAt(linhaCursor)
            linhaCursor -= 1
            colunaCursor = anterior.length
            return true
        }
        return false
    }

    /** Apaga o caractere à direita do cursor (Delete). */
    fun apagarDepois(): Boolean {
        val l = linhas[linhaCursor]
        if (colunaCursor < l.length) {
            snapshot()
            linhas[linhaCursor] = l.substring(0, colunaCursor) + l.substring(colunaCursor + 1)
            return true
        }
        if (linhaCursor < linhas.size - 1) {
            snapshot()
            linhas[linhaCursor] = l + linhas[linhaCursor + 1]
            linhas.removeAt(linhaCursor + 1)
            return true
        }
        return false
    }

    /**
     * C1.6: aplica UMA linha editada pelo campo ativo (diff por prefixo/sufixo
     * comum — UM snapshot por mudança, cursor no ponto do diff). A tela passa
     * o texto do campo; o modelo decide inserção/apagamento.
     */
    fun aplicarLinha(novoTexto: String) {
        val antigo = linhas[linhaCursor]
        if (antigo == novoTexto) return
        snapshot()
        var p = 0
        val minLen = minOf(antigo.length, novoTexto.length)
        while (p < minLen && antigo[p] == novoTexto[p]) p++
        var s = 0
        while (s < minLen - p && antigo[antigo.length - 1 - s] == novoTexto[novoTexto.length - 1 - s]) s++
        val apagado = antigo.length - p - s
        val inserido = novoTexto.substring(p, novoTexto.length - s)
        colunaCursor = p
        if (apagado > 0) {
            val l = antigo
            linhas[linhaCursor] = l.substring(0, p) + l.substring(p + apagado)
        }
        if (inserido.isNotEmpty()) {
            val atual = linhas[linhaCursor]
            linhas[linhaCursor] = atual.substring(0, p) + inserido + atual.substring(p)
        }
        colunaCursor = p + inserido.length
    }

    /** Busca a partir do cursor (circular); devolve true se achou (cursor move). */
    fun buscar(texto: String): Boolean {
        if (texto.isEmpty()) return false
        val total = linhas.size
        var l = linhaCursor
        repeat(total + 1) {
            val idx = linhas[l].indexOf(texto, if (it == 0) colunaCursor else 0)
            if (idx >= 0) {
                linhaCursor = l
                colunaCursor = idx + texto.length
                return true
            }
            l = (l + 1) % total
        }
        return false
    }

    /** Substitui TODAS as ocorrências (devolve a contagem). */
    fun substituirTudo(texto: String, novo: String): Int {
        if (texto.isEmpty()) return 0
        val antes = conteudo()
        val contagem = contar(antes, texto)
        if (contagem == 0) return 0
        snapshot()
        carregar(antes.replace(texto, novo))
        return contagem
    }

    private fun contar(s: String, texto: String): Int {
        var i = 0
        var n = 0
        while (true) {
            i = s.indexOf(texto, i)
            if (i < 0) return n
            n++
            i += texto.length
        }
    }

    /** Tamanho em bytes do conteúdo (UTF-8) — o aviso de 200 KB. */
    fun tamanhoBytes(): Int = conteudo().toByteArray(Charsets.UTF_8).size
}

// ------------------------------------------------------------------ AUTOCOMPLETAR

/**
 * Sugestão do autocompletar OFFLINE: (nome, assinatura, descrição en/pt) —
 * vem do LuaApiCatalog (o MESMO catálogo que gerou LUA_API.md).
 */
class SugestaoLua(
    val nome: String,
    val assinatura: String,
    val descPt: String,
    val descEn: String
)

object AutocompletarLua {

    private val keywordSuggestions = listOf(
        "function", "end", "if", "then", "else", "elseif", "for", "while", "do",
        "local", "return", "nil", "true", "false"
    ).map { SugestaoLua(it, it, "Palavra-chave Lua", "Lua keyword") }

    /**
     * Sugestões para o texto ANTES do cursor:
     *  - termina com "nick."/"scene."/"input."/"ui."/"storage."/"engine."/"signal."/"timer." →
     *    MEMBROS daquele receptor (categoria do LuaApiCatalog);
     *  - prefixo simples → palavras-chave + símbolos globais da API.
     */
    fun sugestoes(textoAntes: String, limite: Int = 8): List<SugestaoLua> {
        val receptor = Regex("(nick|scene|input|ui|storage|engine|signal|timer)\\.$").find(textoAntes)
        if (receptor != null) {
            val cat = receptor.groupValues[1]
            val prefixo = textoAntes.substringAfterLast('.').lowercase()
            return LuaApiCatalog.ENTRIES
                .filter { it.category == cat && it.symbol.lowercase().startsWith("$cat.$prefixo") }
                .take(limite)
                .map { SugestaoLua(it.symbol.substringAfterLast('.'), it.signature, it.descPt, it.descEn) }
        }
        val prefixo = textoAntes.substringAfterLast('.').lowercase()
        if (prefixo.isEmpty()) return emptyList()
        val todas = keywordSuggestions + LuaApiCatalog.ENTRIES.map {
            SugestaoLua(it.symbol, it.signature, it.descPt, it.descEn)
        }
        // o prefixo é o segmento DEPOIS do último ponto ("nick.set_pos" →
        // "set_pos") — casa com o mesmo segmento do nome da sugestão
        return todas
            .filter { it.nome.lowercase().substringAfterLast('.').startsWith(prefixo) }
            .take(limite)
    }
}

// ------------------------------------------------------------------ MODELOS

/** C1.7 — modelos de script novo (vazio, movimento, coletar, gatilho). */
object ModelosScript {

    val NOMES = listOf("vazio", "movimento", "coletar", "gatilho")

    fun conteudo(modelo: String): String = when (modelo) {
        "movimento" -> """-- movimento: o Nick anda em círculo (on_update)
velocidade = params.velocidade or 2

on_update = function(dt)
  local p = nick.position()
  local t = engine.time()
  nick.set_position(
    p.x + math.cos(t * velocidade) * dt * velocidade,
    p.y,
    p.z + math.sin(t * velocidade) * dt * velocidade
  )
end
"""
        "coletar" -> """-- coletar: soma no contador e desaparece (Area Nick)
on_enter = function(outro)
  ui.contagem("moedas", 1)
  nick.destroy()
end
"""
        "gatilho" -> """-- gatilho: emite um sinal quando alguém entra
on_enter = function(outro)
  signal.emit("porta_aberta", { quem = outro.name() })
end
"""
        else -> "-- script novo\n\non_start = function()\n  log.info(\"olá\")\nend\n"
    }
}
