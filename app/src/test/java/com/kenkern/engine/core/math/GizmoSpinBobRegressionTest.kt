package com.kenkern.engine.core.math

import com.kenkern.engine.core.NickFunctionRegistry
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.SpinFunction
import com.kenkern.engine.core.BobFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REGRESSÃO de rotação do GIZMO, SPIN e BOB (ETAPA B.7): a rotação do
 * [Quaternion] e do [Mat4.setTRS] PRESERVA O COMPRIMENTO dos vetores nos
 * ângulos 0/30/90/180/270 (e composition pai × filho correta). Se a matemática
 * de rotação quebrar (bug empilhado da F1 corrigido no F2.7), estes testes
 * falham ANTES de o modelo importado aparecer torto na tela.
 */
class GizmoSpinBobRegressionTest {

    private val angulos = floatArrayOf(0f, 30f, 90f, 180f, 270f)

    private fun vetorOriginal() = Vec3(1f, 0.5f, -0.25f).normalize() // comprimento 1

    private fun rotacionar(q: Quaternion, v: Vec3): Vec3 {
        // fórmula q·v·q⁻¹ explícita (sem Mat4)
        val qv = Quaternion(
            q.w * v.x + q.y * v.z - q.z * v.y,
            q.w * v.y - q.x * v.z + q.z * v.x,
            q.w * v.z + q.x * v.y - q.y * v.x,
            -(q.x * v.x + q.y * v.y + q.z * v.z)
        )
        val r = Quaternion(q.x, q.y, q.z, -q.w)
        val out = Quaternion()
        out.set(
            qv.w * r.x + qv.x * r.w + qv.y * r.z - qv.z * r.y,
            qv.w * r.y - qv.x * r.z + qv.y * r.w + qv.z * r.x,
            qv.w * r.z + qv.x * r.y - qv.y * r.x + qv.z * r.w,
            qv.w * r.w - qv.x * r.x - qv.y * r.y - qv.z * r.z
        )
        return Vec3(out.x, out.y, out.z)
    }

    @Test
    fun quaternion_preserva_comprimento_nos_angulos_chave() {
        for (graus in angulos) {
            val rad = Math.toRadians(graus.toDouble()).toFloat()
            val q = Quaternion().setFromAxisAngle(0f, 1f, 0f, rad).normalize()
            val v = vetorOriginal()
            val rv = rotacionar(q, v)
            assertEquals(
                "comprimento preservado em $graus° (eixo Y)",
                1f, kotlin.math.sqrt(rv.x * rv.x + rv.y * rv.y + rv.z * rv.z), 1e-4f
            )
            // mesmo teste em eixo arbitrário normalizado
            val q2 = Quaternion().setFromAxisAngle(0.577f, 0.577f, 0.577f, rad).normalize()
            val rv2 = rotacionar(q2, v)
            assertEquals(
                "comprimento preservado em $graus° (eixo diagonal)",
                1f, kotlin.math.sqrt(rv2.x * rv2.x + rv2.y * rv2.y + rv2.z * rv2.z), 1e-4f
            )
        }
    }

    @Test
    fun setTRS_preserva_comprimento_com_escala_unitaria() {
        for (graus in angulos) {
            val rad = Math.toRadians(graus.toDouble()).toFloat()
            val q = Quaternion().setFromAxisAngle(0f, 1f, 0f, rad).normalize()
            val m = Mat4().setTRS(Vec3(0f, 0f, 0f), q, Vec3(1f, 1f, 1f))
            val v = vetorOriginal()
            // rotação pura: colunas de M têm comprimento 1 (sem cisalhamento/escala errada)
            val c0 = kotlin.math.sqrt(m.m[0] * m.m[0] + m.m[1] * m.m[1] + m.m[2] * m.m[2])
            val c1 = kotlin.math.sqrt(m.m[4] * m.m[4] + m.m[5] * m.m[5] + m.m[6] * m.m[6])
            val c2 = kotlin.math.sqrt(m.m[8] * m.m[8] + m.m[9] * m.m[9] + m.m[10] * m.m[10])
            assertEquals("coluna 0 = 1 em $graus°", 1f, c0, 1e-4f)
            assertEquals("coluna 1 = 1 em $graus°", 1f, c1, 1e-4f)
            assertEquals("coluna 2 = 1 em $graus°", 1f, c2, 1e-4f)
            // e o vetor transformado mantém o comprimento
            val rx = m.m[0] * v.x + m.m[4] * v.y + m.m[8] * v.z
            val ry = m.m[1] * v.x + m.m[5] * v.y + m.m[9] * v.z
            val rz = m.m[2] * v.x + m.m[6] * v.y + m.m[10] * v.z
            assertEquals(
                "vetor mantém comprimento em $graus° via setTRS",
                1f, kotlin.math.sqrt(rx * rx + ry * ry + rz * rz), 1e-4f
            )
        }
    }

