package com.kenkern.engine.render

import android.util.Log
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
 *
 * ANTI-CRASH: [initialize] e [renderFrame] nunca lançam exceção — falhas de GPU
 * degradam o viewport (modo seguro) em vez de derrubar o app.
 */
class EngineRenderer {

    private val geometry: GeometryPipeline = DefaultGeometryPipeline()
    private val lighting: LightingPipeline = DefaultLightingPipeline()

    private var initialized = false
    private var gridHandle = 0
    private var gridVertexCount = 0
    private var cubeHandle = 0
    private var testMeshHandle = 0
    private var kernRoundtripOk = false

    // Estatísticas para a barra de status (zero alocação por frame).
    @Volatile
    var fps: Int = 0
        private set

    @Volatile
    var drawCount: Int = 0
        private set

    /** ms de update (timestep fixo + world transforms), média móvel EMA 0,9. */
    @Volatile
    var updateMs: Float = 0f
        private set

    /** ms de draw (beginFrame → endFrame), média móvel EMA 0,9. */
    @Volatile
    var drawMs: Float = 0f
        private set

    private var framesThisSecond = 0
    private var fpsWindowStartNanos = 0L

    /** Chamado pelo thread GL uma vez por frame (escrita volátil, sem alocação). */
    fun reportUpdateMs(measuredMs: Float) {
        updateMs = if (updateMs == 0f) measuredMs else updateMs * 0.9f + measuredMs * 0.1f
    }

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

    /** Inicialização segura: cria grade, cubo e malha .KERN; falha = viewport limpo. */
    fun initialize(backend: RenderBackend) {
        initialized = false
        gridHandle = 0
        cubeHandle = 0
        testMeshHandle = 0
        kernRoundtripOk = false
        if (!backend.initialize()) {
            Log.e(TAG, "Backend não inicializou; viewport em modo seguro")
            return
        }
        // Cada construção é independente: se uma falhar, o resto ainda desenha.
        runCatching { buildGrid(backend) }.onFailure { Log.w(TAG, "grid: ${it.message}") }
        runCatching { buildCube(backend) }.onFailure { Log.w(TAG, "cube: ${it.message}") }
        runCatching { buildTestMeshViaKern(backend) }.onFailure { Log.w(TAG, "kern: ${it.message}") }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

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
        updateFps(System.nanoTime())
        val drawStart = System.nanoTime()
        try {
            renderFrameInner(backend, scene, camera, engineTime, selectedId)
        } catch (e: Throwable) {
            // Última linha de defesa: nenhum erro de GL/frame derruba o processo.
            Log.e(TAG, "Frame descartado", e)
            runCatching { backend.beginFrame(0.075f, 0.075f, 0.105f, 1f) }
        }
        val drawElapsedMs = (System.nanoTime() - drawStart) * 1e-6f
        drawMs = if (drawMs == 0f) drawElapsedMs else drawMs * 0.9f + drawElapsedMs * 0.1f
    }

    private fun renderFrameInner(
        backend: RenderBackend,
        scene: Scene?,
        camera: OrbitCamera,
        engineTime: Float,
        selectedId: ReferenceId?
    ) {
        backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
        var gridDrawn = 0

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

        // Grade: 1 VBO estático (GL_STATIC_DRAW) criado uma vez em buildGrid,
        // desenhado com 1 único glDrawArrays(GL_LINES) por frame.
        if (gridHandle != 0) {
            backend.drawLines(gridHandle, gridVertexCount, viewProj, 0.30f, 0.32f, 0.36f, 1f)
            gridDrawn = 1
        }

        // Malha de teste (torus .KERN), girando com o tempo da engine
        if (testMeshHandle != 0) {
            val spin = Math.toRadians((engineTime * 18f).toDouble()).toFloat()
            torusRot.setFromAxisAngle(0f, 1f, 0f, spin)
            torusModel.setTRS(torusPos, torusRot, unitScale)
            geometry.submit(testMeshHandle, torusModel, 0.96f, 0.64f, 0.24f, 1f)
        }

        // Cubo por Nick (travessia indexada: Iterator de ArrayList aloca por frame)
        if (scene != null) {
            val roots = scene.roots
            var top = 0
            for (ri in roots.indices) {
                if (top >= nickStack.size) break
                nickStack[top++] = roots[ri]
            }
            while (top > 0) {
                val nick = nickStack[--top] ?: continue
                if (nick.active && nick.visible && cubeHandle != 0) {
                    geometry.submit(
                        cubeHandle,
                        nick.transform.worldMatrix,
                        if (nick.id == selectedId) 1.0f else 0.82f,
                        if (nick.id == selectedId) 0.72f else 0.84f,
                        if (nick.id == selectedId) 0.18f else 0.88f,
                        1f
                    )
                }
                val children = nick.children
                for (ci in children.indices) {
                    if (top >= nickStack.size) break
                    nickStack[top++] = children[ci]
                }
            }
        }

        geometry.flush(backend, viewProj)
        // Total real de draw calls do frame: grade (1 drawLines) + malhas do flush.
        drawCount = gridDrawn +
            ((geometry as? DefaultGeometryPipeline)?.lastFlushCount ?: 0)
        backend.endFrame()
    }

    private fun updateFps(nowNanos: Long) {
        framesThisSecond++
        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = nowNanos
            return
        }
        val elapsed = nowNanos - fpsWindowStartNanos
        if (elapsed >= 1_000_000_000L) {
            fps = (framesThisSecond * 1_000_000_000L / elapsed).toInt().coerceAtLeast(0)
            framesThisSecond = 0
            fpsWindowStartNanos = nowNanos
        }
    }

    companion object {
        private const val TAG = "Kenkern/EngineRenderer"
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
