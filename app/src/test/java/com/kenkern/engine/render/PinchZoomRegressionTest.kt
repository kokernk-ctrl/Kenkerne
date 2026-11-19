package com.kenkern.engine.render

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

/**
 * Regressão do ZOOM ORBITAL PRESO (v0.1.10) e do OLHAR DE 2 DEDOS no VOO —
 * teste JVM puro com MotionEvent mockado (mockito-core 5, mock-maker inline).
 *
 * BUG 1 (causa raiz confirmada): no ACTION_POINTER_UP o antigo TouchController
 * reancorava TODOS os ponteiros do evento — incluindo o dedo que estava SUBINDO
 * (pointerCount o inclui) — e activeCount = pointerCount ficava 2 com 1 dedo
 * real. O próximo MOVE (pointerCount=1) entrava no ramo da pinça e lançava
 * IllegalArgumentException em getX(1), engolida em silêncio pelo runCatching
 * da KenkernSurfaceView: câmera presa até soltar TODOS os dedos.
 *
 * CENÁRIOS (especificação da tarefa):
 *  1. pinça AFASTA e APROXIMA no MESMO gesto, sem levantar dedos => distance
 *     volta ao inicial (tolerância 1%);
 *  2. idem em GESTOS SEPARADOS (UP dos dois dedos entre eles);
 *  3. levantar UM dedo no meio da pinça: sem exceção, sem zoom fantasma e a
 *     pinça retomada continua funcionando (regressão direta do bug 1);
 *  4. convenção de sinais do zoom (dedos fechando = afastar);
 *  5. VOO: 2 dedos arrastando giram o olhar (0,25°/dp; dedo à direita olha à
 *     direita; dedo para cima olha para cima; clamp ±89°) e NÃO movem a
 *     posição (movimento segue no joystick/botões).
 */
class PinchZoomRegressionTest {

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

    /** Posições de 2 dedos com um span simétrico em torno de (400, 800). */
    private fun spanPts(span: Float): Array<out Pair<Float, Float>> =
        arrayOf(400f - span / 2f to 800f, 400f + span / 2f to 800f)

    /** Sequência de MOVEs de pinça variando o span de fromSpan a toSpan. */
    private fun pinchMoves(tc: TouchController, fromSpan: Float, toSpan: Float, steps: Int) {
        var t = 100L
        for (k in 1..steps) {
            val s = fromSpan + (toSpan - fromSpan) * k / steps
            t += 8
            tc.onTouch(ev(MotionEvent.ACTION_MOVE, t, 0, *spanPts(s)))
        }
    }

    private fun makeController(distance: Float): Pair<OrbitCamera, TouchController> {
        val cam = OrbitCamera()
        cam.distance = distance
        val tc = TouchController(cam, tapSlopPx = 10f, density = 1f)
        return cam to tc
    }

    private fun assertDist(expected: Float, actual: Float, message: String) {
        assertEquals(message, expected, actual, expected * 0.01f) // tolerância 1%
    }

    // ---------------------------------------------------------------- 1. mesmo gesto

