package com.kenkern.engine.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R3 (v0.1.29) — COLAR do clipboard no DocumentoLua: multilinha vira linhas
 * de verdade (sem indentação automática espúria), divide a linha atual no
 * cursor e o resto cola no fim da última linha colada; CRLF/CR normalizados.
 */
class R3DocumentoColarTest {

    @Test
    fun `colar multilinha no cursor zero divide e une o resto`() {
        val doc = DocumentoLua("aa\nbb")
        doc.moverCursor(0, 0)
        doc.colar("x\ny")
        assertEquals(listOf("x", "yaa", "bb"), doc.linhas)
        assertEquals(1, doc.linhaCursor)
        assertEquals(1, doc.colunaCursor) // na junção (depois de "y", antes de "aa")
    }

    @Test
    fun `colar de uma linha insere sem quebrar`() {
        val doc = DocumentoLua("local x = 1")
        doc.moverCursor(0, 11)
        doc.colar("42")
        assertEquals("local x = 142", doc.linhas[0])
        assertEquals(13, doc.colunaCursor)
    }

    @Test
    fun `colar trata CRLF e CR`() {
        val doc = DocumentoLua("a\nb")
        doc.moverCursor(0, 1)
        doc.colar("x\r\ny\rz")
        assertEquals(listOf("ax", "y", "z", "b"), doc.linhas)
        assertEquals(2, doc.linhaCursor)
        assertEquals(1, doc.colunaCursor)
    }

    @Test
    fun `colar vazio não mexe no documento`() {
        val doc = DocumentoLua("a\nb")
        doc.moverCursor(0, 1)
        doc.colar("")
        assertEquals(listOf("a", "b"), doc.linhas)
        assertEquals(0, doc.linhaCursor)
    }
}
