package com.kenkern.engine.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1 (v0.1.19): teste JVM do cálculo de larguras da TopBar.
 *
 * Referência do usuário: paisagem 800dp × 360dp — viewport com ~240dp de
 * altura útil. A TopBar tem 48dp de altura e DEVE garantir que Hierarquia,
 * Inspetor e Resetar layout fiquem SEMPRE visíveis (seção fixa do fim), com a
 * área rolável de ferramentas entre as seções fixas.
 */
class TopBarLayoutTest {

    /** O fim fixo é separador + 3 botões de 40dp (Hierarquia, Inspetor, Resetar). */
    @Test
    fun fimFixoSaoTresBotoesDeQuarenta() {
        assertEquals(9f, TopBarLayout.SEPARADOR_DP, 0f)
        assertEquals(129f, TopBarLayout.larguraFimFixoDp(), 0.001f)
    }

    /** O início fixo é 1 botão (Voltar) + separador. */
    @Test
    fun inicioFixoEhUmBotaoMaisSeparador() {
        assertEquals(49f, TopBarLayout.larguraInicioFixoDp(), 0.001f)
    }

    /** Soma dos botões fixos (4 × 40dp) + separadores + padding ≤ largura da tela de referência (800dp). */
    @Test
    fun secoesFixasCabemEm800dp() {
        val fixa = TopBarLayout.larguraTotalFixaDp()
        // 4 botões × 40dp = 160dp de botões — o resto são separadores e padding.
        assertEquals(160f, TopBarLayout.BOTOES_INICIO * TopBarLayout.BOTAO_DP +
            TopBarLayout.BOTOES_FIM * TopBarLayout.BOTAO_DP, 0.001f)
        assertTrue("soma fixa ($fixa dp) deve ser ≤ 800dp", fixa <= 800f)
        assertEquals(186f, fixa, 0.001f) // 49 + 129 + 8
    }

    /** Em 800dp a área rolável fica com 614dp — mais que o mínimo de 120dp. */
    @Test
    fun rolavelEm800dpEhSuficiente() {
        assertEquals(614f, TopBarLayout.larguraRolavelDp(800f), 0.001f)
        assertTrue(TopBarLayout.rolavelSuficiente(800f))
        assertTrue(TopBarLayout.cabemSecoesFixas(800f))
    }

    /** Telas estreitas: as seções fixas têm prioridade e a rolável nunca fica negativa. */
    @Test
    fun rolavelNuncaNegativa() {
        assertEquals(0f, TopBarLayout.larguraRolavelDp(100f), 0.001f)
        assertFalse(TopBarLayout.cabemSecoesFixas(100f))
        // Retrato estreito de referência (360dp): rolável = 174dp ≥ 120dp.
        assertTrue(TopBarLayout.rolavelSuficiente(360f))
    }
}
