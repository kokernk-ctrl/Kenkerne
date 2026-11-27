package com.kenkern.engine.ui.editor

/**
 * R3 (v0.1.29) — LARGURAS DA BARRA SUPERIOR do editor de código, para a
 * checagem "layout"/"editor" e o teste JVM provarem SEM SOBREPOSIÇÃO por
 * largura (a tela do editor é RETRATO: no alvo de 800x360dp ela ocupa 360dp
 * de largura — o contrato vale para os dois).
 *
 * Barra superior R3: [voltar | nome e estado (peso) | Verificar | menu ⋮].
 * Orçamento fixo: ícone 48 + ícone 48 + margens 16 = 112dp; o Verificar tem
 * LARGURA FIXA (cabe sempre); o nome fica com o RESTO (peso 1f).
 */
object LargurasEditorLua {

    /** KIconButton: alvo de toque 48dp. */
    const val BOTAO_ICONE_DP = 48

    /** KButton "Verificar": texto 13sp + paddings — largura FIXA. */
    const val BOTAO_VERIFICAR_DP = 96

    /** padding horizontal 8+8 do Row. */
    const val MARGENS_DP = 16

    /** Largura mínima legível do campo nome (menos que isso = sobreposição). */
    const val NOME_MINIMO_DP = 80

    /**
     * A barra cabe sem sobreposição na largura dada: fixos + nome mínimo
     * ≤ largura. Em 360dp (retrato de um 800x360) e em 800dp (paisagem) TEM
     * que caber — a regra do projeto é 800x360dp TUDO FUNCIONAL.
     */
    fun semSobreposicao(larguraTelaDp: Int): Boolean {
        val fixos = 2 * BOTAO_ICONE_DP + BOTAO_VERIFICAR_DP + MARGENS_DP
        return larguraTelaDp - fixos >= NOME_MINIMO_DP
    }

    /** Largura que o campo de nome recebe numa tela de [larguraTelaDp]. */
    fun larguraDoNome(larguraTelaDp: Int): Int {
        val fixos = 2 * BOTAO_ICONE_DP + BOTAO_VERIFICAR_DP + MARGENS_DP
        return larguraTelaDp - fixos
    }
}
