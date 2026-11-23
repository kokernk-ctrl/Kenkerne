package com.kenkern.engine.render

import com.kenkern.engine.core.LenteNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4

/**
 * H3 (v0.1.20) — LENTE: câmeras de jogo como NICKS. Matemática PURA (sem GL,
 * sem android — testável na JVM):
 *  - escolha da câmera principal (SÓ UMA por cena; a primeira em pré-ordem
 *    com principal=true vence se o usuário duplicar o flag);
 *  - matrizes PROJEÇÃO (perspectiva com fov/near/far OU ortográfica com
 *    tamanho) e VIEW (olho na posição do Nick, olhando pelo −Z local, up =
 *    +Y local) — as MESMAS convenções da OrbitCamera do editor;
 *  - pirâmide do auxiliar com o FOV REAL do Nick (EditorHelpers.buildPiramide
 *    já recebe fov/aspect).
 */
object LenteNickMatriz {

    /** Achados de UMA busca por câmeras (pré-alocado pelo chamador). */
    class Achados {
        var principal: Nick? = null
        var qualquer: Nick? = null
    }

    /**
     * Varre a cena em pré-ordem: [Achados.principal] = primeira câmera ATIVA
     * com principal=true; [Achados.qualquer] = primeira camera.* ativa.
     * Pilha pré-alocada (zero alocação por frame).
     */
    fun buscarCameras(scene: Scene?, pilha: Array<Nick?>, achados: Achados) {
        achados.principal = null
        achados.qualquer = null
        if (scene == null) return
        var top = 0
        for (r in scene.roots) {
            if (top >= pilha.size) break
            pilha[top++] = r
        }
        while (top > 0) {
            val nick = pilha[--top] ?: continue
            if (!nick.active) continue // pai inativo desativa a subárvore
            val filhos = nick.children
            for (i in filhos.indices.reversed()) {
                if (top >= pilha.size) break
                pilha[top++] = filhos[i]
            }
            if (!nick.kind.startsWith("camera.")) continue
            if (achados.qualquer == null) achados.qualquer = nick
            if (achados.principal == null) {
                val v = LenteNick.Valores()
                LenteNick.ler(nick, v)
                if (v.principal) achados.principal = nick
            }
        }
    }

    /** ReferId do alvo do Follow Camera: campo alvo → Nick; senão o 1º jogador. */
    fun alvoDoFollow(nick: Nick, scene: Scene?): Nick? {
        val v = LenteNick.Valores()
        LenteNick.ler(nick, v)
        val id = v.alvo.trim()
        if (id.isNotEmpty()) {
            val achou = scene?.findNick(ReferenceId(id))
            if (achou != null) return achou
        }
        return primeiroPlayerAtivo(scene)
    }

    /** Primeiro body.player ativo em pré-ordem (mesma regra do NickPlayRuntime). */
    fun primeiroPlayerAtivo(scene: Scene?): Nick? {
        if (scene == null) return null
        val pilha = ArrayList<Nick>(16)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val nick = pilha[head++]
            if (nick.active && nick.kind == "body.player") return nick
            for (c in nick.children) pilha.add(c)
        }
        return null
    }

    /**
     * PROJEÇÃO do Nick: perspectiva (fov/near/far) ou ortográfica
     * (tamanhoOrto = meia-altura). Escreve em [out] e devolve o fov USADO
     * (para o auxiliar e para a escala de overlays); ortográfica devolve
     * `meiaAltura→graus` equivalente? NÃO — devolve -1 para sinalizar ORTO
     * (o auxiliar de câmera ortográfica usa a pirâmide com o fov equivalente
     * = 2×atan(tamanho)).
     */
    fun projecao(nick: Nick, aspect: Float, out: Mat4): Float {
        val v = LenteNick.Valores()
        LenteNick.ler(nick, v)
        return projecaoDe(v, aspect, out)
    }

    /** Projeção a partir dos valores já lidos (evita ler a classe duas vezes). */
    fun projecaoDe(v: LenteNick.Valores, aspect: Float, out: Mat4): Float {
        val a = aspect.coerceAtLeast(0.1f)
        if (v.ortografica) {
            out.setOrtho(v.tamanhoOrto, a, v.near, v.far)
            // fov "equivalente" da pirâmide auxiliar: abertura vertical da boca
            return Math.toDegrees(2.0 * Math.atan(v.tamanhoOrto.toDouble())).toFloat()
        }
        out.setPerspective(v.fov, a, v.near, v.far)
        return v.fov
    }

    /**
     * VIEW do Nick: olho = translação mundial; olhando pelo −Z LOCAL (a
     * câmera aponta como os auxiliares de luz/foco); up = +Y local projetado
     * (lookAt padrão; se −Z for colinear com o up mundial, o lookAt do Mat4
     * resolve pela normalização do side — mesma robustez da OrbitCamera).
     */
    fun view(nick: Nick, out: Mat4) {
        val m = nick.transform.worldMatrix.m
        sEye.set(m[12], m[13], m[14])
        sFwd.set(-m[8], -m[9], -m[10])
        sCenter.set(sEye).add(sFwd)
        out.setLookAt(sEye, sCenter, sUp)
    }

    /**
     * VIEW+POSIÇÃO do FOLLOW Camera: câmera atrás/acima do alvo — posição =
     * alvo + (0, altura, distancia) em MUNDO, olhando para o alvo (ponto de
     * mira levemente acima da base do alvo, AIM_HEIGHT). Escreve o olho em
     * [outEye] e a view em [outView]. Alvo ausente → false (chamador decide).
     */
    fun viewFollow(
        nick: Nick, alvo: Nick,
        outEye: com.kenkern.engine.core.math.Vec3,
        outView: Mat4
    ): Boolean {
        val v = LenteNick.Valores()
        LenteNick.ler(nick, v)
        val m = alvo.transform.worldMatrix.m
        val tx = m[12]; val ty = m[13]; val tz = m[14]
        outEye.set(tx, ty + v.altura, tz + v.distancia)
        sCenter.set(tx, ty + AIM_HEIGHT, tz)
        outView.setLookAt(outEye, sCenter, sUp)
        return true
    }

    private val sEye = com.kenkern.engine.core.math.Vec3()
    private val sFwd = com.kenkern.engine.core.math.Vec3()
    private val sCenter = com.kenkern.engine.core.math.Vec3()
    private val sUp = com.kenkern.engine.core.math.Vec3(0f, 1f, 0f)

    private const val AIM_HEIGHT = 0.65f
}
