package com.kenkern.engine.render

import android.util.Log
import android.view.MotionEvent

/** Roteamento de gestos do gizmo (o toque começa numa alça => controla o gizmo). */
interface GizmoDragHandler {
    /** ACTION_DOWN: true se o toque começou sobre uma alça (captura o gesto). */
    fun onGizmoDown(x: Float, y: Float): Boolean
    fun onGizmoMove(x: Float, y: Float)
    fun onGizmoUp(x: Float, y: Float)
}

/** Classificação do último gesto, publicada para o overlay de debug. */
enum class TouchClass { NONE, PENDING, TAP, DRAG, GIZMO }

/**
 * Controlador de toque do viewport:
 *  - toque sobre alça do gizmo => arrasto do gizmo (conflito resolvido no DOWN,
 *    sem touch slop: o gizmo assume o gesto imediatamente);
 *  - modo ORBITAL: 1 dedo = orbitar (tap curto => seleção; duplo toque => foco);
 *  - modo VOO: 1 dedo OU 2 dedos arrastando = girar o olhar (movimento é do
 *    joystick/botões — nada de movimento foi realocado para o toque);
 *  - 2 dedos no orbital = pan + pinça de zoom exponencial.
 * Sem alocação por evento (arrays pré-alocados por id de ponteiro).
 *
 * CLASSIFICAÇÃO DE TAP (limiares em DP, nunca em px crus):
 *  - tap = gesto de menos de [TAP_MAX_MS] ms com deslocamento do DOWN menor que
 *    [tapSlopPx] (o dono do toque passa 12dp × density, no mínimo o
 *    scaledTouchSlop do sistema — dedo tremendo não desqualifica mais o tap);
 *  - JITTER: enquanto o dedo não cruza o slop, a câmera NÃO se move (antes,
 *    cada MOVE de 1-2px girava a órbita mesmo em um tap); ao cruzar, o delta
 *    acumulado desde o DOWN é aplicado de uma vez (sem salto).
 *  - O tap só é processado no ACTION_UP (nunca no DOWN): seleção acontece ao
 *    SOLTAR, arrasto nunca muda a seleção.
 */
