package com.kenkern.engine.editor

/**
 * G1 (v0.1.19): MATEMÁTICA PURA das larguras da TopBar — sem dependência de
 * Compose/Android, testável na JVM (TopBarLayoutTest).
 *
 * BUG: com botões de voo e auxiliares ativos a barra passava da largura e o
 * botão do Inspetor não aparecia. CORREÇÃO: o layout agora tem TRÊS seções —
 *
 *   [ INÍCIO FIXO ] [ ROLÁVEL horizontal ] [ FIM FIXO ]
 *   Voltar + sep     Undo…Auxiliares+Res.   sep + Hierarquia + Inspetor + Resetar
 *
 * As seções fixas têm largura CONHECIDA (constantes abaixo); o teste prova que
 * a soma delas cabe em 800dp (paisagem de referência) e que a área rolável
 * resultante é suficiente.
 */
object TopBarLayout {

    /** Botão de ícone da TopBar: tamanho FIXO (TopBarIcon usa Modifier.size(40.dp)). */
    const val BOTAO_DP = 40f

    /** Separador vertical: 1dp de traço + 2×4dp de padding horizontal. */
    const val SEPARADOR_DP = 9f

    /** Padding horizontal da Row raiz: 4dp em cada lado. */
    const val PADDING_LATERAL_DP = 8f

    /** Quantidade de botões fixos no FIM: Hierarquia + Inspetor + Resetar layout. */
    const val BOTOES_FIM = 3

    /** Quantidade de botões fixos no INÍCIO: Voltar. */
    const val BOTOES_INICIO = 1

    /** Início fixo: Voltar (40dp) + separador (9dp) = 49dp. */
    fun larguraInicioFixoDp(): Float = BOTOES_INICIO * BOTAO_DP + SEPARADOR_DP

    /** Fim fixo: separador (9dp) + 3 botões (Hierarquia, Inspetor, Resetar) = 129dp. */
    fun larguraFimFixoDp(): Float = SEPARADOR_DP + BOTOES_FIM * BOTAO_DP

    /** Soma das duas seções fixas + padding lateral da barra. */
    fun larguraTotalFixaDp(): Float = larguraInicioFixoDp() + larguraFimFixoDp() + PADDING_LATERAL_DP

    /**
     * Largura disponível para a área rolável de ferramentas; nunca negativa
     * (0 = sem espaço — as seções fixas têm prioridade).
     */
    fun larguraRolavelDp(larguraTelaDp: Float): Float =
        (larguraTelaDp - larguraTotalFixaDp()).coerceAtLeast(0f)

    /** A soma dos botões fixos cabe na largura da tela? */
    fun cabemSecoesFixas(larguraTelaDp: Float): Boolean =
        larguraTotalFixaDp() <= larguraTelaDp

    /** A área rolável que sobra é utilizável (mínimo de ferramentas visíveis)? */
    fun rolavelSuficiente(larguraTelaDp: Float, minimoDp: Float = 120f): Boolean =
        larguraRolavelDp(larguraTelaDp) >= minimoDp
}
