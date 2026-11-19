package com.kenkern.engine.render

import android.view.MotionEvent

/** Roteamento de gestos do gizmo (o toque começa numa alça => controla o gizmo). */
interface GizmoDragHandler {
    /** ACTION_DOWN: true se o toque começou sobre uma alça (captura o gesto). */
    fun onGizmoDown(x: Float, y: Float): Boolean
    fun onGizmoMove(x: Float, y: Float)
    fun onGizmoUp(x: Float, y: Float)
}

/**
 * Controlador de toque do viewport:
 *  - toque sobre alça do gizmo => arrasto do gizmo (conflito resolvido no DOWN,
 *    sem touch slop: o gizmo assume o gesto imediatamente);
 *  - modo ORBITAL: 1 dedo = orbitar (tap curto => seleção; duplo toque => foco);
 *  - modo VOO: 1 dedo = girar o olhar; 2 dedos ignorados (movimento é do joystick);
 *  - 2 dedos no orbital = pan + pinça de zoom exponencial.
 * Sem alocação por evento (arrays pré-alocados por id de ponteiro).
 */
class TouchController(
    private val camera: OrbitCamera,
    private val tapSlopPx: Float = 10f,
    private val onTap: ((x: Float, y: Float) -> Unit)? = null,
    private val onDoubleTap: ((x: Float, y: Float) -> Unit)? = null,
    private val gizmoHandler: GizmoDragHandler? = null
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
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gizmoCaptured) {
                    val p0 = trackedIndex(event, 0)
                    if (p0 >= 0) {
                        lastX = event.getX(0)
                        lastY = event.getY(0)
                        gizmoHandler?.onGizmoMove(lastX, lastY)
                    }
                    return true
                }
                if (activeCount == 1) {
                    val p0 = trackedIndex(event, 0)
                    if (p0 >= 0) {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        val dx = x - prevX[p0]
                        val dy = y - prevY[p0]
                        if (abs2(downX - x, downY - y) > tapSlopPx * tapSlopPx) moved = true
                        if (camera.mode == CameraMode.FLIGHT) {
                            camera.rotateFlight(dx, dy)
                        } else {
                            camera.orbit(dx, dy)
                        }
                        prevX[p0] = x
                        prevY[p0] = y
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
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gizmoCaptured) {
                    gizmoHandler?.onGizmoUp(lastX, lastY)
                    gizmoCaptured = false
                    resetAll()
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
                    return true
                }
                // TAP DE SELEÇÃO: 1 dedo, sem movimento além do slop e gesto CURTO
                // (< 200 ms, < 10 px) — arrasto nunca altera a seleção.
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    !moved && !multiTouchSeen && onTap != null &&
                    event.eventTime - downTimeMs <= TAP_MAX_MS
                ) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val now = event.eventTime
                    if (hasLastTap &&
                        now - lastTapTimeMs <= DOUBLE_TAP_MS &&
                        abs2(x - lastTapX, y - lastTapY) <= tapSlopPx * tapSlopPx
                    ) {
                        hasLastTap = false
                        onDoubleTap?.invoke(x, y)
                    } else {
                        hasLastTap = true
                        lastTapTimeMs = now
                        lastTapX = x
                        lastTapY = y
                        onTap(x, y)
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

        /** Seleção: gesto de no máximo 200 ms. */
        private const val TAP_MAX_MS = 200L

        /** Janela de duplo toque para foco. */
        private const val DOUBLE_TAP_MS = 300L
    }
}
