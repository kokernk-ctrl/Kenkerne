package com.kenkern.engine.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * REGRESSÃO do bug achado pelos testes do importador glTF (F2.7, 2026-11-21):
 * os produtos com w na conversão quaternion→matrix estavam SEM o fator 2 —
 * uma rotação de 90° em Y mapeava (0,0,1) para um vetor de COMPRIMENTO 0,5.
 * Toda rotação de Nick (Spin/Bob, gizmo, importador) esbarrava nisso: a malha
 * encolhia/entortava em vez de girar. Corrigido em Quaternion.toMatrix E em
 * Mat4.setTRS (a fórmula estava duplicada nos dois lugares).
 */
class QuaternionMatrixRegressionTest {

    private fun quatEulerYaw(graus: Float): Quaternion =
        Quaternion().setFromEulerDegrees(0f, graus, 0f)

    @Test
    fun yaw_de_90_mapeia_z_para_x_com_comprimento_1() {
        val m = Mat4().setIdentity()
        quatEulerYaw(90f).toMatrix(m)
        // (0,0,1) → linha 0 da coluna 2 (m[8], m[9], m[10]): x'=sin90, z'=cos90
        assertEquals(1f, m.m[8], 1e-5f)
        assertEquals(0f, m.m[9], 1e-5f)
        assertEquals(0f, m.m[10], 1e-5f)
        // (1,0,0) → coluna 0: cos90=0 em x, −sin90=−1 em z (m[0], m[1], m[2])
        assertEquals(0f, m.m[0], 1e-5f)
        assertEquals(0f, m.m[1], 1e-5f)
        assertEquals(-1f, m.m[2], 1e-5f)
    }

    @Test
    fun yaw_de_90_no_settrs_preserva_comprimento_unitario() {
        val m = Mat4().setTRS(
            Vec3(0f, 0f, 0f), quatEulerYaw(90f), Vec3(1f, 1f, 1f)
        )
        val vx = m.m[8]; val vy = m.m[9]; val vz = m.m[10]
        val len = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz)
        assertEquals(1f, len, 1e-5f)
        assertEquals(1f, vx, 1e-5f)
    }

    @Test
    fun toMatrix_igual_a_setTRS_para_a_mesma_rotacao() {
        val q = Quaternion().setFromEulerDegrees(15f, 40f, 65f)
        val a = Mat4().setIdentity().also { q.toMatrix(it) }
        val b = Mat4().setTRS(Vec3(0f, 0f, 0f), q, Vec3(1f, 1f, 1f))
        for (i in 0..15) assertEquals(a.m[i], b.m[i], 1e-6f)
    }

    @Test
    fun pitch_de_90_mapeia_y_para_z_com_comprimento_1() {
        val m = Mat4().setTRS(
            Vec3(0f, 0f, 0f), Quaternion().setFromEulerDegrees(90f, 0f, 0f), Vec3(1f, 1f, 1f)
        )
        // (0,1,0) → coluna 1 (m[4], m[5], m[6])
        val vy = m.m[5]; val vz = m.m[6]
        val len = kotlin.math.sqrt(m.m[4] * m.m[4] + vy * vy + vz * vz)
        assertEquals(1f, len, 1e-5f)
        assertEquals(1f, vz, 1e-5f)
    }

    @Test
    fun escala_nao_uniforme_com_rotacao_45_yaw_dobra_o_x_do_cubo() {
        // caso do importador: cubo de meia-largura com scale (2,1,1) e yaw 45°
        val yaw45 = Quaternion(0f, sin(kotlin.math.PI.toFloat() / 8f), 0f, cos(kotlin.math.PI.toFloat() / 8f))
        val m = Mat4().setTRS(Vec3(0f, 0f, 0f), yaw45, Vec3(2f, 1f, 1f))
        fun aplica(px: Float, py: Float, pz: Float): Float =
            m.m[0] * px + m.m[4] * py + m.m[8] * pz
        val maxx = maxOf(
            kotlin.math.abs(aplica(0.5f, 0.5f, 0.5f)),
            kotlin.math.abs(aplica(0.5f, -0.5f, -0.5f)),
            kotlin.math.abs(aplica(-0.5f, 0.5f, -0.5f))
        )
        assertTrue("vértice do cubo deve alcançar x≈1,06 (foi $maxx)", maxx > 1.0f)
    }
}
