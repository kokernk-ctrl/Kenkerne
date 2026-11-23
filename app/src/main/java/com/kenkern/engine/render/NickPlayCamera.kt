package com.kenkern.engine.render

import com.kenkern.engine.core.LenteNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot


/** Câmara de jogo separada da órbita/voo do editor. */
internal class NickPlayCamera(private val scene: Scene, player: Nick?, follow: Nick?, basic: Nick?) {
    val camera = OrbitCamera()

    // H3 (v0.1.20): ordem de escolha em Play —
    //  1. CÂMERA PRINCIPAL (camera.* com principal=true — SÓ UMA por cena):
    //     renderiza com fov/near/far/ortográfica DO NICK (matriz própria);
    //  2. FOLLOW CAMERA (camera.follow): alvo (campo alvo ou 1º jogador),
    //     distância e altura DA CLASSE (comportamento do M1 como fallback);
    //  3. CÂMERA FIXA (camera.basic sem principal — teste de câmera fixa do M1);
    //  4. sem nenhuma câmera: nada aqui — o chamador mantém a câmera do editor
    //     e AVISA UMA VEZ no Console (KenkernSurfaceView, H3).
    private val achados = LenteNickMatriz.Achados()
    private val pilha = arrayOfNulls<Nick>(512)
    private val valores = LenteNick.Valores()
    private val principal: Nick?
    private val followNick: Nick?
    private val fixed: Nick?
    private val alvo: Nick?
    private val offset = Vec3(0f, 3f, 6f)

    /** Câmera principal do H3: usa matriz PRÓPRIA (fov/near/far/orto do Nick). */
    val usaMatrizPropria: Boolean get() = principal != null
    val projPropria = Mat4()
    val viewPropria = Mat4()
    var fovPropria: Float = LenteNick.DEF_FOV
        private set

    val active: Boolean get() = principal != null || alvo != null || fixed != null

    init {
        LenteNickMatriz.buscarCameras(scene, pilha, achados)
        principal = achados.principal
        followNick = if (principal == null) follow else null
        fixed = if (principal == null && followNick == null) basic else null
        alvo = when {
            principal != null -> null
            followNick != null ->
                // Alvo EXPLÍCITO do campo "alvo" (ReferenceId); vazio/ausente =
                // o jogador do runtime (compatibilidade do M1 — o recorte
                // antigo não exigia kind=body.player no alvo).
                LenteNickMatriz.alvoDoFollow(followNick, scene) ?: player
            fixed == null -> player // sem NENHUMA câmera: vista seguidora padrão (M1)
            else -> null            // câmera fixa: NÃO segue o jogador (M1)
        }
        // Follow (M1 como fallback): campos distância/altura zerados nos defaults
        // E Nick antigo sem campos → mantém o deslocamento INICIAL do Nick.
        if (followNick != null && alvo != null) {
            LenteNick.ler(followNick, valores)
            if (valores.distancia != LenteNick.DEF_DISTANCIA || valores.altura != LenteNick.DEF_ALTURA) {
                offset.set(0f, valores.altura, valores.distancia)
            } else {
                val p = alvo.transform.worldMatrix.m
                val f = followNick.transform.worldMatrix.m
                val x = f[12] - p[12]
                val y = f[13] - p[13]
                val z = f[14] - p[14]
                val horizontal = x * x + z * z
                if (horizontal.isFinite() && horizontal > 0.01f && y.isFinite()) offset.set(x, y, z)
            }
        }
        update()
    }

    /**
     * H3: matriz PRÓPRIA da câmera principal — chamado PELO RENDER com o
     * aspect REAL do viewport (a projeção depende do tamanho da tela).
     */
    fun prepararProjecaoPropria(aspect: Float) {
        val p = principal ?: return
        LenteNick.ler(p, valores)
        fovPropria = LenteNickMatriz.projecaoDe(valores, aspect, projPropria)
        LenteNickMatriz.view(p, viewPropria)
    }

    fun update() {
        val player = alvo
        if (player != null) {
            val p = player.transform.worldMatrix.m
            val y = AIM_HEIGHT - offset.y
            val h = hypot(offset.x.toDouble(), offset.z.toDouble())
            camera.mode = CameraMode.FLIGHT
            camera.flightPosition.set(p[12] + offset.x, p[13] + offset.y, p[14] + offset.z)
            camera.flightYawDegrees = Math.toDegrees(atan2(offset.x.toDouble(), offset.z.toDouble())).toFloat()
            camera.flightPitchDegrees = Math.toDegrees(atan2(y.toDouble(), h)).toFloat().coerceIn(-89f, 89f)
            // FOV do Nick da câmera (follow/fixa também têm a classe Lente):
            // FOV editado vira a projeção em Play no mesmo frame.
            val camNick = followNick ?: fixed
            if (camNick != null) {
                LenteNick.ler(camNick, valores)
                camera.fovDegrees = valores.fov
            }
        } else if (fixed != null) {
            val m = fixed.transform.worldMatrix.m
            val x = -m[8].toDouble()
            val y = -m[9].toDouble()
            val z = -m[10].toDouble()
            val h = hypot(x, z)
            camera.mode = CameraMode.FLIGHT
            camera.flightPosition.set(m[12], m[13], m[14])
            if (h.isFinite() && y.isFinite() && (h > 1e-6 || abs(y) > 1e-6)) {
                camera.flightYawDegrees = if (h > 1e-6) Math.toDegrees(atan2(-x, -z)).toFloat() else 0f
                camera.flightPitchDegrees = Math.toDegrees(atan2(y, h)).toFloat().coerceIn(-89f, 89f)
            }
            LenteNick.ler(fixed, valores)
            camera.fovDegrees = valores.fov
        }
    }

    private companion object {
        const val AIM_HEIGHT = 0.65f
    }
}
