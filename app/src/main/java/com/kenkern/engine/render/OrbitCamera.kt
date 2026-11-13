package com.kenkern.engine.render

import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Câmera orbital: um dedo gira, dois dedos movem (pan) e fazem pinça de zoom.
 * Toda a matemática é in-place (ZERO alocações por frame).
 */
class OrbitCamera {
    val target = Vec3(0f, 0.8f, 0f)
    var yawDegrees: Float = 35f
    var pitchDegrees: Float = 28f
    var distance: Float = 9f
    var fovDegrees: Float = 55f
    var nearPlane: Float = 0.1f
    var farPlane: Float = 500f

    var minDistance: Float = 1.5f
    var maxDistance: Float = 80f

    private val eye = Vec3()
    private val right = Vec3()
    private val up = Vec3()
    private val forward = Vec3()
    private val worldUp = Vec3(0f, 1f, 0f)

    fun orbit(dxPixels: Float, dyPixels: Float) {
        yawDegrees -= dxPixels * 0.35f
        pitchDegrees += dyPixels * 0.35f
        if (pitchDegrees > 89f) pitchDegrees = 89f
        if (pitchDegrees < -89f) pitchDegrees = -89f
        if (yawDegrees > 360f) yawDegrees -= 360f
        if (yawDegrees < -360f) yawDegrees += 360f
    }

    fun zoom(scale: Float) {
        distance = (distance * scale).coerceIn(minDistance, maxDistance)
    }

    /** Pan em pixels: move o target ao longo dos eixos da câmera. */
    fun pan(dxPixels: Float, dyPixels: Float) {
        computeBasis()
        val s = distance * 0.0016f
        target.addScaled(right, -dxPixels * s)
        target.addScaled(up, dyPixels * s)
    }

    private fun computeBasis() {
        val yawRad = Math.toRadians(yawDegrees.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
        val cp = cos(pitchRad)
        forward.set(-sin(yawRad) * cp, -sin(pitchRad), -cos(yawRad) * cp)
        right.cross(forward, worldUp).normalize()
        up.cross(right, forward).normalize()
    }

    /** Preenche a matriz de visão. */
    fun view(out: Mat4) {
        computeBasis()
        eye.set(target).addScaled(forward, distance)
        out.setLookAt(eye, target, worldUp)
    }

    fun projection(out: Mat4, aspect: Float) {
        out.setPerspective(fovDegrees, aspect.coerceAtLeast(0.1f), nearPlane, farPlane)
    }
}
