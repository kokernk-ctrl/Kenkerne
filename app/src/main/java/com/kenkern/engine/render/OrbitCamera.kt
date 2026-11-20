package com.kenkern.engine.render

import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Modo de câmera do viewport. */
enum class CameraMode { ORBITAL, FLIGHT }

/**
 * Câmera do editor com DOIS modos:
 *
 *  ORBITAL — 1 dedo gira, 2 dedos movem (pan) e fazem pinça ZOOM EXPONENCIAL
 *  (dist *= fator), limites 0,05 a 5000 m. Velocidade do pan proporcional à
 *  distância ao pivô. Near DINÂMICO: cresce com a distância da câmera para
 *  preservar precisão de depth com far = 10000 (depth 24 bits no EGL config).
 *
 *  VOO — joystick move (frente/trás/lados), arrasto gira o olhar, botões
 *  sobem/descem. Velocidade 0,1 a 100 m/s em escala logarítmica. Pode voar
 *  abaixo do chão (a grade infinita é de dupla face).
 *
 * FOCO (F): [focusOn] enquadra um centro/raio (AABB) com distância calculada.
 *
 * Toda a matemática é in-place (ZERO alocações por frame).
 */
class OrbitCamera {

    var mode: CameraMode = CameraMode.ORBITAL

    // ---------- Orbital ----------
    val target = Vec3(0f, 0.8f, 0f)
    var yawDegrees: Float = 35f
    var pitchDegrees: Float = 28f
    var distance: Float = 9f
    var fovDegrees: Float = 55f

    var minDistance: Float = 0.05f
    var maxDistance: Float = 5000f

    // ---------- Voo ----------
    val flightPosition = Vec3(6f, 2.5f, 9f)
    var flightYawDegrees: Float = 35f
    var flightPitchDegrees: Float = -10f
    var flightSpeed: Float = 4f // m/s (0,1 a 100; slider logarítmico)

    // Base da câmera (pré-alocada; zero alocação por frame)
    private val eye = Vec3()
    private val right = Vec3()
    private val up = Vec3()
    private val forward = Vec3()
    private val worldUp = Vec3(0f, 1f, 0f)

    /** Alternância Orbital <-> Voo preservando posição do olho e direção de olhar. */
    fun toggleMode() {
        if (mode == CameraMode.ORBITAL) {
            computeBasis()
            flightPosition.set(target).addScaled(forward, distance)
            flightYawDegrees = yawDegrees
            flightPitchDegrees = -pitchDegrees
            mode = CameraMode.FLIGHT
        } else {
            computeBasis()
            target.set(flightPosition).addScaled(forward, distance)
            yawDegrees = flightYawDegrees
            pitchDegrees = -flightPitchDegrees
            mode = CameraMode.ORBITAL
        }
    }

    /** Near efetivo: escala com a distância da câmera (mínimo 0,05; NaN cai no padrão). */
    fun effectiveNear(): Float = if (mode == CameraMode.ORBITAL) {
        val d = if (distance.isFinite()) distance else 9f
        (d * NEAR_DIST_FACTOR).coerceAtLeast(NEAR_MIN)
    } else {
        NEAR_MIN
    }

    /** FOV seguro para tan(): 0,1–179,9° (NaN/Inf -> padrão 55). Evita tan→∞/NaN. */
    private fun safeFov(): Float = if (fovDegrees.isFinite()) fovDegrees.coerceIn(0.1f, 179.9f) else 55f

    /** tan(fov/2) do FOV seguro (nunca 0: tan(0,05°) > 0,0008). */
    private fun safeTanHalfFov(): Float =
        kotlin.math.tan(Math.toRadians((safeFov() * 0.5f).toDouble())).toFloat()

    // ---------- Orbital: gestos ----------

    fun orbit(dxPixels: Float, dyPixels: Float) {
        yawDegrees -= dxPixels * 0.35f
        pitchDegrees += dyPixels * 0.35f
        if (pitchDegrees > 89f) pitchDegrees = 89f
        if (pitchDegrees < -89f) pitchDegrees = -89f
        if (yawDegrees > 360f) yawDegrees -= 360f
        if (yawDegrees < -360f) yawDegrees += 360f
    }