class TouchController(
    private val camera: OrbitCamera,
    private val tapSlopPx: Float = 10f,
    private val density: Float = 1f,
    private val onTap: ((x: Float, y: Float) -> Unit)? = null,
    private val onDoubleTap: ((x: Float, y: Float) -> Unit)? = null,
    private val gizmoHandler: GizmoDragHandler? = null,
    private val debug: ViewportDebugState? = null
) {

    private val prevX = FloatArray(MAX_POINTERS)
    private val prevY = FloatArray(MAX_POINTERS)
    private val active = BooleanArray(MAX_POINTERS)
    private var activeCount = 0

    // Detecção de tap (seleção), duplo toque (foco) e captura pelo gizmo
    private var gizmoCaptured = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTimeMs = 0L
    private var multiTouchSeen = false
    private var moved = false
    private var hasLastTap = false
    private var lastTapTimeMs = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    // ---- Diagnóstico da pinça (overlay de debug, ETAPA A) ----
    // span no momento em que a pinça começou (2º dedo pousou); 0 = sem pinça
    private var pinchStartSpan = 0f
    // span corrente entre os 2 dedos (0 quando não há pinça)
    private var pinchCurrentSpan = 0f
    // fator aplicado no último MOVE da pinça (oldDist/newDist; 1 = sem zoom)
    private var pinchLastFactor = 1f
    // motivo do último reset de baseline (DOWN/UP/CANCEL/troca de dedos)
    private var lastBaselineReset: String = RESET_NONE

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetAll()
                track(event, 0)
                activeCount = 1
                multiTouchSeen = false
                moved = false
                downX = event.getX(0)
                downY = event.getY(0)
                lastX = downX
                lastY = downY
                downTimeMs = event.eventTime
                // CONFLITO DE GESTOS decidido no DOWN: alça do gizmo vence a câmera
                // (e a câmera ignora todo o resto até o ACTION_UP).
                gizmoCaptured = gizmoHandler?.onGizmoDown(downX, downY) ?: false
                debug?.publishDown(downX, downY, gizmoCaptured)
                // baseline da pinça nasce limpo a cada DOWN (1 dedo na tela)
                pinchCurrentSpan = 0f
                markBaselineReset(RESET_DOWN, event)
                publishPinchDebug(1)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // FIX DO ZOOM PRESO: reancora TODOS os ponteiros presentes no
                // evento (o track só do novo dedo deixava a primeira distância
                // da pinça com âncora velha do gesto de 1 dedo). O nº de dedos
                // passa a ser o pointerCount do evento (verdade absoluta) em vez
                // de contador incremental que podia inflar com dedos mortos.
                for (i in 0 until event.pointerCount) track(event, i)
                activeCount = event.pointerCount
                multiTouchSeen = true
                // novo baseline da pinça: span no instante em que ela começa
                pinchStartSpan = spanOf(event)
                markBaselineReset(RESET_JOIN, event)
                publishPinchDebug(event.pointerCount)
                if (gizmoCaptured) {
                    // segundo dedo durante arrasto do gizmo: cancela o gesto
                    gizmoHandler?.onGizmoUp(lastX, lastY)
                    gizmoCaptured = false
                    debug?.publishClass(TouchClass.DRAG, "2º dedo cancelou o gizmo")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gizmoCaptured) {
                    val p0 = trackedIndex(event, 0)
                    if (p0 >= 0) {
                        lastX = event.getX(0)
                        lastY = event.getY(0)
                        gizmoHandler?.onGizmoMove(lastX, lastY)
                        debug?.publishMove(lastX, lastY, true, true, event.eventTime - downTimeMs)
                    }
                    return true
                }
                if (activeCount == 1) {
                    val p0 = trackedIndex(event, 0)
                    if (p0 >= 0) {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        if (!moved && abs2(downX - x, downY - y) > tapSlopPx * tapSlopPx) {
                            moved = true
                        }
                        // JITTER: sob o slop o dedo não move a câmera. Ao cruzar o
                        // slop, o delta acumulado desde o DOWN é aplicado de uma vez
                        // (prevX só é atualizado dentro daqui).
                        if (moved) {
                            val dx = x - prevX[p0]
                            val dy = y - prevY[p0]
                            if (camera.mode == CameraMode.FLIGHT) {
                                camera.rotateFlight(dx, dy)
                                // ETAPA A (v0.1.12): publica o delta em dp (px/density)
                                // do último MOVE de 1 dedo no voo — diagnóstico do eixo X.
                                debug?.publishFlight(GESTURE_LOOK_1, dx / density, dy / density)
                            } else {
                                camera.orbit(dx, dy)
                            }
                            prevX[p0] = x
                            prevY[p0] = y
                        }
                        // Mantém a posição final também sob o slop; o UP pode
                        // chegar sem um MOVE adicional e alimenta a telemetria.
                        lastX = x
                        lastY = y
                        debug?.publishMove(x, y, false, moved, event.eventTime - downTimeMs)
                        publishPinchDebug(1)
                    }
                } else if (activeCount >= 2 && camera.mode == CameraMode.ORBITAL) {
                    val a = trackedIndex(event, 0)
                    val b = trackedIndex(event, 1)
                    // Guarda de consistência: só leia os ponteiros 0/1 se o EVENTO
                    // realmente os tem — um estado residual nunca mais lança
                    // IllegalArgumentException engolida pelo runCatching da view.
                    if (a >= 0 && b >= 0 && event.pointerCount >= 2) {
                        val ax = event.getX(0); val ay = event.getY(0)
                        val bx = event.getX(1); val by = event.getY(1)
                        val newDist = kotlin.math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                        val oldDist = kotlin.math.hypot(
                            (prevX[b] - prevX[a]).toDouble(), (prevY[b] - prevY[a]).toDouble()
                        ).toFloat()
                        if (oldDist > 1f && newDist > 1f) {
                            // ZOOM EXPONENCIAL: dist *= fator
                            val factor = oldDist / newDist
                            camera.zoom(factor)
                            pinchLastFactor = factor
                        }
                        pinchCurrentSpan = newDist // span corrente para o overlay
                        val newMidX = (ax + bx) * 0.5f
                        val newMidY = (ay + by) * 0.5f
                        val oldMidX = (prevX[a] + prevX[b]) * 0.5f
                        val oldMidY = (prevY[a] + prevY[b]) * 0.5f
                        camera.pan(newMidX - oldMidX, newMidY - oldMidY)
                        prevX[a] = ax; prevY[a] = ay
                        prevX[b] = bx; prevY[b] = by
                        debug?.publishMove((ax + bx) * 0.5f, (ay + by) * 0.5f, false, true, event.eventTime - downTimeMs)
                        publishPinchDebug(event.pointerCount)
                    }
                } else if (activeCount >= 2 && camera.mode == CameraMode.FLIGHT) {
                    // VOO + 2 DEDOS = OLHAR (antes os 2 dedos eram simplesmente
                    // ignorados; o movimento continua no joystick/botões — nada foi
                    // realocado). Delta do PONTO MÉDIO convertido para DP e passado
                    // ao rotateFlight: 0,25°/dp efetivos (especificação). Os SINAIS
                    // são os MESMOS do gesto de 1 dedo: nesta câmera yaw DIMINUI
                    // para olhar à direita (forward.x = -sin(yaw), veja OrbitCamera
                    // computeBasis) — dedo à direita => olha à direita; dedo para
                    // cima => olha para cima. Clamp ±89° fica no rotateFlight.
                    // Zero alocação por evento.
                    val a = trackedIndex(event, 0)
                    val b = trackedIndex(event, 1)
                    if (a >= 0 && b >= 0 && event.pointerCount >= 2) {
                        val ax = event.getX(0); val ay = event.getY(0)
                        val bx = event.getX(1); val by = event.getY(1)
                        val dxdp = ((ax + bx) * 0.5f - (prevX[0] + prevX[1]) * 0.5f) / density
                        val dydp = ((ay + by) * 0.5f - (prevY[0] + prevY[1]) * 0.5f) / density
                        camera.rotateFlight(dxdp, dydp)
                        debug?.publishFlight(GESTURE_LOOK_2, dxdp, dydp)
                        prevX[0] = ax; prevY[0] = ay
                        prevX[1] = bx; prevY[1] = by
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gizmoCaptured) {
                    gizmoHandler?.onGizmoUp(lastX, lastY)
                    gizmoCaptured = false
                    resetAll()
                    debug?.publishClass(TouchClass.GIZMO, "gizmo solto (2º dedo)")
                    return true
                }
                // FIX DO ZOOM PRESO (causa raiz): o evento de POINTER_UP AINDA
                // CONTÉM o dedo que está subindo (pointerCount o inclui). Reancorar
                // tudo mantinha o DEDO MORTO como ativo com activeCount=2 — e o
                // próximo MOVE (com pointerCount=1) caía na pinça e lançava
                // IllegalArgumentException em getX(1), engolida pelo runCatching
                // da KenkernSurfaceView: câmera presa até soltar TUDO. Agora só os
                // ponteiros que FICARAM são reancorados.
                resetAll()
                for (i in 0 until event.pointerCount) {
                    if (i == event.actionIndex) continue // pula o dedo que subiu
                    track(event, i)
                }
                activeCount = (event.pointerCount - 1).coerceAtLeast(0)
                pinchStartSpan = 0f // pinça terminou: sem span de baseline
                pinchCurrentSpan = 0f
                markBaselineReset(RESET_SPLIT, event)
                publishPinchDebug(activeCount)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gizmoCaptured) {
                    gizmoHandler?.onGizmoUp(lastX, lastY)
                    gizmoCaptured = false
                    resetAll()
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        debug?.publishClass(TouchClass.GIZMO, "arrasto do gizmo concluído")
                    } else {
                        debug?.publishClass(TouchClass.NONE, "cancelado pelo sistema")
                    }
                    return true
                }
                val elapsedMs = event.eventTime - downTimeMs
                val terminalX = if (
                    event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount > 0
                ) event.getX(0) else lastX
                val terminalY = if (
                    event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount > 0
                ) event.getY(0) else lastY
                val movedPx = kotlin.math.sqrt(abs2(downX - terminalX, downY - terminalY))
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    movedPx > tapSlopPx
                ) {
                    moved = true
                }
                // TAP DE SELEÇÃO: 1 dedo, movimento < slop (12dp) e gesto < 250 ms —
                // arrasto nunca altera a seleção. Processado no UP, nunca no DOWN.
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    !moved && !multiTouchSeen && onTap != null &&
                    elapsedMs <= TAP_MAX_MS
                ) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val now = event.eventTime
                    if (hasLastTap &&
                        now - lastTapTimeMs <= DOUBLE_TAP_MS &&
                        abs2(x - lastTapX, y - lastTapY) <= tapSlopPx * tapSlopPx
                    ) {
                        hasLastTap = false
                        debug?.publishUp(TouchClass.TAP, "duplo toque => foco (não seleciona)", elapsedMs, movedPx)
                        onDoubleTap?.invoke(x, y)
                    } else {
                        hasLastTap = true
                        lastTapTimeMs = now
                        lastTapX = x
                        lastTapY = y
                        debug?.publishUp(TouchClass.TAP, "tap válido => picking no UP", elapsedMs, movedPx)
                        onTap(x, y)
                    }
                } else {
                    // Por que NÃO foi tap (motivo visível no overlay de debug):
                    when {
                        event.actionMasked == MotionEvent.ACTION_CANCEL ->
                            debug?.publishClass(TouchClass.NONE, "cancelado pelo sistema")
                        multiTouchSeen ->
                            debug?.publishUp(TouchClass.DRAG, "2 dedos (pan/zoom) — sem seleção", elapsedMs, movedPx)
                        moved ->
                            debug?.publishUp(
                                TouchClass.DRAG,
                                "movimento ${fmt(movedPx / density)}dp > slop ${fmt(tapSlopPx / density)}dp — virou arrasto de câmera",
                                elapsedMs, movedPx
                            )
                        elapsedMs > TAP_MAX_MS ->
                            debug?.publishUp(
                                TouchClass.DRAG,
                                "gesto de ${elapsedMs}ms > ${TAP_MAX_MS}ms — tap longo descartado",
                                elapsedMs, movedPx
                            )
                        else ->
                            debug?.publishClass(TouchClass.NONE, "sem gesto")
                    }
                }
                markBaselineReset(
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) RESET_CANCEL else RESET_UP,
                    event
                )
                pinchCurrentSpan = 0f
                publishPinchDebug(0)
                resetAll()
            }
        }
        return true
    }

    /** True entre DOWN e UP quando o gizmo tem a posse do gesto (para requestDisallowInterceptTouchEvent). */
    fun isGizmoCapturedNow(): Boolean = gizmoCaptured

    private fun abs2(dx: Float, dy: Float): Float = dx * dx + dy * dy

    /** px -> dp só para a MENSAGEM de debug (o limiar real é em px = dp*density). */
    private fun fmt(dp: Float): String = String.format(java.util.Locale.US, "%.0f", dp)

    private fun resetAll() {
        for (i in active.indices) active[i] = false
        activeCount = 0
    }

    /** Span (distância) entre os ponteiros 0 e 1; 0 quando há menos de 2. */
    private fun spanOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return kotlin.math.hypot(
            (event.getX(1) - event.getX(0)).toDouble(),
            (event.getY(1) - event.getY(0)).toDouble()
        ).toFloat()
    }

    /** Logcat + registro do motivo do último reset de baseline da pinça. */
    private fun markBaselineReset(reason: String, event: MotionEvent) {
        lastBaselineReset = reason
        Log.d(TAG, "baseline reset: $reason · dedos=${event.pointerCount} · span=${spanOf(event)}")
    }

    /**
     * Publica o estado da pinça para a linha de debug do overlay (ETAPA A).
     * Só escritas de primitivos @Volatile — ZERO alocação por evento.
     */
    private fun publishPinchDebug(fingers: Int) {
        debug?.publishPinch(fingers, pinchStartSpan, pinchCurrentSpan, pinchLastFactor, lastBaselineReset)
    }

    private fun track(event: MotionEvent, pointerIndex: Int) {
        val slot = pointerIndex.coerceIn(0, MAX_POINTERS - 1)
        active[slot] = true
        prevX[slot] = event.getX(pointerIndex)
        prevY[slot] = event.getY(pointerIndex)
    }

    private fun trackedIndex(event: MotionEvent, pointerIndex: Int): Int {
        if (pointerIndex >= MAX_POINTERS) return -1
        val slot = pointerIndex.coerceIn(0, MAX_POINTERS - 1)
        return if (active[slot]) slot else -1
    }

    companion object {
        private const val TAG = "Kenkern/Touch"
        private const val MAX_POINTERS = 8

        // Motivos de reset de baseline (constantes: ZERO alocação por evento)
        const val RESET_NONE = "—"
        const val RESET_DOWN = "DOWN"
        const val RESET_UP = "UP"
        const val RESET_CANCEL = "CANCEL"
        const val RESET_JOIN = "1→2 dedos"
        const val RESET_SPLIT = "2→1 dedos"

        // Rótulos do gesto de voo para o overlay (constantes)
        const val GESTURE_LOOK_1 = "olhar (1 dedo)"
        const val GESTURE_LOOK_2 = "olhar (2 dedos)"

        /** Seleção: gesto de no máximo 250 ms (antes 200 ms — dedos reais seguram mais). */
        const val TAP_MAX_MS = 250L

        /** Janela de duplo toque para foco. */
        private const val DOUBLE_TAP_MS = 300L
    }
}
