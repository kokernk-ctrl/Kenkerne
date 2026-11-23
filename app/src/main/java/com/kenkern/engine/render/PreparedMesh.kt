package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Packed CPU buffers, no GL calls. Project meshes prepare this on the worker. */
class PreparedMesh private constructor(
    internal val vertices: FloatBuffer,
    internal val indices: ByteBuffer,
    internal val indexCount: Int,
    internal val shortIndices: Boolean
) {
    companion object {
        internal const val STRIDE = 32

        internal fun pack(mesh: MeshData): PreparedMesh {
            require(mesh.vertexCount > 0 && mesh.indices.isNotEmpty()) { "malha sem geometria" }
            val vertices = ByteBuffer.allocateDirect(Math.multiplyExact(mesh.vertexCount, STRIDE))
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (v in 0 until mesh.vertexCount) {
                if (v and 4095 == 0 && Thread.currentThread().isInterrupted) throw InterruptedException()
                vertices.put(mesh.positions, v * 3, 3)
                vertices.put(mesh.normals, v * 3, 3)
                vertices.put(mesh.uvs, v * 2, 2)
            }
            vertices.position(0)
            val useShort = mesh.vertexCount <= 65535
            val indices = ByteBuffer.allocateDirect(Math.multiplyExact(mesh.indices.size, if (useShort) 2 else 4))
                .order(ByteOrder.nativeOrder())
            if (useShort) {
                val shorts = indices.asShortBuffer()
                for (index in mesh.indices) shorts.put(index.toShort())
            } else {
                indices.asIntBuffer().put(mesh.indices)
            }
            return PreparedMesh(vertices, indices, mesh.indices.size, useShort)
        }
    }
}
