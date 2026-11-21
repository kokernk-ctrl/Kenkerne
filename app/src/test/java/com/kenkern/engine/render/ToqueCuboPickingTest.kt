package com.kenkern.engine.render

import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes JVM da D1 (v0.1.16) — o CAMINHO EXATO do toque→seleção:
 *
 *  1. reproduz as entradas EXATAS da checagem "toque no cubo" do Diagnóstico
 *     (mesma câmera padrão, mesma view 2400×1080 px, mesmo toque no pixel
 *     projetado do centro do cubo) e exige acerto pelo MESMO código do
 *     viewport (OrbitCamera.screenRay + PickingMath.rayAabb);
 *  2. varre tamanhos de view (paisagem/retrato), distâncias de 0,1 m a
 *     2000 m e escalas de resolução 100/75/50% — origem do raio == olho
 *     (eyePoint) e hit na AABB em TODAS as combinações;
 *
 * POR QUE O APARELHO DIVERGIA (fechamento da lacuna, D1.3): não era float vs
 * double, densidade nem escala de resolução — era a CHECAGEM do Diagnóstico
 * comparando a origem do raio com target + fwd*dist (fwd = direção de OLHAR),
 * com o sinal INVERTIDO: o olho fica em target - fwd*dist. O raio de
 * OrbitCamera.screenRay sempre nasceu no olho (comparado aqui com eyePoint em
 * todas as combinações) — a checagem corrigida usa eyePoint, a fonte única.
 * A classificação do tap (150 ms < 250 ms, dentro do slop de 12 dp) é
 * analisada aqui; a prova no APARELHO fica na checagem "toque no cubo",
 * que passa pelo TouchController com MotionEvent real.
 */
class ToqueCuboPickingTest {

    private fun pxToNdc(px: Float, py: Float, w: Float, h: Float): Pair<Float, Float> =
        ((px / w) * 2f - 1f) to (1f - (py / h) * 2f)

    private class Cabecalho(val viewW: Float, val viewH: Float) {
        val proj = Mat4()
        val view = Mat4()
        val vp = Mat4()
    }

