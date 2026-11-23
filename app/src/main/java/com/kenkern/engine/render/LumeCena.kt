package com.kenkern.engine.render

import com.kenkern.engine.core.LuzNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.Scene
import kotlin.math.cos

/**
 * H2 (v0.1.20) — LUME: coleta as luzes dos NICKS da cena e monta o conjunto
 * de iluminação do frame. PURA (sem GL, sem android): testável na JVM.
 *
 * Regras da tarefa:
 *  - DIRECIONAL: a primeira light.directional ATIVA e LIGADA substitui o sol
 *    fixo do editor; NENHUMA na cena (ou todas desligadas) mantém o sol padrão;
 *  - PONTUAL/SPOT: até [MAX_PONTUAIS_SPOTS] ativas por frame, escolhidas pela
 *    MAIOR CONTRIBUIÇÃO para a câmera (intensidade / (1 + d²), d = distância
 *    luz→câmera);
 *  - luz DESLIGADA (campo ligado=false) ou inativa (nick.active=false,
 *    inclusive por pai) nunca ilumina. "Visível" é flag do AUXILIAR de editor
 *    (E5) e NÃO corta luz — o interruptor da luz é o campo `ligado` da classe;
 *  - luz sem a classe "Luz" (Nick antigo) usa os DEFAULTS do tipo;
 *  - ATENUAÇÃO SUAVE: janela (1 − (d/alcance)⁴)² × 1/(1 + d²) — zero no
 *    alcance e contínua lá (mesma fórmula no shader);
 *  - ARRAYS PRIMITIVOS REAPROVEITADOS: [Frame] é pré-alocado — NADA é criado
 *    por frame;
 *  - ATUALIZAÇÃO NO MESMO FRAME: o renderer chama [coletar] antes do flush.
 *
 * Convenção de direção: a luz viaja pelo −Z LOCAL do Nick (como uma câmera
 * apontando) — direcional e spot. A posição é a translação mundial.
 */
object LumeCena {

    /** Teto da tarefa: 4 luzes pontuais/spot ativas por frame. */
    const val MAX_PONTUAIS_SPOTS = 4

    /** Máximo coletado por frame (o corte por contribuição escolhe as 4). */
    private const val MAX_COLETADAS = 16

    /** Sentinel do cone: cosExterno ≤ -1.5 = luz PONTUAL (sem cone). */
    const val CONE_PONTUAL = -2f

    /** Conjunto do frame — PRÉ-ALOCADO e reaproveitado (zero alocação). */
    class Frame {
        @JvmField val dir = FloatArray(3)        // direcional: direção de VIAGEM
        @JvmField val cor = FloatArray(3)        // direcional: cor
        @JvmField var intensidadeDirecional: Float = 0f
        @JvmField var temDirecional: Boolean = false

        @JvmField var nPontuais: Int = 0         // ≤ MAX_PONTUAIS_SPOTS
        @JvmField val pos = FloatArray(MAX_PONTUAIS_SPOTS * 3)
        @JvmField val corLs = FloatArray(MAX_PONTUAIS_SPOTS * 3) // cor × intensidade
        @JvmField val alcance = FloatArray(MAX_PONTUAIS_SPOTS)
        @JvmField val dirLs = FloatArray(MAX_PONTUAIS_SPOTS * 3) // viagem (spot; unitária)
        @JvmField val cone = FloatArray(MAX_PONTUAIS_SPOTS * 2)  // [cosInterno, cosExterno] × 4

        /** Rascunhos internos (coleta e ordenação — nunca saem daqui). */
        internal val pilha = arrayOfNulls<Nick>(512)
        internal val candidatas = arrayOfNulls<Nick>(MAX_COLETADAS)
        internal val candidataContrib = FloatArray(MAX_COLETADAS)
        internal val valores = LuzNick.Valores()
        internal val dirScratch = FloatArray(3)

        fun reset() {
            temDirecional = false
            intensidadeDirecional = 0f
            nPontuais = 0
        }
    }

    /**
     * Coleta o conjunto do frame. Ordem pre-order; a primeira direcional ativa
     * e ligada vence; pontuais/spot entram por contribuição decrescente.
     */
    fun coletar(scene: Scene?, camX: Float, camY: Float, camZ: Float, frame: Frame) {
        frame.reset()
        if (scene == null) return
        val pilha = frame.pilha
        var top = 0
        for (r in scene.roots) {
            if (top >= pilha.size) break
            pilha[top++] = r
        }
        var nCandidatas = 0
        while (top > 0) {
            val nick = pilha[--top] ?: continue
            if (!nick.active) continue // pai inativo desativa a subárvore (poda)
            val filhos = nick.children
            // Empilha em ordem REVERSA para o LIFO visitar na ordem da árvore
            // (mesma técnica do NickPlayRuntime): "primeira direcional" e
            // desempate de contribuição dependem da ordem pre-order.
            for (i in filhos.indices.reversed()) {
                if (top >= pilha.size) break
                pilha[top++] = filhos[i]
            }
            if (!nick.kind.startsWith("light.")) continue
            val valores = frame.valores
            LuzNick.ler(nick, valores)
            if (!valores.ligado || valores.intensidade <= 0f) continue
            val m = nick.transform.worldMatrix.m
            when (nick.kind) {
                "light.directional" -> {
                    // Primeira direcional ativa+ligada substitui o sol.
                    if (!frame.temDirecional) {
                        frame.temDirecional = true
                        direcaoViagem(m, frame.dir)
                        frame.cor[0] = valores.corX; frame.cor[1] = valores.corY; frame.cor[2] = valores.corZ
                        frame.intensidadeDirecional = valores.intensidade
                    }
                }
                "light.point", "light.spot" -> {
                    val px = m[12]; val py = m[13]; val pz = m[14]
                    val dx = px - camX; val dy = py - camY; val dz = pz - camZ
                    val d2 = dx * dx + dy * dy + dz * dz
                    // Contribuição para a CÂMERA: mais perto e mais intensa
                    // vence (1/(1+d²) é suave e nunca explode).
                    val contrib = valores.intensidade / (1f + d2)
                    nCandidatas = inserirPorContribuicao(frame, nCandidatas, nick, contrib)
                }
            }
        }
        preencherSaidas(frame, nCandidatas)
    }

