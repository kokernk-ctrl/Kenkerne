package com.kenkern.engine.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * T0.5 (v0.1.21) — CENA 2.5D DO HUB: matemática PURA da animação de fundo
 * (paralaxe de estrelas + buraco negro sutil com disco de acreção), desenhada
 * por Canvas a 30 FPS. Sem GL no Hub: custo mínimo, ZERO alocação por frame
 * (os valores saem em [out] pré-alocado).
 *
 * Regras da tarefa:
 *  - 30 FPS ([FPS_ALVO]) — o Hub não precisa de mais que isso;
 *  - PAUSA quando o Hub não está visível (o composable não roda o laço);
 *  - RESPEITA "reduzir animações" do sistema ([animacoesReduzidas]) — fundo
 *    estático (tempo congela; o buraco negro fica parado, o app continua);
 *  - sutil: brilho e movimento pequenos — o Hub continua legível.
 */
object HubCena2D {

    /** FPS alvo do fundo (tarefa: 30 FPS no Hub). */
    const val FPS_ALVO: Int = 30

    /** Intervalo entre frames em ns (1/30 s). */
    const val INTERVALO_NS: Long = 33_333_333L

    /** Tempo de UMA órbita do disco de acreção (s) — lento e sutil. */
    const val ORBITA_SEGUNDOS: Float = 18f

    /** Velocidade base da paralaxe (px/s por unidade de profundidade). */
    const val VELOCIDADE_PARALAXE: Float = 6f

    /**
     * Deslocamento HORIZONTAL de uma camada de estrelas (paralaxe 2.5D):
     * camadas PROFUNDAS andam MENOS ([profundidade] 0–1; 1 = mais perto).
     * Sinal negativo: o fundo anda para trás do tempo (deriva suave).
     * Enrola no tamanho da tela (o chamador aplica % largura).
     */
    fun deslocamentoCamada(tempoSegundos: Float, profundidade: Float, out: FloatArray) {
        val p = profundidade.coerceIn(0f, 1f)
        out[0] = -tempoSegundos * VELOCIDADE_PARALAXE * p
    }

    /**
     * Posição de UM brilho em órbita ao redor do buraco negro (frações do
     * raio do disco; ângulo cresce no tempo). out = [x, y] normalizados.
     */
    fun pontoOrbita(tempoSegundos: Float, fase: Float, raio: Float, out: FloatArray) {
        val ang = tempoSegundos * (2f * PI.toFloat() / ORBITA_SEGUNDOS) + fase
        out[0] = cos(ang) * raio
        out[1] = sin(ang) * raio * 0.62f // disco achatado (perspectiva 2.5D)
    }

    /**
     * Pulso do brilho do disco de acreção — SUAVE (0,85–1,0) e lento; nunca
     * pisca (0 = nada: sutilidade da tarefa).
     */
    fun pulsoAcrecao(tempoSegundos: Float): Float {
        val fase = sin(tempoSegundos * (2f * PI.toFloat() / (ORBITA_SEGUNDOS * 0.5f)))
        return 0.925f + 0.075f * fase
    }

    /**
     * "Reduzir animações" do sistema: escala de duração ≤ 0,01 (0 no Android)
     * ou economia de bateria — o fundo fica ESTÁTICO (sem laço de frames).
     */
    fun animacoesReduzidas(escalaDuracaoAnimator: Float): Boolean =
        !(escalaDuracaoAnimator.isFinite()) || escalaDuracaoAnimator <= 0.01f
}
