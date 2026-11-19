package com.kenkern.engine.render

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
 *  - modo VOO: 1 dedo = girar o olhar; 2 dedos ignorados (movimento é do joystick);
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
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                if (idx in 0 until MAX_POINTERS) {
                    track(event, idx)
                    activeCount++
                    multiTouchSeen = true
                }
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
                            } else {
                                camera.orbit(dx, dy)
                            }
                            prevX[p0] = x
                            prevY[p0] = y
                        }
                        debug?.publishMove(x, y, false, moved, event.eventTime - downTimeMs)
                    }
                } else if (activeCount >= 2 && camera.mode == CameraMode.ORBITAL) {
                    val a = trackedIndex(event, 0)
                    val b = trackedIndex(event, 1)
                    if (a >= 0 && b >= 0) {
                        val ax = event.getX(0); val ay = event.getY(0)
                        val bx = event.getX(1); val by = event.getY(1)
                        val newDist = kotlin.math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                        val oldDist = kotlin.math.hypot(
                            (prevX[b] - prevX[a]).toDouble(), (prevY[b] - prevY[a]).toDouble()
                        ).toFloat()
                        if (oldDist > 1f && newDist > 1f) {
                            // ZOOM EXPONENCIAL: dist *= fator
                            camera.zoom(oldDist / newDist)
                        }
                        val newMidX = (ax + bx) * 0.5f
                        val newMidY = (ay + by) * 0.5f
                        val oldMidX = (prevX[a] + prevX[b]) * 0.5f
                        val oldMidY = (prevY[a] + prevY[b]) * 0.5f
                        camera.pan(newMidX - oldMidX, newMidY - oldMidY)
                        prevX[a] = ax; prevY[a] = ay
                        prevX[b] = bx; prevY[b] = by
                        debug?.publishMove((ax + bx) * 0.5f, (ay + by) * 0.5f, false, true, event.eventTime - downTimeMs)
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
                // O Android re-indexa os ponteiros restantes: reancora tudo do zero
                // para evitar saltos de câmera.
                resetAll()
                for (i in 0 until event.pointerCount) {
                    track(event, i)
                }
                activeCount = event.pointerCount
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
                val movedPx = kotlin.math.sqrt(abs2(downX - lastX, downY - lastY))
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
        private const val MAX_POINTERS = 8

        /** Seleção: gesto de no máximo 250 ms (antes 200 ms — dedos reais seguram mais). */
        const val TAP_MAX_MS = 250L

        /** Janela de duplo toque para foco. */
        private const val DOUBLE_TAP_MS = 300L
    }
}
