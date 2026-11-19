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

/**
 * Dados estáticos de um modo: MALHA de triângulos (alças sólidas: cilindros,
 * cones, toros e cubos — NUNCA GL_LINES), ranges por alça e alvos de hit-test.
 */
class GizmoModeData(
    val tris: FloatArray,                              // [x,y,z,r,g,b,a] por vértice (GL_TRIANGLES)
    val triRanges: HashMap<GizmoHandle, IntArray>,     // alça -> [offset, count] (vértices)
    val hitSegments: HashMap<GizmoHandle, FloatArray>, // alça -> [x1,y1,z1,x2,y2,z2, ...]
    val hitQuads: HashMap<GizmoHandle, FloatArray>     // alça -> 4 cantos [x,y,z] x4
)

/**
 * GIZMO DE TRANSFORMAÇÃO da KENKERN (Mover / Girar / Escalar).
 *
 *  DESENHO: alças como MALHA (cilindro + cone nas setas, toros nos anéis,
 *  hastes + cubos de extremidade + cubo central na escala). Tamanho CONSTANTE
 *  na tela: braço = ~18% da altura do viewport. Sem teste de profundidade
 *  (sempre sobre a cena). Alça sob o dedo em AMARELO; demais eixos APAGADOS.
 *
 *  HIT-TEST EM PIXELS: cada alça é projetada para a tela; distância do toque
 *  ao segmento projetado; tolerância 28dp. Empate vale a alça mais próxima.
 *  Planos (XY/XZ/YZ) = quadrados ~40dp perto da origem.
 *
 *  ARRASTO (a partir do ponto de pegada):
 *   - eixo: ponto mais próximo raio-reta; desloca pela diferença do inicial;
 *   - plano: interseção raio-plano;
 *   - girar: ângulo do toque em torno do centro MEDIDO NO PLANO DO ANEL
 *     (interseção raio-plano), diferença angular a partir do início;
 *   - escalar: razão entre a distância atual e a inicial ao longo do eixo.
 *  Estado: UM único Command por arrasto (snapshot inicial + final).
 *
 *  ZERO alocação por evento: todos os temporários são campos pré-alocados.
 *
 * Cores: X #E5534B, Y #57AB5A, Z #539BF5; alça sob o dedo: amarelo.
 */
class TransformGizmo private constructor(buildMesh: Boolean) {

    /*
     * ORDEM DE INICIALIZAÇÃO (corrige o NPE em torusPoint via buildRotateData):
     * inicializadores de propriedade executam NA ORDEM EM QUE SÃO DECLARADOS.
     * moveData/rotateData/scaleData chamam build*Data(), então TODAS as outras
     * propriedades (estado de arrasto, vetores auxiliares, buffers) são
     * declaradas ANTES deles — auditado: buildMoveData, buildRotateData e
     * buildScaleData não leem nem escrevem nenhum campo declarado abaixo
     * (torus/torusPoint usam buffers LOCAIS, não campos).
     */

    // ---------- Sessão de arrasto (instância única, pré-alocada) ----------
    private var dragHandle: GizmoHandle = GizmoHandle.NONE
    private var dragNick: Nick? = null
    private var startSnapshot: TransformSnapshot? = null
    private var dragScreenScale = 1f
    private val startWorldPos = Vec3()
    private val axisWorld = Vec3()
    private val planeNormalW = Vec3()
    private val startHitPoint = Vec3()
    private val startRadial = Vec3()      // rotação: vetor centro->toque no plano do anel
    private var startAxisS = 0f           // eixo: parâmetro da pegada na reta do eixo
    private var startTouchDist = 0f       // escala uniforme: distância de tela inicial
    private var startScreenAngle = 0f     // fallback de rotação (anel de perfil)
    private var rotateSign = 1f
    private var rotateUseScreen = false   // true = anel visto de perfil -> ângulo de tela
    private val parentRot = Quaternion()
    private val parentRotInv = Quaternion()
    private var parentMatrix: Mat4? = null
    private val rotExtractor = com.kenkern.engine.core.RotationFromMatrix()

    // Scratches (zero alocação por evento)
    private val sModel = Mat4()
    private val sMvp = Mat4()   // MVP = viewProj × sModel (hit-test em pixels de tela)
    private val sScaleVec = Vec3()
    private val sIdentRot = Quaternion()
    private val sWorldRot = Quaternion()
    private val sRayOrigin = Vec3()
    private val sRayDir = Vec3()
    private val sTmpA = Vec3()
    private val sTmpB = Vec3()
    private val sTmpC = Vec3()
    private val sQa = Quaternion()
    private val sQb = Quaternion()
    private val sQc = Quaternion()
    private val sEye = Vec3()
    private val sFwd = Vec3()
    private val sLocalTarget = Vec3()
    private val sQuadX = FloatArray(4)
    private val sQuadY = FloatArray(4)

