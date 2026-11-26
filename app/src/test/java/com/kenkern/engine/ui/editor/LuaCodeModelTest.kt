package com.kenkern.engine.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1 (v0.1.26) — TESTES DO EDITOR DE CÓDIGO LUA: tokenizador (realce),
 * documento (undo/redo, indentação automática, busca/substituir, diff por
 * linha, limite 200 KB) e autocompletar offline (contexto nick./input. …).
 */
class LuaCodeModelTest {

    // ---------- TOKENIZADOR ----------

    @Test
    fun `palavras-chave strings comentários e números ficam marcados`() {
        val spans = TokenizadorLua.marcarLinha(
            "local x = 42 -- conta\n",
            TokenizadorLua.Estado()
        )
        val tipos = spans.map { it.tipo }
        assertTrue(TokenTipo.PALAVRA_CHAVE in tipos)
        assertTrue(TokenTipo.NUMERO in tipos)
        assertEquals(TokenTipo.COMENTARIO, tipos.last())
    }

    @Test
    fun `string com aspas vai até a aspa de fecho`() {
        val linha = "log.info(\"olá mundo\")"
        val spans = TokenizadorLua.marcarLinha(linha, TokenizadorLua.Estado())
        val string = spans.first { it.tipo == TokenTipo.STRING }
        assertEquals("\"olá mundo\"", linha.substring(string.ini, string.fim))
    }

    @Test
    fun `comentário de bloco atravessa linhas`() {
        val e = TokenizadorLua.Estado()
        val l1 = TokenizadorLua.marcarLinha("--[[ início", e)
        assertEquals(TokenTipo.COMENTARIO, l1.first().tipo)
        assertTrue("continua em bloco", e.emBloco)
        val l2 = TokenizadorLua.marcarLinha("fim ]] local y", e)
        assertEquals(TokenTipo.COMENTARIO, l2.first().tipo)
        assertFalse("bloco fechado", e.emBloco)
        val palavra = l2.first { it.tipo == TokenTipo.PALAVRA_CHAVE }
        assertEquals("local", "fim ]] local y".substring(palavra.ini, palavra.fim))
    }

    @Test
    fun `símbolos da API ficam marcados como API`() {
        val spans = TokenizadorLua.marcarLinha("nick.set_position(1, 2, 3)", TokenizadorLua.Estado())
        assertTrue(TokenTipo.API in spans.map { it.tipo })
    }

    // ---------- DOCUMENTO: undo/redo ----------

    @Test
    fun `undo e redo restauram o conteúdo`() {
        val d = DocumentoLua("a = 1")
        d.moverCursor(0, 5) // carregar deixa o cursor em (0,0) — o teste digita no fim
        d.inserirTexto("2")
        assertEquals("a = 12", d.conteudo())
        assertTrue(d.desfazerEdicao())
        assertEquals("a = 1", d.conteudo())
        assertTrue(d.refazerEdicao())
        assertEquals("a = 12", d.conteudo())
        assertTrue("segundo undo volta ao carregado", d.desfazerEdicao())
        assertEquals("a = 1", d.conteudo())
    }

    @Test
    fun `limite de 100 níveis de undo`() {
        val d = DocumentoLua("")
        repeat(150) { d.inserirTexto("x") }
        var n = 0
        while (d.desfazerEdicao()) n++
        assertEquals(100, n)
    }

    // ---------- DOCUMENTO: indentação automática (C1.2) ----------

    @Test
    fun `enter após then indenta um nível`() {
        val d = DocumentoLua("if x then")
        d.moverCursor(0, 9)
        d.quebrarLinha()
        assertEquals("    ", d.linhas[1])
        assertEquals(1, d.linhaCursor)
        assertEquals(4, d.colunaCursor)
    }