    @Test
    fun `pinça afasta e aproxima no MESMO gesto sem levantar dedos - distance volta ao inicial`() {
        val (cam, tc) = makeController(distance = 9f)

        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, *spanPts(200f)))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, *spanPts(200f)))

        // AFASTAR: produto dos fatores = 200/100 => distance 9 -> 18
        pinchMoves(tc, 200f, 100f, 10)
        assertDist(18f, cam.distance, "afastar no mesmo gesto deve dobrar a distância")

        // APROXIMAR no MESMO gesto: produto = 100/200 => 18 -> 9
        pinchMoves(tc, 100f, 200f, 10)
        assertDist(9f, cam.distance, "distance deve voltar ao inicial (tol 1%)")
    }

    // ---------------------------------------------------------------- 2. gestos separados

    @Test
    fun `pinça em GESTOS SEPARADOS também volta à distância inicial`() {
        val (cam, tc) = makeController(distance = 9f)

        // GESTO 1: afastar 200 -> 120
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, *spanPts(200f)))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, *spanPts(200f)))
        pinchMoves(tc, 200f, 120f, 8)
        assertDist(9f * 200f / 120f, cam.distance, "gesto 1 (afastar)")

        // solta os DOIS dedos: POINTER_UP (dedo 1) e depois UP (dedo 0)
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_UP, 200, 1, *spanPts(120f)))
        tc.onTouch(ev(MotionEvent.ACTION_UP, 208, 0, *spanPts(120f)))

        // GESTO 2: aproximar 120 -> 200
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 400, 0, *spanPts(120f)))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 416, 1, *spanPts(120f)))
        pinchMoves(tc, 120f, 200f, 8)
        assertDist(9f, cam.distance, "distance volta ao inicial após gestos separados (tol 1%)")

        tc.onTouch(ev(MotionEvent.ACTION_POINTER_UP, 600, 1, *spanPts(200f)))
        tc.onTouch(ev(MotionEvent.ACTION_UP, 608, 0, *spanPts(200f)))
    }

    // ---------------------------------------------------------------- 3. dedo morto (bug 1)

    @Test
    fun `levantar UM dedo no meio da pinça não congela nem zooma - e a pinça retomada funciona`() {
        val (cam, tc) = makeController(distance = 9f)

        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, *spanPts(200f)))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, *spanPts(200f)))
        pinchMoves(tc, 200f, 150f, 4)
        val distBefore = cam.distance // ~12 (9 * 200/150)

        // Levanta o DEDO 1: o evento AINDA contém 2 ponteiros (actionIndex=1).
        // ANTES do fix: o dedo morto ficava ativo, activeCount=2 e o próximo
        // MOVE de 1 dedo lançava IllegalArgumentException (engolida) — câmera
        // presa. DEPOIS: vira órbita de 1 dedo, sem exceção e sem zoom.
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_UP, 100, 1, *spanPts(150f)))

        var t = 120L
        for (k in 1..5) {
            t += 8
            tc.onTouch(ev(MotionEvent.ACTION_MOVE, t, 0, 200f + k * 10f to 800f)) // pointerCount=1
        }
        assertEquals(
            "MOVE de 1 dedo pós-POINTER_UP não pode alterar a distância",
            distBefore, cam.distance, 0.01f
        )

        // Retoma a pinça e APROXIMA de volta ao ponto de partida (150 -> 200):
        // 12 * (150/200) = 9.
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 300, 1, *spanPts(150f)))
        pinchMoves(tc, 150f, 200f, 6)
        assertDist(9f, cam.distance, "pinça retomada após levantar 1 dedo volta ao inicial (tol 1%)")
    }

    // ---------------------------------------------------------------- 4. convenção do zoom

    @Test
    fun `convenção - dedos FECHANDO afastam e dedos ABRINDO aproximam`() {
        val (cam, tc) = makeController(distance = 9f)

        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, *spanPts(200f)))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, *spanPts(200f)))

        pinchMoves(tc, 200f, 100f, 1) // UM MOVE: fator 200/100 = 2.0 exato
        assertEquals(18f, cam.distance, 0.01f)

        pinchMoves(tc, 100f, 200f, 1) // UM MOVE: fator 100/200 = 0.5 exato
        assertEquals(9f, cam.distance, 0.01f)
    }

    // ---------------------------------------------------------------- 5. voo: olhar com 2 dedos

    @Test
    fun `VOO - 2 dedos arrastando giram o olhar e NÃO movem a posição`() {
        val cam = OrbitCamera()
        cam.mode = CameraMode.FLIGHT
        cam.flightYawDegrees = 35f
        cam.flightPitchDegrees = 10f
        val startX = cam.flightPosition.x
        val startY = cam.flightPosition.y
        val startZ = cam.flightPosition.z

        // density = 2: 100px de arrasto médio = 50dp => 50 * 0,25°/dp = 12,5°
        val tc = TouchController(cam, tapSlopPx = 10f, density = 2f)

        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 0, 0, 100f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_DOWN, 16, 1, 100f to 500f, 300f to 500f))

        // 2 dedos para a DIREITA (dx médio = +100px = 50dp): nesta câmera yaw
        // DIMINUI para olhar à direita (forward.x = -sin(yaw), OrbitCamera).
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 32, 0, 200f to 500f, 400f to 500f))
        assertEquals(
            "dedo à direita deve olhar à direita (yaw -12,5°)",
            35f - 12.5f, cam.flightYawDegrees, 0.01f
        )
        assertEquals(10f, cam.flightPitchDegrees, 0.01f)

        // Movimento continua INTACTO: 2 dedos não mudam a posição (joystick/botões)
        assertEquals(startX, cam.flightPosition.x, 1e-4f)
        assertEquals(startY, cam.flightPosition.y, 1e-4f)
        assertEquals(startZ, cam.flightPosition.z, 1e-4f)

        // 2 dedos para CIMA (dy médio = -80px = -40dp): pitch AUMENTA (olha para cima)
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 48, 0, 200f to 420f, 400f to 420f))
        assertEquals(
            "dedo para cima deve olhar para cima (pitch +10°)",
            20f, cam.flightPitchDegrees, 0.01f
        )

        // Clamp ±89°: arrasto gigante para baixo nunca passa do limite
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 64, 0, 200f to 2000f, 400f to 2000f))
        assertTrue(
            "pitch deve respeitar o clamp ±89° (foi ${cam.flightPitchDegrees})",
            cam.flightPitchDegrees in -89f..89f
        )

        // 1 dedo continua girando o olhar como antes (mapa de gestos preservado).
        // O gesto de 1 dedo é em PIXELS CRUS (comportamento antigo intacto):
        // dx = 140px => yaw -= 140 * 0,25°/px = 35°.
        tc.onTouch(ev(MotionEvent.ACTION_POINTER_UP, 80, 1, 200f to 2000f, 400f to 2000f))
        tc.onTouch(ev(MotionEvent.ACTION_UP, 88, 0, 200f to 2000f))
        val yawBefore = cam.flightYawDegrees
        tc.onTouch(ev(MotionEvent.ACTION_DOWN, 100, 0, 500f to 500f))
        tc.onTouch(ev(MotionEvent.ACTION_MOVE, 116, 0, 640f to 500f))
        assertEquals(
            "1 dedo no voo continua girando o olhar (0,25°/px como antes)",
            yawBefore - 35f, cam.flightYawDegrees, 0.01f
        )
    }
}
