package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4

/**
 * RenderBackend: abstração da API gráfica. Nesta fase existe apenas o
 * GlesBackend (OpenGL ES 3.0); a estrutura permite adicionar o VulkanBackend
 * depois sem mexer no Core da engine.
 *
 * Orçamento de draw calls do editor (cena padrão 1 cubo + grade):
 * céu 1 + grade 1 + malhas N + contorno (se seleção) 1 + gizmo 1.
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

    /**
     * Cria buffer da grade: interleaved [x, y, z, ênfase] por vértice.
     * Ênfase: 0 = linha 1 m, 1 = linha 10 m, 2 = eixo X, 3 = eixo Z.
     */
    fun createGridBuffer(data: FloatArray): Int

    /** Cria buffer de linhas coloridas (interleaved x, y, z, r, g, b) — usado pelo gizmo. */
    fun createColoredLineBuffer(data: FloatArray): Int

    /** Céu: triângulo de tela cheia com gradiente zênite→horizonte e sol na direção da luz. */
    fun drawSky(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        sunX: Float, sunY: Float, sunZ: Float
    )

    /** Grade em 1 draw call (GL_LINES), com fade por distância no fragment shader. */
    fun drawGrid(handle: Int, vertexCount: Int, viewProj: Mat4, camX: Float, camY: Float, camZ: Float)

    fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float)

    /** Contorno (inverted hull): backfaces da malha inflada em cor sólida. */
    fun drawMeshOutline(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float)

    /** Gizmo de eixos no canto superior direito (linhas coloridas girando com a câmera). */
    fun drawGizmo(handle: Int, vertexCount: Int, mvp: Mat4)

    /**
     * Cria buffer do gizmo de transformação: interleaved [x, y, z, r, g, b, a]
     * por vértice (stride 28). Desenhado sem teste de profundidade, com blend.
     */
    fun createGizmoBuffer(data: FloatArray): Int

    /** Linhas do gizmo de transformação (sub-ranges por alça), sem depth test. */
    fun drawGizmoLines(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4)

    /** Triângulos do gizmo de transformação (preenchimento dos planos). */
    fun drawGizmoTris(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4)

    /** Redesenha uma alça com cor sólida (destaque amarelo sob o dedo). */
    fun drawGizmoHandleOverride(
        handle: Int, offset: Int, vertexCount: Int, mvp: Mat4,
        r: Float, g: Float, b: Float, a: Float
    )

    fun destroyMesh(handle: Int)

    fun destroyLineBuffer(handle: Int)

    fun endFrame()

    fun destroy()
}