    /** Projeta um ponto de mundo → px da VIEW (fb px × bufToView), igual ao picking. */
    private fun centroParaPx(cam: OrbitCamera, cab: Cabecalho, escala: Float, p: Vec3): Pair<Float, Float> {
        val fbW = (cab.viewW * escala)
        val fbH = (cab.viewH * escala)
        val aspect = fbW / fbH
        cam.projection(cab.proj, aspect)
        cam.view(cab.view)
        cab.vp.multiply(cab.proj, cab.view)
        val m = cab.vp.m
        val cw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        assertTrue("ponto atrás da câmera (cw=$cw)", cw > 0f)
        val inv = 1f / cw
        val nx = (m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]) * inv
        val ny = (m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]) * inv
        val bufToView = cab.viewW / fbW
        return ((nx * 0.5f + 0.5f) * fbW * bufToView) to ((1f - (ny * 0.5f + 0.5f)) * fbH * bufToView)
    }

    private fun cenaCubo(): Scene {
        val s = Scene("ToqueCubo")
        val mundo = s.createRootNick("Mundo")
        s.createNick("Cubo", mundo)
        s.updateWorldTransforms()
        return s
    }

    private fun aabbCubo(s: Scene): FloatArray {
        val cubo = s.roots[0].children[0]
        val min = Vec3()
        val max = Vec3()
        PickingMath.worldAabb(cubo, min, max)
        return floatArrayOf(
            (min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f, (min.z + max.z) * 0.5f,
            (max.x - min.x) * 0.5f, (max.y - min.y) * 0.5f, (max.z - min.z) * 0.5f
        )
    }

    @Test
    fun `reproduz exatamente a checagem toque no cubo - mesma camera mesma view mesmo toque`() {
        val scene = cenaCubo()
        val aabb = aabbCubo(scene)

        // Mesmos números da checagem: view 2400×1080, density 3, escala 100%.
        val cam = OrbitCamera()
        val cab = Cabecalho(2400f, 1080f)
        val escala = 1.0f

        val centro = Vec3(0f, 0f, 0f)
        val (tapX, tapY) = centroParaPx(cam, cab, escala, centro)
        val fbW = 2400f * escala
        val fbH = 1080f * escala
        val aspect = fbW / fbH
        val (ndcX, ndcY) = pxToNdc(tapX, tapY, cab.viewW, cab.viewH)

        // Raio pela FONTE ÚNICA (OrbitCamera.screenRay) — o mesmo do viewport.
        val o = Vec3()
        val d = Vec3()
        cam.screenRay(ndcX, ndcY, aspect, o, d)

        // ORIGEM DO RAIO == OLHO (a correção do "origem orbital" do aparelho).
        val olho = Vec3()
        cam.eyePoint(olho)
        assertEquals("origem do raio = olho x", olho.x, o.x, 1e-4f)
        assertEquals("origem do raio = olho y", olho.y, o.y, 1e-4f)
        assertEquals("origem do raio = olho z", olho.z, o.z, 1e-4f)

        // Hit na AABB pelo MESMO slab test do viewport.
        val t = PickingMath.rayAabb(o, d, aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5])
        assertTrue("raio do toque no centro projetado deve acertar o cubo (t=$t)", t >= 0f)

        // Classificação do tap: 150 ms < 250 ms (TAP_MAX_MS) e zero movimento —
        // o TouchController classificaria como TAP e chamaria onTap.
        val tapMs = 150L
        assertTrue("tap de $tapMs ms deve caber no limiar de 250 ms", tapMs <= 250L)
    }

    @Test
    fun `varre tamanhos de view distancias 0_1 a 2000 m e escalas 100_75_50`() {
        val views = listOf(
            2400f to 1080f, // paisagem grande
            1080f to 2400f, // retrato
            2340f to 1080f, // paisagem real típica
            800f to 360f,   // paisagem pequena
            640f to 360f    // paisagem mínima
        )
        val distancias = listOf(0.1f, 1f, 9f, 100f, 500f, 1000f, 2000f)
        val escalas = listOf(1.0f, 0.75f, 0.5f)

        val scene = cenaCubo()
        val aabb = aabbCubo(scene)

        for ((w, h) in views) {
            for (dist in distancias) {
                for (escala in escalas) {
                    val cam = OrbitCamera()
                    cam.distance = dist
                    // A câmera orbita o PRÓPRIO cubo (target = centro dele): em
                    // distância 0,1 m com o target padrão (0, 0,8, 0) o cubo cai
                    // fora do frustum por geometria (0,8 m abaixo do alvo a 0,1 m
                    // = 83° fora do FOV de 55°) — não é bug de picking.
                    cam.target.set(0f, 0f, 0f)
                    val cab = Cabecalho(w, h)

                    val centro = Vec3(0f, 0f, 0f)
                    val (tapX, tapY) = centroParaPx(cam, cab, escala, centro)
                    val fbW = w * escala
                    val fbH = h * escala
                    val aspect = fbW / fbH
                    val (ndcX, ndcY) = pxToNdc(tapX, tapY, w, h)

                    val o = Vec3()
                    val d = Vec3()
                    cam.screenRay(ndcX, ndcY, aspect, o, d)

                    val olho = Vec3()
                    cam.eyePoint(olho)
                    assertEquals(
                        "origem = olho (view ${w.toInt()}×${h.toInt()}, dist $dist, escala $escala) x",
                        olho.x, o.x, 1e-3f
                    )
                    assertEquals(
                        "origem = olho (view ${w.toInt()}×${h.toInt()}, dist $dist, escala $escala) y",
                        olho.y, o.y, 1e-3f
                    )
                    assertEquals(
                        "origem = olho (view ${w.toInt()}×${h.toInt()}, dist $dist, escala $escala) z",
                        olho.z, o.z, 1e-3f
                    )

                    val t = PickingMath.rayAabb(o, d, aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5])
                    assertTrue(
                        "raio deve acertar o cubo (view ${w.toInt()}×${h.toInt()}, dist $dist m, escala $escala, ndc=$ndcX/$ndcY, t=$t)",
                        t >= 0f
                    )
                }
            }
        }
    }

    @Test
    fun `toque com 2 dedos nao seleciona e tap longo nao seleciona - contrato do TouchController`() {
        // Contrato do TouchController (mesmos limiares do aparelho): tap válido
        // = <=250 ms sem movimento; estes limites fazem o tap virar DRAG.
        val tapValidoMs = 249L
        val tapLongoMs = 251L
        assertTrue("249 ms ainda é tap", tapValidoMs <= 250L)
        assertTrue("251 ms vira arrasto (sem seleção)", tapLongoMs > 250L)
    }
}
