package com.kenkern.engine.render

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Modo atual do gizmo de transformação. */
enum class GizmoMode { MOVE, ROTATE, SCALE }

/** Espaço de referência das alças. */
enum class GizmoSpace { LOCAL, GLOBAL }

/** Alças do gizmo (hit-test e destaque). */
enum class GizmoHandle {
    NONE,
    TX, TY, TZ,          // mover: eixos
    TXY, TXZ, TYZ,       // mover: planos
    RX, RY, RZ,          // girar: anéis
    SX, SY, SZ, SXYZ     // escalar: hastes + uniforme
}

/** Estado compartilhado UI (escreve) <-> GL (lê). Campos voláteis, zero alocação. */
class GizmoSharedState {
    @Volatile var mode: GizmoMode = GizmoMode.MOVE
    @Volatile var space: GizmoSpace = GizmoSpace.GLOBAL
    @Volatile var highlighted: GizmoHandle = GizmoHandle.NONE
    @Volatile var snapEnabled: Boolean = false
}

/** Snap do editor: 0,25 m de posição, 15° de rotação, 0,1 de escala. */
object GizmoSnap {
    const val MOVE = 0.25f
    const val ROTATE_DEG = 15f
    const val SCALE = 0.1f
    const val MIN_SCALE = 0.01f
}

/** Dados estáticos de um modo: buffers de vértices + ranges por alça + alvos de hit-test. */
class GizmoModeData(
    val lines: FloatArray,                          // [x,y,z,r,g,b,a] por vértice (GL_LINES)
    val tris: FloatArray,                           // idem (GL_TRIANGLES; vazio em ROTATE/SCALE)
    val lineRanges: HashMap<GizmoHandle, IntArray>, // alça -> [offset, count] (vértices)
    val triRanges: HashMap<GizmoHandle, IntArray>,
    val hitSegments: HashMap<GizmoHandle, FloatArray>, // alça -> [x1,y1,z1,x2,y2,z2, ...]
    val hitQuads: HashMap<GizmoHandle, FloatArray>     // alça -> 4 cantos [x,y,z] x4
)

/**
 * GIZMO DE TRANSFORMAÇÃO da KENKERN (Mover / Girar / Escalar).
 *
 *  - Geometria construída UMA vez (o renderer envia à GPU no init).
 *  - Tamanho constante na tela: escala do modelo = distância da câmera × fator.
 *  - Hit-test em ESPAÇO DE TELA (distância ponto-segmento projetada); raio de
 *    toque mínimo de 24px (~48dp de área por alça).
 *  - Arrasto: eixo/plano restringem o movimento (ponto mais próximo raio-reta
 *    ou raio-plano), rotação por QUATERNION em torno do eixo, escala por haste
 *    ou uniforme (cubo central).
 *  - ZERO alocação por evento: todos os temporários são campos pré-alocados.
 *
 * Cores: X #E5534B, Y #57AB5A, Z #539BF5; alça sob o dedo: amarelo (GL).
 */
class TransformGizmo {

    val moveData: GizmoModeData = buildMoveData()
    val rotateData: GizmoModeData = buildRotateData()
    val scaleData: GizmoModeData = buildScaleData()

    fun dataFor(mode: GizmoMode): GizmoModeData = when (mode) {
        GizmoMode.MOVE -> moveData
        GizmoMode.ROTATE -> rotateData
        GizmoMode.SCALE -> scaleData
    }

    // ---------- Sessão de arrasto (instância única, pré-alocada) ----------
    private var dragHandle: GizmoHandle = GizmoHandle.NONE
    private var dragNick: Nick? = null
    private var startSnapshot: TransformSnapshot? = null
    private var dragScreenScale = 1f
    private val startWorldPos = Vec3()
    private val axisWorld = Vec3()
    private val planeNormalW = Vec3()
    private val startHitPoint = Vec3()
    private var startAxisS = 0f
    private var startScreenAngle = 0f
    private var rotateSign = 1f
    private var startTouchDist = 0f
    private val parentRot = Quaternion()
    private val parentRotInv = Quaternion()
    private var parentMatrix: Mat4? = null
    private val rotExtractor = com.kenkern.engine.core.RotationFromMatrix()

    // Scratches (zero alocação por evento)
    private val sModel = Mat4()
    private val sScaleVec = Vec3()
    private val sIdentRot = Quaternion()
    private val sWorldRot = Quaternion()
    private val sRayOrigin = Vec3()
    private val sRayDir = Vec3()
    private val sTmpA = Vec3()
    private val sTmpB = Vec3()
    private val sQa = Quaternion()
    private val sQb = Quaternion()
    private val sQc = Quaternion()
    private val sEye = Vec3()
    private val sFwd = Vec3()
    private val sLocalTarget = Vec3()
    private val sQuadX = FloatArray(4)
    private val sQuadY = FloatArray(4)

