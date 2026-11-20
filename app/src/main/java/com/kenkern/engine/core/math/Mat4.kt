package com.kenkern.engine.core.math

import kotlin.math.tan

/**
 * Matriz 4x4 column-major (mesma convenção do OpenGL).
 * Armazenamento: m[col * 4 + row]. Todas as operações in-place, ZERO alocações no loop.
 */
class Mat4 {

    val m = FloatArray(16)

    fun setIdentity(): Mat4 {
        m[0] = 1f; m[1] = 0f; m[2] = 0f; m[3] = 0f
        m[4] = 0f; m[5] = 1f; m[6] = 0f; m[7] = 0f
        m[8] = 0f; m[9] = 0f; m[10] = 1f; m[11] = 0f
        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
        return this
    }

    fun copyFrom(o: Mat4): Mat4 {
        System.arraycopy(o.m, 0, m, 0, 16)
        return this
    }

    /** Projeção perspectiva (column-major), fovY em graus. */
    fun setPerspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
        val f = 1f / tan(Math.toRadians(fovYDeg.toDouble()).toFloat() * 0.5f)
        val nf = 1f / (near - far)
        m[0] = f / aspect; m[1] = 0f; m[2] = 0f; m[3] = 0f
        m[4] = 0f; m[5] = f; m[6] = 0f; m[7] = 0f
        m[8] = 0f; m[9] = 0f; m[10] = (far + near) * nf; m[11] = -1f
        m[12] = 0f; m[13] = 0f; m[14] = 2f * far * near * nf; m[15] = 0f
        return this
    }

    /** Matriz de câmera look-at (column-major). Não aloca. */
    fun setLookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
        sF.set(center).sub(eye).normalize()
        sS.cross(sF, up).normalize()
        sU.cross(sS, sF)
        m[0] = sS.x; m[1] = sU.x; m[2] = -sF.x; m[3] = 0f
        m[4] = sS.y; m[5] = sU.y; m[6] = -sF.y; m[7] = 0f
        m[8] = sS.z; m[9] = sU.z; m[10] = -sF.z; m[11] = 0f
        m[12] = -sS.dot(eye); m[13] = -sU.dot(eye); m[14] = sF.dot(eye); m[15] = 1f
        return this
    }

    /** this = pos * rot * esc (TRSD), direto nos elementos, sem matrizes temporárias. */
    fun setTRS(pos: Vec3, rot: Quaternion, scl: Vec3): Mat4 {
        val r = rot
        val sx = scl.x; val sy = scl.y; val sz = scl.z
        val xx = r.x + r.x; val yy = r.y + r.y; val zz = r.z + r.z
        val xy = r.x * yy; val xz = r.x * zz; val xw = r.x * r.w * 2f
        val yz = r.y * zz; val yw = r.y * r.w * 2f
        val zw = r.z * r.w * 2f
        val xxx = r.x * xx; val xyy = r.y * yy; val xzz = r.z * zz
        val yyy = r.y * yy; val zzz = r.z * zz
        m[0] = (1f - (yyy + zzz)) * sx; m[4] = (xy - zw) * sy; m[8] = (xz + yw) * sz; m[12] = pos.x
        m[1] = (xy + zw) * sx; m[5] = (1f - (xxx + zzz)) * sy; m[9] = (yz - xw) * sz; m[13] = pos.y
        m[2] = (xz - yw) * sx; m[6] = (yz + xw) * sy; m[10] = (1f - (xxx + xyy)) * sz; m[14] = pos.z
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
        return this
    }

    /** this = a * b (column-major). Seguro para aliasing (usa scratch interno estático). */
    fun multiply(a: Mat4, b: Mat4): Mat4 {
        for (c in 0 until 4) {
            val b0 = b.m[c * 4]; val b1 = b.m[c * 4 + 1]; val b2 = b.m[c * 4 + 2]; val b3 = b.m[c * 4 + 3]
            sTmp[c * 4] = a.m[0] * b0 + a.m[4] * b1 + a.m[8] * b2 + a.m[12] * b3
            sTmp[c * 4 + 1] = a.m[1] * b0 + a.m[5] * b1 + a.m[9] * b2 + a.m[13] * b3
            sTmp[c * 4 + 2] = a.m[2] * b0 + a.m[6] * b1 + a.m[10] * b2 + a.m[14] * b3
            sTmp[c * 4 + 3] = a.m[3] * b0 + a.m[7] * b1 + a.m[11] * b2 + a.m[15] * b3
        }
        System.arraycopy(sTmp, 0, m, 0, 16)
        return this
    }

    companion object {
        private val sTmp = FloatArray(16)
        private val sF = Vec3()
        private val sS = Vec3()
        private val sU = Vec3()
    }
}
