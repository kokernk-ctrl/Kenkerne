package com.kenkern.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * C0 (v0.1.25): TODOS os 7 corpos (rigid, character, player, enemy, npc,
 * collider, static) têm forma visível própria no editor — NUNCA cubo cinza genérico.
 */
class BodyMeshesTest {

    @Test
    fun `todos os 7 corpos tem forma geometrica valida e nao sao cubo cinza`() {
        val cor = FloatArray(3)
        val assinaturas = HashSet<String>()
        for (kind in BodyMeshes.KINDS_CORPO) {
            assertTrue("$kind deve ter forma própria", BodyMeshes.temFormaPropria(kind))
            val idx = BodyMeshes.indiceDe(kind)
            assertTrue("$kind índice válido ($idx)", idx in 0 until BodyMeshes.TOTAL)
            val malha = BodyMeshes.construirPorKind(kind)
            assertNotNull("$kind gerou MeshData", malha)
            malha!!
            assertTrue("$kind tem vértices (${malha.vertexCount})", malha.vertexCount > 24)
            assertTrue("$kind tem índices múltiplos de 3", malha.indices.isNotEmpty() && malha.indices.size % 3 == 0)
            assertEquals(malha.vertexCount * 3, malha.positions.size)
            assertEquals(malha.vertexCount * 3, malha.normals.size)
            assertEquals(malha.vertexCount * 2, malha.uvs.size)
            for (v in malha.positions) assertTrue("$kind posição finita", v.isFinite())
            for (n in malha.normals) assertTrue("$kind normal finita", n.isFinite())
            for (i in malha.indices) assertTrue("$kind índice dentro do range", i in 0 until malha.vertexCount)

            BodyMeshes.corDoCorpo(idx, cor)
            val ehCinzaGenerico = abs(cor[0] - 0.604f) < 0.02f &&
                abs(cor[1] - 0.604f) < 0.02f &&
                abs(cor[2] - 0.604f) < 0.02f
            assertTrue("$kind NÃO pode usar cinza genérico #9A9A9A", !ehCinzaGenerico)

            assinaturas.add("${malha.vertexCount}:${malha.indices.size}:${cor[0]},${cor[1]},${cor[2]}")
        }
        assertEquals("cada um dos 7 corpos tem assinatura distinta", 7, assinaturas.size)
        assertNotEquals(-1, BodyMeshes.indiceDe("body.static"))
        assertEquals(-1, BodyMeshes.indiceDe("nick.mesh"))
    }
}
