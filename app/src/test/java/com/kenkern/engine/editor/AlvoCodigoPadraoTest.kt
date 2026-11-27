package com.kenkern.engine.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Onda 0 (v0.1.28, P3) — PORTA DE ENTRADA DO EDITOR DE CÓDIGO: a aba
 * "Código" (seletor Cena/UI/Código da TopBar) abre o editor de código com o
 * alvo PADRÃO — o PRIMEIRO script do projeto (mesma ordem da lista de abas
 * do editor de código, alfabética); projeto sem script → null → a UI cria
 * um do modelo "vazio" (o navegador de arquivos nasce com conteúdo, e o
 * caminho fica Hub → projeto → Código = 2 toques).
 */
class AlvoCodigoPadraoTest {

    @Test
    fun comScriptsAbreOPrimeiroDaLista() {
        assertEquals(
            "a.lua",
            EditorViewModel.alvoCodigoPadrao(listOf("a.lua", "b.lua", "c.lua"))
        )
    }

    @Test
    fun semScriptsDevolNullParaCriarDoModeloVazio() {
        assertNull(EditorViewModel.alvoCodigoPadrao(emptyList()))
    }

    @Test
    fun listaDeUmScriptAbreEleMesmo() {
        assertEquals("movimento.lua", EditorViewModel.alvoCodigoPadrao(listOf("movimento.lua")))
    }
}