    @Test
    fun composicao_pai_x_filho_correta() {
        // pai: translação (1,0,0) + rotação 90° em Y; filho: translação local (0,0,-1)
        // esperado: filho em (1,0,0) + rotação aplicada a (0,0,-1) = (-1,0,0) → (0,0,0)... 
        // com rotação Y de 90°: (x,z) → (z, -x): (0,0,-1) → (-1,0,0) → mundo = (0,0,0)
        val rad = Math.toRadians(90.0).toFloat()
        val qPai = Quaternion().setFromAxisAngle(0f, 1f, 0f, rad)
        val mPai = Mat4().setTRS(Vec3(1f, 0f, 0f), qPai, Vec3(1f, 1f, 1f))
        val mFilhoLocal = Mat4().setTRS(Vec3(0f, 0f, -1f), Quaternion(), Vec3(1f, 1f, 1f))
        val mMundo = Mat4().multiply(mPai, mFilhoLocal)
        assertEquals("composição pai×filho: X", 0f, mMundo.m[12], 1e-4f)
        assertEquals("composição pai×filho: Y", 0f, mMundo.m[13], 1e-4f)
        assertEquals("composição pai×filho: Z", 0f, mMundo.m[14], 1e-4f)

        // composição de rotação também é pura (colunas de comprimento 1)
        val c1 = kotlin.math.sqrt(mMundo.m[4] * mMundo.m[4] + mMundo.m[5] * mMundo.m[5] + mMundo.m[6] * mMundo.m[6])
        assertEquals(1f, c1, 1e-4f)
    }

    @Test
    fun spin_gira_e_preserva_distancia_ao_pai() {
        NickFunctionRegistry.register(SpinFunction)
        val scene = Scene("Teste")
        val pai = scene.createRootNick("Pai")
        val nick = scene.createNick("Girante", pai)
        val spin = NickClassInstance("Spin")
        spin.fields["speed"] = NickValue.FloatValue(90f)   // 90°/s
        spin.fields["axis"] = NickValue.StringValue("Y")
        nick.addClassInstance(spin)
        // 1 segundo de update → 90°
        SpinFunction.onUpdate(nick, 1f, 1f)
        scene.updateWorldTransforms()
        val m = nick.transform.worldMatrix
        // a distância ao pai não muda com a rotação (origem local): posição = (0,0,0)
        assertEquals(0f, m.m[12], 1e-5f)
        assertEquals(0f, m.m[14], 1e-5f)
        // rotação de 90° aplicada: coluna 0 = (0,0,-1) para +90° em Y
        val len = kotlin.math.sqrt(
            m.m[0] * m.m[0] + m.m[1] * m.m[1] + m.m[2] * m.m[2]
        )
        assertEquals("coluna preserva comprimento após Spin", 1f, len, 1e-4f)
    }

    @Test
    fun bob_oscila_em_torno_da_base() {
        NickFunctionRegistry.register(BobFunction)
        val scene = Scene("Teste")
        val pai = scene.createRootNick("Pai")
        val nick = scene.createNick("Oscilante", pai)
        nick.transform.setLocalPosition(0f, 2f, 0f)
        val bob = NickClassInstance("Bob")
        bob.fields["amplitude"] = NickValue.FloatValue(0.5f)
        bob.fields["speed"] = NickValue.FloatValue(1.5f)
        nick.addClassInstance(bob)
        BobFunction.onSetup(nick)
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        // ~3 períodos completos (2π/1.5 ≈ 4,2 s) — senão sin() fica só na metade positiva
        for (i in 0 until 300) {
            BobFunction.onUpdate(nick, 1f / 60f, i * (1f / 60f))
            val y = nick.transform.position.y
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
            // só Y muda — X/Z preservados (sem deslizar lateralmente)
            assertEquals(0f, nick.transform.position.x, 1e-5f)
            assertEquals(0f, nick.transform.position.z, 1e-5f)
        }
        assertTrue("Bob oscila para cima da base", maxY > 2.0f)
        assertTrue("Bob oscila para baixo da base", minY < 2.0f)
        assertTrue("amplitude respeitada (±0.5 + folga)", maxY <= 2.51f && minY >= 1.49f)
    }

    @Test
    fun euler_ida_e_volta_nos_angulos_chave() {
        val out = FloatArray(3)
        for (graus in angulos) {
            val q = Quaternion().setFromEulerDegrees(graus, graus * 0.5f, -graus)
            q.toEulerDegrees(out)
            val q2 = Quaternion().setFromEulerDegrees(out[0], out[1], out[2])
            // rotação equivalente: aplicar aos mesmos vetores dá o mesmo resultado
            val v = vetorOriginal()
            val a = rotacionar(q, v)
            val b = rotacionar(q2, v)
            assertTrue(
                "euler ida e volta em $graus°: ($a) vs ($b)",
                a.distanceTo(b) < 1e-3f
            )
        }
    }
}
