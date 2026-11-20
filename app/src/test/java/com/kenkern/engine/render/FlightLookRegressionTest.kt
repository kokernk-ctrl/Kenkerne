package com.kenkern.engine.render

import android.view.MotionEvent
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import kotlin.math.atan2
import kotlin.math.sin

/**
 * Regressão do OLHAR HORIZONTAL MORTO no modo VOO (v0.1.12) — teste JVM puro
 * com MotionEvent mockado (mockito-core 5, mock-maker inline).
 *
 * CAUSA COMPROVADA (b): OrbitCamera.computeBasis() calculava o yawRad SEMPRE
 * do yaw do ORBITAL (yawDegrees), inclusive no ramo FLIGHT — o
 * flightYawDegrees atualizado pelo rotateFlight nunca era lido pela base.
 * O pitch funcionava porque o ramo FLIGHT lia flightPitchDegrees. Resultado
 * no aparelho (v0.1.11): 1 dedo cima/baixo girava; 1 dedo esquerda/direita
 * não fazia NADA; 2 dedos na horizontal também não girariam (mesma causa).
 *
 * ESTE TESTE NÃO OLHA SÓ O CAMPO: valida a DIREÇÃO FORWARD DA VIEW via
 * forwardVector() E pela projeção de um ponto 1 m à frente do olhar no
 * NDC CENTRO da matriz proj×view (o que a view realmente renderiza).
 *
 * CENÁRIOS (especificação da tarefa):
 *  1. 1 dedo HORIZONTAL no voo muda yaw E a direção forward da view;
 *  2. 1 dedo VERTICAL muda pitch (dedo para cima = olha para cima);
 *  3. 2 dedos HORIZONTAIS idem (mesma convenção de sinais);
 *  4. posição de voo INTOCADA pelo olhar (movimento é do joystick/botões);
 *  5. ORBITAL não muda: mesmo com flightYawDegrees divergente, a base
 *     orbital continua usando yawDegrees.
 */
class FlightLookRegressionTest {

    // ---------------------------------------------------------------- utilitários

    /** MotionEvent simulado (mockito): action, tempo, actionIndex e ponteiros. */
    private fun ev(action: Int, eventTime: Long, actionIndex: Int = 0, vararg pts: Pair<Float, Float>): MotionEvent {
        val e = mock(MotionEvent::class.java)
        Mockito.`when`(e.actionMasked).thenReturn(action)
        Mockito.`when`(e.eventTime).thenReturn(eventTime)
        Mockito.`when`(e.pointerCount).thenReturn(pts.size)
        Mockito.`when`(e.actionIndex).thenReturn(actionIndex)
        pts.forEachIndexed { i, p ->
            Mockito.`when`(e.getX(i)).thenReturn(p.first)
            Mockito.`when`(e.getY(i)).thenReturn(p.second)
        }
        return e
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

    /**
     * Prova de que a MATRIZ DE VIEW usa o yaw: um ponto exatamente à frente
     * do olhar (posição + forward × 1 m) tem que cair no NDC (0, 0) — centro
     * da tela. Com o bug, o forward do teste girava mas a view não.
     */
    private fun assertViewCentraOOlhar(cam: OrbitCamera, msg: String) {
        val fwd = Vec3()
        cam.forwardVector(fwd)
        val proj = Mat4()
        val view = Mat4()
        val vp = Mat4()
        cam.projection(proj, 16f / 9f)
        cam.view(view)
        vp.multiply(proj, view)
        val p = Vec3().set(cam.flightPosition).addScaled(fwd, 1f)
        val (nx, ny) = projectToNdc(vp, p)
        assertEquals("$msg: ponto à frente do olhar projeta no centro X", 0f, nx, 1e-3f)
        assertEquals("$msg: ponto à frente do olhar projeta no centro Y", 0f, ny, 1e-3f)
    }

    /** Direção horizontal do olhar em graus a partir do -Z, positiva para a direita (+X). */
    private fun headingGraus(fwd: Vec3): Float =
        Math.toDegrees(atan2(fwd.x, -fwd.z).toDouble()).toFloat()

    // ---------------------------------------------------------------- 1. 1 dedo horizontal

    @Test
    fun `1 dedo HORIZONTAL no voo muda yaw E o forward da VIEW - dedo à direita olha à direita`() {
        val cam = OrbitCamera()
        cam.mode = CameraMode.FLIGHT
        cam.flightYawDegrees = 0f
        cam.flightPitchDegrees = 0f
        val startX = cam.flightPosition.x
        val startY = cam.flightPosition.y
        val startZ = cam.flightPosition.z

        // density = 2 (igual ao teste da pinça): 100px = 50dp
        val tc = TouchController(cam, tapSlopPx = 10f, density = 2f)
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, 500f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 16, 0, 600f to 500f)) // +100px = +50dp

