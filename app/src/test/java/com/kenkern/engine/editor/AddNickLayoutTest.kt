package com.kenkern.engine.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G2 (v0.1.19): teste JVM do cálculo de alturas da janela "Adicionar Nick".
 *
 * Referência do usuário: paisagem 800dp × 360dp — viewport com ~240dp de altura
 * útil (360 − TopBar 48 − StatusBar 24 − barras do sistema). A janela inteira
 * (cabeçalho fixo + LazyColumn única) tem que funcionar com 240dp.
 */
class AddNickLayoutTest {

    /** O cabeçalho fixo é UMA linha de 48dp — exatamente 20% de 240dp. */
    @Test
    fun cabecalhoUmaLinhaDe48dp() {
        assertEquals(48f, AddNickLayout.CABECALHO_DP, 0f)
        assertTrue("48dp deve ser ≤ 20% de 240dp (48dp)", AddNickLayout.alturaCabecalhoNoLimite(240f))
        // Com mais altura também passa; com altura de 200dp o 20% (40dp) não
        // comporta o cabeçalho de 48dp — sinaliza aparelho abaixo do mínimo.
        assertTrue(AddNickLayout.alturaCabecalhoNoLimite(360f))
        assertFalse(AddNickLayout.alturaCabecalhoNoLimite(200f))
    }

    /** Altura mínima da lista: chips filho/raiz (36+8) + abas (36+8) + 1 cartão (52) + padding inferior (16). */
    @Test
    fun alturaMinimaDaLista() {
        assertEquals(156f, AddNickLayout.alturaMinimaListaDp(), 0.001f) // 44+44+52+16
        // Com o cabeçalho de 48dp: 48 + 156 = 204 ≤ 240 → a lista mínima cabe
        // nos ~240dp de viewport útil em paisagem 800×360.
        assertTrue(AddNickLayout.listaMinimaVisivel(240f))
        assertTrue(AddNickLayout.listaMinimaVisivel(360f))
        assertFalse(AddNickLayout.listaMinimaVisivel(150f))
    }

    /** O último tipo do catálogo é alcançável com 240dp, mesmo com inset de navegação de 24dp. */
    @Test
    fun ultimoTipoAlcancavelCom240dp() {
        assertTrue(AddNickLayout.ultimoTipoAlcancavel(240f))
        assertTrue(AddNickLayout.ultimoTipoAlcancavel(240f, 24f)) // barra de gestos
        // Com inset de 24dp o mínimo sobe para 204+24 = 228 ≤ 240 — a folga é
        // pequena mas existe; com 180dp de altura útil NÃO alcança.
        assertFalse(AddNickLayout.ultimoTipoAlcancavel(180f, 24f))
    }

    /** contentPadding inferior = base 16dp + inset (0 → 16; 24 → 40). */
    @Test
    fun paddingInferiorSomaInset() {
        assertEquals(16f, AddNickLayout.paddingInferiorDp(0f), 0.001f)
        assertEquals(40f, AddNickLayout.paddingInferiorDp(24f), 0.001f)
    }

    /** Chips compactos de 36dp (seletor filho/raiz e abas) — nunca radio buttons de 48dp. */
    @Test
    fun chipsCompactosDe36dp() {
        assertEquals(36f, AddNickLayout.CHIP_DP, 0f)
    }
}
