package com.kenkern.engine.assets.mesh

import kotlin.math.abs

/**
 * Constantes e codecs do formato .KERN (ver docs/KERN_SPEC.md).
 */
object KernFormat {
    const val MAGIC = "KERN"
    const val VERSION: Short = 1

    const val HEADER_SIZE = 80
    const val VERTEX_SIZE = 24
    const val LOD_ENTRY_SIZE = 20

    const val FLAG_HAS_CLUSTERS: Short = 1

    const val INDEX_TYPE_U16: Byte = 0
    const val INDEX_TYPE_U32: Byte = 1

    /** Quantiza um escalar float para u16 usando a faixa [min, max]. */
    fun quantizeU16(value: Float, min: Float, max: Float): Int {
        val span = if (max - min < 1e-9f) 1f else max - min
        val t = ((value - min) / span).coerceIn(0f, 1f)
        return (t * 65535f + 0.5f).toInt().coerceIn(0, 65535)
    }

    /** Desquantiza u16 para float na faixa [min, max]. */
    fun dequantizeU16(raw: Int, min: Float, max: Float): Float =
        min + (raw / 65535f) * (max - min)

    private fun signOf(v: Float): Float = if (v >= 0f) 1f else -1f

    /** Codifica uma direção unitária em 16 bits (octaédrico 8:8). */
    fun encodeOctahedral16(nx: Float, ny: Float, nz: Float): Int {
        val denom = abs(nx) + abs(ny) + abs(nz)
        var ox = if (denom > 1e-12f) nx / denom else 0f
        var oy = if (denom > 1e-12f) ny / denom else 0f
        if (nz < 0f) {
            val tx = (1f - abs(oy)) * signOf(ox)
            val ty = (1f - abs(ox)) * signOf(oy)
            ox = tx
            oy = ty
        }
        val u = ((ox * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val v = ((oy * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (u shl 8) or v
    }

    /** Decodifica direção octaédrica 8:8 para (x, y, z) em [out]. */
    fun decodeOctahedral16(packed: Int, out: FloatArray) {
        var u = (((packed shr 8) and 0xFF) / 255f) * 2f - 1f
        var v = ((packed and 0xFF) / 255f) * 2f - 1f
        var z = 1f - abs(u) - abs(v)
        if (z < 0f) {
            val oldU = u
            u = (1f - abs(v)) * signOf(oldU)
            v = (1f - abs(oldU)) * signOf(v)
        }
        val len = kotlin.math.sqrt(u * u + v * v + z * z)
        out[0] = if (len > 1e-9f) u / len else 0f
        out[1] = if (len > 1e-9f) v / len else 0f
        out[2] = if (len > 1e-9f) z / len else 0f
    }
}
