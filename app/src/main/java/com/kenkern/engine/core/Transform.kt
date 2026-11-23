package com.kenkern.engine.core

import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3

/** Snapshot imutável usado por undo/redo do transform. */
data class TransformSnapshot(
    val px: Float, val py: Float, val pz: Float,
    val qx: Float, val qy: Float, val qz: Float, val qw: Float,
    val sx: Float, val sy: Float, val sz: Float
)

/**
 * Transform de um Nick: posição, rotação (quaternion) e escala locais.
 * As matrizes são pré-alocadas; updateWorldTransforms() da cena as preenche.
 */
class Transform {

    val position = Vec3()
    val rotation = Quaternion()
    val scale = Vec3(1f, 1f, 1f)

    val localMatrix = Mat4()
    val worldMatrix = Mat4()

    val worldPosition = Vec3()
    val worldRotation = Quaternion()
    val worldScale = Vec3(1f, 1f, 1f)

    fun setLocalPosition(x: Float, y: Float, z: Float) {
        position.set(x, y, z)
    }

    fun setLocalScale(x: Float, y: Float, z: Float) {
        scale.set(x, y, z)
    }

    fun snapshot(): TransformSnapshot = TransformSnapshot(
        position.x, position.y, position.z,
        rotation.x, rotation.y, rotation.z, rotation.w,
        scale.x, scale.y, scale.z
    )

    fun apply(s: TransformSnapshot) {
        position.set(s.px, s.py, s.pz)
        rotation.set(s.qx, s.qy, s.qz, s.qw)
        scale.set(s.sx, s.sy, s.sz)
    }

    /** Extrai posição/rotação/escala do mundo a partir de worldMatrix (aproximação de escala por norma das colunas). */
    fun deriveWorldComponents() {
        val m = worldMatrix.m
        val sx = kotlin.math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        val sy = kotlin.math.sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
        val sz = kotlin.math.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
        worldScale.set(sx, sy, sz)
        worldPosition.set(m[12], m[13], m[14])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            // Column-major: R[row][col] = m[col*4+row]; cada coluna é dividida pela sua escala.
            val sRot = rotationScratch.get()!!
            sRot.set(
                m[0] / sx, m[4] / sy, m[8] / sz,
                m[1] / sx, m[5] / sy, m[9] / sz,
                m[2] / sx, m[6] / sy, m[10] / sz
            )
            sRot.toMatrixComponents(worldRotation)
        } else {
            worldRotation.setIdentity()
        }
    }

    companion object {
        private val rotationScratch = ThreadLocal.withInitial { RotationFromMatrix() }
    }
}

/** Helper interno para extrair quaternion de matriz sem alocar. */
class RotationFromMatrix {
    private var m00 = 0f; private var m01 = 0f; private var m02 = 0f
    private var m10 = 0f; private var m11 = 0f; private var m12 = 0f
    private var m20 = 0f; private var m21 = 0f; private var m22 = 0f

    fun set(
        a00: Float, a01: Float, a02: Float,
        a10: Float, a11: Float, a12: Float,
        a20: Float, a21: Float, a22: Float
    ) {
        m00 = a00; m01 = a01; m02 = a02
        m10 = a10; m11 = a11; m12 = a12
        m20 = a20; m21 = a21; m22 = a22
    }

    /** Matriz de rotação pura (column-major: [col*4+row]) -> quaternion em [out]. */
    fun toMatrixComponents(out: com.kenkern.engine.core.math.Quaternion) {
        val m00t = m00; val m11t = m11; val m22t = m22
        val trace = m00t + m11t + m22t
        if (trace > 0f) {
            val s = kotlin.math.sqrt(trace + 1f) * 2f
            out.set(
                (m21 - m12) / s,
                (m02 - m20) / s,
                (m10 - m01) / s,
                0.25f * s
            )
        } else if (m00t > m11t && m00t > m22t) {
            val s = kotlin.math.sqrt(1f + m00t - m11t - m22t) * 2f
            out.set(
                0.25f * s,
                (m01 + m10) / s,
                (m02 + m20) / s,
                (m21 - m12) / s
            )
        } else if (m11t > m22t) {
            val s = kotlin.math.sqrt(1f + m11t - m00t - m22t) * 2f
            out.set(
                (m01 + m10) / s,
                0.25f * s,
                (m12 + m21) / s,
                (m02 - m20) / s
            )
        } else {
            val s = kotlin.math.sqrt(1f + m22t - m00t - m11t) * 2f
            out.set(
                (m02 + m20) / s,
                (m12 + m21) / s,
                0.25f * s,
                (m10 - m01) / s
            )
        }
        out.normalize()
    }
}
