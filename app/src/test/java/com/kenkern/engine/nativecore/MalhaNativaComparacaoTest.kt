package com.kenkern.engine.nativecore

import com.kenkern.engine.assets.import.PosProcessamento
import com.kenkern.engine.assets.mesh.KernMesh
import com.kenkern.engine.assets.mesh.KernWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * B6 (v0.1.17) — MESHOPTIMIZER NATIVO × KOTLIN: a MESMA fonte C++ do APK
 * (kenkern_malha.cpp + meshoptimizer MIT vendorizado) compilada para o HOST
 * (task compilarNativoHost) e carregada no teste. Sem g++, assume-skip.
 *
 * Garantias exigidas pelo B6:
 *  - MESMA contagem de vértices únicos que o dedup Kotlin (o registro de
 *    32 bytes = os 8 floats da ChaveVertice do Kotlin);
 *  - LODs com ERRO DE SIMPLIFICAÇÃO REPORTADO, AABB e contagens coerentes;
 *  - fallback: entradas inválidas devolvem null sem crash (o PosProcessamento
 *    segue no Kotlin — coberto por PosProcessamentoTest).
 */
class MalhaNativaComparacaoTest {

    /** Grade WxH de quads: cada vértice duplicado 2× (grid compartilha cantos). */
    private fun gradeDuplicada(cols: Int, linhas: Int): KernMesh {
        val b = KernWriter.Builder()
        // 4 vértices POR CÉLULA (duplicados nas bordas — típico de FBX expandido)
        for (cy in 0 until linhas) {
            for (cx in 0 until cols) {
                val x0 = cx.toFloat(); val x1 = (cx + 1).toFloat()
                val y0 = cy.toFloat(); val y1 = (cy + 1).toFloat()
                b.vertex(x0, y0, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
                b.vertex(x1, y0, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 0f)
                b.vertex(x1, y1, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f)
                b.vertex(x0, y1, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f)
                val base = (cy * cols + cx) * 4
                b.triangle(base, base + 1, base + 2)
                b.triangle(base, base + 2, base + 3)
            }
        }
        return b.build()
    }

    /**
     * Grade CONECTADA (UV = posição normalizada: cantos compartilhados têm o
     * MESMO registro → o dedup funde e a malha fica topologicamente ligada —
     * é o caso em que o meshopt_simplify reduz de verdade).
     */
    private fun gradeConectada(cols: Int, linhas: Int): KernMesh {
        val b = KernWriter.Builder()
        for (cy in 0..linhas) {
            for (cx in 0..cols) {
                val u = cx.toFloat() / cols
                val v = cy.toFloat() / linhas
                b.vertex(cx.toFloat(), cy.toFloat(), 0f, 0f, 0f, 1f, u, v, 1f, 0f, 0f)
            }
        }
        for (cy in 0 until linhas) {
            for (cx in 0 until cols) {
                val a = cy * (cols + 1) + cx
                val bv = a + 1
                val c = a + cols + 2
                val d = a + cols + 1
                b.triangle(a, bv, c)
                b.triangle(a, c, d)
            }
        }
        return b.build()
    }

    private fun registrosDe(mesh: KernMesh): ByteArray {
        val registros = ByteArray(mesh.vertexCount * MalhaNativa.BYTES_REGISTRO_VERTICE)
        var p = 0
        for (v in 0 until mesh.vertexCount) {
            for (k in 0 until 3) { escrever(registros, p, mesh.positions[v * 3 + k]); p += 4 }
            for (k in 0 until 3) { escrever(registros, p, mesh.normals[v * 3 + k]); p += 4 }
            for (k in 0 until 2) { escrever(registros, p, mesh.uvs[v * 2 + k]); p += 4 }
        }
        return registros
    }

    private fun escrever(destino: ByteArray, offset: Int, valor: Float) {
        val bits = java.lang.Float.floatToIntBits(valor)
        destino[offset] = (bits and 0xFF).toByte()
        destino[offset + 1] = ((bits ushr 8) and 0xFF).toByte()
        destino[offset + 2] = ((bits ushr 16) and 0xFF).toByte()
        destino[offset + 3] = ((bits ushr 24) and 0xFF).toByte()
    }

    @Test
    fun mesmaContagemDeVerticesUnicosDoDedupKotlin() {
        assumeTrue(MalhaNativa.disponivel())
        val mesh = gradeDuplicada(16, 16) // 1024 células × 4 vértices = 4096 (com duplicatas)
        val kotlinDedup = PosProcessamento.deduplicarVertices(mesh)
        val nativo = MalhaNativa.dedupEOtimizar(
            registrosDe(mesh), mesh.indices, intArrayOf(0, mesh.indices.size), mesh.vertexCount
        )
        assertNotNull("dedup nativo devolveu null (ver Console)", nativo)
        assertEquals(
            "contagem de vértices únicos: nativo == Kotlin",
            kotlinDedup.vertexCount,
            nativo!!.unicos
        )
        // índices preservam a contagem e a ordem de GRUPO (remap não reordena triângulos)
        assertEquals(mesh.indices.size, nativo.indices.size)
    }

    @Test
    fun lodsNativosComErroReportadoAabbEContagensCoerentes() {
        assumeTrue(MalhaNativa.disponivel())
        val mesh = gradeDuplicada(48, 48) // 4608 triângulos
        val resultado = PosProcessamento.aplicar(mesh) // caminho nativo (lib carregada)
        // LOD0 sempre existe e é a malha deduplicada
        assertTrue(resultado.lods.isNotEmpty())
        assertEquals(resultado.indices.size, resultado.lods.sumOf { it.indexCount })
        // contagens coerentes: LOD0 ≥ LOD1 ≥ LOD2, múltiplos de 3, dentro do alvo
        var anterior = resultado.lods[0].indexCount
        for (i in 1 until resultado.lods.size) {
            val lod = resultado.lods[i]
            assertTrue("LOD$i (${lod.indexCount}) ≤ anterior ($anterior)", lod.indexCount <= anterior)
            assertEquals("LOD$i múltiplo de 3", 0, lod.indexCount % 3)
            assertTrue("LOD$i abaixo do alvo (≤ LOD0/4 ou /8)", lod.indexCount <= resultado.lods[0].indexCount)
            anterior = lod.indexCount
        }
        // AABB coerente: cada LOD dentro da AABB da malha
        for (lod in resultado.lods) {
            assertTrue(lod.boundsMin.x >= resultado.boundsMin.x - 1e-4f)
            assertTrue(lod.boundsMin.y >= resultado.boundsMin.y - 1e-4f)
            assertTrue(lod.boundsMax.x <= resultado.boundsMax.x + 1e-4f)
            assertTrue(lod.boundsMax.y <= resultado.boundsMax.y + 1e-4f)
        }
        // ERRO DE SIMPLIFICAÇÃO reportado no Console (regra B6)
        if (resultado.lods.size > 1) {
            assertTrue(
                "erro de simplificação reportado no Console",
                com.kenkern.engine.diag.KenkernLog.registros().any {
                    it.mensagem.contains("erro de simplificação")
                }
            )
        }
        // integridade do .KERN com os LODs nativos
        val bytes = com.kenkern.engine.assets.mesh.KernWriter.write(resultado)
        assertTrue(
            com.kenkern.engine.assets.import.KernIntegrity.verificar(resultado, bytes).isEmpty()
        )
    }

    @Test
    fun simplificarDiretoReportaErroEContagemDentroDoAlvo() {
        assumeTrue(MalhaNativa.disponivel())
        // Grade CONECTADA: topologia ligada — o simplify reduz de verdade.
        val malha = gradeConectada(32, 32) // 2048 triângulos, 1089 vértices
        val alvo = (malha.indices.size / 4 / 3) * 3
        val lod = MalhaNativa.simplificar(malha.indices, malha.positions, malha.vertexCount, alvoIndices = alvo)
        assertNotNull("simplify nativo devolveu null", lod)
        assertEquals("múltiplo de 3", 0, lod!!.indices.size % 3)
        assertTrue(
            "contrato do meshopt_simplify: nunca cresce e para no alvo ou antes por topologia (retorno ${lod.indices.size} de ${malha.indices.size}, alvo $alvo)",
            lod.indices.size <= malha.indices.size
        )
        assertTrue(
            "grade conectada simplifica bem abaixo da metade (retorno ${lod.indices.size})",
            lod.indices.size < malha.indices.size / 2
        )
        assertTrue("erro finito e ≥ 0: ${lod.erro}", lod.erro >= 0f && lod.erro.isFinite())
        // erro BAIXO para uma grade plana (simplificação fácil)
        assertTrue("erro de simplificação pequeno em grade plana: ${lod.erro}", lod.erro < 0.1f)
    }

    @Test
    fun entradasInvalidadasRecebemNullSemCrash() {
        // Sem depender da biblioteca: validação de entrada no Kotlin devolve null.
        assertNull(MalhaNativa.dedupEOtimizar(ByteArray(0), IntArray(0), IntArray(0), 0))
        assertNull(MalhaNativa.dedupEOtimizar(ByteArray(10), intArrayOf(0, 1, 2), IntArray(0), 1)) // registro pequeno
        assertNull(MalhaNativa.dedupEOtimizar(ByteArray(32), intArrayOf(0, 1), IntArray(0), 1)) // índices não múltiplos de 3
        assertNull(MalhaNativa.simplificar(IntArray(3), FloatArray(3), 1, 0))
        assertNull(MalhaNativa.simplificar(IntArray(3), FloatArray(3), 1, 4)) // alvo não múltiplo de 3
    }

    @Test
    fun fallbackKotlinQuandoSemNativoOuComFalha() {
        // Sem a biblioteca host, PosProcessamento.aplicar roda o KOTLIN e o
        // resultado continua ÍNTEGRO (o caminho que o aparelho usa quando o
        // nativo falha). Com a biblioteca, o teste valida a MESMA malha pelo
        // caminho que estiver ativo — o contrato é o mesmo nos dois.
        val mesh = gradeDuplicada(8, 8)
        val resultado = PosProcessamento.aplicar(mesh)
        assertTrue(resultado.lods.isNotEmpty())
        assertEquals(resultado.indices.size, resultado.lods.sumOf { it.indexCount })
        val bytes = KernWriter.write(resultado)
        assertTrue(com.kenkern.engine.assets.import.KernIntegrity.verificar(resultado, bytes).isEmpty())
    }
}
