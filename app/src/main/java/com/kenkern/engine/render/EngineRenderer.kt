package com.kenkern.engine.render

import android.util.Log
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.RotationFromMatrix
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.diag.KenkernLog
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderer do viewport da KENKERN:
 *  - céu (gradiente zênite→horizonte + sol discreto na direção da luz);
 *  - GRADE INFINITA (1 draw call): raio contra o plano Y=0 no fragment shader,
 *    anti-moiré por fwidth, fade por distância, dupla face (câmera abaixo do chão);
 *  - cubo por Nick ativo/visível (material #9A9A9A, Lambert + ambiente hemisférico);
 *  - contorno laranja #FFA940 (inverted hull) no Nick selecionado;
 *  - gizmo de eixos no canto superior direito, girando com a câmera;
 *  - gizmo de transformação como MALHA (alça sob o dedo amarela, demais apagados);
 *  - malha de teste (torus) gerada, salva em .KERN e recarregada (roundtrip).
 *
 * PERF: a fila de geometria é RESETADA a cada frame (sem isso ela acumula
 * submissões até saturar e redesenha a cena centenas de vezes por frame).
 * Orçamento: céu 1 + malhas N + contorno 1 + grade 1 + gizmo ≤ 2 + eixos 1.
 *
 * ANTI-CRASH: [initialize] e [renderFrame] nunca lançam exceção — falhas de GPU
 * degradam o viewport (modo seguro) em vez de derrubar o app.
 */
class EngineRenderer {

    private val geometry: GeometryPipeline = DefaultGeometryPipeline()
    private val lighting: LightingPipeline = DefaultLightingPipeline()

    private var initialized = false
    private var cubeHandle = 0
    private var testMeshHandle = 0
    private var gizmoHandle = 0
    private var gizmoVertexCount = 0
    private var kernRoundtripOk = false

    // Buffers GL do gizmo de transformação (MALHA de triângulos, 1 VBO por modo)
    private var tgTrisMove = 0
    private var tgTrisRotate = 0
    private var tgTrisScale = 0
    private var tGizmo: TransformGizmo? = null

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

    // Scratches do frame (pré-alocados; nada é criado no loop).
    private val scratchDir = FloatArray(3)
    private val scratchAmbient = FloatArray(3)
    private val scratchLightColor = FloatArray(3)
    private val camFwd = Vec3()
    private val camEye = Vec3()
    private val sunDir = Vec3()

    // Gizmo: rotação da view (translação zerada) × escala fixa.
    private val gizmoRot = Mat4()
    private val gizmoScale = Mat4()
    private val gizmoMvp = Mat4()

    // Contorno (inverted hull): world × inflação.
    private val outlineInflation = Mat4()
    private val outlineScratch = Mat4()

    // Gizmo de transformação (scratches do thread GL)
    private val tgWorldPos = Vec3()
    private val tgWorldRot = Quaternion()
    private val tgModel = Mat4()
    private val tgMvp = Mat4()
    private val tgIdentRot = Quaternion()
    private val tgScaleVec = Vec3()
    private val tgRotExtractor = RotationFromMatrix()

    private val nickStack = arrayOfNulls<Nick>(512)

    // Cores do editor (especificação de visual).
    private val materialGray = 0.6039f   // #9A9A9A
    private val outlineR = 1.0f
    private val outlineG = 0.6627f       // #FFA940
    private val outlineB = 0.2510f

    /** Inicialização segura: céu/grade infinita/cubo/gizmos/malha .KERN; falha = viewport limpo. */
    fun initialize(backend: RenderBackend, transformGizmo: TransformGizmo? = null) {
        initialized = false
        cubeHandle = 0
        testMeshHandle = 0
        gizmoHandle = 0
        tgTrisMove = 0; tgTrisRotate = 0; tgTrisScale = 0
        tGizmo = transformGizmo
        kernRoundtripOk = false
        outlineInflation.setIdentity()
        outlineInflation.m[0] = 1.06f; outlineInflation.m[5] = 1.06f; outlineInflation.m[10] = 1.06f
        gizmoScale.setIdentity()
        gizmoScale.m[0] = 0.62f; gizmoScale.m[5] = 0.62f; gizmoScale.m[10] = 0.62f
        if (!backend.initialize()) {
            Log.e(TAG, "Backend não inicializou; viewport em modo seguro")
            return
        }
        // Cada construção é independente: se uma falhar, o resto ainda desenha —
        // mas a falha É REGISTRADA no Console (regra: runCatching não engole).
        runCatching { buildCube(backend) }.onFailure { KenkernLog.w(TAG, "cube não construído (viewport segue sem cubo)", it) }
        runCatching { buildGizmo(backend) }.onFailure { KenkernLog.w(TAG, "gizmo de eixos não construído", it) }
        runCatching { buildTransformGizmoBuffers(backend) }.onFailure { KenkernLog.w(TAG, "buffers do gizmo de transformação não construídos", it) }
        runCatching { buildTestMeshViaKern(backend) }.onFailure { KenkernLog.w(TAG, "malha de teste .KERN não construída", it) }
        initialized = true
    }

    /** Upload ÚNICO dos buffers estáticos do gizmo de transformação (1 VBO de triângulos por modo). */
    private fun buildTransformGizmoBuffers(backend: RenderBackend) {
        val g = tGizmo ?: return
        if (g.moveData.tris.isNotEmpty()) tgTrisMove = backend.createGizmoBuffer(g.moveData.tris)
        if (g.rotateData.tris.isNotEmpty()) tgTrisRotate = backend.createGizmoBuffer(g.rotateData.tris)
        if (g.scaleData.tris.isNotEmpty()) tgTrisScale = backend.createGizmoBuffer(g.scaleData.tris)
    }

    fun isInitialized(): Boolean = initialized

    // ---------- Construção (uma vez, fora do loop) ----------

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

    /** Gizmo de eixos: 6 linhas (±X, ±Y, ±Z) coloridas por vértice — 1 VBO, 1 draw call. */
    private fun buildGizmo(backend: RenderBackend) {
        val data = FloatArray(12 * 2 * 6)
        var i = 0
        fun line(
            ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
            r: Float, g: Float, b: Float
        ) {
            data[i++] = ax; data[i++] = ay; data[i++] = az; data[i++] = r; data[i++] = g; data[i++] = b
            data[i++] = bx; data[i++] = by; data[i++] = bz; data[i++] = r; data[i++] = g; data[i++] = b
        }
        line(0f, 0f, 0f, 1f, 0f, 0f, 0.95f, 0.25f, 0.25f)   // +X vermelho
        line(0f, 0f, 0f, -1f, 0f, 0f, 0.40f, 0.12f, 0.12f)  // -X apagado
        line(0f, 0f, 0f, 0f, 1f, 0f, 0.30f, 0.90f, 0.35f)   // +Y verde
        line(0f, 0f, 0f, 0f, -1f, 0f, 0.12f, 0.34f, 0.14f)  // -Y apagado
        line(0f, 0f, 0f, 0f, 0f, 1f, 0.35f, 0.55f, 0.95f)   // +Z azul
        line(0f, 0f, 0f, 0f, 0f, -1f, 0.12f, 0.18f, 0.40f)  // -Z apagado
        gizmoVertexCount = data.size / 6
        gizmoHandle = backend.createColoredLineBuffer(data)
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
        selectedId: ReferenceId?,
        gizmoState: GizmoSharedState? = null
    ) {
        updateFps(System.nanoTime())
        val drawStart = System.nanoTime()
        try {
            renderFrameInner(backend, scene, camera, engineTime, selectedId, gizmoState)
        } catch (e: Throwable) {
            // Última linha de defesa: nenhum erro de GL/frame derruba o processo.
            Log.e(TAG, "Frame descartado", e)
            KenkernLog.e(TAG, "Frame descartado", e)
            runCatching { backend.beginFrame(0.075f, 0.075f, 0.105f, 1f) }
                .onFailure { KenkernLog.e(TAG, "clear emergencial do frame também falhou", it) }
        }
        val drawElapsedMs = (System.nanoTime() - drawStart) * 1e-6f
        drawMs = if (drawMs == 0f) drawElapsedMs else drawMs * 0.9f + drawElapsedMs * 0.1f
    }

    private fun renderFrameInner(
        backend: RenderBackend,
        scene: Scene?,
        camera: OrbitCamera,
        engineTime: Float,
        selectedId: ReferenceId?,
        gizmoState: GizmoSharedState?
    ) {
        // CRÍTICO: zera a fila de submissões — sem isso ela acumula entre frames
        // até saturar a capacidade e redesenhar a cena centenas de vezes.
        geometry.reset()

        backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
        var skyDrawn = 0
        var gridDrawn = 0
        var gizmoDrawn = 0

        val aspect = backend.viewportWidth.toFloat() / backend.viewportHeight.toFloat()
        camera.projection(proj, aspect)
        camera.view(view)
        viewProj.multiply(proj, view)

        // Base da câmera derivada da matriz de view (linhas = eixos da câmera em mundo).
        camFwd.set(-view.m[2], -view.m[6], -view.m[10])
        // Olho correto nos DOIS modos (orbital e voo).
        camera.eyePoint(camEye)

        // Sol = direção PARA a luz (oposta ao sentido de viagem da luz direcional).
        lighting.fillDirectional(scratchDir)
        lighting.fillAmbient(scratchAmbient)
        val intensity = lighting.fillLightColor(scratchLightColor)
        sunDir.set(-scratchDir[0], -scratchDir[1], -scratchDir[2]).normalize()

        // Céu (1 draw call): gradiente zênite→horizonte + sol discreto.
        if (sunDir.lengthSquared() > 0.5f) {
            backend.drawSky(
                camFwd.x, camFwd.y, camFwd.z,
                view.m[0], view.m[4], view.m[8],
                view.m[1], view.m[5], view.m[9],
                tanHalfFov(camera.fovDegrees), aspect,
                sunDir.x, sunDir.y, sunDir.z
            )
            skyDrawn = 1
        }

        backend.setLighting(
            scratchDir[0], scratchDir[1], scratchDir[2],
            scratchAmbient[0], scratchAmbient[1], scratchAmbient[2],
            scratchLightColor[0], scratchLightColor[1], scratchLightColor[2],
            intensity
        )

        // Malha de teste (torus .KERN), girando com o tempo da engine
        if (testMeshHandle != 0) {
            val spin = Math.toRadians((engineTime * 18f).toDouble()).toFloat()
            torusRot.setFromAxisAngle(0f, 1f, 0f, spin)
            torusModel.setTRS(torusPos, torusRot, unitScale)
            geometry.submit(testMeshHandle, torusModel, materialGray, materialGray, materialGray, 1f)
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
                        materialGray, materialGray, materialGray, 1f
                    )
                    if (nick.id == selectedId) {
                        // Contorno #FFA940 (inverted hull): world × inflação 1.06.
                        outlineScratch.multiply(nick.transform.worldMatrix, outlineInflation)
                        geometry.submitOutline(
                            cubeHandle, outlineScratch, outlineR, outlineG, outlineB, 1f
                        )
                    }
                }
                val children = nick.children
                for (ci in children.indices) {
                    if (top >= nickStack.size) break
                    nickStack[top++] = children[ci]
                }
            }
        }

        geometry.flush(backend, viewProj)

        // GRADE INFINITA (1 draw call): depois das malhas (elas a ocluem via depth),
        // raio contra o plano Y=0 no fragment; dupla face; fade por fwidth + distância.
        backend.drawInfiniteGrid(
            camFwd.x, camFwd.y, camFwd.z,
            view.m[0], view.m[4], view.m[8],
            view.m[1], view.m[5], view.m[9],
            tanHalfFov(camera.fovDegrees), aspect,
            camEye.x, camEye.y, camEye.z
        )
        gridDrawn = 1

        // Gizmo de transformação como MALHA: ≤ 2 dc (completo / apagado + alça amarela).
        var tGizmoDc = 0
        val g = tGizmo
        if (g != null && gizmoState != null && selectedId != null && scene != null) {
            val nick = scene.findNick(selectedId)
            if (nick != null && nick.active && nick.visible) {
                val wm = nick.transform.worldMatrix
                tgWorldPos.set(wm.m[12], wm.m[13], wm.m[14])
                val ss = g.screenScaleFor(camera, tgWorldPos)
                extractWorldRotation(wm, tgWorldRot)
                if (gizmoState.space == GizmoSpace.LOCAL) {
                    tgScaleVec.set(ss, ss, ss)
                    tgModel.setTRS(tgWorldPos, tgWorldRot, tgScaleVec)
                } else {
                    tgIdentRot.setIdentity()
                    tgScaleVec.set(ss, ss, ss)
                    tgModel.setTRS(tgWorldPos, tgIdentRot, tgScaleVec)
                }
                tgMvp.multiply(viewProj, tgModel)

                val data = g.dataFor(gizmoState.mode)
                val trisHandle = when (gizmoState.mode) {
                    GizmoMode.MOVE -> tgTrisMove
                    GizmoMode.ROTATE -> tgTrisRotate
                    GizmoMode.SCALE -> tgTrisScale
                }
                val total = data.tris.size / 7
                if (trisHandle != 0 && total > 0) {
                    val hl = gizmoState.highlighted
                    val range = if (hl != GizmoHandle.NONE) data.triRanges[hl] else null
                    if (range != null && range[1] > 0) {
                        // demais eixos APAGADOS + alça sob o dedo em AMARELO
                        backend.drawGizmoTrisDimmed(trisHandle, 0, total, tgMvp, 0.35f)
                        tGizmoDc++
                        backend.drawGizmoTrisSolid(
                            trisHandle, range[0], range[1], tgMvp, 1f, 0.85f, 0.10f, 1f
                        )
                        tGizmoDc++
                    } else {
                        backend.drawGizmoTris(trisHandle, 0, total, tgMvp)
                        tGizmoDc++
                    }
                }
            }
        }

        // Gizmo de eixos (1 draw call): rotação da view × escala, canto superior direito.
        if (gizmoHandle != 0) {
            gizmoRot.copyFrom(view)
            gizmoRot.m[12] = 0f; gizmoRot.m[13] = 0f; gizmoRot.m[14] = 0f
            gizmoMvp.multiply(gizmoScale, gizmoRot)
            backend.drawGizmo(gizmoHandle, gizmoVertexCount, gizmoMvp)
            gizmoDrawn = 1
        }

        backend.endFrame()

        // Total real de draw calls do frame (estatística da StatusBar).
        drawCount = skyDrawn + gridDrawn +
            ((geometry as? DefaultGeometryPipeline)?.lastFlushCount ?: 0) +
            gizmoDrawn + tGizmoDc
    }

    private fun extractWorldRotation(m: Mat4, out: Quaternion) {
        val mm = m.m
        val sx = kotlin.math.sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
        val sy = kotlin.math.sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
        val sz = kotlin.math.sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            tgRotExtractor.set(
                mm[0] / sx, mm[4] / sy, mm[8] / sz,
                mm[1] / sx, mm[5] / sy, mm[9] / sz,
                mm[2] / sx, mm[6] / sy, mm[10] / sz
            )
            tgRotExtractor.toMatrixComponents(out)
        } else {
            out.setIdentity()
        }
    }

    private fun tanHalfFov(fovDegrees: Float): Float {
        val half = Math.toRadians(fovDegrees.toDouble()).toFloat() * 0.5f
        return kotlin.math.tan(half)
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
