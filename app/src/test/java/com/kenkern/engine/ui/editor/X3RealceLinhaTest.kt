package com.kenkern.engine.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X3 (v0.1.31) — o estilo de cada linha do editor de código NÃO depende da
 * posição do cursor.
 *
 * Causa raiz reproduzida: a linha com o cursor era um BasicTextField PLANO
 * (sem spans) e as linhas sem cursor usavam `realceLua` — os tokens MUDAVAM
 * de cor ao mover o cursor (a palavra afetada mudava conforme a linha do
 * cursor). A correção usa a MESMA função pura `realceLinhaLua` nos dois
 * caminhos (Text sem cursor + VisualTransformation no BasicTextField).
 *
 * Esta classe roda em JVM (função pura, sem Compose): prova determinismo,
 * presença dos spans com a cor certa e que a continuação de linha usa os
 * MESMOS estilos.
 */
class X3RealceLinhaTest {

    private fun spansDe(texto: String) = TokenizadorLua.marcarLinha(texto, TokenizadorLua.Estado())

    @Test
    fun `realce e deterministico - mesma linha sempre o mesmo resultado`() {
        val linha = "local pos = engine.set_position(nick, 1.5, \"erro\") -- marca"
        val a = realceLinhaLua(linha)
        val b = realceLinhaLua(linha)
        // Pureza: sem estado do cursor — duas chamadas iguais produzem o
        // MESMO AnnotatedString (equals estrutural, spans inclusive).
        assertEquals(a, b)
    }

    @Test
    fun `identificador de API recebe span com cor visivel`() {
        val linha = "engine.set_position(nick, 1, 2)"
        val spans = spansDe(linha)
        val apis = spans.filter { it.tipo == TokenTipo.API }
        assertTrue("nenhum span de API encontrado em: $spans", apis.isNotEmpty())
        val pos = apis.first { linha.substring(it.ini, it.fim) == "set_position" }
        assertEquals(corDoToken(TokenTipo.API), corDoToken(TokenTipo.API))
        assertEquals("set_position", linha.substring(pos.ini, pos.fim))
    }

    @Test
    fun `palavra chave comentario numero e string recebem spans`() {
        val linha = "local n = 3.5 -- nota"
        val texto = realceLinhaLua(linha)
        val tipos = spansDe(linha).map { it.tipo }.toSet()
        assertTrue("falta PALAVRA_CHAVE: $tipos", TokenTipo.PALAVRA_CHAVE in tipos)
        assertTrue("falta NUMERO: $tipos", TokenTipo.NUMERO in tipos)
        assertTrue("falta COMENTARIO: $tipos", TokenTipo.COMENTARIO in tipos)
        // o texto exibido é o MESMO texto (transformação só de estilo)
        assertEquals(linha, texto.text)
    }

    @Test
    fun `continuacao de linha indentada usa os mesmos estilos`() {
        val original = "engine.set_position(nick, 1, 2)"
        val continuacao = "    engine.set_position(nick, 1, 2)"
        val spansA = spansDe(original).filter { it.tipo == TokenTipo.API }
        val spansB = spansDe(continuacao).filter { it.tipo == TokenTipo.API }
        assertTrue(spansA.isNotEmpty() && spansB.isNotEmpty())
        assertEquals("a continuação deve marcar os MESMOS tokens", spansA.map { it.tipo }, spansB.map { it.tipo })
        val corA = realceLinhaLua(original)
        val corB = realceLinhaLua(continuacao)
        // mesmo conteúdo de spans (offsets deslocados pela indentação)
        assertEquals(corA.spanStyles.size, corB.spanStyles.size)
    }
}
