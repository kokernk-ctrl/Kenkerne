package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4

/** Deterministic no-GL backend shared by render lifecycle unit tests. */
internal class RenderBackendTestDouble : RenderBackend {
    override val name: String = "test"
    override var viewportWidth: Int = 800
    override var viewportHeight: Int = 600
    override val maxTextureSize: Int = 4096

    var coloredLineBufferCreates: Int = 0
        private set
    var textureUploads: Int = 0
        private set
    var nextTextureHandle: Int = 1

    override fun initialize(registrarBootTrace: Boolean): Boolean = true
    override fun resize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }
    override fun beginFrame(clearR: Float, clearG: Float, clearB: Float, clearA: Float) = Unit
    override fun setLighting(
        dirX: Float, dirY: Float, dirZ: Float,
        ambientR: Float, ambientG: Float, ambientB: Float,
        lightR: Float, lightG: Float, lightB: Float,
        intensity: Float
    ) = Unit
    override fun createMesh(mesh: MeshData): Int = 1
    override fun createColoredLineBuffer(data: FloatArray): Int = ++coloredLineBufferCreates
    override fun drawSky(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        sunX: Float, sunY: Float, sunZ: Float
    ) = Unit
    override fun drawInfiniteGrid(
        fwdX: Float, fwdY: Float, fwdZ: Float,
        rightX: Float, rightY: Float, rightZ: Float,
        upX: Float, upY: Float, upZ: Float,
        tanHalfFov: Float, aspect: Float,
        camX: Float, camY: Float, camZ: Float
    ) = Unit
    override fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) = Unit
    override fun drawMeshOutline(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) = Unit
    override fun drawGizmo(handle: Int, vertexCount: Int, mvp: Mat4) = Unit
    override fun drawWorldLines(handle: Int, vertexCount: Int, mvp: Mat4) = Unit
    override fun createGizmoBuffer(data: FloatArray): Int = 1
    override fun drawGizmoTris(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4) = Unit
    override fun drawGizmoTrisDimmed(handle: Int, offset: Int, vertexCount: Int, mvp: Mat4, alphaMul: Float) = Unit
    override fun drawGizmoTrisSolid(
        handle: Int, offset: Int, vertexCount: Int, mvp: Mat4,
        r: Float, g: Float, b: Float, a: Float
    ) = Unit
    override fun createTextureFromKimg(kimg: ByteArray): Int {
        textureUploads++
        return nextTextureHandle
    }
    override fun destroyMesh(handle: Int) = Unit
    override fun destroyLineBuffer(handle: Int) = Unit
    override fun endFrame() = Unit
    override fun destroy() = Unit
}