        // O CAMPO mudou: yaw -= 50dp × 0,25°/dp = -12,5° (0,25°/px com 100px: -25°)
        assertEquals("yaw do campo deve cair 25° (0,25°/px)", -25f, cam.flightYawDegrees, 0.01f)

        // O FORWARD DA VIEW girou (não só o campo): facing -Z, direita = +X
        val fwd = Vec3()
        cam.forwardVector(fwd)
        assertEquals(
            "dedo à direita deve olhar à direita (heading +25°)",
            25f, headingGraus(fwd), 0.05f
        )
        assertTrue("forward deve ganhar componente +X (direita)", fwd.x > 0.4f)
        assertEquals("forward.y intacto numa rotação pura de yaw", 0f, fwd.y, 1e-4f)

        // A VIEW usa o yaw (a prova forte): olhar cai no centro do NDC
        assertViewCentraOOlhar(cam, "após 1 dedo horizontal")

        // posição intocada pelo olhar
        assertEquals(startX, cam.flightPosition.x, 1e-4f)
        assertEquals(startY, cam.flightPosition.y, 1e-4f)
        assertEquals(startZ, cam.flightPosition.z, 1e-4f)
    }

    // ---------------------------------------------------------------- 2. 1 dedo vertical

    @Test
    fun `1 dedo VERTICAL no voo muda pitch E forward_y - dedo para cima olha para cima`() {
        val cam = OrbitCamera()
        cam.mode = CameraMode.FLIGHT
        cam.flightYawDegrees = 0f
        cam.flightPitchDegrees = 0f

        val tc = TouchController(cam, tapSlopPx = 10f, density = 2f)
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, 500f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 16, 0, 500f to 400f)) // -100px = -50dp (para cima)

        assertEquals("pitch do campo deve subir 25°", 25f, cam.flightPitchDegrees, 0.01f)
        val fwd = Vec3()
        cam.forwardVector(fwd)
        assertEquals(
            "dedo para cima deve olhar para cima (forward.y = sin(25°))",
            sin(Math.toRadians(25.0)).toFloat(), fwd.y, 1e-3f
        )
        assertTrue("yaw não pode mudar no gesto vertical", cam.flightYawDegrees == 0f)
        assertViewCentraOOlhar(cam, "após 1 dedo vertical")
    }

    // ---------------------------------------------------------------- 3. 2 dedos horizontal

    @Test
    fun `2 dedos HORIZONTAIS no voo giram o olhar - mesma convenção de sinais`() {
        val cam = OrbitCamera()
        cam.mode = CameraMode.FLIGHT
        cam.flightYawDegrees = 35f
        cam.flightPitchDegrees = 10f

        val tc = TouchController(cam, tapSlopPx = 10f, density = 2f)
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, 100f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, 100f to 500f, 300f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 32, 0, 200f to 500f, 400f to 500f)) // médio +100px

        // 2 dedos em dp: 100px/2 = 50dp → yaw -= 12,5° (35 → 22,5)
        assertEquals(
            "2 dedos à direita: yaw do campo cai 12,5°",
            35f - 12.5f, cam.flightYawDegrees, 0.01f
        )
        val fwd = Vec3()
        cam.forwardVector(fwd)
        // heading absoluto = -yaw (atan2(fwd.x, -fwd.z)); o que prova a convenção
        // é o DELTA: de -35° para -22,5° = girou +12,5° para a DIREITA.
        assertEquals(
            "2 dedos à direita: forward da view gira +12,5° para a direita",
            -22.5f, headingGraus(fwd), 0.05f
        )
        assertViewCentraOOlhar(cam, "após 2 dedos horizontais")
    }

    // ---------------------------------------------------------------- 4. convenção orbital

    @Test
    fun `ORBITAL não muda - base continua usando yawDegrees e ignora flightYawDegrees`() {
        val cam = OrbitCamera()
        cam.mode = CameraMode.ORBITAL
        cam.yawDegrees = 35f
        cam.pitchDegrees = 0f
        cam.flightYawDegrees = -100f // divergente de propósito

        val fwd = Vec3()
        cam.forwardVector(fwd)
        // Em ORBITAL o forwardVector devolve o OLHAR = inverso da base
        // (OrbitCamera.forwardVector: "forward aponta do alvo para o olho").
        // Olhar com yawDegrees=35° => heading 180-35 = 145°. Se a base
        // erroneamente usasse flightYawDegrees (-100), daria 180+100 = -80°.
        assertEquals(
            "orbital deve continuar seguindo yawDegrees (35°) e ignorar flightYawDegrees",
            145f, headingGraus(fwd), 0.05f
        )
    }
}
