package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4

/**
 * RenderBackend: abstração da API gráfica. Nesta fase existe apenas o
 * GlesBackend (OpenGL ES 3.0); a estrutura permite adicionar o VulkanBackend
 * depois sem mexer no Core da engine.
 *
 * Orçamento de draw calls do editor (cena padrão 1 cubo + grade):
 * céu 1 + malhas N + contorno (se seleção) 1 + grade infinita 1 + gizmo ≤ 2 + eixos 1.
 */
interface RenderBackend {
    val name: String

    var viewportWidth: Int
    var viewportHeight: Int

    /** GL_MAX_TEXTURE_SIZE do backend (0 = desconhecido antes do initialize). */
    val maxTextureSize: Int get() = 0

    /**
     * Inicializa recursos da API gráfica. NUNCA lança: false = sem recursos de render.
     * [registrarBootTrace] = false SOMENTE no Diagnóstico offscreen (não marca a
     * etapa "shader compilado" do boot_trace — o marcador pertence ao boot do editor).
     */
    fun initialize(registrarBootTrace: Boolean = true): Boolean
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

    /** Cria buffer de linhas coloridas (interleaved x, y, z, r, g, b) — gizmo de eixos. */
    fun createColoredLineBuffer(data: FloatArray): Int

    /** Céu: triângulo de tela cheia com gradiente zênite→horizonte e sol na direção da luz. */
    fun drawSky(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        sunX: Float, sunY: Float, sunZ: Float
    )

    /**
     * GRADE INFINITA: 1 draw call (triângulo de tela cheia). O fragment shader
     * traça um raio por pixel contra o plano Y=0 e desenha as linhas com fade
     * por distância e anti-moiré por fwidth. Dupla face (câmera abaixo do chão).
     */
    fun drawInfiniteGrid(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        camX: Float, camY: Float, camZ: Float
    )

    fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float)

    /** Contorno (inverted hull): backfaces da malha inflada em cor sólida. */
    fun drawMeshOutline(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float)

    /** Gizmo de eixos no canto superior direito (linhas coloridas girando com a câmera). */
    fun drawGizmo(handle: Int, vertexCount: Int, mvp: Mat4)

    /**
     * Cria buffer do gizmo de transformação: interleaved [x, y, z, r, g, b, a]
     * por vértice (stride 28), GL_TRIANGLES. Desenhado sem teste de profundidade.
     */
    fun createGizmoBuffer(data: FloatArray): Int

    /** Malha completa do gizmo (alças: cilindros/cones/toros/cubos), sem depth test. */
    fun drawGizmoTris(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4)

    /** Desenha o gizmo inteiro com alpha multiplicado (demais eixos apagados). */
    fun drawGizmoTrisDimmed(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4, alphaMul: Float)

    /** Redesenha uma alça com cor sólida (destaque AMARELO sob o dedo). */
    fun drawGizmoTrisSolid(
        handle: Int, offset: Int, vertexCount: Int, mvp: Mat4,
        r: Float, g: Float, b: Float, a: Float
    )

    fun destroyMesh(handle: Int)

    fun destroyLineBuffer(handle: Int)

    /**
     * Cria textura de GPU a partir de um arquivo .KIMG (ETC2/ASTC/RGBA8 —
     * F2.9). Retorna handle opaco (0 = falhou, com erro registrado pelo
     * chamador). Implementado pelo GlesBackend; outros backends retornam 0.
     */
    fun createTextureFromKimg(kimg: ByteArray): Int = 0

    /** Libera textura criada por [createTextureFromKimg]. */
    fun destroyTexture(handle: Int) = Unit

    /** Desenha um SUB-RANGE do buffer de índices com textura opcional (0 = cor sólida). */
    fun drawMeshRange(
        handle: Int, indexOffset: Int, indexCount: Int, textureHandle: Int,
        model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float
    ) = Unit

    fun endFrame()

    fun destroy()
}
