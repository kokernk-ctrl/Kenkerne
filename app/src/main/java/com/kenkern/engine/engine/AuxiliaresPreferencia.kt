package com.kenkern.engine.engine

/**
 * H1 (v0.1.20): "Mostrar auxiliares" é PREFERÊNCIA DO USUÁRIO — o Play/Pausa
 * NUNCA a altera; eles apenas SUPRIMEM o desenho (e o picking) enquanto a
 * sessão de jogo está PLAYING/PAUSED. No STOP tudo volta exatamente como o
 * usuário deixou.
 *
 * CAUSA RAIZ corrigida aqui (KenkernSurfaceView.pickNickAt:784, do E5):
 * a condição de picking dos auxiliares usava `playState != STOPPED` — o
 * INVERSO da regra do E5 ("pegáveis fora de Play"): no editor (incluindo
 * DEPOIS do Stop) os auxiliares ficavam NÃO pegáveis e em Play (quando são
 * invisíveis) seriam pegáveis. Efeito relatado no aparelho: depois de um
 * Play→Stop, os auxiliares "não voltam" como objetos selecionáveis/editáveis.
 *
 * Funções PURAS (sem estado): o estado dono da preferência segue sendo o
 * espelho `mostrarAuxiliares` do SurfaceView (escrito SÓ pela UI) — aqui
 * apenas a DECISÃO por estado de play fica testável na JVM e igual para os
 * dois caminhos (desenho no EngineRenderer e picking no SurfaceView).
 */
object AuxiliaresPreferencia {

    /**
     * Desenho dos auxiliares de editor: LIGADO na preferência do usuário E
     * fora da sessão de jogo (STOPPED). Play/Pausa suprimem; Stop restaura —
     * sem jamais escrever na preferência.
     */
    @JvmStatic
    fun deveDesenhar(ligado: Boolean, playState: PlayState): Boolean =
        ligado && playState == PlayState.STOPPED

    /**
     * Picking dos auxiliares: MESMA regra do desenho — invisível em Play =
     * não pegável; visível no editor (STOPPED) = pegável (tolerância de 24dp).
     * Invisível nunca seleciona objeto (regra "cena = tela").
     */
    @JvmStatic
    fun devePegar(ligado: Boolean, playState: PlayState): Boolean =
        deveDesenhar(ligado, playState)
}
