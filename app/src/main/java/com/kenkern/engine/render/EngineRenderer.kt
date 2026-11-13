package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderer do viewport da KENKERN:
 *  - grade no chão;
 *  - malha de teste (torus) gerada, salva em .KERN e recarregada (roundtrip do formato);
 *  - cubo desenhado para cada Nick ativo/visível da cena (seleção destacada).
 */
class EngineRenderer {

    private val geometry: GeometryPipeline = DefaultGeometryPipeline()
    private val lighting: LightingPipeline = DefaultLightingPipeline()

    private var gridHandle = 0
    private var gridVertexCount = 0
    private var cubeHandle = 0
    private var testMeshHandle = 0
    private var kernRoundtripOk = false

    private val proj = Mat4()
    private val view = Mat4()
    private val viewProj = Mat4()

    private val torusModel = Mat4()
    private val torusPos = Vec3(0f, 1.4f, 0f)
    private val torusRot = Quaternion()
    private val unitScale = Vec3(1f, 1f, 1f)

    private val scratchDir = FloatArray(3)
    private val scratchAmbient = FloatArray(3)
    private val scratchLightColor = FloatArray(3)

    private val nickStack = arrayOfNulls<Nick>(512)

    fun initialize(backend: RenderBackend) {
        geometry.reset()
        buildGrid(backend)
        buildCube(backend)
        buildTestMeshViaKern(backend)
    }

    // ---------- Construção (uma vez, fora do loop) ----------

    private fun buildGrid(backend: RenderBackend) {
        val half = 10
        val floats = FloatArray(((half * 2 + 1) * 2 * 2) * 3)
        var i = 0
        for (g in -half..half) {
            // linha paralela ao eixo X
            floats[i++] = -half.toFloat(); floats[i++] = 0f; floats[i++] = g.toFloat()
            floats[i++] = half.toFloat(); floats[i++] = 0f; floats[i++] = g.toFloat()
            // linha paralela ao eixo Z
            floats[i++] = g.toFloat(); floats[i++] = 0f; floats[i++] = -half.toFloat()
            floats[i++] = g.toFloat(); floats[i++] = 0f; floats[i++] = half.toFloat()
        }
        gridVertexCount = floats.size / 3
        gridHandle = backend.createLineBuffer(floats)
    }

    private fun buildCube(backend: RenderBackend) {
        val p = CUBE_POSITIONS
        val n = CUBE_NORMALS_PER_QUAD
        val positions = FloatArray(24 * 3)
        val normals = FloatArray(24 * 3)
        val uvs = FloatArray(24 * 2)
        val tangents = FloatArray(24 * 3)
        for (v in 0 until 24) {
            positions[v * 3] = p[v * 3]; positions[v * 3 + 1] = p[v * 3 + 1]; positions[v * 3 + 2] = p[v * 3 + 2]
            normals[v * 3] = n[(v / 4) * 3]; normals[v * 3 + 1] = n[(v / 4) * 3 + 1]; normals[v * 3 + 2] = n[(v / 4) * 3 + 2]
            uvs[v * 2] = if (v % 4 == 0 || v % 4 == 3) 0f else 1f
            uvs[v * 2 + 1] = if (v % 4 < 2) 0f else 1f
            tangents[v * 3] = 1f; tangents[v * 3 + 1] = 0f; tangents[v * 3 + 2] = 0f
        }
        val indices = IntArray(36)
        var ii = 0
        for (quad in 0 until 6) {
            val b = quad * 4
            indices[ii++] = b; indices[ii++] = b + 1; indices[ii++] = b + 2
            indices[ii++] = b; indices[ii++] = b + 2; indices[ii++] = b + 3
        }
        cubeHandle = backend.createMesh(MeshData(positions, normals, uvs, indices, Vec3(-0.5f), Vec3(0.5f)))
    }

