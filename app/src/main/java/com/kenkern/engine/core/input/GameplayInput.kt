package com.kenkern.engine.core.input

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.Scene
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Result scratch shared by math, touch router and tests. x right, y down, angle in degrees clockwise. */
internal class StickSample {
    var x = 0f
    var y = 0f
    var strength = 0f
    var angle = 0f
    var pressed = false
    var presses = 0
    var moves = 0
    var releases = 0

    fun axes(x: Float, y: Float, dead: Float, exponent: Float, snap: Boolean) {
        val length = hypot(x, y)
        if (!length.isFinite() || length <= dead) {
            this.x = 0f; this.y = 0f; strength = 0f; angle = 0f
            return
        }
        strength = ((length.coerceAtMost(1f) - dead) / (1f - dead)).pow(exponent)
        var radians = atan2(y, x)
        if (snap) radians = (radians / OCTANT).roundToInt() * OCTANT
        angle = (radians * (180f / PI.toFloat()) + 360f) % 360f
        this.x = cos(radians) * strength
        this.y = sin(radians) * strength
    }

    fun copyFrom(other: StickSample) {
        x = other.x; y = other.y; strength = other.strength; angle = other.angle
        pressed = other.pressed; presses = other.presses; moves = other.moves; releases = other.releases
    }

    private companion object { const val OCTANT = (PI / 4).toFloat() }
}

/** Session-scoped contact router. Only a down can claim a pointer; contacts never migrate. */
internal class GameplayInput(scene: Scene, defaultPlayer: Nick?) {
    internal class Control(val config: TouchControlConfig, val target: Nick?, val follow: Nick?, val fallback: Boolean = false) {
        val sample = StickSample()
        var pointer = NO_POINTER
        var baseX = 0f
        var baseY = 0f
        var lastX = 0f
        var lastY = 0f
        @Volatile var projection = Long.MIN_VALUE
    }

    val controls: List<Control> = buildList {
        val nicks = HashMap<String, Nick>()
        val authored = ArrayList<Nick>()
        fun visit(nick: Nick) {
            if (!nick.active) return
            nicks[nick.id.value] = nick
            if (nick.visible && (nick.kind == TouchControlSchema.JOYSTICK || nick.kind == TouchControlSchema.BUTTON)) authored.add(nick)
            for (child in nick.children) visit(child)
        }
        for (root in scene.roots) visit(root)
        for (nick in authored) {
            val config = TouchControlConfig.fromNick(nick)
            val target = if (config.targetId.isBlank()) defaultPlayer else nicks[config.targetId]?.takeIf { it.kind == "body.player" }
            add(Control(config, target, nicks[config.followId]))
        }
        if (defaultPlayer != null && none {
            it.target === defaultPlayer && it.config.action == TouchControlSchema.MOVE && !it.config.button &&
                (it.config.anchor != TouchAnchor.FOLLOW || it.follow != null)
        }) {
            add(Control(TouchControlConfig("", TouchControlSchema.MOVE, deadZone = 0f), defaultPlayer, null, fallback = true))
        }
    }

    val hasProjection = controls.any { it.follow != null && it.config.anchor == TouchAnchor.FOLLOW }

    @Synchronized
    fun read(control: Control, out: StickSample) = out.copyFrom(control.sample)

    @Synchronized
    fun readJoystick(name: String, out: StickSample): Boolean {
        for (i in controls.indices) {
            val c = controls[i]
            if (!c.config.button && !c.fallback && c.config.name == name) {
                out.copyFrom(c.sample)
                return true
            }
        }
        return false
    }

    /** Dominant vector across sources; ties preserve traversal order. Digital actions aggregate by OR. */
    @Synchronized
    fun readAction(name: String, out: StickSample) {
        var best = -1f
        var pressed = false
        var presses = 0; var moves = 0; var releases = 0
        out.axes(0f, 0f, 0f, 1f, false)
        for (i in controls.indices) {
            val c = controls[i]
            if (c.config.action != name) continue
            val s = c.sample
            if (s.strength > best) { best = s.strength; out.copyFrom(s) }
            pressed = pressed || s.pressed
            presses += s.presses; moves += s.moves; releases += s.releases
        }
        out.pressed = pressed; out.presses = presses; out.moves = moves; out.releases = releases
    }

    @Synchronized
    fun down(id: Long, x: Float, y: Float, w: Float, h: Float, density: Float): Boolean {
        if (id == NO_POINTER || !x.isFinite() || !y.isFinite() || w <= 0f || h <= 0f) return false
        for (i in controls.indices) if (controls[i].pointer == id) return false
        // A visible button/fixed hit takes priority over a floating activation zone.
        for (pass in 0..1) for (i in controls.indices) {
            val c = controls[i]
            if (c.pointer != NO_POINTER) continue
            val fixed = c.config.button || c.config.type == StickType.FIXED
            if ((pass == 0) != fixed) continue
            val anchor = c.anchor(w, h, density)
            if (anchor == Long.MIN_VALUE) continue
            val radius = c.radius(w, h, density)
            val bx = unpackX(anchor); val by = unpackY(anchor)
            if (fixed) {
                if (hypot(x - bx, y - by) > radius) continue
            } else if (c.config.anchor == TouchAnchor.FOLLOW) {
                if (hypot(x - bx, y - by) > radius) continue
            } else if ((x < w / 2f) != (bx < w / 2f) || (y < h / 2f) != (by < h / 2f)) continue
            c.pointer = id
            c.baseX = if (fixed) bx else x
            c.baseY = if (fixed) by else y
            c.sample.pressed = true
            c.sample.presses++
            update(c, x, y, radius, moved = false)
            return true
        }
        return false
    }

