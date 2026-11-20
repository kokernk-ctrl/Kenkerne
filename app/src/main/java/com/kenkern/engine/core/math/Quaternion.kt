package com.kenkern.engine.core.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quaternion mutável (x, y, z, w). Operações in-place, ZERO alocações no loop.
 * Convenção de Euler para o Inspetor: ordem ZYX (roll em X, pitch em Y, yaw em Z), graus.
 */
class Quaternion(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f, var w: Float = 1f) {

    fun set(nx: Float, ny: Float, nz: Float, nw: Float): Quaternion {
        x = nx; y = ny; z = nz; w = nw
        return this
    }

    fun set(o: Quaternion): Quaternion = set(o.x, o.y, o.z, o.w)

    fun setIdentity(): Quaternion = set(0f, 0f, 0f, 1f)

    /** Define a rotação a partir de eixo (já normalizado) e ângulo em radianos. */
    fun setFromAxisAngle(ax: Float, ay: Float, az: Float, angleRad: Float): Quaternion {
        val s = sin(angleRad * 0.5f)
        return set(ax * s, ay * s, az * s, cos(angleRad * 0.5f))
    }

    /** Euler em graus: roll (X), pitch (Y), yaw (Z), aplicados na ordem Z depois Y depois X. */
    fun setFromEulerDegrees(degX: Float, degY: Float, degZ: Float): Quaternion {
        val rx = Math.toRadians(degX.toDouble()).toFloat() * 0.5f
        val ry = Math.toRadians(degY.toDouble()).toFloat() * 0.5f
        val rz = Math.toRadians(degZ.toDouble()).toFloat() * 0.5f
        val sr = sin(rx); val cr = cos(rx)
        val sp = sin(ry); val cp = cos(ry)
        val sy = sin(rz); val cy = cos(rz)
        x = sr * cp * cy - cr * sp * sy
        y = cr * sp * cy + sr * cp * sy
        z = cr * cp * sy - sr * sp * cy
        w = cr * cp * cy + sr * sp * sy
        return this
    }

    /** Inverso de [setFromEulerDegrees]; retorna (rollX, pitchY, yawZ) em graus. */
    fun toEulerDegrees(out: FloatArray) {
        out[0] = Math.toDegrees(
            atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
        ).toFloat()
        val sinp = 2.0 * (w * y - z * x)
        out[1] = Math.toDegrees(
            if (kotlin.math.abs(sinp) >= 1.0) {
                kotlin.math.sign(sinp) * Math.PI / 2
            } else {
                kotlin.math.asin(sinp)
            }
        ).toFloat()
        out[2] = Math.toDegrees(
            atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))
        ).toFloat()
    }

    /** this = this * q. */
    fun mul(q: Quaternion): Quaternion {
        val tx = w * q.x + x * q.w + y * q.z - z * q.y
        val ty = w * q.y - x * q.z + y * q.w + z * q.x
        val tz = w * q.z + x * q.y - y * q.x + z * q.w
        val tw = w * q.w - x * q.x - y * q.y - z * q.z
        return set(tx, ty, tz, tw)
    }

    fun normalize(): Quaternion {
        val len = sqrt(x * x + y * y + z * z + w * w)
        return if (len > 1e-12f) {
            val inv = 1f / len
            x *= inv; y *= inv; z *= inv; w *= inv
            this
        } else {
            setIdentity()
        }
    }

    /** Escreve a matriz de rotação (column-major, OpenGL) em [out]. */
    fun toMatrix(out: Mat4) {
        val m = out.m
        val xx = x + x; val yy = y + y; val zz = z + z
        val xy = x * yy; val xz = x * zz; val xw = x * ww() * 2f
        val yz = y * zz; val yw = y * ww() * 2f
        val zw = z * ww() * 2f
        val xxx = x * xx; val xyy = y * yy; val xzz = z * zz
        val yyy = y * yy; val zzz = z * zz
        m[0] = 1f - (yyy + zzz); m[4] = xy - zw; m[8] = xz + yw
        m[1] = xy + zw; m[5] = 1f - (xxx + zzz); m[9] = yz - xw
        m[2] = xz - yw; m[6] = yz + xw; m[10] = 1f - (xxx + xyy)
        m[3] = 0f; m[7] = 0f; m[11] = 0f
        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
    }

    private fun ww(): Float = w

    fun copy(): Quaternion = Quaternion(x, y, z, w)

    override fun toString(): String = "($x, $y, $z, $w)"
}
