package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Testes JVM do importador glTF/GLB → .KERN (BACKLOG F2.7):
 * GLB (chunks JSON+BIN), .gltf (data URI), transform de nodes (TRS/quaternion
 * + herança), merge de primitivas, meta de compressão, teste de integridade e
 * ERROS CLAROS (regra: nada silencioso).
 */
class GltfImporterTest {

    // ------------------------------------------------------------ happy path

    @Test
    fun quad_glb_completo_importa_com_normais_uv_e_integridade() {
        val r = GltfImporter.importar(GltfAmostras.quadGlb())
        assertEquals(4, r.mesh.vertexCount)
        assertEquals(6, r.mesh.indices.size)
        assertEquals(2, r.mesh.indices.size / 3) // triângulos
        assertEquals(1, r.mesh.lods.size) // regra: toda malha tem ≥ 1 LOD
        assertEquals(1, r.report.primitivas)
        assertFalse(r.report.normaisCalculadas) // NORMAL veio no arquivo
        // AABB do quad: min (0,0,0) max (1,1,0)
        assertEquals(0f, r.mesh.boundsMin.x, 1e-5f)
        assertEquals(1f, r.mesh.boundsMax.x, 1e-5f)
        assertEquals(1f, r.mesh.boundsMax.y, 1e-5f)
        // Normais vieram do arquivo: todas +Z
        assertEquals(1f, r.mesh.normals[2], 1e-4f)
        // UV do vértice 2 = (1,1)
        assertEquals(1f, r.mesh.uvs[4], 1e-5f)
        assertEquals(1f, r.mesh.uvs[5], 1e-5f)
        // Integridade: roundtrip .KERN sem problemas
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do quad falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun gltf_com_data_uri_importa_e_calcula_normais_por_face() {
        val r = GltfImporter.importar(GltfAmostras.quadGltfDataUri())
        assertEquals(4, r.mesh.vertexCount)
        assertEquals(6, r.mesh.indices.size)
        assertTrue(r.report.normaisCalculadas) // sem NORMAL no arquivo
        // Normais acumuladas (2 faces coplanares) → +Z unitário
        assertEquals(0f, r.mesh.normals[0], 1e-4f)
        assertEquals(0f, r.mesh.normals[1], 1e-4f)
        assertEquals(1f, r.mesh.normals[2], 1e-3f)
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do quad data-URI falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun cubo_com_transform_de_nodes_faz_merge_e_aplica_matriz() {
        val r = GltfImporter.importar(GltfAmostras.cuboComTransformGlb())
        // 2 instâncias × 8 vértices = 16; 2 × 36 índices = 72
        assertEquals(16, r.mesh.vertexCount)
        assertEquals(72, r.mesh.indices.size)
        assertEquals(2, r.report.nodesComMesh)
        assertEquals(2, r.report.primitivas)
        assertTrue(r.report.normaisCalculadas) // sem NORMAL no cubo

        // Node 0 tem escala (2,1,1) → AABB em X dobra (±1) e rotação 45° em Y
        // mistura X/Z; node 1 sobe 3 em Y. Checagens conservadoras:
        val maxx = maxAbsComponente(r.mesh.positions, 0)
        val maxy = maxAbsComponente(r.mesh.positions, 1)
        assertTrue("X deve crescer com escala 2 (maxx=$maxx)", maxx > 0.99f)
        assertTrue("Y deve ter vértice em 3±0.5 (maxy=$maxy)", maxy > 2.9f)

        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do cubo com transform falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun meta_de_compressao_denso_gera_relatorio_e_kern_menor_que_origem() {
        val r = GltfImporter.importar(GltfAmostras.densoGlb(30000))
        assertEquals(30000, r.mesh.vertexCount)
        // glTF: 32B/vértice (pos12+norm12+uv8) + índices u16 → .KERN 24B/vértice
        assertTrue(
            "razão de compressão esperada ≥ 1.2 (foi ${r.report.razao})",
            r.report.razao >= 1.2f
        )
        assertTrue(
            "bytesKern (${r.report.bytesKern}) deve ser menor que origem (${r.report.bytesOrigem})",
            r.report.bytesKern < r.report.bytesOrigem
        )
        // A meta 100 MB → ≤ 20 MB (≥ 5×) é da pipeline completa (F2.8+):
        // o relatório registra, o teste documenta o estado atual.
        assertFalse(r.report.metaRoadmap100Para20)
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do denso falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun resumo_do_relatorio_monta_texto_legivel() {
        val r = GltfImporter.importar(GltfAmostras.quadGlb())
        val resumo = r.report.resumo()
        assertTrue(resumo.contains("origem"))
        assertTrue(resumo.contains(".KERN"))
        assertTrue(resumo.contains("4 vértices"))
    }

    // ------------------------------------------------------------ integridade

    @Test
    fun integridade_repassa_o_roundtrip_do_kernreader() {
        val r = GltfImporter.importar(GltfAmostras.quadGlb())
        val reaberta = KernReader.read(r.bytesKern)
        assertEquals(r.mesh.vertexCount, reaberta.vertexCount)
        for (i in r.mesh.indices.indices) {
            assertEquals(r.mesh.indices[i], reaberta.indices[i])
        }
        val erroPassos = KernIntegrity.medirErroMaximo(r.mesh, r.bytesKern)
        assertTrue("erro de quantização esperado ≤ 1 passo (foi $erroPassos)", erroPassos <= 1.001f)
    }

    // ------------------------------------------------------------ erros claros

    @Test
    fun arquivo_com_magic_quebrado_da_erro_claro() {
        try {
            GltfImporter.importar(GltfAmostras.magicQuebrado())
            assertFalse("deveria ter lançado GltfImportException", true)
        } catch (e: GltfImportException) {
            assertTrue(e.message!!.contains("glTF") || e.message!!.contains(".gltf"))
        }
    }

    @Test
    fun acessor_inexistente_da_erro_claro() {
        try {
            GltfImporter.importar(GltfAmostras.acessorFaltando())
            assertFalse("deveria ter lançado GltfImportException", true)
        } catch (e: GltfImportException) {
            assertTrue(e.message!!.contains("acessor"))
        }
    }

    @Test
    fun buffer_externo_da_erro_claro_com_orientacao() {
        try {
            GltfImporter.importar(GltfAmostras.bufferExterno())
            assertFalse("deveria ter lançado GltfImportException", true)
        } catch (e: GltfImportException) {
            assertTrue("mensagem deve explicar a solução", e.message!!.contains("embutidos"))
        }
    }

    @Test
    fun glb_truncado_da_erro_claro() {
        val completo = GltfAmostras.quadGlb()
        val truncado = completo.copyOfRange(0, completo.size / 2)
        try {
            GltfImporter.importar(truncado)
            assertFalse("deveria ter lançado GltfImportException", true)
        } catch (e: GltfImportException) {
            // declara X bytes mas o arquivo tem Y — mensagem precisa
            assertTrue(e.message!!.contains("declara"))
        }
    }

    @Test
    fun json_corrompido_da_erro_claro() {
        val lixo = "{\"asset\": {\"version\": \"2.0\"" // JSON incompleto
        try {
            GltfImporter.importar(GltfImporter.empacotarGlb(lixo, null))
            assertFalse("deveria ter lançado GltfImportException", true)
        } catch (e: GltfImportException) {
            assertTrue(e.message!!.contains("JSON"))
        }
    }

    // ------------------------------------------------------------ helpers

    private fun maxAbsComponente(pos: FloatArray, componente: Int): Float {
        var max = 0f
        var i = componente
        while (i < pos.size) {
            max = maxOf(max, abs(pos[i]))
            i += 3
        }
        return max
    }
}
