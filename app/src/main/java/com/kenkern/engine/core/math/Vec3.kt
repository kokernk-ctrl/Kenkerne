package com.kenkern.engine.core.math

/**
 * Vetor 3D mutável. Todas as operações são in-place para garantir
 * ZERO alocações no loop de update e de render.
 */
class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {

    fun set(nx: Float, ny: Float, nz: Float): Vec3 {
        x = nx; y = ny; z = nz
        return this
    }

    fun set(o: Vec3): Vec3 = set(o.x, o.y, o.z)

    fun add(o: Vec3): Vec3 {
        x += o.x; y += o.y; z += o.z
        return this
    }

    fun sub(o: Vec3): Vec3 {
        x -= o.x; y -= o.y; z -= o.z
        return this
    }

    fun addScaled(o: Vec3, s: Float): Vec3 {
        x += o.x * s; y += o.y * s; z += o.z * s
        return this
    }

    fun scale(s: Float): Vec3 {
        x *= s; y *= s; z *= s
        return this
    }

    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalize(): Vec3 {
        val len = length()
        return if (len > 1e-12f) {
            val inv = 1f / len
            x *= inv; y *= inv; z *= inv
            this
        } else {
            set(0f, 0f, 0f)
        }
    }

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    /** this = a x b (in-place, sem alocar). */
    fun cross(a: Vec3, b: Vec3): Vec3 {
        val cx = a.y * b.z - a.z * b.y
        val cy = a.z * b.x - a.x * b.z
        val cz = a.x * b.y - a.y * b.x
        return set(cx, cy, cz)
    }

    fun distanceTo(o: Vec3): Float {
        val dx = x - o.x; val dy = y - o.y; val dz = z - o.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun copy(): Vec3 = Vec3(x, y, z)

    override fun toString(): String = "($x, $y, $z)"
}