    @Synchronized
    fun move(id: Long, x: Float, y: Float, w: Float, h: Float, density: Float): Boolean {
        if (id == NO_POINTER) return false
        for (i in controls.indices) {
            val c = controls[i]
            if (c.pointer != id) continue
            if (!x.isFinite() || !y.isFinite() || c.anchor(w, h, density) == Long.MIN_VALUE) {
                release(c)
            } else if (!c.config.button && (c.lastX != x || c.lastY != y)) {
                update(c, x, y, c.radius(w, h, density), moved = true)
            }
            return true
        }
        return false
    }

    @Synchronized
    fun up(id: Long): Boolean {
        if (id == NO_POINTER) return false
        for (i in controls.indices) {
            val c = controls[i]
            if (c.pointer != id) continue
            release(c)
            return true
        }
        return false
    }

    @Synchronized
    fun cancel() {
        for (i in controls.indices) if (controls[i].pointer != NO_POINTER) release(controls[i])
    }

    private fun release(c: Control) {
        c.pointer = NO_POINTER
        c.sample.pressed = false
        c.sample.releases++
        c.sample.axes(0f, 0f, 0f, 1f, false)
    }

    private fun update(c: Control, x: Float, y: Float, radius: Float, moved: Boolean) {
        c.lastX = x; c.lastY = y
        if (c.config.button) return
        val travel = (radius * .54f).coerceAtLeast(1f)
        var dx = x - c.baseX; var dy = y - c.baseY
        if (c.config.type == StickType.DYNAMIC) {
            val len = hypot(dx, dy)
            if (len > travel) {
                dx *= travel / len; dy *= travel / len
                c.baseX = x - dx; c.baseY = y - dy
            }
        }
        c.sample.axes(dx / travel, dy / travel, c.config.deadZone, c.config.response, c.config.snap8)
        if (moved) c.sample.moves++
    }

    /** Follow coordinates are normalized to the rendered viewport. Offscreen points hide the control. */
    @Synchronized
    fun project(matrix: FloatArray) {
        for (i in controls.indices) {
            val c = controls[i]
            val m = c.follow?.transform?.worldMatrix?.m ?: continue
            if (c.config.anchor != TouchAnchor.FOLLOW) continue
            c.projection = projectPoint(matrix, m[12], m[13], m[14])
            if (c.projection == Long.MIN_VALUE && c.pointer != NO_POINTER) release(c)
        }
    }

    fun Control.radius(w: Float, h: Float, density: Float) = minOf(config.sizeDp * density / 2f, w / 2f, h / 2f)

    fun Control.anchor(w: Float, h: Float, density: Float): Long {
        if (config.anchor == TouchAnchor.FOLLOW) {
            val p = projection
            return if (p == Long.MIN_VALUE) p else pack(unpackX(p) * w, unpackY(p) * h)
        }
        val radius = radius(w, h, density)
        val insetX = (radius + 12f * density).coerceAtMost(w / 2f)
        val insetY = (radius + 12f * density).coerceAtMost(h / 2f)
        return pack(
            if (config.anchor == TouchAnchor.TOP_LEFT || config.anchor == TouchAnchor.BOTTOM_LEFT) insetX else w - insetX,
            if (config.anchor == TouchAnchor.TOP_LEFT || config.anchor == TouchAnchor.TOP_RIGHT) insetY else h - insetY
        )
    }

    companion object {
        private const val NO_POINTER = Long.MIN_VALUE
        fun pack(x: Float, y: Float): Long = (x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xffffffffL)
        fun unpackX(pair: Long): Float = Float.fromBits((pair ushr 32).toInt())
        fun unpackY(pair: Long): Float = Float.fromBits(pair.toInt())

        fun projectPoint(m: FloatArray, x: Float, y: Float, z: Float): Long {
            val w = m[3] * x + m[7] * y + m[11] * z + m[15]
            if (!w.isFinite() || w <= 1e-5f) return Long.MIN_VALUE
            val nx = (m[0] * x + m[4] * y + m[8] * z + m[12]) / w
            val ny = (m[1] * x + m[5] * y + m[9] * z + m[13]) / w
            val nz = (m[2] * x + m[6] * y + m[10] * z + m[14]) / w
            if (nx !in -1f..1f || ny !in -1f..1f || nz !in -1f..1f) return Long.MIN_VALUE
            return pack((nx + 1f) / 2f, (1f - ny) / 2f)
        }
    }
}
