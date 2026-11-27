package com.kenkern.engine.ui.jogo

import com.kenkern.engine.engine.PlayState

/**
 * P1 (v0.1.25) — ROTEAMENTO DE TOQUE DA JANELA DE JOGO (modelo puro, sem
 * Android/Compose — testável na JVM e usado pela checagem "play-sair" do
 * Diagnóstico).
 *
 * CAUSA RAIZ DO BUG (prova: KenkernSurfaceView.kt:541-542, v0.1.24):
 * `if (requestedPlayState != PlayState.STOPPED) return true` consumia TODO
 * toque em Play — e a View nativa (AndroidView) recebia o dispatch ANTES dos
 * botões Compose, então Pausar/Sair ficavam mortos e o usuário ficava preso
 * na Janela de Jogo.
 *
 * REGRA ÚNICA (fonte única para a SurfaceView, os botões e o Diagnóstico):
 *  - toque DENTRO da região de um botão da janela de jogo → o botão recebe;
 *    a cena NUNCA consome (a SurfaceView devolve false e o toque sobe para o
 *    Compose, que entrega ao botão com zIndex acima do AndroidView);
 *  - toque FORA das regiões → a cena consome (controle do jogo);
 *  - quando o watchdog dispara (sem frame por WATCHDOG_MS), o botão "Forçar
 *    saída" entra na frente e a região dele tem prioridade;
 *  - voltar (BACK) do sistema SEMPRE sai do Play (PLAYING ou PAUSED → STOPPED).
 *
 * ZERO alocação por evento: a lista de regiões é fixada quando a janela de
 * jogo muda de layout (não por frame) e a decisão é tomada apenas no DOWN
 * (MOVE/UP reusam a região do DOWN dentro da SurfaceView).
 */
enum class AlvoToque { BOTAO_PAUSAR, BOTAO_SAIR, BOTAO_FORCAR, CENA }

/** Região retangular em px da View (mesmo espaço do MotionEvent). */
class RegiaoBotao(
    val alvo: AlvoToque,
    val esquerda: Float,
    val topo: Float,
    val direita: Float,
    val baixo: Float
) {
    fun contem(x: Float, y: Float): Boolean =
        x >= esquerda && x <= direita && y >= topo && y <= baixo
}

/** Resultado do roteamento de um toque na janela de jogo. */
class DecisaoToque(
    val alvo: AlvoToque,
    /** true = a SurfaceView consome (controle do jogo); false = delega ao Compose. */
    val cenaConsome: Boolean,
    /** Texto curto com o motivo — vai para o log e para o overlay de toque. */
    val motivo: String
)

object RoteadorToqueJogo {

    /** P1.3: sem frame renderizado por este intervalo → watchdog de "Forçar saída". */
    const val WATCHDOG_MS = 3000L

    /**
     * Decide quem recebe um toque na janela de jogo.
     * @param estado PlayState atual (STOPPED nunca chega aqui — janela de jogo desligada).
     * @param x/y coordenadas do toque em px da View.
     * @param regioes regiões dos botões (Pausar/Sair/Forçar) em px da View.
     * @param watchdogEmAlerta true quando o loop de render está travado (sem frame por WATCHDOG_MS).
     */
    fun decidir(
        estado: PlayState,
        x: Float,
        y: Float,
        regioes: List<RegiaoBotao>,
        watchdogEmAlerta: Boolean
    ): DecisaoToque {
        // Regiões em ordem de prioridade: Forçar saída (watchdog) fica na FRENTE.
        val ordenadas = if (watchdogEmAlerta) {
            regioes.sortedByDescending { if (it.alvo == AlvoToque.BOTAO_FORCAR) 1 else 0 }
        } else regioes
        for (regiao in ordenadas) {
            if (regiao.contem(x, y)) {
                return DecisaoToque(
                    alvo = regiao.alvo,
                    cenaConsome = false,
                    motivo = "botão ${regiao.alvo} recebe o toque (cena NÃO consome)"
                )
            }
        }
        return DecisaoToque(
            alvo = AlvoToque.CENA,
            cenaConsome = true,
            motivo = "fora dos botões — cena consome (controle do jogo)"
        )
    }

    /**
     * P1.3: a ação do botão BACK do sistema na janela de jogo — SEMPRE sai
     * (PLAYING → STOPPED, PAUSED → STOPPED). Modelo puro para a JVM.
     */
    fun acaoBack(estado: PlayState): PlayState = PlayState.STOPPED

    /**
     * L2 (v0.1.28): a AÇÃO de cada alvo de toque — a checagem "play-sair" do
     * Diagnóstico simula o toque (decidir) e aplica a ação (este método),
     * provando que o jogo PAUSA (PLAYING→PAUSED), CONTINUA (PAUSED→PLAYING)
     * e SAI (→STOPPED) no TESTE — a UI do aparelho só confirma.
     * CENA não muda estado (o toque é o controle do jogo).
     */
    fun acao(alvo: AlvoToque, estado: PlayState): PlayState = when (alvo) {
        AlvoToque.BOTAO_PAUSAR -> if (estado == PlayState.PLAYING) PlayState.PAUSED else PlayState.PLAYING
        AlvoToque.BOTAO_SAIR, AlvoToque.BOTAO_FORCAR -> PlayState.STOPPED
        AlvoToque.CENA -> estado
    }
}