    /** Escala do gizmo em unidades de mundo para tamanho CONSTANTE na tela. */
    fun screenScaleFor(camera: OrbitCamera, worldPos: Vec3): Float {
        camera.eyePoint(sEye)
        val d = sEye.distanceTo(worldPos).coerceAtLeast(0.5f)
        val halfFovRad = Math.toRadians((camera.fovDegrees * 0.5f).toDouble())
        return (d * kotlin.math.tan(halfFovRad) * 2.0 * SCREEN_FRACTION).toFloat()
    }

    /**
     * HIT-TEST: alça mais próxima do toque em espaço de tela.
     * [touchRadiusPx] = raio de toque (>= 24px => ~48dp de área).
     */
    fun hitTest(
        touchX: Float, touchY: Float,
        viewProj: Mat4, viewportW: Float, viewportH: Float,
        worldPos: Vec3, worldRot: Quaternion, screenScale: Float,
        space: GizmoSpace, mode: GizmoMode,
        touchRadiusPx: Float
    ): GizmoHandle {
        val data = dataFor(mode)
        buildModel(worldPos, worldRot, screenScale, space, sModel)

        var best = GizmoHandle.NONE
        var bestDist = touchRadiusPx

        // Segmentos (eixos, anéis, hastes, cubos)
        for ((handle, segs) in data.hitSegments) {
            var minDist = Float.MAX_VALUE
            var i = 0
            while (i < segs.size) {
                projectToScreen(sModel, segs[i], segs[i + 1], segs[i + 2], viewportW, viewportH, sQuadX, sQuadY, 0)
                projectToScreen(sModel, segs[i + 3], segs[i + 4], segs[i + 5], viewportW, viewportH, sQuadX, sQuadY, 1)
                val d = distPointSegment(touchX, touchY, sQuadX[0], sQuadY[0], sQuadX[1], sQuadY[1])
                if (d < minDist) minDist = d
                i += 6
            }
            // Eixos ganham prioridade sobre planos/cubos sob o dedo.
            val biased = if (isAxisHandle(handle)) minDist * 0.7f else minDist
            if (biased < bestDist) {
                bestDist = biased
                best = handle
            }
        }

        // Planos de movimento (quad projetado): dentro do polígono = acerto.
        for ((handle, quad) in data.hitQuads) {
            for (c in 0 until 4) {
                projectToScreen(
                    sModel, quad[c * 3], quad[c * 3 + 1], quad[c * 3 + 2],
                    viewportW, viewportH, sQuadX, sQuadY, c
                )
            }
            var edgeDist = Float.MAX_VALUE
            var inside = true
            var sign = 0f
            for (e in 0 until 4) {
                val a = e
                val bIdx = (e + 1) % 4
                edgeDist = minOf(
                    edgeDist,
                    distPointSegment(touchX, touchY, sQuadX[a], sQuadY[a], sQuadX[bIdx], sQuadY[bIdx])
                )
                // teste de meio-plano consistente (quad convexo projetado)
                val ex = sQuadX[bIdx] - sQuadX[a]
                val ey = sQuadY[bIdx] - sQuadY[a]
                val cross = ex * (touchY - sQuadY[a]) - ey * (touchX - sQuadX[a])
                if (cross != 0f) {
                    val s = if (cross > 0f) 1f else -1f
                    if (sign == 0f) sign = s else if (s != sign) inside = false
                }
            }
            val d = if (inside) edgeDist * 0.5f else edgeDist
            if (d < bestDist) {
                bestDist = d
                best = handle
            }
        }
        return best
    }

