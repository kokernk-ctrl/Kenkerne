package com.kenkern.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

/**
 * E5 (v0.1.18) — AUXILIARES DE EDITOR (geometria pura, sem GL):
 *  - FORMA por tipo: direcional=SETA · pontual=ESFERA · foco=CONE ·
 *    câmera=PIRÂMIDE com FOV REAL · área=CAIXA em arame · demais=LOSANGO;
 *  - COR POR GRUPO (luz âmbar-claro, câmera azul-claro, áreas verde, base cinza);
 *  - todos os builders devolvem pares GL_LINES com 6 floats/vértice
 *    (pos3+rgb — o formato do gizmo de eixos e do wireframe do colisor).
 */
class EditorHelpersTest {

    private val cor = floatArrayOf(1f, 0f, 0f)

    @Test
    fun formaDeCadaTipoEAuxiliar() {
        assertEquals(EditorHelpers.FORMA_SETA, EditorHelpers.formaDo("light.directional"))
        assertEquals(EditorHelpers.FORMA_ESFERA, EditorHelpers.formaDo("light.point"))
        assertEquals(EditorHelpers.FORMA_CONE, EditorHelpers.formaDo("light.spot"))
        assertEquals(EditorHelpers.FORMA_PIRAMIDE, EditorHelpers.formaDo("camera.basic"))
        assertEquals(EditorHelpers.FORMA_CAIXA, EditorHelpers.formaDo("area.volume"))
        // demais tipos sem malha: ícone losango
        assertEquals(EditorHelpers.FORMA_DIAMANTE, EditorHelpers.formaDo("area.spawn"))
        assertEquals(EditorHelpers.FORMA_DIAMANTE, EditorHelpers.formaDo("area.signal"))
        assertEquals(EditorHelpers.FORMA_DIAMANTE, EditorHelpers.formaDo("nick.empty"))
    }

    @Test
    fun corPorGrupo() {
        val out = FloatArray(3)
        EditorHelpers.corDoGrupo("light.directional", out)
        assertEquals(1.00f, out[0], 1e-4f); assertEquals(0.835f, out[1], 1e-4f); assertEquals(0.310f, out[2], 1e-4f)
        EditorHelpers.corDoGrupo("camera.basic", out)
        assertEquals(0.310f, out[0], 1e-4f); assertEquals(0.765f, out[1], 1e-4f); assertEquals(0.969f, out[2], 1e-4f)
        EditorHelpers.corDoGrupo("area.volume", out)
        assertEquals(0.400f, out[0], 1e-4f); assertEquals(0.733f, out[1], 1e-4f); assertEquals(0.416f, out[2], 1e-4f)
        EditorHelpers.corDoGrupo("nick.empty", out)
        assertEquals(0.690f, out[0], 1e-4f); assertEquals(0.745f, out[1], 1e-4f); assertEquals(0.773f, out[2], 1e-4f)
    }

    @Test
    fun geometriasSaoParesDeLinhasValidos() {
        // 6 floats/vértice, contagem PAR (GL_LINES), cores dentro de [0..1].
        val formas = listOf(
            EditorHelpers.buildSeta(cor), EditorHelpers.buildEsfera(cor), EditorHelpers.buildCone(cor),
            EditorHelpers.buildPiramide(cor, 60f, 4f / 3f), EditorHelpers.buildCaixa(cor), EditorHelpers.buildDiamante(cor)
        )
        assertTrue(formas.all { it.size > 0 && it.size % 12 == 0 })
        formas.forEach { d ->
            for (v in 0 until d.size / 6) {
                for (c in 3 until 6) {
                    val cor = d[v * 6 + c]
                    assertTrue("cor no range", cor >= 0f && cor <= 1f)
                }
                // posição finita (sem NaN/inf gerado por tan em fov inválida)
                for (p in 0 until 3) assertTrue(d[v * 6 + p].isFinite())
            }
        }
    }

    @Test
    fun piramideTemOFovReal() {
        // meia-altura da base = tan(fov/2) · meia-largura = meia-altura × aspect.
        val p = EditorHelpers.buildPiramide(floatArrayOf(1f, 1f, 1f), 90f, 2f)
        val meiaAltura = tan(Math.toRadians(45.0)).toFloat() // fov 90° → 45° de meia
        val meiaLargura = meiaAltura * 2f
        // cantos da base estão no buffer: ±largura, ±altura, z = -1
        val xs = HashSet<Float>()
        val ys = HashSet<Float>()
        var i = 0
        while (i < p.size) {
            xs.add(p[i]); ys.add(p[i + 1])
            i += 6
        }
        assertTrue("canto −largura presente (FOV real)", xs.contains(-meiaLargura))
        assertTrue("canto +largura presente (FOV real)", xs.contains(meiaLargura))
        assertTrue("canto −altura presente (FOV real)", ys.contains(-meiaAltura))
        assertTrue("canto +altura presente (FOV real)", ys.contains(meiaAltura))
    }

    @Test
    fun caixaDeAreaTemArestasDoVolumeEmArame() {
        val c = EditorHelpers.buildCaixa(floatArrayOf(0f, 1f, 0f))
        // 12 arestas × 2 vértices × 6 floats
        assertEquals(12 * 2 * 6, c.size)
        fun FloatArray.tem(v: Float) = any { it == v }
        assertTrue("vértice no canto (+0,5,+0,5,+0,5) presente", c.tem(0.5f))
        assertTrue("vértice no canto (−0,5) presente", c.tem(-0.5f))
    }

    // ---------------- H2 (v0.1.20): alcance e ângulo REAIS ----------------

    @Test
    fun esferaAlcanceUsaORaioReal() {
        val c = EditorHelpers.buildEsferaAlcance(floatArrayOf(1f, 1f, 1f), 3f)
        assertTrue("pares de vértices (6 floats)", c.size % 6 == 0 && c.size >= 6)
        val maxAbs = (0 until c.size / 6).maxOf {
            maxOf(kotlin.math.abs(c[it * 6]), kotlin.math.abs(c[it * 6 + 1]), kotlin.math.abs(c[it * 6 + 2]))
        }
        assertEquals("maior |coord| = alcance 3 m", 3f, maxAbs, 1e-3f)
    }

    @Test
    fun coneAlcanceUsaAnguloEAlturaReais() {
        val altura = 5f
        val externo = 40f
        val c = EditorHelpers.buildConeAlcance(floatArrayOf(1f, 1f, 1f), externo, altura)
        assertTrue(c.size % 6 == 0 && c.size >= 6)
        val maxZ = (0 until c.size / 6).maxOf { kotlin.math.abs(c[it * 6 + 2]) }
        assertEquals("boca do cone a −altura (−5)", altura, maxZ, 1e-3f)
        val raioEsperado = altura * kotlin.math.tan(Math.toRadians((externo * 0.5f).toDouble())).toFloat()
        val maxXY = (0 until c.size / 6).maxOf {
            maxOf(kotlin.math.abs(c[it * 6]), kotlin.math.abs(c[it * 6 + 1]))
        }
        assertEquals("raio da boca = altura × tan(externo/2)", raioEsperado, maxXY, 1e-3f)
    }
}
