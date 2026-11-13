package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4

/**
 * RenderBackend: abstração da API gráfica. Nesta fase existe apenas o
 * GlesBackend (OpenGL ES 3.0); a estrutura permite adicionar o VulkanBackend
 * depois sem mexer no Core da engine.
 */
interface RenderBackend {
    val name: String

    var viewportWidth: Int
    var viewportHeight: Int

    /** Inicializa recursos da API gráfica. NUNCA lança: false = sem recursos de render. */
    fun initialize(): Boolean
    fun resize(width: Int, height: Int)

    fun beginFrame(clearR: Float, clearG: Float, clearB: Float, clearA: Float)

    /** Define a iluminação do frame (implementação simples; KELU substitui depois). */
    fun setLighting(
        dirX: Float, dirY: Float, dirZ: Float,
        ambientR: Float, ambientG: Float, ambientB: Float,
        lightR: Float, lightG: Float, lightB: Float,
        intensity: Float
    )

    /** Cria/upload de malha; retorna handle opaco (0 = falha). */
    fun createMesh(mesh: MeshData): Int

    /** Cria buffer de linhas (somente posições xyz). */
    fun createLineBuffer(positions: FloatArray): Int

    fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float)

    fun drawLines(handle: Int, vertexCount: Int, mvp: Mat4, r: Float, g: Float, b: Float, a: Float)

    fun destroyMesh(handle: Int)

    fun destroyLineBuffer(handle: Int)

    fun endFrame()

    fun destroy()
}
