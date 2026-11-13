package com.kenkern.engine.render

import android.view.MotionEvent

/**
 * Controlador de toque do viewport: 1 dedo = orbitar; 2 dedos = pan + pinça de zoom.
 * Sem alocação por evento (arrays pré-alocados por id de ponteiro).
 */
class TouchController(private val camera: OrbitCamera) {

    private val prevX = FloatArray(MAX_POINTERS)
    private val prevY = FloatArray(MAX_POINTERS)
    private val active = BooleanArray(MAX_POINTERS)
    private var activeCount = 0

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetAll()
                track(event, 0)
                activeCount = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                if (idx in 0 until MAX_POINTERS) {
                    track(event, idx)
                    activeCount++
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCount == 1) {
                    val p0 = trackedIndex(event, 0)
                    if (p0 >= 0) {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        camera.orbit(x - prevX[p0], y - prevY[p0])
                        prevX[p0] = x
                        prevY[p0] = y
                    }
                } else if (activeCount >= 2) {
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
                // O Android re-indexa os ponteiros restantes: reancora tudo do zero
                // para evitar saltos de câmera.
                resetAll()
                for (i in 0 until event.pointerCount) {
                    track(event, i)
                }
                activeCount = event.pointerCount
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetAll()
        }
        return true
    }

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
    }
}
