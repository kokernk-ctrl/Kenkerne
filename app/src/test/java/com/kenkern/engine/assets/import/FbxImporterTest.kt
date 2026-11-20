package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Testes JVM do importador FBX → .KERN (BACKLOG F2.8): parser binário
 * (records u32/u64), arrays zlib, triangulação de polígonos (fan), mappings
 * de normal/UV (ByPolygonVertex/ByVertice/ByPolygon × Direct/IndexToDirect),
 * transform de Models (Lcl T/R/S em graus + herança), merge de instâncias,
 * meta de compressão, integridade e ERROS CLAROS (regra: nada silencioso).
 */
class FbxImporterTest {

    // ------------------------------------------------------------ happy path

    @Test
    fun quad_fbx_completo_importa_com_normais_uv_e_integridade() {
        val r = FbxImporter.importar(FbxAmostras.quadFbx())
        // FBX EXPANDE cantos: 2 polígonos de triângulo = 6 cantos = 6 vértices
        assertEquals(6, r.mesh.vertexCount)
        assertEquals(6, r.mesh.indices.size)
        assertEquals(1, r.mesh.lods.size) // regra: toda malha tem ≥ 1 LOD
        assertEquals(1, r.report.primitivas)
        assertEquals(1, r.report.nodesComMesh)
        assertFalse(r.report.normaisCalculadas) // Normals veio no arquivo
        // AABB do quad: min (0,0,0) max (1,1,0)
        assertEquals(0f, r.mesh.boundsMin.x, 1e-5f)
        assertEquals(1f, r.mesh.boundsMax.x, 1e-5f)
        assertEquals(1f, r.mesh.boundsMax.y, 1e-5f)
        // Posições preservadas (o quad é pequeno — quantização u16 não perde aqui)
        assertEquals(0f, r.mesh.positions[0], 1e-3f)
        assertEquals(1f, r.mesh.positions[3], 1e-3f) // canto 1 = cp 1 (1,0,0)
        // Normais do arquivo: todas +Z
        for (v in 0 until 6) {
            assertEquals(1f, r.mesh.normals[v * 3 + 2], 1e-4f)
        }
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do quad falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun poligono_ngon_quad_triangula_em_leque() {
        val r = FbxImporter.importar(FbxAmostras.ngonQuadFbx())
        // 1 polígono de 4 cantos → fan = 2 triângulos, 4 vértices expandidos
        assertEquals(4, r.mesh.vertexCount)
        assertEquals(6, r.mesh.indices.size)
        // Fan preserva a ordem: (0,1,2) e (0,2,3)
        assertEquals(0, r.mesh.indices[0]); assertEquals(1, r.mesh.indices[1]); assertEquals(2, r.mesh.indices[2])
        assertEquals(0, r.mesh.indices[3]); assertEquals(2, r.mesh.indices[4]); assertEquals(3, r.mesh.indices[5])
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do ngon falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun normais_by_vertice_compartilham_por_control_point() {
        val r = FbxImporter.importar(FbxAmostras.normaisPorVerticeFbx())
        assertEquals(6, r.mesh.vertexCount) // 2 triângulos × 3 cantos expandidos
        // 4 normais por cp → cada canto herda a normal do cp dele (+Z)
        for (v in 0 until 6) {
            assertEquals(0f, r.mesh.normals[v * 3], 1e-4f)
            assertEquals(0f, r.mesh.normals[v * 3 + 1], 1e-4f)
            assertEquals(1f, r.mesh.normals[v * 3 + 2], 1e-4f)
        }
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade ByVertice falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun normais_ausentes_sao_acumuladas_por_face() {
        val r = FbxImporter.importar(FbxAmostras.semNormaisFbx())
        assertEquals(6, r.mesh.vertexCount) // 2 triângulos × 3 cantos expandidos
        assertTrue(r.report.normaisCalculadas)
        // Quad no plano z=0 com winding CCW → normal acumulada = +Z unitária
        for (v in 0 until 6) {
            assertEquals(0f, r.mesh.normals[v * 3], 1e-4f)
            assertEquals(0f, r.mesh.normals[v * 3 + 1], 1e-4f)
            assertEquals(1f, r.mesh.normals[v * 3 + 2], 1e-3f)
        }
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade sem-normais falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun uv_index_to_direct_e_flip_da_convencao_gltf() {
        val r = FbxImporter.importar(FbxAmostras.quadFbx())
        // FBX: v de BAIXO (uv[0]=(0,0) é o canto y=0) → convenção glTF: v' = 1 - v
        // Cantos: (cp,uvIndex) = (0,0)(1,1)(2,2)(0,0)(2,2)(3,3)
        // canto 0: uv (0,0) → (0, 1)
        assertEquals(0f, r.mesh.uvs[0], 1e-5f); assertEquals(1f, r.mesh.uvs[1], 1e-5f)
        // canto 1: uv (1,0) → (1, 1)
        assertEquals(1f, r.mesh.uvs[2], 1e-5f); assertEquals(1f, r.mesh.uvs[3], 1e-5f)
        // canto 2: uv (1,1) → (1, 0)
        assertEquals(1f, r.mesh.uvs[4], 1e-5f); assertEquals(0f, r.mesh.uvs[5], 1e-5f)
        // canto 3: uvIndex 0 → (0, 1)
        assertEquals(0f, r.mesh.uvs[6], 1e-5f); assertEquals(1f, r.mesh.uvs[7], 1e-5f)
        // canto 5: uvIndex 3 → (0, 0)
        assertEquals(0f, r.mesh.uvs[10], 1e-5f); assertEquals(0f, r.mesh.uvs[11], 1e-5f)
    }

    @Test
    fun arrays_comprimidos_zlib_inflam_corretamente() {
        val r = FbxImporter.importar(FbxAmostras.zlibFbx())
        assertEquals(6, r.mesh.vertexCount)
        assertEquals(6, r.mesh.indices.size)
        // Mesmo resultado esperado do quad sem compressão
        assertEquals(1f, r.mesh.normals[2], 1e-4f)
        assertEquals(0f, r.mesh.boundsMin.x, 1e-5f)
        assertEquals(1f, r.mesh.boundsMax.x, 1e-5f)
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade zlib falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun cubo_compartilhado_por_2_models_faz_merge_e_aplica_trs() {
        val r = FbxImporter.importar(FbxAmostras.cuboCompartilhadoFbx())
        // 6 polígonos de quad = 12 triângulos = 36 índices; 24 cantos expandidos
        // por instância; 2 instâncias fundidas = 48 vértices / 72 índices
        assertEquals(48, r.mesh.vertexCount)
        assertEquals(72, r.mesh.indices.size)
        assertEquals(2, r.report.primitivas)   // 2 (geometry, model)
        assertEquals(2, r.report.nodesComMesh) // 2 models
        // Model A: R_y(90°) × S(2,1,1) → z' = ±1 (x original escalado), x' = ±0.5
        assertEquals(0.5f, r.mesh.boundsMax.x, 1e-4f)
        assertEquals(-0.5f, r.mesh.boundsMin.x, 1e-4f)
        // Model B: T(0,3,0) → y vai até 3.5
        assertEquals(3.5f, r.mesh.boundsMax.y, 1e-4f)
        assertEquals(-0.5f, r.mesh.boundsMin.y, 1e-4f)
        // Model A rotacionado: z' ∈ [-1, 1]
        assertEquals(1f, r.mesh.boundsMax.z, 1e-4f)
        assertEquals(-1f, r.mesh.boundsMin.z, 1e-4f)
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade do cubo compartilhado falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun versao_7500_com_headers_u64_importa() {
        val r = FbxImporter.importar(FbxAmostras.v7500Fbx())
        assertEquals(6, r.mesh.vertexCount) // 2 triângulos × 3 cantos
        assertEquals(6, r.mesh.indices.size)
        val problemas = KernIntegrity.verificar(r.mesh, r.bytesKern)
        assertTrue("integridade v7500 falhou: $problemas", problemas.isEmpty())
    }

    @Test
    fun meta_de_compressao_tem_origem_kern_e_razao() {
        val r = FbxImporter.importar(FbxAmostras.quadFbx())
        assertTrue(r.report.bytesOrigem > 0)
        assertTrue(r.report.bytesKern > 0)
        assertTrue(r.report.razao > 0f)
        assertTrue(r.report.resumo().contains(".KERN"))
    }

    @Test
    fun roundtrip_kern_reader_le_a_malha_do_importador() {
        val r = FbxImporter.importar(FbxAmostras.quadFbx())
        val malhaLida = KernReader.read(r.bytesKern)
        assertEquals(r.mesh.vertexCount, malhaLida.vertexCount)
        assertEquals(r.mesh.indices.size, malhaLida.indices.size)
        // Triângulo 0 preservado no roundtrip
        assertEquals(r.mesh.indices[0], malhaLida.indices[0])
        assertEquals(r.mesh.indices[2], malhaLida.indices[2])
    }

    // ------------------------------------------------------------ erros claros

    @Test
    fun fbx_ascii_da_erro_claro_pedindo_binario() {
        try {
            FbxImporter.importar(FbxAmostras.asciiFbx())
            fail("FBX ASCII deveria dar erro claro")
        } catch (e: FbxImportException) {
            assertTrue("mensagem deve citar ASCII/binário: ${e.message}",
                e.message!!.contains("ASCII") && e.message!!.contains("binário"))
        }
    }

    @Test
    fun magic_quebrado_da_erro_claro() {
        try {
            FbxImporter.importar(FbxAmostras.magicQuebrado())
            fail("magic quebrado deveria dar erro claro")
        } catch (e: FbxImportException) {
            assertTrue(e.message!!.contains("magic"))
        }
    }

    @Test
    fun fbx_6x_da_erro_claro_pedindo_conversao() {
        try {
            FbxImporter.importar(FbxAmostras.versao6100Fbx())
            fail("FBX 6.x deveria dar erro claro")
        } catch (e: FbxImportException) {
            assertTrue(e.message!!.contains("6.x"))
        }
    }

    @Test
    fun poligono_aberto_da_erro_claro() {
        try {
            FbxImporter.importar(FbxAmostras.poligonoAbertoFbx())
            fail("polígono sem fechamento deveria dar erro claro")
        } catch (e: FbxImportException) {
            assertTrue(e.message!!.contains("polígono"))
        }
    }

    @Test
    fun arquivo_vazio_da_erro_claro() {
        try {
            FbxImporter.importar(ByteArray(0))
            fail("arquivo vazio deveria dar erro claro")
        } catch (e: FbxImportException) {
            assertTrue(e.message!!.contains("vazio"))
        }
    }
}
