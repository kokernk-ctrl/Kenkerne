package com.kenkern.engine.editor

/**
 * G2 (v0.1.19): MATEMÁTICA PURA das alturas da janela "Adicionar Nick" — sem
 * dependência de Compose/Android, testável na JVM (AddNickLayoutTest).
 *
 * BUG: título + busca + abas + rodapé ocupavam os ~240dp de viewport útil em
 * paisagem (800×360) e a lista de tipos ficava com altura zero; as abas
 * encolhiam para "R…", "B…", "C…". CORREÇÃO: cabeçalho fixo de UMA linha de
 * 48dp (título + busca + fechar) e TODO o resto em UM único LazyColumn —
 * chips "filho/raiz" (36dp), abas de categoria como chips com nome COMPLETO
 * em linha com rolagem horizontal e os cartões de tipo.
 *
 * A janela funciona com 240dp de ALTURA ÚTIL (referência do usuário: paisagem
 * 800×360; viewport = 360 − TopBar 48 − StatusBar 24 − barras do sistema).
 */
object AddNickLayout {

    /** Cabeçalho fixo: UMA linha de 48dp (título + busca + fechar). */
    const val CABECALHO_DP = 48f

    /** Chip compacto (seletor filho/raiz e abas de categoria): 36dp. */
    const val CHIP_DP = 36f

    /** Padding vertical total dos grupos de chips (4dp em cima + 4dp embaixo). */
    const val PADDING_CHIPS_DP = 8f

    /** Cartão de tipo mínimo: ícone + 2 linhas de texto + paddings verticais. */
    const val CARTAO_MINIMO_DP = 52f

    /** Padding inferior base do LazyColumn (soma-se ao inset de navegação). */
    const val PADDING_INFERIOR_BASE_DP = 16f

    /** Limite do G2: o cabeçalho fixo ocupa no máximo 20% da altura útil. */
    const val LIMITE_CABECALHO = 0.20f

    /** contentPadding inferior = base + inset das barras do sistema. */
    fun paddingInferiorDp(insetInferiorDp: Float): Float =
        PADDING_INFERIOR_BASE_DP + insetInferiorDp

    /** O cabeçalho fixo de 48dp está dentro do limite de 20% da altura útil? */
    fun alturaCabecalhoNoLimite(alturaUtilDp: Float): Boolean =
        CABECALHO_DP <= alturaUtilDp * LIMITE_CABECALHO

    /**
     * Altura mínima para a LISTA ser utilizável: 2 grupos de chips (filho/raiz
     * e abas, cada um 36dp + padding) + 1 cartão de tipo + padding inferior.
     */
    fun alturaMinimaListaDp(): Float =
        CHIP_DP + PADDING_CHIPS_DP + CHIP_DP + PADDING_CHIPS_DP +
            CARTAO_MINIMO_DP + paddingInferiorDp(0f)

    /** O primeiro cartão de tipo fica inteiramente visível com essa altura útil? */
    fun listaMinimaVisivel(alturaUtilDp: Float): Boolean =
        alturaMinimaListaDp() <= alturaUtilDp - CABECALHO_DP

    /**
     * O ÚLTIMO tipo do catálogo é alcançável: a lista rola e o padding inferior
     * (base + inset) cabe dentro do espaço restante após o cabeçalho.
     */
    fun ultimoTipoAlcancavel(alturaUtilDp: Float, insetInferiorDp: Float = 0f): Boolean =
        alturaMinimaListaDp() + insetInferiorDp <= alturaUtilDp - CABECALHO_DP
}