    /** Pinça ZOOM EXPONENCIAL: distância multiplicada por fator (limites 0,05–5000). */
    fun zoom(scale: Float) {
        // Guardas ANTI-NaN: fator inválido vira 1 (sem zoom); NaN não atravessa coerceIn.
        val s = if (scale.isFinite()) scale else 1f
        val d = if (distance.isFinite()) distance else 9f
        distance = (d * s).coerceIn(minDistance, maxDistance)
    }

    /** Pan em pixels: velocidade proporcional à distância ao pivô. */
    fun pan(dxPixels: Float, dyPixels: Float) {
        computeBasis()
        val s = distance * 0.0016f
        target.addScaled(right, -dxPixels * s)
        target.addScaled(up, dyPixels * s)
    }

    // ---------- Voo: gestos/controles ----------

    /** Arrasto gira o OLHAR (não a órbita). Mesma convenção de sinais do orbit. */
    fun rotateFlight(dxPixels: Float, dyPixels: Float) {
        flightYawDegrees -= dxPixels * 0.25f
        flightPitchDegrees -= dyPixels * 0.25f
        if (flightPitchDegrees > 89f) flightPitchDegrees = 89f
        if (flightPitchDegrees < -89f) flightPitchDegrees = -89f
        if (flightYawDegrees > 360f) flightYawDegrees -= 360f
        if (flightYawDegrees < -360f) flightYawDegrees += 360f
    }

    /** Joystick: frente/trás (fwdAmt) e lados (strafeAmt), velocidade m/s. */
    fun moveFlight(fwdAmt: Float, strafeAmt: Float, dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        computeBasis()
        flightPosition.addScaled(forward, fwdAmt * flightSpeed * dtSeconds)
        flightPosition.addScaled(right, strafeAmt * flightSpeed * dtSeconds)
    }

    /** Botões subir/descer. */
    fun elevateFlight(upAmt: Float, dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        flightPosition.y += upAmt * flightSpeed * dtSeconds
    }

    /** Slider logarítmico: 0 -> 0,1 m/s, 1 -> 100 m/s. */
    fun setFlightSpeedNormalized(t: Float) {
        val clamped = t.coerceIn(0f, 1f)
        flightSpeed = (0.1f * Math.pow(1000.0, clamped.toDouble()).toFloat()).coerceIn(0.1f, 100f)
    }

    /** Posição atual do slider (log) para a UI. */
    fun flightSpeedNormalized(): Float =
        (Math.log10((flightSpeed / 0.1f).toDouble()).toFloat() / 3f).coerceIn(0f, 1f)

    /**
     * FOCO (F / toque duplo): posiciona a câmera para enquadrar uma esfera
     * (centro + raio da AABB). Distância pelo FOV com margem de enquadramento.
     * ANTI-NaN: centro/raio inválidos (AABB vazia ou transform patológico) caem
     * no ponto padrão da cena; a distância final fica sempre nos limites.
     */
    fun focusOn(centerX: Float, centerY: Float, centerZ: Float, radius: Float) {
        val cx = if (centerX.isFinite()) centerX else 0f
        val cy = if (centerY.isFinite()) centerY else 0.8f
        val cz = if (centerZ.isFinite()) centerZ else 0f
        val r = if (radius.isFinite() && radius > 0f) radius else 4f
        val fit = (r * FOCUS_MARGIN / safeTanHalfFov().coerceAtLeast(1e-4f))
            .coerceIn(1.5f, maxDistance)
        if (mode == CameraMode.ORBITAL) {
            target.set(cx, cy, cz)
            distance = fit
        } else {
            computeBasis()
            flightPosition.set(cx, cy, cz).addScaled(forward, -fit)
        }
    }

    // ---------- Base e matrizes ----------