    /**
     * Insertion sort desc por contribuição em arrays pré-alocados (≤16):
     * devolve o novo total. Cheio (16), a candidata entra só se vencer a
     * MENOR contribuição (que sai).
     */
    private fun inserirPorContribuicao(frame: Frame, nAtual: Int, nick: Nick, contrib: Float): Int {
        if (nAtual == 0) {
            frame.candidataContrib[0] = contrib
            frame.candidatas[0] = nick
            return 1
        }
        val limite = if (nAtual < MAX_COLETADAS) nAtual else MAX_COLETADAS - 1
        if (nAtual >= MAX_COLETADAS && contrib <= frame.candidataContrib[MAX_COLETADAS - 1]) return nAtual
        var pos = limite
        while (pos > 0 && frame.candidataContrib[pos - 1] < contrib) {
            frame.candidataContrib[pos] = frame.candidataContrib[pos - 1]
            frame.candidatas[pos] = frame.candidatas[pos - 1]
            pos--
        }
        frame.candidataContrib[pos] = contrib
        frame.candidatas[pos] = nick
        return if (nAtual < MAX_COLETADAS) nAtual + 1 else nAtual
    }

    /** Copia as top MAX_PONTUAIS_SPOTS candidatas para as saídas do frame. */
    private fun preencherSaidas(frame: Frame, nCandidatas: Int) {
        val n = nCandidatas.coerceAtMost(MAX_PONTUAIS_SPOTS)
        frame.nPontuais = n
        for (i in 0 until n) {
            val nick = frame.candidatas[i] ?: continue
            val valores = frame.valores
            LuzNick.ler(nick, valores)
            val m = nick.transform.worldMatrix.m
            frame.pos[i * 3] = m[12]; frame.pos[i * 3 + 1] = m[13]; frame.pos[i * 3 + 2] = m[14]
            frame.corLs[i * 3] = valores.corX * valores.intensidade
            frame.corLs[i * 3 + 1] = valores.corY * valores.intensidade
            frame.corLs[i * 3 + 2] = valores.corZ * valores.intensidade
            frame.alcance[i] = valores.alcance
            if (nick.kind == "light.spot") {
                direcaoViagem(m, frame.dirScratch)
                frame.dirLs[i * 3] = frame.dirScratch[0]
                frame.dirLs[i * 3 + 1] = frame.dirScratch[1]
                frame.dirLs[i * 3 + 2] = frame.dirScratch[2]
                frame.cone[i * 2] = cos(Math.toRadians(valores.anguloInterno.toDouble()).toFloat())
                frame.cone[i * 2 + 1] = cos(Math.toRadians(valores.anguloExterno.toDouble()).toFloat())
            } else {
                frame.dirLs[i * 3] = 0f; frame.dirLs[i * 3 + 1] = 0f; frame.dirLs[i * 3 + 2] = -1f
                frame.cone[i * 2] = CONE_PONTUAL
                frame.cone[i * 2 + 1] = CONE_PONTUAL
            }
        }
    }

    /**
     * Direção de VIAGEM da luz = −Z local do Nick em MUNDO (a luz "aponta"
     * como uma câmera), normalizada; zero → cai para −Y (luz de cima).
     */
    fun direcaoViagem(m: FloatArray, out: FloatArray) {
        var x = -m[8]; var y = -m[9]; var z = -m[10]
        val len = kotlin.math.sqrt(x * x + y * y + z * z)
        if (len > 1e-6f) {
            out[0] = x / len; out[1] = y / len; out[2] = z / len
        } else {
            out[0] = 0f; out[1] = -1f; out[2] = 0f
        }
    }

    /**
     * ATENUAÇÃO SUAVE (mesma fórmula do shader, para testes JVM): janela
     * (1 − (d/alcance)⁴)² — ZERO no alcance e contínua lá — × 1/(1 + d²).
     */
    fun atenuacao(d: Float, alcance: Float): Float {
        if (d >= alcance) return 0f
        val t = d / alcance
        val janela = 1f - t * t * t * t
        val j2 = janela * janela
        return j2 / (1f + d * d)
    }
}
