package com.kenkern.engine.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E4 (v0.1.18) — LAYOUT DOS PAINÉIS FLUTUANTES (funções PURAS do
 * [PanelStateStore.companion], sem Context):
 *
 * CAUSA RAIZ do "painéis não movem nem redimensionam": PanelUiState.floatX/Y/W/H
 * eram `var Float` COMUNS (PanelStateStore.kt:28-31 da v0.1.17) — o
 * detectDragGestures do cabeçalho escrevia nelas, mas Modifier.offset{}/
 * width()/height() não reagem a variáveis comuns: nada recompunha e o painel
 * ficava parado na tela. Agora são MutableState<Float> + clamp dentro do
 * viewport + alça de 48dp + ACOPLAR/RESETAR + persistência POR ORIENTAÇÃO.
 */
class PanelLayoutTest {

    // ------------------------------------------------- clamp dentro do viewport

    @Test
    fun clampMantemPainelInteiroDentroDoViewport() {
        // arrasto para fora dos 4 lados → volta para dentro
        assertEquals(0f to 0f, PanelStateStore.clampDentro(-50f, -30f, 200f, 150f, 800f, 600f))
        assertEquals(600f to 450f, PanelStateStore.clampDentro(900f, 700f, 200f, 150f, 800f, 600f))
        // dentro: não mexe
        assertEquals(100f to 50f, PanelStateStore.clampDentro(100f, 50f, 200f, 150f, 800f, 600f))
    }

    @Test
    fun clampComPainelMaiorQueOViewportEncostaEmZero() {
        // painel maior que o bounds: encosta no canto (nunca some da tela)
        assertEquals(0f to 0f, PanelStateStore.clampDentro(50f, 50f, 900f, 700f, 800f, 600f))
    }

    // ------------------------------------------------- parse/format da linha

    @Test
    fun parseLinhaValidaDevolveOs7Campos() {
        val e = PanelStateStore.parseLinha("0,1,240,-12,34,300,380", 180f, 360f)
        assertEquals(false, e.visible)
        assertEquals(true, e.docked)
        assertEquals(240f, e.dockWidthDp)
        assertEquals(-12f, e.floatX)
        assertEquals(34f, e.floatY)
        assertEquals(300f, e.floatW)
        assertEquals(380f, e.floatH)
    }

    @Test
    fun parseLinhaCorrompidaCaiNoPadraoProfissional() {
        // lixo, campos a menos, números inválidos: visível+acoplado SEM lançar
        val e = PanelStateStore.parseLinha("lixo", 180f, 360f)
        assertTrue(e.visible)
        assertTrue(e.docked)
        val curta = PanelStateStore.parseLinha("1,1,240", 180f, 360f)
        assertTrue(curta.visible && curta.docked)
        val nan = PanelStateStore.parseLinha("1,0,999999,xa,yb,1,2", 180f, 360f)
        assertEquals(360f, nan.dockWidthDp) // largura limitada ao máximo do painel
    }

    @Test
    fun parseLimitaLarguraAcopladaEFaixaFlutuanteMinima() {
        val e = PanelStateStore.parseLinha("1,1,5000,0,0,10,10", 180f, 360f)
        assertEquals("largura acoplada limitada ao máximo", 360f, e.dockWidthDp)
        assertEquals("largura flutuante respeita o mínimo", PanelStateStore.MIN_FLOAT_W_DP, e.floatW)
        assertEquals("altura flutuante respeita o mínimo", PanelStateStore.MIN_FLOAT_H_DP, e.floatH)
    }

    @Test
    fun formatarEVoltarParse() {
        val linha = PanelStateStore.formatarLinha(true, false, 240f, 11.6f, -3.2f, 301.5f, 380.9f)
        val e = PanelStateStore.parseLinha(linha, 180f, 360f)
        assertEquals(true, e.visible)
        assertEquals(false, e.docked)
        assertEquals(240f, e.dockWidthDp)
        assertEquals(12f, e.floatX)   // persiste INTEIROS (dp): 11,6 → 12
        assertEquals(-3f, e.floatY)
        assertEquals(302f, e.floatW)
        assertEquals(381f, e.floatH)
    }

    // ------------------------------------------------- chave por orientação

    @Test
    fun chaveDePrefsSeparaOrientacoes() {
        // E4: retrato e paisagem guardam layouts INDEPENDENTES por painel
        assertEquals("hierarchy#land", PanelStateStore.chavePrefs("hierarchy", "land"))
        assertEquals("hierarchy#port", PanelStateStore.chavePrefs("hierarchy", "port"))
        assertTrue(
            PanelStateStore.chavePrefs("hierarchy", "land") != PanelStateStore.chavePrefs("hierarchy", "port")
        )
    }

    @Test
    fun minimosDoPainelFlutuanteSaoOsDaEspecificacao() {
        // tamanho mínimo do painel flutuante (menor que isso o dedo não redimensiona direito)
        assertEquals(220f, PanelStateStore.MIN_FLOAT_W_DP)
        assertEquals(200f, PanelStateStore.MIN_FLOAT_H_DP)
    }
}
