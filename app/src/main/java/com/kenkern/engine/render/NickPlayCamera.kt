package com.kenkern.engine.render

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot


/** Câmara de jogo separada da órbita/voo do editor. */
internal class NickPlayCamera(player: Nick?, follow: Nick?, basic: Nick?) {
    val camera = OrbitCamera()
    private val target = if (follow != null || basic == null) player else null
    private val fixed = if (target == null) basic else null
    private val offset = Vec3(0f, 3f, 6f)

    val active: Boolean get() = target != null || fixed != null

    init {
        if (target != null && follow != null) {
            val p = target.transform.worldMatrix.m
            val f = follow.transform.worldMatrix.m
            val x = f[12] - p[12]
            val y = f[13] - p[13]
            val z = f[14] - p[14]
            val horizontal = x * x + z * z
            if (horizontal.isFinite() && horizontal > 0.01f && y.isFinite()) offset.set(x, y, z)
        }
        update()
    }

    fun update() {
        val player = target
        if (player != null) {
            val p = player.transform.worldMatrix.m
            val y = AIM_HEIGHT - offset.y
            val h = hypot(offset.x.toDouble(), offset.z.toDouble())
            camera.mode = CameraMode.FLIGHT
            camera.flightPosition.set(p[12] + offset.x, p[13] + offset.y, p[14] + offset.z)
            camera.flightYawDegrees = Math.toDegrees(atan2(offset.x.toDouble(), offset.z.toDouble())).toFloat()
            camera.flightPitchDegrees = Math.toDegrees(atan2(y.toDouble(), h)).toFloat().coerceIn(-89f, 89f)
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
        }
    }

    private companion object {
        const val AIM_HEIGHT = 0.65f
    }
}