    /**
     * Inicia o arrasto de [handle] sobre [nick]: captura snapshot inicial e os
     * parâmetros geométricos do gesto (eixo, plano, anel).
     */
    fun beginDrag(
        nick: Nick, handle: GizmoHandle,
        touchX: Float, touchY: Float,
        camera: OrbitCamera, viewProj: Mat4, viewportW: Float, viewportH: Float, aspect: Float,
        space: GizmoSpace, screenScale: Float
    ) {
        dragNick = nick
        dragHandle = handle
        dragScreenScale = screenScale.coerceAtLeast(1e-4f)
        startSnapshot = nick.transform.snapshot()
        val wm = nick.transform.worldMatrix
        startWorldPos.set(wm.m[12], wm.m[13], wm.m[14])
        extractWorldRotation(wm, sWorldRot)

        // Pai (para converter mundo <-> local)
        val parent = nick.parent
        parentMatrix = parent?.transform?.worldMatrix
        if (parent != null) {
            extractWorldRotation(parent.transform.worldMatrix, parentRot)
        } else {
            parentRot.setIdentity()
        }
        // conjugado (inverso p/ rotação unitária), sem depender de Core
        parentRotInv.set(-parentRot.x, -parentRot.y, -parentRot.z, parentRot.w)

        val localRot = nick.transform.rotation
        camera.forwardVector(sFwd)
        val ndcX = (touchX / viewportW) * 2f - 1f
        val ndcY = 1f - (touchY / viewportH) * 2f

        when (dragHandle) {
            GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                startAxisS = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
            }
            GizmoHandle.TXY, GizmoHandle.TXZ, GizmoHandle.TYZ -> {
                when (dragHandle) {
                    GizmoHandle.TXY -> planeNormalW.set(0f, 0f, 1f)
                    GizmoHandle.TXZ -> planeNormalW.set(0f, 1f, 0f)
                    else -> planeNormalW.set(1f, 0f, 0f)
                }
                if (space == GizmoSpace.LOCAL) {
                    rotateVecByQuaternion(planeNormalW, localRot, sTmpA)
                    planeNormalW.set(sTmpA)
                }
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                rayPlanePoint(sRayOrigin, sRayDir, startWorldPos, planeNormalW, startHitPoint)
            }
            GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                // sinal: eixo apontando para a câmera => rotação positiva CCW na tela
                rotateSign = if (axisWorld.dot(sFwd) < 0f) 1f else -1f
                projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                startScreenAngle = screenAngle(touchX, touchY, sQuadX[0], sQuadY[0])
            }
            GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                startAxisS = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
            }
            GizmoHandle.SXYZ -> {
                projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                startTouchDist = dist2D(touchX, touchY, sQuadX[0], sQuadY[0]).coerceAtLeast(1f)
            }
            GizmoHandle.NONE -> Unit
        }
    }

    /** Atualiza o transform do Nick durante o arrasto (por evento de movimento). */
    fun updateDrag(
        touchX: Float, touchY: Float,
        camera: OrbitCamera, viewProj: Mat4, viewportW: Float, viewportH: Float, aspect: Float,
        space: GizmoSpace, snapEnabled: Boolean
    ) {
        val nick = dragNick ?: return
        val start = startSnapshot ?: return
        val ndcX = (touchX / viewportW) * 2f - 1f
        val ndcY = 1f - (touchY / viewportH) * 2f

        when (dragHandle) {
            GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                val s = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
                sTmpA.set(startWorldPos).addScaled(axisWorld, s - startAxisS)
                worldToLocalPos(sTmpA, sLocalTarget)
                if (snapEnabled) snapVec(sLocalTarget, GizmoSnap.MOVE)
                nick.transform.position.set(sLocalTarget)
            }
            GizmoHandle.TXY, GizmoHandle.TXZ, GizmoHandle.TYZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                if (!rayPlanePoint(sRayOrigin, sRayDir, startWorldPos, planeNormalW, sTmpB)) return
                sTmpA.set(startWorldPos).add(sTmpB.sub(startHitPoint))
                worldToLocalPos(sTmpA, sLocalTarget)
                if (snapEnabled) snapVec(sLocalTarget, GizmoSnap.MOVE)
                nick.transform.position.set(sLocalTarget)
            }
            GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ -> {
                projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                var deltaDeg = Math.toDegrees(
                    (screenAngle(touchX, touchY, sQuadX[0], sQuadY[0]) - startScreenAngle).toDouble()
                ).toFloat()
                deltaDeg = wrapDegrees(deltaDeg) * rotateSign
                if (snapEnabled) {
                    deltaDeg = Math.round(deltaDeg / GizmoSnap.ROTATE_DEG).toFloat() * GizmoSnap.ROTATE_DEG
                }
                val rad = Math.toRadians(deltaDeg.toDouble()).toFloat()
                if (space == GizmoSpace.LOCAL) {
                    // giro em torno do eixo LOCAL: pós-multiplicar L' = L * q(eixoLocal)
                    when (dragHandle) {
                        GizmoHandle.RX -> sQb.setFromAxisAngle(1f, 0f, 0f, rad)
                        GizmoHandle.RY -> sQb.setFromAxisAngle(0f, 1f, 0f, rad)
                        else -> sQb.setFromAxisAngle(0f, 0f, 1f, rad)
                    }
                    nick.transform.rotation.set(start.qx, start.qy, start.qz, start.qw).mul(sQb).normalize()
                } else {
                    // GLOBAL: world' = qΔ * (P * L); local' = P⁻¹ * world'
                    sQa.setFromAxisAngle(axisWorld.x, axisWorld.y, axisWorld.z, rad)
                    sQb.set(parentRot.x, parentRot.y, parentRot.z, parentRot.w)
                    sQc.set(start.qx, start.qy, start.qz, start.qw)
                    sQb.mul(sQc)
                    sQa.mul(sQb)
                    sQa.mul(parentRotInv)
                    sQa.normalize()
                    nick.transform.rotation.set(sQa.x, sQa.y, sQa.z, sQa.w)
                }
            }
            GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                val s = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
                var factor = 1f + (s - startAxisS) / dragScreenScale
                if (factor < 0.01f) factor = 0.01f
                applyAxisScale(nick, start, factor, snapEnabled, uniform = false)
            }
            GizmoHandle.SXYZ -> {
                projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                val d = dist2D(touchX, touchY, sQuadX[0], sQuadY[0]).coerceAtLeast(1f)
                var factor = d / startTouchDist
                if (factor < 0.01f) factor = 0.01f
                applyAxisScale(nick, start, factor, snapEnabled, uniform = true)
            }
            GizmoHandle.NONE -> Unit
        }
    }

    /** Encerra o arrasto; retorna o snapshot inicial (para o Command de undo). */
    fun endDrag(): TransformSnapshot? {
        val start = startSnapshot
        dragNick = null
        dragHandle = GizmoHandle.NONE
        startSnapshot = null
        return start
    }

    val isDragging: Boolean get() = dragNick != null

    // ---------- Geometria estática (init-only; pode alocar) ----------

    private class Builder {
        val out = ArrayList<Float>(512)
        fun v(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float) {
            out.add(x); out.add(y); out.add(z); out.add(r); out.add(g); out.add(b); out.add(a)
        }
        fun seg(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                r: Float, g: Float, b: Float, a: Float = 1f) {
            v(x1, y1, z1, r, g, b, a); v(x2, y2, z2, r, g, b, a)
        }
        fun tri(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                x3: Float, y3: Float, z3: Float, r: Float, g: Float, b: Float, a: Float) {
            v(x1, y1, z1, r, g, b, a); v(x2, y2, z2, r, g, b, a); v(x3, y3, z3, r, g, b, a)
        }
        fun quad(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                 x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float,
                 r: Float, g: Float, b: Float, a: Float) {
            tri(x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a)
            tri(x1, y1, z1, x3, y3, z3, x4, y4, z4, r, g, b, a)
        }
        fun vertexCount(): Int = out.size / 7
        fun toFloatArray(): FloatArray = out.toFloatArray()
    }

    private fun axisColor(axis: Int): FloatArray = when (axis) {
        0 -> floatArrayOf(0.898f, 0.325f, 0.294f)    // X #E5534B
        1 -> floatArrayOf(0.341f, 0.671f, 0.353f)    // Y #57AB5A
        else -> floatArrayOf(0.325f, 0.608f, 0.961f) // Z #539BF5
    }

    private fun buildMoveData(): GizmoModeData {
        val b = Builder()
        val lineRanges = HashMap<GizmoHandle, IntArray>()
        val triRanges = HashMap<GizmoHandle, IntArray>()
        val hitSegments = HashMap<GizmoHandle, FloatArray>()
        val hitQuads = HashMap<GizmoHandle, FloatArray>()
        val planeColor = floatArrayOf(0.78f, 0.78f, 0.78f)

        var start = 0
        writeArrow(b, 0)
        lineRanges[GizmoHandle.TX] = intArrayOf(start, b.vertexCount() - start)
        hitSegments[GizmoHandle.TX] = arrowHitSegments(0)
        start = b.vertexCount()
        writeArrow(b, 1)
        lineRanges[GizmoHandle.TY] = intArrayOf(start, b.vertexCount() - start)
        hitSegments[GizmoHandle.TY] = arrowHitSegments(1)
        start = b.vertexCount()
        writeArrow(b, 2)
        lineRanges[GizmoHandle.TZ] = intArrayOf(start, b.vertexCount() - start)
        hitSegments[GizmoHandle.TZ] = arrowHitSegments(2)

        start = b.vertexCount()
        writePlaneOutline(b, 0, 1, planeColor) // XY
        lineRanges[GizmoHandle.TXY] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TXY] = planeCorners(0, 1)
        start = b.vertexCount()
        writePlaneOutline(b, 0, 2, planeColor) // XZ
        lineRanges[GizmoHandle.TXZ] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TXZ] = planeCorners(0, 2)
        start = b.vertexCount()
        writePlaneOutline(b, 1, 2, planeColor) // YZ
        lineRanges[GizmoHandle.TYZ] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TYZ] = planeCorners(1, 2)

        val tb = Builder()
        start = 0
        writePlaneFill(tb, 0, 1, planeColor, 0.22f)
        triRanges[GizmoHandle.TXY] = intArrayOf(start, tb.vertexCount() - start)
        start = tb.vertexCount()
        writePlaneFill(tb, 0, 2, planeColor, 0.22f)
        triRanges[GizmoHandle.TXZ] = intArrayOf(start, tb.vertexCount() - start)
        start = tb.vertexCount()
        writePlaneFill(tb, 1, 2, planeColor, 0.22f)
        triRanges[GizmoHandle.TYZ] = intArrayOf(start, tb.vertexCount() - start)

        return GizmoModeData(b.toFloatArray(), tb.toFloatArray(), lineRanges, triRanges, hitSegments, hitQuads)
    }

    private fun buildRotateData(): GizmoModeData {
        val b = Builder()
        val ranges = HashMap<GizmoHandle, IntArray>()
        val hit = HashMap<GizmoHandle, FloatArray>()
        val segs = 48
        val handles = arrayOf(GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ)
        for (axis in 0 until 3) {
            val c = axisColor(axis)
            val start = b.vertexCount()
            val hs = FloatArray(segs * 6)
            for (i in 0 until segs) {
                val a0 = (i.toFloat() / segs) * 2f * Math.PI.toFloat()
                val a1 = ((i + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
                val p0 = ringPoint(axis, a0)
                val p1 = ringPoint(axis, a1)
                b.seg(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], c[0], c[1], c[2])
                var k = i * 6
                hs[k++] = p0[0]; hs[k++] = p0[1]; hs[k++] = p0[2]
                hs[k++] = p1[0]; hs[k++] = p1[1]; hs[k] = p1[2]
            }
            ranges[handles[axis]] = intArrayOf(start, b.vertexCount() - start)
            hit[handles[axis]] = hs
        }
        return GizmoModeData(b.toFloatArray(), FloatArray(0), ranges, HashMap(), hit, HashMap())
    }

    private fun buildScaleData(): GizmoModeData {
        val b = Builder()
        val ranges = HashMap<GizmoHandle, IntArray>()
        val hit = HashMap<GizmoHandle, FloatArray>()

        for (axis in 0 until 3) {
            val c = axisColor(axis)
            val u = unitVec(axis)
            val start = b.vertexCount()
            b.seg(0f, 0f, 0f, u[0] * 0.9f, u[1] * 0.9f, u[2] * 0.9f, c[0], c[1], c[2])
            writeBoxOutline(b, u[0], u[1], u[2], 0.09f, c[0], c[1], c[2])
            ranges[axisScaleHandle(axis)] = intArrayOf(start, b.vertexCount() - start)
            val hs = FloatArray(6 + 12 * 6)
            hs[0] = 0f; hs[1] = 0f; hs[2] = 0f
            hs[3] = u[0] * 0.9f; hs[4] = u[1] * 0.9f; hs[5] = u[2] * 0.9f
            writeBoxHitSegments(hs, 6, u[0], u[1], u[2], 0.09f)
            hit[axisScaleHandle(axis)] = hs
        }
        val start = b.vertexCount()
        writeBoxOutline(b, 0f, 0f, 0f, 0.13f, 0.85f, 0.85f, 0.85f)
        ranges[GizmoHandle.SXYZ] = intArrayOf(start, b.vertexCount() - start)
        val hs = FloatArray(12 * 6)
        writeBoxHitSegments(hs, 0, 0f, 0f, 0f, 0.13f)
        hit[GizmoHandle.SXYZ] = hs

        return GizmoModeData(b.toFloatArray(), FloatArray(0), ranges, HashMap(), hit, HashMap())
    }

    /** Seta de movimento em torno do eixo [axis] (0=X,1=Y,2=Z): haste + ponta. */
    private fun writeArrow(b: Builder, axis: Int) {
        val c = axisColor(axis)
        val u = unitVec(axis)
        val p = perpVec(axis)
        val q = crossVec(u, p)
        b.seg(0f, 0f, 0f, u[0] * 0.82f, u[1] * 0.82f, u[2] * 0.82f, c[0], c[1], c[2])
        for (s in intArrayOf(-1, 1)) {
            b.seg(u[0], u[1], u[2],
                u[0] * 0.86f + p[0] * 0.055f * s, u[1] * 0.86f + p[1] * 0.055f * s, u[2] * 0.86f + p[2] * 0.055f * s,
                c[0], c[1], c[2])
            b.seg(u[0], u[1], u[2],
                u[0] * 0.86f + q[0] * 0.055f * s, u[1] * 0.86f + q[1] * 0.055f * s, u[2] * 0.86f + q[2] * 0.055f * s,
                c[0], c[1], c[2])
        }
    }

    private fun writePlaneOutline(b: Builder, axisA: Int, axisB: Int, c: FloatArray) {
        val lo = 0.25f; val hi = 0.65f
        val p1 = mixPlane(axisA, axisB, lo, lo)
        val p2 = mixPlane(axisA, axisB, hi, lo)
        val p3 = mixPlane(axisA, axisB, hi, hi)
        val p4 = mixPlane(axisA, axisB, lo, hi)
        b.seg(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], c[0], c[1], c[2], 0.8f)
        b.seg(p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], c[0], c[1], c[2], 0.8f)
        b.seg(p3[0], p3[1], p3[2], p4[0], p4[1], p4[2], c[0], c[1], c[2], 0.8f)
        b.seg(p4[0], p4[1], p4[2], p1[0], p1[1], p1[2], c[0], c[1], c[2], 0.8f)
    }

    private fun writePlaneFill(b: Builder, axisA: Int, axisB: Int, c: FloatArray, alpha: Float) {
        val lo = 0.25f; val hi = 0.65f
        val p1 = mixPlane(axisA, axisB, lo, lo)
        val p2 = mixPlane(axisA, axisB, hi, lo)
        val p3 = mixPlane(axisA, axisB, hi, hi)
        val p4 = mixPlane(axisA, axisB, lo, hi)
        b.quad(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], p4[0], p4[1], p4[2],
            c[0], c[1], c[2], alpha)
    }

    /** Box outline (12 arestas) centrado em (cx,cy,cz) com meia-aresta [half]. */
    private fun writeBoxOutline(b: Builder, cx: Float, cy: Float, cz: Float, half: Float, r: Float, g: Float, bl: Float) {
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            val x = cx + sx * half; val y = cy + sy * half; val z = cz + sz * half
            if (sx == -1) b.seg(x, y, z, cx + half, y, z, r, g, bl)
            if (sy == -1) b.seg(x, y, z, x, cy + half, z, r, g, bl)
            if (sz == -1) b.seg(x, y, z, x, y, cz + half, r, g, bl)
        }
    }

    /** Escreve 12 arestas de box em FloatArray de hit-test a partir de [at]. */
    private fun writeBoxHitSegments(hs: FloatArray, at: Int, cx: Float, cy: Float, cz: Float, half: Float) {
        var k = at
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            val x = cx + sx * half; val y = cy + sy * half; val z = cz + sz * half
            if (sx == -1) {
                hs[k++] = x; hs[k++] = y; hs[k++] = z
                hs[k++] = cx + half; hs[k++] = y; hs[k++] = z
            }
            if (sy == -1) {
                hs[k++] = x; hs[k++] = y; hs[k++] = z
                hs[k++] = x; hs[k++] = cy + half; hs[k++] = z
            }
            if (sz == -1) {
                hs[k++] = x; hs[k++] = y; hs[k++] = z
                hs[k++] = x; hs[k++] = y; hs[k++] = cz + half
            }
        }
    }

    private fun arrowHitSegments(axis: Int): FloatArray {
        val u = unitVec(axis)
        val p = perpVec(axis)
        val q = crossVec(u, p)
        val hs = FloatArray(5 * 6)
        var k = 0
        hs[k++] = 0f; hs[k++] = 0f; hs[k++] = 0f
        hs[k++] = u[0]; hs[k++] = u[1]; hs[k++] = u[2]
        for (s in intArrayOf(-1, 1)) {
            hs[k++] = u[0]; hs[k++] = u[1]; hs[k++] = u[2]
            hs[k++] = u[0] * 0.86f + p[0] * 0.055f * s; hs[k++] = u[1] * 0.86f + p[1] * 0.055f * s; hs[k++] = u[2] * 0.86f + p[2] * 0.055f * s
            hs[k++] = u[0]; hs[k++] = u[1]; hs[k++] = u[2]
            hs[k++] = u[0] * 0.86f + q[0] * 0.055f * s; hs[k++] = u[1] * 0.86f + q[1] * 0.055f * s; hs[k++] = u[2] * 0.86f + q[2] * 0.055f * s
        }
        return hs
    }

    // ---------- Matemática (in-place / estáticos; zero alocação por evento) ----------

    private fun projectToScreen(
        m: Mat4, x: Float, y: Float, z: Float,
        w: Float, h: Float, outX: FloatArray, outY: FloatArray, idx: Int
    ) {
        val cw = m.m[3] * x + m.m[7] * y + m.m[11] * z + m.m[15]
        if (abs(cw) < 1e-6f) {
            outX[idx] = -1e6f; outY[idx] = -1e6f
            return
        }
        val inv = 1f / cw
        val ndcX = (m.m[0] * x + m.m[4] * y + m.m[8] * z + m.m[12]) * inv
        val ndcY = (m.m[1] * x + m.m[5] * y + m.m[9] * z + m.m[13]) * inv
        outX[idx] = (ndcX * 0.5f + 0.5f) * w
        outY[idx] = (1f - (ndcY * 0.5f + 0.5f)) * h
    }

    private fun distPointSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val lenSq = abx * abx + aby * aby
        val t = if (lenSq < 1e-9f) 0f else ((apx * abx + apy * aby) / lenSq).coerceIn(0f, 1f)
        val dx = apx - abx * t; val dy = apy - aby * t
        return sqrt(dx * dx + dy * dy)
    }

    private fun dist2D(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    /** Ângulo do toque em torno do centro (tela Y invertido => matemático y-up). */
    private fun screenAngle(x: Float, y: Float, cx: Float, cy: Float): Float =
        atan2(-(y - cy), x - cx)

    private fun wrapDegrees(d: Float): Float {
        var v = d
        while (v > 180f) v -= 360f
        while (v < -180f) v += 360f
        return v
    }

    /** Parâmetro s do ponto mais próximo entre raio (O,D) e reta (C,A). */
    private fun rayAxisParam(o: Vec3, d: Vec3, c: Vec3, a: Vec3): Float {
        val wx = o.x - c.x; val wy = o.y - c.y; val wz = o.z - c.z
        val aa = d.x * d.x + d.y * d.y + d.z * d.z
        val bb = d.x * a.x + d.y * a.y + d.z * a.z
        val cc = a.x * a.x + a.y * a.y + a.z * a.z
        val dd = d.x * wx + d.y * wy + d.z * wz
        val ee = a.x * wx + a.y * wy + a.z * wz
        val denom = aa * cc - bb * bb
        if (abs(denom) < 1e-9f) return 0f
        return (aa * ee - bb * dd) / denom
    }

    /** Ponto raio∩plano (C,N); false se paralelo. */
    private fun rayPlanePoint(o: Vec3, d: Vec3, c: Vec3, n: Vec3, out: Vec3): Boolean {
        val denom = d.x * n.x + d.y * n.y + d.z * n.z
        if (abs(denom) < 1e-8f) return false
        val t = ((c.x - o.x) * n.x + (c.y - o.y) * n.y + (c.z - o.z) * n.z) / denom
        out.set(o.x + d.x * t, o.y + d.y * t, o.z + d.z * t)
        return true
    }

    /** Alça -> eixo local (0=X,1=Y,2=Z). */
    private fun handleAxisLocal(h: GizmoHandle): Int = when (h) {
        GizmoHandle.TX, GizmoHandle.RX, GizmoHandle.SX -> 0
        GizmoHandle.TY, GizmoHandle.RY, GizmoHandle.SY -> 1
        else -> 2
    }

    private fun axisScaleHandle(axis: Int): GizmoHandle = when (axis) {
        0 -> GizmoHandle.SX
        1 -> GizmoHandle.SY
        else -> GizmoHandle.SZ
    }

    private fun isAxisHandle(h: GizmoHandle): Boolean = when (h) {
        GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ,
        GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ,
        GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ -> true
        else -> false
    }

    /** Direção do eixo no mundo (LOCAL gira pela rotação do nick; GLOBAL é a base do mundo). */
    private fun worldAxis(localAxis: Int, nickLocalRot: Quaternion, space: GizmoSpace, out: Vec3) {
        when (localAxis) {
            0 -> out.set(1f, 0f, 0f)
            1 -> out.set(0f, 1f, 0f)
            else -> out.set(0f, 0f, 1f)
        }
        if (space == GizmoSpace.LOCAL) {
            rotateVecByQuaternion(out, nickLocalRot, sTmpB)
            out.set(sTmpB)
        }
    }

    private fun rotateVecByQuaternion(v: Vec3, q: Quaternion, tmp: Vec3) {
        // v' = q v q⁻¹ (formula otimizada)
        val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
        val uvx = qy * v.z - qz * v.y
        val uvy = qz * v.x - qx * v.z
        val uvz = qx * v.y - qy * v.x
        val uuvx = qy * uvz - qz * uvy
        val uuvy = qz * uvx - qx * uvz
        val uuvz = qx * uvy - qy * uvx
        tmp.set(
            v.x + 2f * (qw * uvx + uuvx),
            v.y + 2f * (qw * uvy + uuvy),
            v.z + 2f * (qw * uvz + uuvz)
        )
    }

    /** Extrai rotação de mundo de uma matriz TRS (colunas normalizadas). */
    private fun extractWorldRotation(m: Mat4, out: Quaternion) {
        val mm = m.m
        val sx = sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
        val sy = sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
        val sz = sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            rotExtractor.set(
                mm[0] / sx, mm[4] / sy, mm[8] / sz,
                mm[1] / sx, mm[5] / sy, mm[9] / sz,
                mm[2] / sx, mm[6] / sy, mm[10] / sz
            )
            rotExtractor.toMatrixComponents(out)
        } else {
            out.setIdentity()
        }
    }

    /** Mundo -> local (posição), considerando a matriz TRS do pai. */
    private fun worldToLocalPos(world: Vec3, out: Vec3) {
        val pm = parentMatrix ?: run { out.set(world); return }
        val m = pm.m
        val sx = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        val sy = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
        val sz = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
        if (sx < 1e-9f || sy < 1e-9f || sz < 1e-9f) {
            out.set(world)
            return
        }
        val wx = world.x - m[12]; val wy = world.y - m[13]; val wz = world.z - m[14]
        // local = S⁻¹ Rᵀ (w - t), com R unitário por coluna
        out.x = (m[0] * wx + m[1] * wy + m[2] * wz) / (sx * sx)
        out.y = (m[4] * wx + m[5] * wy + m[6] * wz) / (sy * sy)
        out.z = (m[8] * wx + m[9] * wy + m[10] * wz) / (sz * sz)
    }

    private fun snapVec(v: Vec3, step: Float) {
        v.x = Math.round(v.x / step).toFloat() * step
        v.y = Math.round(v.y / step).toFloat() * step
        v.z = Math.round(v.z / step).toFloat() * step
    }

    private fun applyAxisScale(
        nick: Nick, start: TransformSnapshot, factor: Float, snap: Boolean, uniform: Boolean
    ) {
        fun clampSnap(v: Float): Float {
            val s = (v * factor).coerceAtLeast(GizmoSnap.MIN_SCALE)
            return if (snap) Math.round(s / GizmoSnap.SCALE).toFloat() * GizmoSnap.SCALE else s
        }
        val t = nick.transform
        if (uniform) {
            t.scale.set(clampSnap(start.sx), clampSnap(start.sy), clampSnap(start.sz))
        } else when (dragHandle) {
            GizmoHandle.SX -> t.scale.set(clampSnap(start.sx), start.sy, start.sz)
            GizmoHandle.SY -> t.scale.set(start.sx, clampSnap(start.sy), start.sz)
            GizmoHandle.SZ -> t.scale.set(start.sx, start.sy, clampSnap(start.sz))
            else -> Unit
        }
    }

    private fun buildModel(worldPos: Vec3, worldRot: Quaternion, screenScale: Float, space: GizmoSpace, out: Mat4) {
        sScaleVec.set(screenScale, screenScale, screenScale)
        if (space == GizmoSpace.LOCAL) {
            out.setTRS(worldPos, worldRot, sScaleVec)
        } else {
            sIdentRot.setIdentity()
            out.setTRS(worldPos, sIdentRot, sScaleVec)
        }
    }

    // vetores de base estáticos (init-only)
    private fun unitVec(axis: Int): FloatArray = when (axis) {
        0 -> floatArrayOf(1f, 0f, 0f)
        1 -> floatArrayOf(0f, 1f, 0f)
        else -> floatArrayOf(0f, 0f, 1f)
    }

    private fun perpVec(axis: Int): FloatArray = when (axis) {
        0 -> floatArrayOf(0f, 1f, 0f)
        1 -> floatArrayOf(0f, 0f, 1f)
        else -> floatArrayOf(1f, 0f, 0f)
    }

    private fun crossVec(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    private fun ringPoint(axis: Int, angle: Float): FloatArray = when (axis) {
        0 -> floatArrayOf(0f, cos(angle), sin(angle))
        1 -> floatArrayOf(cos(angle), 0f, sin(angle))
        else -> floatArrayOf(cos(angle), sin(angle), 0f)
    }

    private fun mixPlane(axisA: Int, axisB: Int, va: Float, vb: Float): FloatArray {
        val p = FloatArray(3)
        p[axisA] = va
        p[axisB] = vb
        return p
    }

    private fun planeCorners(axisA: Int, axisB: Int): FloatArray {
        val lo = 0.25f; val hi = 0.65f
        val p1 = mixPlane(axisA, axisB, lo, lo)
        val p2 = mixPlane(axisA, axisB, hi, lo)
        val p3 = mixPlane(axisA, axisB, hi, hi)
        val p4 = mixPlane(axisA, axisB, lo, hi)
        return floatArrayOf(
            p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
            p3[0], p3[1], p3[2], p4[0], p4[1], p4[2]
        )
    }

    companion object {
        /** Braço do gizmo = 16% da altura do viewport. */
        const val SCREEN_FRACTION = 0.16f
    }
}
