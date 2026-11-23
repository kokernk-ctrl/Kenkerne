package com.kenkern.engine.engine

import kotlin.math.abs
import kotlin.math.hypot

/**
 * M1.3 (v0.1.22) — INPUT DE JOGO: matemática PURA do joystick de tela e das
 * ações nomeadas (mover, pular, atacar). Sem android.* — testável na JVM.
 *
 * Regras do bloco JOYSTICK E INPUT DE TELA (adendo v4):
 *  - NORMALIZAÇÃO: vetor do centro ao dedo, dividido pelo raio máximo, |v| ≤ 1;
 *  - ZONA MORTA: raio interno (fração 0–1); dentro dela o vetor é ZERO
 *    (dedo apoiado no centro não move) e a SAÍDA re-normaliza do limiar à
 *    borda (sem salto brusco na borda da zona morta);
 *  - CURVA DE RESPOSTA: exponencial suave (v^1,5) — leve no centro, firme
 *    na borda;
 *  - TRAVAR EM 8 DIREÇÕES: quando [oitoDirecoes], o ângulo é ajustado para o
 *    mais próximo de 0/45/90/… (anda em diagonal perfeita);
 *  - MULTITOQUE: quem processa os ponteiros é a UI — aqui cada TOQUE vira
 *    UMA chamada; dois dedos (joystick + botão) não interferem porque as
 *    funções são PURAS por dedo;
 *  - AÇÕES NOMEADAS: mover (vetor), pular e atacar (pressionado/soltado com
 *    borda de 1 tick — [estadoPular]/[estadoAtacar]).
 */
object InputJogo {

    /** Curva de resposta (0–1 → 0–1): suave no centro, firme na borda. */
    fun curva(v: Float): Float {
        val x = v.coerceIn(0f, 1f)
        return if (x <= 0f) 0f else Math.pow(x.toDouble(), 1.5).toFloat()
    }

    /**
     * Vetor FINAL do joystick: normaliza, aplica zona morta (re-normalizando
     * do limiar), curva de resposta e trava de 8 direções. Escreve em
     * [out] (tamanho 2: x, y) — zero alocação por frame.
     */
    fun aplicar(
        dx: Float, dy: Float, raioMax: Float, zonaMorta: Float,
        oitoDirecoes: Boolean, out: FloatArray
    ) {
        val raio = if (raioMax.isFinite() && raioMax > 1e-4f) raioMax else 1f
        val zona = zonaMorta.coerceIn(0f, 0.95f)
        val nx = dx / raio
        val ny = dy / raio
        val len = hypot(nx.toDouble(), ny.toDouble()).toFloat()
        if (len <= zona || len <= 1e-6f) {
            out[0] = 0f; out[1] = 0f
            return
        }
        var mag = curva(len)
        if (oitoDirecoes) {
            // ângulo ajustado ao múltiplo de 45° mais próximo (anda reto/diagonal)
            val ang = kotlin.math.atan2(ny.toDouble(), nx.toDouble())
            val ajustado = Math.round(ang / (Math.PI / 4.0)) * (Math.PI / 4.0)
            out[0] = (kotlin.math.cos(ajustado)).toFloat() * mag
            out[1] = (kotlin.math.sin(ajustado)).toFloat() * mag
            // reescala para |v| = mag (a diagonal 45° tem |v| = mag)
            val l2 = hypot(out[0].toDouble(), out[1].toDouble()).toFloat()
            if (l2 > 1e-6f) {
                out[0] = out[0] / l2 * mag
                out[1] = out[1] / l2 * mag
            }
        } else {
            val escala = mag / len
            out[0] = nx * escala
            out[1] = ny * escala
        }
        // nunca sai do círculo unitário (curva garante, por segurança de novo)
        val l = hypot(out[0].toDouble(), out[1].toDouble()).toFloat()
        if (l > 1f) {
            out[0] /= l; out[1] /= l
        }
    }

    /**
     * Âncora padrão do joystick por configuração (chave estável): "fixo" nasce
     * no canto fixo; "flutuante" nasce ONDE O DEDO TOCA (dentro da zona);
     * "dinâmico" segue o dedo até o limite. Devolve o deslocamento do centro.
     */
    fun centroPorTipo(tipo: String, toqueX: Float, toqueY: Float, out: FloatArray) {
        when (tipo) {
            "flutuante" -> { out[0] = toqueX; out[1] = toqueY } // nasce no toque
            "dinamico" -> { out[0] = toqueX; out[1] = toqueY }  // segue o toque
            else -> { out[0] = 0f; out[1] = 0f }                 // fixo: âncora
        }
    }

    /**
     * Botão de AÇÃO: borda de 1 tick — [estadoAnterior] × [pressionadoAgora].
     * Devolve TRUE apenas no tick de PRESSÃO (o pulo não repete segurando).
     */
    fun bordaDePressao(estadoAnterior: Boolean, pressionadoAgora: Boolean): Boolean =
        !estadoAnterior && pressionadoAgora
}

/**
 * Estado MUTÁVEL do input de jogo por sessão de Play (1 instância por sessão;
 * escrita pela UI thread, leitura pelo tick de física). Ações nomeadas:
 * mover (vetor −1..1), pular, atacar.
 */
class EstadoInputJogo {
    @JvmField var moverX: Float = 0f
    @JvmField var moverY: Float = 0f
    @JvmField var pularPressionado: Boolean = false
    @JvmField var atacarPressionado: Boolean = false

    // bordas de 1 tick (consumidas pela física)
    @JvmField var pularBorda: Boolean = false
    @JvmField var atacarBorda: Boolean = false
    private var pularAntes = false
    private var atacarAntes = false

    /** Chama 1× por tick DEPOIS que a física consumiu as bordas. */
    fun fimDoTick() {
        pularBorda = InputJogo.bordaDePressao(pularAntes, pularPressionado)
        atacarBorda = InputJogo.bordaDePressao(atacarAntes, atacarPressionado)
        pularAntes = pularPressionado
        atacarAntes = atacarPressionado
    }

    fun resetar() {
        moverX = 0f; moverY = 0f
        pularPressionado = false; atacarPressionado = false
        pularBorda = false; atacarBorda = false
        pularAntes = false; atacarAntes = false
    }
}