    /**
     * Malha de teste: torus gerado proceduralmente, serializado em .KERN,
     * lido de volta com o KernReader e enviado à GPU — valida o formato completo.
     */
    private fun buildTestMeshViaKern(backend: RenderBackend) {
        val builder = KernWriter.Builder()
        val segU = 48
        val segV = 24
        val bigR = 1.7f
        val smallR = 0.6f
        for (iu in 0 until segU) {
            val u = (iu.toFloat() / segU) * 2f * Math.PI.toFloat()
            val cu = cos(u); val su = sin(u)
            for (iv in 0 until segV) {
                val v = (iv.toFloat() / segV) * 2f * Math.PI.toFloat()
                val cv = cos(v); val sv = sin(v)
                val ringR = bigR + smallR * cv
                val px = ringR * cu
                val py = smallR * sv
                val pz = ringR * su
                // normal analítica
                val nx = cv * cu; val ny = sv; val nz = cv * su
                // tangente ao longo de u
                val tx = -su; val ty = 0f; val tz = cu
                builder.vertex(px, py, pz, nx, ny, nz, iu.toFloat() / segU, iv.toFloat() / segV, tx, ty, tz)
            }
        }
        for (iu in 0 until segU) {
            val iu2 = (iu + 1) % segU
            for (iv in 0 until segV) {
                val iv2 = (iv + 1) % segV
                val a = iu * segV + iv
                val b = iu2 * segV + iv
                val c = iu2 * segV + iv2
                val d = iu * segV + iv2
                builder.triangle(a, b, c)
                builder.triangle(a, c, d)
            }
        }
        val kernMesh = builder.build()
        val bytes = KernWriter.write(kernMesh)
        val reloaded = KernReader.read(bytes)
        kernRoundtripOk = true
        testMeshHandle = backend.createMesh(MeshData.fromKern(reloaded))
    }

    fun isKernRoundtripOk(): Boolean = kernRoundtripOk

    // ---------- Frame ----------

    fun renderFrame(
        backend: RenderBackend,
        scene: Scene?,
        camera: OrbitCamera,
        engineTime: Float,
        selectedId: ReferenceId?
    ) {
        backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)

        val aspect = backend.viewportWidth.toFloat() / backend.viewportHeight.toFloat()
        camera.projection(proj, aspect)
        camera.view(view)
        viewProj.multiply(proj, view)

        val dir = lighting.fillDirectional(scratchDir)
        val amb = lighting.fillAmbient(scratchAmbient)
        val intensity = lighting.fillLightColor(scratchLightColor)
        backend.setLighting(
            scratchDir[0], scratchDir[1], scratchDir[2],
            scratchAmbient[0], scratchAmbient[1], scratchAmbient[2],
            scratchLightColor[0], scratchLightColor[1], scratchLightColor[2],
            intensity
        )

        // Grade
        backend.drawLines(gridHandle, gridVertexCount, viewProj, 0.30f, 0.32f, 0.36f, 1f)

        // Malha de teste (torus .KERN), girando com o tempo da engine
        val spin = Math.toRadians((engineTime * 18f).toDouble()).toFloat()
        torusRot.setFromAxisAngle(0f, 1f, 0f, spin)
        torusModel.setTRS(torusPos, torusRot, unitScale)
        geometry.submit(testMeshHandle, torusModel, 0.96f, 0.64f, 0.24f, 1f)

        // Cubo por Nick
        if (scene != null) {
            var top = 0
            for (root in scene.roots) {
                if (top >= nickStack.size) break
                nickStack[top++] = root
            }
            while (top > 0) {
                val nick = nickStack[--top] ?: continue
                if (nick.active && nick.visible) {
                    geometry.submit(
                        cubeHandle,
                        nick.transform.worldMatrix,
                        if (nick.id == selectedId) 1.0f else 0.82f,
                        if (nick.id == selectedId) 0.72f else 0.84f,
                        if (nick.id == selectedId) 0.18f else 0.88f,
                        1f
                    )
                }
                for (child in nick.children) {
                    if (top >= nickStack.size) break
                    nickStack[top++] = child
                }
            }
        }

        geometry.flush(backend, viewProj)
        backend.endFrame()
    }

    companion object {
        private val CUBE_POSITIONS = floatArrayOf(
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
            -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f,
            -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f
        )
        private val CUBE_NORMALS_PER_QUAD = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, -1f,
            0f, 1f, 0f,
            0f, -1f, 0f,
            1f, 0f, 0f,
            -1f, 0f, 0f
        )
    }
}