    @Test
    fun `nova linha dentro de function herda recuo`() {
        val d = DocumentoLua("function f()")
        d.moverCursor(0, 12)
        d.quebrarLinha()
        assertEquals("    ", d.linhas[1])
        d.inserirTexto("end")
        assertTrue(d.conteudo().contains("    end"))
    }

    // ---------- DOCUMENTO: busca e substituir (C1.2) ----------

    @Test
    fun `busca circular acha e move o cursor`() {
        val d = DocumentoLua("um dois tres\ndois quatro")
        d.moverCursor(0, 0)
        assertTrue(d.buscar("dois"))
        assertEquals(0, d.linhaCursor)
        assertEquals(7, d.colunaCursor)
        assertTrue(d.buscar("dois"))
        assertEquals(1, d.linhaCursor)
        assertTrue(d.buscar("dois"))
        assertEquals(0, d.linhaCursor) // circular
    }

    @Test
    fun `substituir tudo troca todas as ocorrências`() {
        val d = DocumentoLua("a = 1\nb = a + a")
        val n = d.substituirTudo("a", "valor")
        assertEquals(3, n)
        assertEquals("valor = 1\nb = valor + valor", d.conteudo())
    }

    // ---------- DOCUMENTO: diff por linha (C1.6) ----------

    @Test
    fun `aplicarLinha insere no meio e mantém o cursor`() {
        val d = DocumentoLua("local x = ")
        d.moverCursor(0, 10)
        d.aplicarLinha("local x = 42")
        assertEquals("local x = 42", d.conteudo())
        assertEquals(12, d.colunaCursor)
    }

    @Test
    fun `apagar antes junta linhas no início`() {
        val d = DocumentoLua("linha um\nlinha dois")
        d.moverCursor(1, 0)
        assertTrue(d.apagarAntes())
        assertEquals("linha umlinha dois", d.conteudo())
        assertEquals(0, d.linhaCursor)
        assertEquals(8, d.colunaCursor)
    }

    // ---------- LIMITE 200 KB (C1.6) ----------

    @Test
    fun `tamanho em bytes abaixo e acima do aviso`() {
        val pequeno = DocumentoLua("-- script pequeno")
        assertTrue(pequeno.tamanhoBytes() < 200 * 1024)
        val grande = DocumentoLua("-- " + "x".repeat(210 * 1024))
        assertTrue(grande.tamanhoBytes() > 200 * 1024)
    }

    // ---------- AUTOCOMPLETAR (C1.4) ----------

    @Test
    fun `apos nick ponto mostra membros do nick`() {
        val s = AutocompletarLua.sugestoes("local nome = nick.")
        assertTrue("membros do nick", s.any { it.nome == "set_position" })
        assertFalse(s.any { it.nome.startsWith("scene.") })
    }

    @Test
    fun `apos input ponto mostra membros do input`() {
        val s = AutocompletarLua.sugestoes("local j = input.")
        assertTrue(s.isNotEmpty())
    }

    @Test
    fun `prefixo simples sugere palavras-chave e API`() {
        val s = AutocompletarLua.sugestoes("funct")
        assertTrue(s.any { it.nome == "function" })
    }

    @Test
    fun `cada sugestao tem assinatura e descricoes en e pt`() {
        val s = AutocompletarLua.sugestoes("nick.set_pos")
        assertEquals(1, s.size)
        val sug = s.first()
        assertTrue(sug.assinatura.contains("set_position"))
        assertTrue(sug.descPt.isNotBlank())
        assertTrue(sug.descEn.isNotBlank())
    }

    // ---------- MODELOS (C1.7) ----------

    @Test
    fun `modelos de script têm conteúdo para os 4 tipos`() {
        for (nome in ModelosScript.NOMES) {
            val c = ModelosScript.conteudo(nome)
            assertTrue("$nome vazio?", c.isNotBlank())
        }
        assertTrue(ModelosScript.conteudo("coletar").contains("ui.contagem"))
        assertTrue(ModelosScript.conteudo("gatilho").contains("signal.emit"))
    }
}