    // Scratches EXCLUSIVOS do thread GL para publicação de debug (projectHandles
    // ToScreen): NÃO compartilham os scratches acima, que o hit-test da UI usa.
    private val dbgModel = Mat4()
    private val dbgMvp = Mat4()
    private val dbgScaleVec = Vec3()
    private val dbgIdentRot = Quaternion()

    // ---------- Dados estáticos dos três modos (POR ÚLTIMO: só aqui os campos
    // acima já existem; a cadeia build*Data -> torus/torusPoint usa apenas
    // variáveis locais, então nada depende de propriedades declaradas abaixo) ----------
    val moveData: GizmoModeData = if (buildMesh) buildMoveData() else EMPTY_MODE_DATA
    val rotateData: GizmoModeData = if (buildMesh) buildRotateData() else EMPTY_MODE_DATA
    val scaleData: GizmoModeData = if (buildMesh) buildScaleData() else EMPTY_MODE_DATA

    /** Construtor padrão do app: gizmo completo, com as malhas dos três modos. */
    constructor() : this(buildMesh = true)

    fun dataFor(mode: GizmoMode): GizmoModeData = when (mode) {
        GizmoMode.MOVE -> moveData
        GizmoMode.ROTATE -> rotateData
        GizmoMode.SCALE -> scaleData
    }

    /** Escala do gizmo em unidades de mundo para tamanho CONSTANTE na tela (braço ≈ 18% da altura). */
    fun screenScaleFor(camera: OrbitCamera, worldPos: Vec3): Float {
        camera.eyePoint(sEye)
        val d = sEye.distanceTo(worldPos).coerceAtLeast(0.5f)
        val halfFovRad = Math.toRadians((camera.fovDegrees * 0.5f).toDouble())
        return (d * kotlin.math.tan(halfFovRad) * 2.0 * SCREEN_FRACTION).toFloat()
    }

