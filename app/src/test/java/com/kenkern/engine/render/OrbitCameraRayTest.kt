package com.kenkern.engine.render

import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste de unidade (JVM puro — sem Android) da convenção de raio de tela:
 *
 *  1. UM RAIO PELO CENTRO DA TELA deve coincidir com a DIREÇÃO da câmera
 *     (screenRay(0,0) == forwardVector) — a base do picking por toque;
 *  2. o raio que passa por um ponto projetado deve atravessar o ponto de
 *     mundo — valida a inversão de Y (`ndcY = 1 - 2y/h`) usada pelo
 *     requestPick contra a matriz de projeção do render;
 *  3. o raio pela alça do gizmo PROJETADA deve acertar a MESMA alça no
 *     hit-test — valida que projeção (desenho) e hit-test usam a mesma
 *     convenção (se falhar, círculos e alças ficariam desalinhados).
 */
class OrbitCameraRayTest {

    private fun makeCamera(): OrbitCamera = OrbitCamera().apply {
        target.set(0f, 0f, 0f)
        yawDegrees = 35f
        pitchDegrees = 28f
        distance = 9f
        fovDegrees = 55f
    }

    /** Projeta um ponto de mundo em NDC (column-major, mesma convenção do gizmo). */
    private fun projectToNdc(vp: Mat4, p: Vec3): Pair<Float, Float> {
        val m = vp.m
        val cw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        val inv = 1f / cw
        val x = (m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]) * inv
        val y = (m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]) * inv
        return x to y
    }

    /** Inverso EXATO do requestPick (KenkernSurfaceView): px -> NDC. */
    private fun pxToNdc(px: Float, py: Float, w: Float, h: Float): Pair<Float, Float> =
        ((px / w) * 2f - 1f) to (1f - (py / h) * 2f)

    private fun distancePointRay(p: Vec3, o: Vec3, d: Vec3): Float {
        val po = Vec3().set(p).sub(o)
        val t = po.dot(d)
        val closest = Vec3().set(o).addScaled(d, t)
        return closest.distanceTo(p)
    }

    @Test
    fun `raio pelo centro da tela coincide com a direção da câmera`() {
        val fwd = Vec3()
        val o = Vec3()
        val d = Vec3()

        // ORBITAL: três atitudes diferentes
        for (setup in listOf(
            Triple(0f, 0f, 0f), Triple(35f, 28f, 0f), Triple(-120f, 45f, 0f)
        )) {
            val c = makeCamera()
            c.yawDegrees = setup.first
            c.pitchDegrees = setup.second
            c.forwardVector(fwd)
            val eye = Vec3()
            c.eyePoint(eye)
            c.screenRay(0f, 0f, 16f / 9f, o, d)
            assertEquals("origem orbital = olho (yaw ${setup.first})", eye.x, o.x, 1e-4f)
            assertEquals("origem orbital = olho y (yaw ${setup.first})", eye.y, o.y, 1e-4f)
            assertEquals("origem orbital = olho z (yaw ${setup.first})", eye.z, o.z, 1e-4f)
            assertEquals(fwd.x, d.x, 1e-4f)
            assertEquals(fwd.y, d.y, 1e-4f)
            assertEquals(fwd.z, d.z, 1e-4f)
        }

        // VOO: mesmo contrato
        val f = makeCamera()
        f.mode = CameraMode.FLIGHT
        f.flightPosition.set(3f, 2f, 5f)
        f.flightYawDegrees = -40f
        f.flightPitchDegrees = 12f
        f.forwardVector(fwd)
        f.screenRay(0f, 0f, 9f / 16f, o, d)
        assertEquals("origem voo = posição de voo", f.flightPosition.x, o.x, 1e-4f)
        assertEquals(fwd.x, d.x, 1e-4f)
        assertEquals(fwd.y, d.y, 1e-4f)
        assertEquals(fwd.z, d.z, 1e-4f)
    }

    @Test
    fun `raio do topo da tela passa pelo ponto projetado - convenção de Y`() {
        val c = makeCamera()
        val w = 1080f
        val h = 2400f
        val aspect = w / h
        val proj = Mat4()
        val view = Mat4()
        val vp = Mat4()
        c.projection(proj, aspect)
        c.view(view)
        vp.multiply(proj, view)
        val o = Vec3()
        val d = Vec3()

        // TOPO da tela: requestPick manda ndcY = +1 (y = 0 px)
        c.screenRay(0f, 1f, aspect, o, d)
        val qTop = Vec3().set(o).addScaled(d, 5f)
        val (nxTop, nyTop) = projectToNdc(vp, qTop)
        assertEquals("topo da tela projeta ndcY=+1", 1f, nyTop, 1e-3f)
        assertEquals(0f, nxTop, 1e-3f)

        // o px projetado volta para o MESMO ndc pelo inverso do requestPick
        val pxTopX = (nxTop * 0.5f + 0.5f) * w
        val pxTopY = (1f - (nyTop * 0.5f + 0.5f)) * h
        val (backX, backY) = pxToNdc(pxTopX, pxTopY, w, h)
        assertEquals(0f, backX, 1e-4f)
        assertEquals(1f, backY, 1e-4f)

        // BASE da tela: ndcY = -1, simetricamente
        c.screenRay(0f, -1f, aspect, o, d)
        val qBottom = Vec3().set(o).addScaled(d, 5f)
        val (nxBot, nyBot) = projectToNdc(vp, qBottom)
        assertEquals("base da tela projeta ndcY=-1", -1f, nyBot, 1e-3f)
        assertEquals(0f, nxBot, 1e-3f)
    }

    @Test
    fun `raio pela alça projetada do gizmo acerta a mesma alça no hit-test`() {
        val c = makeCamera()
        val w = 1080f
        val h = 2400f
        val aspect = w / h
        val proj = Mat4()
        val view = Mat4()
        val vp = Mat4()
        c.projection(proj, aspect)
        c.view(view)
        vp.multiply(proj, view)

        val gizmo = TransformGizmo()
        val worldPos = Vec3(0f, 0f, 0f)
        val ident = Quaternion()
        ident.setIdentity()
        val ss = gizmo.screenScaleFor(c, worldPos)

        val outX = FloatArray(TransformGizmo.DEBUG_HANDLE_COUNT)
        val outY = FloatArray(TransformGizmo.DEBUG_HANDLE_COUNT)
        val outValid = BooleanArray(TransformGizmo.DEBUG_HANDLE_COUNT)
        gizmo.projectHandlesToScreen(
            vp, w, h, worldPos, ident, ss,
            GizmoSpace.GLOBAL, GizmoMode.MOVE, outX, outY, outValid
        )

        val iTX = TransformGizmo.DEBUG_HANDLE_ORDER.indexOf(GizmoHandle.TX)
        assertTrue("alça TX projetada deve ser válida", outValid[iTX])

        // O raio pelo px projetado da ponta da seta TX passa PERTO do ponto TX em mundo
        val tip = Vec3(ss, 0f, 0f)
        val (nx, ny) = pxToNdc(outX[iTX], outY[iTX], w, h)
        val o = Vec3()
        val d = Vec3()
        c.screenRay(nx, ny, aspect, o, d)
        val dist = distancePointRay(tip, o, d)
        assertTrue("raio do toque na alça deve passar perto da ponta TX (dist=$dist)", dist < 0.05f)

        // E o hit-test no MESMO px acerta a MESMA alça (tolerância 28dp @ density 3)
        val handle = gizmo.hitTest(
            outX[iTX], outY[iTX], vp, w, h, worldPos, ident, ss,
            GizmoSpace.GLOBAL, GizmoMode.MOVE, 28f * 3f
        )
        assertEquals("hit-test na alça projetada deve devolver TX", GizmoHandle.TX, handle)

        // Canto da tela, longe das alças: NONE
        val miss = gizmo.hitTest(
            w * 0.04f, h * 0.96f, vp, w, h, worldPos, ident, ss,
            GizmoSpace.GLOBAL, GizmoMode.MOVE, 28f * 3f
        )
        assertEquals("canto da tela não deve acertar alça", GizmoHandle.NONE, miss)
    }
}
