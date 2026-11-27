package com.kenkern.engine.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3 (v0.1.29) — LAYOUT DA BARRA SUPERIOR DO EDITOR SEM SOBREPOSIÇÃO,
 * calculado por LARGURAS (a checagem "editor" usa o MESMO objeto).
 * Barra R3: [voltar | nome e estado | Verificar | menu ⋮] — o sintoma
 * v0.1.28: "Verificar cortado na borda direita e cobrindo o rótulo salvo"
 * (elementos soltos após o nome) + "linha de teclas coberta pelos botões
 * A−, A+, ⏎ e Buscar" (overlay flutuante sobre a faixa de teclas).
 */
class R3LargurasEditorLuaTest {

    @Test
    fun `barra cabe em 360dp (retrato do alvo 800x360) e em 800dp`() {
        // 800x360dp é o alvo do projeto; o editor de código é RETRATO → 360dp
        assertTrue("sobreposição a 360dp", LargurasEditorLua.semSobreposicao(360))
        assertTrue("sobreposição a 800dp", LargurasEditorLua.semSobreposicao(800))
    }

    @Test
    fun `o campo de nome recebe largura legível nas duas telas`() {
        assertTrue(LargurasEditorLua.larguraDoNome(360) >= LargurasEditorLua.NOME_MINIMO_DP)
        assertTrue(LargurasEditorLua.larguraDoNome(800) >= LargurasEditorLua.NOME_MINIMO_DP)
        assertEquals(360 - 208, LargurasEditorLua.larguraDoNome(360))
    }

    @Test
    fun `orçamento fixo da barra é o declarado (2 ícones + Verificar + margens)`() {
        val fixos = 2 * LargurasEditorLua.BOTAO_ICONE_DP + LargurasEditorLua.BOTAO_VERIFICAR_DP +
            LargurasEditorLua.MARGENS_DP
        assertEquals(208, fixos)
    }
}