    /**
     * HIT-TEST EM PIXELS: alça mais próxima do toque; tolerância
     * [touchRadiusPx] (28dp). Empate vale a mais próxima (sem viés artificial).
     * [outNearestPx] opcional: recebe a distância (px) à alça mais próxima MESMO
     * quando nenhuma alça fica dentro da tolerância (diagnóstico do overlay:
     * "nenhuma alça, mais próxima a Ndp"). Sem alocação.
     */
    fun hitTest(
        touchX: Float, touchY: Float,
        viewProj: Mat4, viewportW: Float, viewportH: Float,
        worldPos: Vec3, worldRot: Quaternion, screenScale: Float,
        space: GizmoSpace, mode: GizmoMode,
        touchRadiusPx: Float,
        outNearestPx: FloatArray? = null
    ): GizmoHandle {
        val data = dataFor(mode)
        buildModel(worldPos, worldRot, screenScale, space, sModel)
        // MVP COMPLETO: os alvos do hit-test (segmentos/quadrados LOCAIS do gizmo)
        // precisam ser projetados pela MESMA matriz do desenho (viewProj × model).
        // Projetar só pelo model colocava as alças em posições de tela erradas —
        // o hit-test nunca coincidia com as alças desenhadas (causa raiz da
        // "alça não reage ao toque"; exposto pelo teste de unidade).
        sMvp.multiply(viewProj, sModel)

        var best = GizmoHandle.NONE
        var bestDist = touchRadiusPx
        var nearest = Float.MAX_VALUE

        // Segmentos (eixos, anéis, hastes, cubo central): distância ao segmento projetado.
        for ((handle, segs) in data.hitSegments) {
            var minDist = Float.MAX_VALUE
            var i = 0
            while (i < segs.size) {
                projectToScreen(sMvp, segs[i], segs[i + 1], segs[i + 2], viewportW, viewportH, sQuadX, sQuadY, 0)
                projectToScreen(sMvp, segs[i + 3], segs[i + 4], segs[i + 5], viewportW, viewportH, sQuadX, sQuadY, 1)
                val d = distPointSegment(touchX, touchY, sQuadX[0], sQuadY[0], sQuadX[1], sQuadY[1])
                if (d < minDist) minDist = d
                i += 6
            }
            if (minDist < nearest) nearest = minDist
            if (minDist < bestDist) {
                bestDist = minDist
                best = handle
            }
        }

        // Planos de movimento (quadrado ~40dp projetado): dentro do polígono = acerto.
        for ((handle, quad) in data.hitQuads) {
            for (c in 0 until 4) {
                projectToScreen(
                    sMvp, quad[c * 3], quad[c * 3 + 1], quad[c * 3 + 2],
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
                val ex = sQuadX[bIdx] - sQuadX[a]
                val ey = sQuadY[bIdx] - sQuadY[a]
                val cross = ex * (touchY - sQuadY[a]) - ey * (touchX - sQuadX[a])
                if (cross != 0f) {
                    val s = if (cross > 0f) 1f else -1f
                    if (sign == 0f) sign = s else if (s != sign) inside = false
                }
            }
            val d = if (inside) edgeDist * 0.5f else edgeDist
            if (d < nearest) nearest = d
            if (d < bestDist) {
                bestDist = d
                best = handle
            }
        }
        if (outNearestPx != null && outNearestPx.isNotEmpty()) {
            // -1 = nenhum alvo de hit-test (gizmo vazio/degradado); senão a distância px.
            outNearestPx[0] = if (nearest == Float.MAX_VALUE) -1f else nearest
        }
        return best
    }

    /**
     * PUBLICAÇÃO DE DEBUG (thread GL, por frame): projeta UM ponto representativo
     * de cada alça para a tela (px do FRAMEBUFFER). O overlay desenha círculos
     * verdes nesses pontos: se não coincidirem com as alças desenhadas, a
     * projeção (viewProj/viewport) está errada. Ponto válido exige cw > 0
     * (na frente da câmera) e NDC dentro da tela com margem. Sem alocação.
     */
    fun projectHandlesToScreen(
        viewProj: Mat4, viewportW: Float, viewportH: Float,
        worldPos: Vec3, worldRot: Quaternion, screenScale: Float,
        space: GizmoSpace, mode: GizmoMode,
        outX: FloatArray, outY: FloatArray, outValid: BooleanArray
    ) {
        dbgScaleVec.set(screenScale, screenScale, screenScale)
        if (space == GizmoSpace.LOCAL) {
            dbgModel.setTRS(worldPos, worldRot, dbgScaleVec)
        } else {
            dbgIdentRot.setIdentity()
            dbgModel.setTRS(worldPos, dbgIdentRot, dbgScaleVec)
        }
        // MVP = viewProj × model: projeta o ponto LOCAL pela matriz COMPLETA
        // (a mesma do desenho). Projetar só pelo model ignoraria a câmera.
        dbgMvp.multiply(viewProj, dbgModel)
        val mm = dbgMvp.m
        val n = minOf(DEBUG_HANDLE_POINTS.size, outX.size, outY.size, outValid.size)
        for (i in 0 until n) {
            val p = DEBUG_HANDLE_POINTS[i]
            val cw = mm[3] * p[0] + mm[7] * p[1] + mm[11] * p[2] + mm[15]
            if (abs(cw) < 1e-6f) {
                outX[i] = -1f; outY[i] = -1f; outValid[i] = false
                continue
            }
            val inv = 1f / cw
            val ndcX = (mm[0] * p[0] + mm[4] * p[1] + mm[8] * p[2] + mm[12]) * inv
            val ndcY = (mm[1] * p[0] + mm[5] * p[1] + mm[9] * p[2] + mm[13]) * inv
            val valid = cw > 0f && ndcX >= -1.5f && ndcX <= 1.5f && ndcY >= -1.5f && ndcY <= 1.5f
            outX[i] = (ndcX * 0.5f + 0.5f) * viewportW
            outY[i] = (1f - (ndcY * 0.5f + 0.5f)) * viewportH
            outValid[i] = valid
        }
    }

    /**
     * Inicia o arrasto de [handle] sobre [nick]: captura snapshot inicial e os
     * parâmetros geométricos do gesto (eixo, plano, anel) A PARTIR DA PEGADA.
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
        parentRotInv.set(-parentRot.x, -parentRot.y, -parentRot.z, parentRot.w)

        val localRot = nick.transform.rotation
        camera.forwardVector(sFwd)
        val ndcX = (touchX / viewportW) * 2f - 1f
        val ndcY = 1f - (touchY / viewportH) * 2f

        when (dragHandle) {
            // MOVER EIXO: parâmetro s da pegada na reta do eixo (ponto mais próximo raio-reta).
            GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                startAxisS = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
            }
            // MOVER PLANO: interseção raio-plano na pegada.
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
            // GIRAR: vetor radial (centro->pegada) NO PLANO DO ANEL via raio-plano.
            GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                rotateSign = if (axisWorld.dot(sFwd) < 0f) 1f else -1f
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                rotateUseScreen = true
                if (rayPlanePoint(sRayOrigin, sRayDir, startWorldPos, axisWorld, sTmpB)) {
                    sTmpA.set(sTmpB).sub(startWorldPos)
                    sTmpA.addScaled(axisWorld, -sTmpA.dot(axisWorld)) // projeta no plano do anel
                    if (sTmpA.lengthSquared() > 1e-8f) {
                        startRadial.set(sTmpA).normalize()
                        rotateUseScreen = false
                    }
                }
                if (rotateUseScreen) {
                    // anel de perfil: fallback por ângulo de tela em torno do centro
                    projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                    startScreenAngle = screenAngle(touchX, touchY, sQuadX[0], sQuadY[0])
                }
            }
            // ESCALAR EIXO: razão s_atual / s_inicial ao longo do eixo.
            GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ -> {
                worldAxis(handleAxisLocal(dragHandle), localRot, space, axisWorld)
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                startAxisS = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
                if (abs(startAxisS) < 1e-3f) startAxisS = 1e-3f
            }
            // ESCALAR UNIFORME: razão das distâncias de tela ao centro.
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
            // MOVER EIXO: ponto mais próximo raio-reta; desloca pela diferença p/ inicial.
            GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                val s = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
                sTmpA.set(startWorldPos).addScaled(axisWorld, s - startAxisS)
                worldToLocalPos(sTmpA, sLocalTarget)
                if (snapEnabled) snapVec(sLocalTarget, GizmoSnap.MOVE)
                nick.transform.position.set(sLocalTarget)
            }
            // MOVER PLANO: interseção raio-plano; desloca pela diferença p/ pegada.
            GizmoHandle.TXY, GizmoHandle.TXZ, GizmoHandle.TYZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                if (!rayPlanePoint(sRayOrigin, sRayDir, startWorldPos, planeNormalW, sTmpB)) return
                sTmpA.set(startWorldPos).add(sTmpB.sub(startHitPoint))
                worldToLocalPos(sTmpA, sLocalTarget)
                if (snapEnabled) snapVec(sLocalTarget, GizmoSnap.MOVE)
                nick.transform.position.set(sLocalTarget)
            }
            // GIRAR: diferença angular em torno do eixo, MEDIDA NO PLANO DO ANEL.
            GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ -> {
                var deltaDeg = Float.NaN
                if (!rotateUseScreen) {
                    camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                    if (rayPlanePoint(sRayOrigin, sRayDir, startWorldPos, axisWorld, sTmpB)) {
                        sTmpA.set(sTmpB).sub(startWorldPos)
                        sTmpA.addScaled(axisWorld, -sTmpA.dot(axisWorld))
                        if (sTmpA.lengthSquared() > 1e-8f) {
                            // ângulo assinado de startRadial para o vetor atual em torno do eixo
                            sTmpC.cross(startRadial, sTmpA)
                            val sine = sTmpC.dot(axisWorld)
                            val cosine = startRadial.dot(sTmpA)
                            deltaDeg = Math.toDegrees(atan2(sine.toDouble(), cosine.toDouble()).toDouble()).toFloat()
                        }
                    }
                } else {
                    projectToScreen(viewProj, startWorldPos.x, startWorldPos.y, startWorldPos.z, viewportW, viewportH, sQuadX, sQuadY, 0)
                    deltaDeg = Math.toDegrees(
                        (screenAngle(touchX, touchY, sQuadX[0], sQuadY[0]) - startScreenAngle).toDouble()
                    ).toFloat()
                    deltaDeg = wrapDegrees(deltaDeg) * rotateSign
                }
                if (deltaDeg.isNaN()) return
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
            // ESCALAR EIXO: razão entre distância atual e inicial ao longo do eixo.
            GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ -> {
                camera.screenRay(ndcX, ndcY, aspect, sRayOrigin, sRayDir)
                val s = rayAxisParam(sRayOrigin, sRayDir, startWorldPos, axisWorld)
                var factor = s / startAxisS
                if (factor < 0.01f) factor = 0.01f
                if (factor > 1000f) factor = 1000f
                applyAxisScale(nick, start, factor, snapEnabled, uniform = false)
            }
            // ESCALAR UNIFORME: razão das distâncias de tela ao centro.
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

    private class TriBuilder {
        val out = ArrayList<Float>(2048)
        fun v(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float) {
            out.add(x); out.add(y); out.add(z); out.add(r); out.add(g); out.add(b); out.add(a)
        }
        fun tri(
            x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float, r: Float, g: Float, b: Float, a: Float
        ) {
            v(x1, y1, z1, r, g, b, a); v(x2, y2, z2, r, g, b, a); v(x3, y3, z3, r, g, b, a)
        }
        fun quad(
            x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float,
            r: Float, g: Float, b: Float, a: Float
        ) {
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

    /** Cilindro sólido ao longo de [axis], de [from] a [to], com tampas. */
    private fun cylinder(
        b: TriBuilder, axis: Int, from: Float, to: Float, radius: Float,
        segs: Int, c: FloatArray, alpha: Float = 1f
    ) {
        val u = unitVec(axis)
        val p = perpVec(axis)
        val q = crossVec(u, p)
        for (i in 0 until segs) {
            val t0 = (i.toFloat() / segs) * 2f * Math.PI.toFloat()
            val t1 = ((i + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
            val c0 = cos(t0); val s0 = sin(t0); val c1 = cos(t1); val s1 = sin(t1)
            val ax0 = u[0] * from + p[0] * (radius * c0) + q[0] * (radius * s0)
            val ay0 = u[1] * from + p[1] * (radius * c0) + q[1] * (radius * s0)
            val az0 = u[2] * from + p[2] * (radius * c0) + q[2] * (radius * s0)
            val ax1 = u[0] * from + p[0] * (radius * c1) + q[0] * (radius * s1)
            val ay1 = u[1] * from + p[1] * (radius * c1) + q[1] * (radius * s1)
            val az1 = u[2] * from + p[2] * (radius * c1) + q[2] * (radius * s1)
            val bx0 = u[0] * to + p[0] * (radius * c0) + q[0] * (radius * s0)
            val by0 = u[1] * to + p[1] * (radius * c0) + q[1] * (radius * s0)
            val bz0 = u[2] * to + p[2] * (radius * c0) + q[2] * (radius * s0)
            val bx1 = u[0] * to + p[0] * (radius * c1) + q[0] * (radius * s1)
            val by1 = u[1] * to + p[1] * (radius * c1) + q[1] * (radius * s1)
            val bz1 = u[2] * to + p[2] * (radius * c1) + q[2] * (radius * s1)
            b.quad(ax0, ay0, az0, bx0, by0, bz0, bx1, by1, bz1, ax1, ay1, az1, c[0], c[1], c[2], alpha)
            // tampas (cull desativado no draw; ventos consistentes por segurança)
            b.tri(u[0] * from, u[1] * from, u[2] * from, ax1, ay1, az1, ax0, ay0, az0, c[0], c[1], c[2], alpha)
            b.tri(u[0] * to, u[1] * to, u[2] * to, bx0, by0, bz0, bx1, by1, bz1, c[0], c[1], c[2], alpha)
        }
    }

    /** Cone sólido: base circular em [base], ponta em [tip]. */
    private fun cone(
        b: TriBuilder, axis: Int, base: Float, tip: Float, baseRadius: Float,
        segs: Int, c: FloatArray, alpha: Float = 1f
    ) {
        val u = unitVec(axis)
        val p = perpVec(axis)
        val q = crossVec(u, p)
        for (i in 0 until segs) {
            val t0 = (i.toFloat() / segs) * 2f * Math.PI.toFloat()
            val t1 = ((i + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
            val c0 = cos(t0); val s0 = sin(t0); val c1 = cos(t1); val s1 = sin(t1)
            val ax0 = u[0] * base + p[0] * (baseRadius * c0) + q[0] * (baseRadius * s0)
            val ay0 = u[1] * base + p[1] * (baseRadius * c0) + q[1] * (baseRadius * s0)
            val az0 = u[2] * base + p[2] * (baseRadius * c0) + q[2] * (baseRadius * s0)
            val ax1 = u[0] * base + p[0] * (baseRadius * c1) + q[0] * (baseRadius * s1)
            val ay1 = u[1] * base + p[1] * (baseRadius * c1) + q[1] * (baseRadius * s1)
            val az1 = u[2] * base + p[2] * (baseRadius * c1) + q[2] * (baseRadius * s1)
            b.tri(u[0] * tip, u[1] * tip, u[2] * tip, ax0, ay0, az0, ax1, ay1, az1, c[0], c[1], c[2], alpha)
            b.tri(u[0] * base, u[1] * base, u[2] * base, ax1, ay1, az1, ax0, ay0, az0, c[0], c[1], c[2], alpha)
        }
    }

    /** Toro sólido no plano perpendicular a [axis], centro na origem. */
    private fun torus(
        b: TriBuilder, axis: Int, majorR: Float, minorR: Float,
        majorSegs: Int, minorSegs: Int, c: FloatArray, alpha: Float = 1f
    ) {
        val u = unitVec(axis)
        val p = perpVec(axis)
        val q = crossVec(u, p)
        // Buffers LOCAIS (antigos campos sPa/sPb/sPc/sPd): torus() roda só na
        // CONSTRUÇÃO (init-only). Um campo declarado depois do inicializador de
        // rotateData ainda seria null aqui — a causa exata do NPE em torusPoint.
        // Variáveis locais não dependem da ordem de inicialização de campos.
        val pa = FloatArray(3)
        val pb = FloatArray(3)
        val pc = FloatArray(3)
        val pd = FloatArray(3)
        for (i in 0 until majorSegs) {
            val m0 = (i.toFloat() / majorSegs) * 2f * Math.PI.toFloat()
            val m1 = ((i + 1).toFloat() / majorSegs) * 2f * Math.PI.toFloat()
            for (j in 0 until minorSegs) {
                val n0 = (j.toFloat() / minorSegs) * 2f * Math.PI.toFloat()
                val n1 = ((j + 1).toFloat() / minorSegs) * 2f * Math.PI.toFloat()
                torusPoint(u, p, q, majorR, minorR, m0, n0, pa)
                torusPoint(u, p, q, majorR, minorR, m1, n0, pb)
                torusPoint(u, p, q, majorR, minorR, m1, n1, pc)
                torusPoint(u, p, q, majorR, minorR, m0, n1, pd)
                b.quad(
                    pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
                    pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
                    c[0], c[1], c[2], alpha
                )
            }
        }
    }

    private fun torusPoint(
        u: FloatArray, p: FloatArray, q: FloatArray,
        majorR: Float, minorR: Float, m: Float, n: Float, out: FloatArray
    ) {
        val cm = cos(m); val sm = sin(m)
        val cn = cos(n); val sn = sin(n)
        val ring = majorR + minorR * cn
        out[0] = p[0] * (ring * cm) + q[0] * (ring * sm) + u[0] * (minorR * sn)
        out[1] = p[1] * (ring * cm) + q[1] * (ring * sm) + u[1] * (minorR * sn)
        out[2] = p[2] * (ring * cm) + q[2] * (ring * sm) + u[2] * (minorR * sn)
    }

    /** Cubo sólido centrado em (cx,cy,cz). */
    private fun solidBox(
        b: TriBuilder, cx: Float, cy: Float, cz: Float, half: Float,
        r: Float, g: Float, bl: Float, alpha: Float = 1f
    ) {
        val x0 = cx - half; val x1 = cx + half
        val y0 = cy - half; val y1 = cy + half
        val z0 = cz - half; val z1 = cz + half
        // +X / -X
        b.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, bl, alpha)
        b.quad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, bl, alpha)
        // +Y / -Y
        b.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, alpha)
        b.quad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r, g, bl, alpha)
        // +Z / -Z
        b.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, alpha)
        b.quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, bl, alpha)
    }

    /** Preenchimento do plano de movimento (quadrado entre [lo] e [hi] nos dois eixos). */
    private fun planeFill(
        b: TriBuilder, axisA: Int, axisB: Int, lo: Float, hi: Float,
        c: FloatArray, alpha: Float
    ) {
        val p1 = mixPlane(axisA, axisB, lo, lo)
        val p2 = mixPlane(axisA, axisB, hi, lo)
        val p3 = mixPlane(axisA, axisB, hi, hi)
        val p4 = mixPlane(axisA, axisB, lo, hi)
        b.quad(
            p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], p4[0], p4[1], p4[2],
            c[0], c[1], c[2], alpha
        )
    }

    // ---------- Construção dos modos ----------

    private fun buildMoveData(): GizmoModeData {
        val b = TriBuilder()
        val triRanges = HashMap<GizmoHandle, IntArray>()
        val hitSegments = HashMap<GizmoHandle, FloatArray>()
        val hitQuads = HashMap<GizmoHandle, FloatArray>()
        val planeColor = floatArrayOf(0.82f, 0.82f, 0.82f)

        // Setas: haste (cilindro) + ponta (cone) por eixo.
        for (axis in 0 until 3) {
            val c = axisColor(axis)
            val start = b.vertexCount()
            cylinder(b, axis, 0.15f, 0.72f, 0.034f, 12, c)
            cone(b, axis, 0.72f, 1.0f, 0.095f, 12, c)
            triRanges[axisHandle(axis)] = intArrayOf(start, b.vertexCount() - start)
            hitSegments[axisHandle(axis)] = axisHitSegment(axis)
        }
        // Planos de movimento: quadrados ~40dp perto da origem.
        var start = b.vertexCount()
        planeFill(b, 0, 1, PLANE_LO, PLANE_HI, planeColor, 0.30f) // XY
        triRanges[GizmoHandle.TXY] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TXY] = planeCorners(0, 1)
        start = b.vertexCount()
        planeFill(b, 0, 2, PLANE_LO, PLANE_HI, planeColor, 0.30f) // XZ
        triRanges[GizmoHandle.TXZ] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TXZ] = planeCorners(0, 2)
        start = b.vertexCount()
        planeFill(b, 1, 2, PLANE_LO, PLANE_HI, planeColor, 0.30f) // YZ
        triRanges[GizmoHandle.TYZ] = intArrayOf(start, b.vertexCount() - start)
        hitQuads[GizmoHandle.TYZ] = planeCorners(1, 2)

        return GizmoModeData(b.toFloatArray(), triRanges, hitSegments, hitQuads)
    }

    private fun buildRotateData(): GizmoModeData {
        val b = TriBuilder()
        val triRanges = HashMap<GizmoHandle, IntArray>()
        val hitSegments = HashMap<GizmoHandle, FloatArray>()
        val handles = arrayOf(GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ)
        for (axis in 0 until 3) {
            val c = axisColor(axis)
            val start = b.vertexCount()
            torus(b, axis, RING_R, RING_TUBE, 44, 8, c)
            triRanges[handles[axis]] = intArrayOf(start, b.vertexCount() - start)
            hitSegments[handles[axis]] = ringHitSegments(axis)
        }
        return GizmoModeData(b.toFloatArray(), triRanges, hitSegments, HashMap())
    }

    private fun buildScaleData(): GizmoModeData {
        val b = TriBuilder()
        val triRanges = HashMap<GizmoHandle, IntArray>()
        val hitSegments = HashMap<GizmoHandle, FloatArray>()
        for (axis in 0 until 3) {
            val c = axisColor(axis)
            val u = unitVec(axis)
            val start = b.vertexCount()
            cylinder(b, axis, 0.15f, 0.72f, 0.034f, 12, c)
            solidBox(b, u[0] * 0.88f, u[1] * 0.88f, u[2] * 0.88f, 0.10f, c[0], c[1], c[2])
            triRanges[axisScaleHandle(axis)] = intArrayOf(start, b.vertexCount() - start)
            hitSegments[axisScaleHandle(axis)] = axisHitSegment(axis)
        }
        // Cubo central (escala uniforme)
        val start = b.vertexCount()
        solidBox(b, 0f, 0f, 0f, 0.14f, 0.85f, 0.85f, 0.85f)
        triRanges[GizmoHandle.SXYZ] = intArrayOf(start, b.vertexCount() - start)
        hitSegments[GizmoHandle.SXYZ] = FloatArray(6) // segmento degenerado = ponto central
        return GizmoModeData(b.toFloatArray(), triRanges, hitSegments, HashMap())
    }

    // ---------- Alvos de hit-test ----------

    /** Segmento central->extremidade do braço do eixo (usado por mover e escalar). */
    private fun axisHitSegment(axis: Int): FloatArray {
        val u = unitVec(axis)
        return floatArrayOf(0f, 0f, 0f, u[0], u[1], u[2])
    }

    /** Anéis: [chords] cordas ao longo do círculo (aproximação fina para pixels). */
    private fun ringHitSegments(axis: Int, chords: Int = 24): FloatArray {
        val hs = FloatArray(chords * 6)
        var k = 0
        for (i in 0 until chords) {
            val a0 = (i.toFloat() / chords) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / chords) * 2f * Math.PI.toFloat()
            val p0 = ringPoint(axis, a0)
            val p1 = ringPoint(axis, a1)
            hs[k++] = p0[0]; hs[k++] = p0[1]; hs[k++] = p0[2]
            hs[k++] = p1[0]; hs[k++] = p1[1]; hs[k] = p1[2]
        }
        return hs
    }

    private fun planeCorners(axisA: Int, axisB: Int): FloatArray {
        val p1 = mixPlane(axisA, axisB, PLANE_LO, PLANE_LO)
        val p2 = mixPlane(axisA, axisB, PLANE_HI, PLANE_LO)
        val p3 = mixPlane(axisA, axisB, PLANE_HI, PLANE_HI)
        val p4 = mixPlane(axisA, axisB, PLANE_LO, PLANE_HI)
        return floatArrayOf(
            p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
            p3[0], p3[1], p3[2], p4[0], p4[1], p4[2]
        )
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

    private fun axisHandle(axis: Int): GizmoHandle = when (axis) {
        0 -> GizmoHandle.TX
        1 -> GizmoHandle.TY
        else -> GizmoHandle.TZ
    }

    private fun axisScaleHandle(axis: Int): GizmoHandle = when (axis) {
        0 -> GizmoHandle.SX
        1 -> GizmoHandle.SY
        else -> GizmoHandle.SZ
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

    companion object {
        /** Braço do gizmo = 18% da altura do viewport (especificação). */
        const val SCREEN_FRACTION = 0.18f

        /** Quadrado dos planos de movimento: ~40dp na tela (0,33 do braço de 18%). */
        const val PLANE_LO = 0.12f
        const val PLANE_HI = 0.45f

        /** Raio do anel de rotação e espessura do tubo (fração do braço). */
        const val RING_R = 0.82f
        const val RING_TUBE = 0.032f

        /**
         * Ordem FIXA das alças publicadas para o overlay de debug (mesma ordem de
         * [DEBUG_HANDLE_POINTS]). O overlay mapeia índice -> nome por esta tabela.
         */
        val DEBUG_HANDLE_ORDER: Array<GizmoHandle> = arrayOf(
            GizmoHandle.TX, GizmoHandle.TY, GizmoHandle.TZ,
            GizmoHandle.TXY, GizmoHandle.TXZ, GizmoHandle.TYZ,
            GizmoHandle.RX, GizmoHandle.RY, GizmoHandle.RZ,
            GizmoHandle.SX, GizmoHandle.SY, GizmoHandle.SZ,
            GizmoHandle.SXYZ
        )

        /**
         * Pontos representativos de cada alça em coordenadas LOCAIS do gizmo
         * (ponta da seta, canto externo do plano, ponto do anel, cubo da haste,
         * centro). Multiplicados pela matriz do gizmo em projectHandlesToScreen.
         */
        val DEBUG_HANDLE_POINTS: Array<FloatArray> = arrayOf(
            floatArrayOf(1f, 0f, 0f),                       // TX (ponta da seta)
            floatArrayOf(0f, 1f, 0f),                       // TY
            floatArrayOf(0f, 0f, 1f),                       // TZ
            floatArrayOf(PLANE_HI, PLANE_HI, 0f),           // TXY (canto do plano)
            floatArrayOf(PLANE_HI, 0f, PLANE_HI),           // TXZ
            floatArrayOf(0f, PLANE_HI, PLANE_HI),           // TYZ
            floatArrayOf(0f, RING_R, 0f),                   // RX (ponto do anel)
            floatArrayOf(RING_R, 0f, 0f),                   // RY
            floatArrayOf(RING_R, 0f, 0f),                   // RZ (mesmo ponto: os anéis passam por aqui)
            floatArrayOf(0.88f, 0f, 0f),                    // SX (cubo da haste)
            floatArrayOf(0f, 0.88f, 0f),                    // SY
            floatArrayOf(0f, 0f, 0.88f),                    // SZ
            floatArrayOf(0f, 0f, 0f)                        // SXYZ (centro)
        )

        /** Número de alças publicadas para o overlay ([ViewportDebugState]). */
        @JvmField
        val DEBUG_HANDLE_COUNT = DEBUG_HANDLE_ORDER.size

        /** Dados vazios compartilhados: sem malha e sem alças (render desenha nada). */
        private val EMPTY_MODE_DATA =
            GizmoModeData(FloatArray(0), HashMap(), HashMap(), HashMap())

        /**
         * GIZMO VAZIO (fallback anti-crash): pula a construção das malhas.
         * O render não desenha nada (tris vazio => total == 0) e o hit-test
         * devolve NONE (mapas vazios) — o editor segue vivo, sem gizmo.
         */
        fun empty(): TransformGizmo = TransformGizmo(buildMesh = false)
    }
}