    private fun computeBasis() {
        // FIX DO OLHAR HORIZONTAL NO VOO (v0.1.12 — causa (b) comprovada):
        // o yawRad era calculado SEMPRE do yaw do ORBITAL (yawDegrees), também
        // no ramo FLIGHT — o flightYawDegrees atualizado pelo rotateFlight nunca
        // era lido pela base: pitch girava (flightPitchDegrees era lido por modo)
        // e yaw não, nem com 1 nem com 2 dedos. Agora o yaw segue o modo:
        // ORBITAL = yawDegrees (inalterado); VOO = flightYawDegrees. Efeito
        // colateral corrigido de graça: moveFlight/pan/screenRay/view no voo
        // passam a seguir o olhar real (antes seguiam o yaw congelado da
        // alternância de modo).
        val yawRad = Math.toRadians(
            (if (mode == CameraMode.FLIGHT) flightYawDegrees else yawDegrees).toDouble()
        ).toFloat()
        if (mode == CameraMode.ORBITAL) {
            val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
            val cp = cos(pitchRad)
            forward.set(-sin(yawRad) * cp, -sin(pitchRad), -cos(yawRad) * cp)
        } else {
            val pitchRad = Math.toRadians(flightPitchDegrees.toDouble()).toFloat()
            val cp = cos(pitchRad)
            forward.set(-sin(yawRad) * cp, sin(pitchRad), -cos(yawRad) * cp)
        }
        right.cross(forward, worldUp).normalize()
        up.cross(right, forward).normalize()
    }

    /** Preenche a matriz de visão (funciona nos dois modos). */
    fun view(out: Mat4) {
        computeBasis()
        if (mode == CameraMode.ORBITAL) {
            eye.set(target).addScaled(forward, distance)
            out.setLookAt(eye, target, worldUp)
        } else {
            sLookTarget.set(flightPosition).addScaled(forward, 1f)
            out.setLookAt(flightPosition, sLookTarget, worldUp)
        }
    }

    /** Posição do olho (câmera) em mundo, sem alocar. */
    fun eyePoint(out: Vec3) {
        computeBasis()
        if (mode == CameraMode.ORBITAL) {
            out.set(target).addScaled(forward, distance)
        } else {
            out.set(flightPosition)
        }
    }

    /**
     * Direção "para frente" da câmera = DIREÇÃO DE OLHAR real (a mesma da
     * matriz de view), sem alocar. No ORBITAL a base interna `forward` aponta
     * do alvo para o olho (vetor de COSTA), então aqui ela é invertida; no VOO
     * `forward` já é o olhar.
     */
    fun forwardVector(out: Vec3) {
        computeBasis()
        if (mode == CameraMode.ORBITAL) {
            out.set(-forward.x, -forward.y, -forward.z)
        } else {
            out.set(forward)
        }
    }

    /**
     * Raio em mundo que passa pelo ponto de tela (NDC), com a MESMA convenção
     * da matriz de projeção (foi validado por teste de unidade: um ponto na
     * borda superior do raio projeta ndcY = +1). Usado pelo picking e pelo gizmo.
     *
     * CONVENÇÕES POR MODO:
     *  - VOO: `forward` É o olhar => dir = fwd + right*(ndcX*t*aspect) + up*(ndcY*t).
     *  - ORBITAL: `forward` aponta do alvo para o OLHO (costa); o olhar real é
     *    -forward, e o "direita da tela" é -right (o `up` é o mesmo). Usar +forward
     *    aqui mandava o raio para TRÁS da câmera: o toque no cubo nunca acertava
     *    (seleção por toque morta no modo orbital — causa raiz corrigida aqui).
     * Sem alocação.
     */
    fun screenRay(ndcX: Float, ndcY: Float, aspect: Float, outOrigin: Vec3, outDir: Vec3) {
        computeBasis()
        val t = safeTanHalfFov()
        if (mode == CameraMode.ORBITAL) {
            outOrigin.set(target).addScaled(forward, distance)
            outDir.set(forward).scale(-1f)
                .addScaled(right, -ndcX * t * aspect)
                .addScaled(up, ndcY * t)
                .normalize()
        } else {
            outOrigin.set(flightPosition)
            outDir.set(forward)
                .addScaled(right, ndcX * t * aspect)
                .addScaled(up, ndcY * t)
                .normalize()
        }
    }

    fun projection(out: Mat4, aspect: Float) {
        // FOV/near/far sempre finitos e dentro dos limites (nunca NaN nos uniforms).
        out.setPerspective(safeFov(), aspect.coerceAtLeast(0.1f), effectiveNear(), FAR_PLANE)
    }

    companion object {
        const val FAR_PLANE: Float = 10000f
        const val NEAR_MIN: Float = 0.05f
        /** near = distância * 0,002 (ex.: 500 m -> near 1,0; 5000 m -> near 10). */
        const val NEAR_DIST_FACTOR: Float = 0.002f
        const val FOCUS_MARGIN: Float = 2.2f

        private val sLookTarget = Vec3()
    }
}
