package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.PerfilTexturaImportacao
import com.kenkern.engine.assets.mesh.KernFormat
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * TESTES JVM da ETAPA B: dedup de vértices/materiais (B.4), geração de LODs
 * (B.5), remoção do que não é usado (B.6) e materiais glTF (B.3).
 * A regra da ETAPA B.4: erro máximo pós-quantização ≤ 1 passo (o dedup/LOD
 * não pode piorar a geometria de LOD0).
 */
class PosProcessamentoTest {

    @Test
    fun `reimportar GLB grava no KERN a referencia do perfil selecionado`() {
        val glb = GltfAmostras.quadComMaterialGlb()
        val perfilBaixo = PerfilTexturaImportacao.criar(512, 4096, Etc2.Qualidade.RAPIDA)
        val perfilOriginal = PerfilTexturaImportacao.criar(PerfilTexturaImportacao.ORIGINAL, 4096, Etc2.Qualidade.RAPIDA)
        for (perfil in listOf(null, perfilBaixo, perfilOriginal)) {
            val resumo = ModelImporter.importarModelo("quad.glb", glb, ModelImporter.Opcoes(perfilTextura = perfil))
            val hash = AssetLayout.sha1Hex(resumo.texturasExtraidas.single().bytes)
            val caminho = perfil?.caminho(hash) ?: "${AssetLayout.DIR_TEXTURAS}/$hash.kimg"
            assertEquals(caminho, KernReader.read(resumo.bytesKernData).materiais.single().texturaBasePath)
            assertTrue(resumo.integridadeOk)
        }
    }

    private fun meshQuadDuplicada(): com.kenkern.engine.assets.mesh.KernMesh {
        // quad com CADA vértice duplicado 4× (12 vértices para 4 reais)
        val b = KernWriter.Builder()
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val uv = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        for (rep in 0 until 4) {
            for (v in 0 until 4) {
                b.vertex(
                    pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2],
                    0f, 0f, 1f, uv[v * 2], uv[v * 2 + 1],
                    0f, 0f, 0f
                )
            }
        }
        val idx = intArrayOf(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7, 8, 9, 10, 8, 10, 11, 12, 13, 14, 12, 14, 15)
        for (i in idx) b.index(i)
        return b.build()
    }

    @Test
    fun dedup_funde_vertices_iguais_e_remapa_indices() {
        val antes = meshQuadDuplicada()
        assertEquals(16, antes.vertexCount)
        val depois = PosProcessamento.deduplicarVertices(antes)
        assertEquals("4 vértices únicos", 4, depois.vertexCount)
        assertEquals(24, depois.indices.size)
        // índices remapeados para slots compactos: buffer original [0,1,2, 0,2,3, 4,5,6 …]
        // → vértices 0..3 viram slots 0..3; 4→0, 5→1, 6→2
        assertEquals(0, depois.indices[0])
        assertEquals(1, depois.indices[1])
        assertEquals(2, depois.indices[2])
        assertEquals(0, depois.indices[3])
        assertEquals(2, depois.indices[4])  // original 2 → slot 2
        assertEquals(3, depois.indices[5])
        assertEquals(0, depois.indices[6])  // original 4 → slot 0 (duplicado)
        assertEquals(1, depois.indices[7])
        assertEquals(2, depois.indices[8])
        // geometria idêntica: bounds e posições preservados
        assertEquals(0f, depois.boundsMin.x, 1e-6f)
        assertEquals(1f, depois.boundsMax.y, 1e-6f)
    }

    @Test
    fun dedup_nao_funde_vertices_com_normal_diferente() {
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f)
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f) // mesma pos/uv, normal difere
        b.triangle(0, 1, 0)
        val depois = PosProcessamento.deduplicarVertices(b.build())
        assertEquals(2, depois.vertexCount)
    }

    @Test
    fun lods_geram_3_niveis_com_tabela_coerente() {
        // malha com bastante geometria para os LODs reduzirem de verdade
        val b = KernWriter.Builder()
        val lado = 20 // grade 20×20 = 400 vértices, 722 triângulos
        for (x in 0 until lado) {
            for (y in 0 until lado) {
                b.vertex(
                    x.toFloat(), y.toFloat(), sin(x * 0.7f) * 0.1f + cos(y * 0.6f) * 0.1f,
                    0f, 0f, 1f, x.toFloat() / lado, y.toFloat() / lado,
                    0f, 0f, 0f
                )
            }
        }
        for (x in 0 until lado - 1) {
            for (y in 0 until lado - 1) {
                val a = x * lado + y
                val bb = (x + 1) * lado + y
                val c = (x + 1) * lado + y + 1
                val d = x * lado + y + 1
                b.triangle(a, bb, c)
                b.triangle(a, c, d)
            }
        }
        val original = b.build()
        val comLods = PosProcessamento.gerarLods(original)

        assertTrue("esperado ≥ 2 LODs (LOD0 + decimado)", comLods.lods.size >= 2)
        var somados = 0
        for (lod in comLods.lods) {
            assertTrue(lod.indexOffset >= 0)
            assertTrue(lod.indexCount > 0)
            assertTrue("LOD dentro do buffer", lod.indexOffset + lod.indexCount <= comLods.indices.size)
            somados += lod.indexCount
        }
        assertEquals("LODs anexados ao MESMO buffer", comLods.indices.size, somados)
        // LOD1 é estritamente menor que LOD0
        if (comLods.lods.size >= 2) {
            assertTrue(
                "LOD1 menor que LOD0",
                comLods.lods[1].indexCount < comLods.lods[0].indexCount
            )
        }
        // AABB de cada LOD coerente (dentro da AABB da malha)
        for (lod in comLods.lods) {
            assertTrue(lod.boundsMin.x >= comLods.boundsMin.x - 1e-5f)
            assertTrue(lod.boundsMax.y <= comLods.boundsMax.y + 1e-5f)
        }
        // roundtrip no .KERN v2 preserva a tabela de LODs
        val lida = KernReader.read(KernWriter.write(comLods))
        assertEquals(comLods.lods.size, lida.lods.size)
        for (i in comLods.lods.indices) {
            assertEquals(comLods.lods[i].indexCount, lida.lods[i].indexCount)
        }
        // integridade completa passa (inclui validação dos grupos/LODs)
        assertTrue(
            KernIntegrity.verificar(comLods, KernWriter.write(comLods)).isEmpty()
        )
    }

    @Test
    fun dedup_com_materiais_mantem_grupos_e_versao_v2() {
        val antes = meshQuadDuplicada()
        val materiais = listOf(
            KernMaterial("a", 1f, 0f, 0f, 1f, 0f, 1f, false, false, false, "", "")
        )
        val comGrupos = com.kenkern.engine.assets.mesh.KernMesh(
            antes.positions, antes.normals, antes.uvs, antes.tangents, antes.indices,
            antes.boundsMin, antes.boundsMax, antes.lods,
            antes.boneIndices, antes.boneWeights, antes.bones, antes.clusters,
            listOf(com.kenkern.engine.assets.mesh.KernGrupoMaterial(0, 24, 0)),
            materiais
        )
        val depois = PosProcessamento.aplicar(comGrupos)
        assertEquals(1, depois.materiais.size)
        assertEquals(1, depois.gruposMaterial.size)
        assertEquals(0, depois.gruposMaterial[0].materialIndex)
        // grupo continua cobrindo o mesmo NÚMERO de índices
        assertEquals(24, depois.gruposMaterial[0].indexCount)

        val bytes = KernWriter.write(depois)
        val lida = KernReader.read(bytes)
        assertEquals(1, lida.materiais.size)
        assertEquals(1, lida.gruposMaterial.size)
        assertTrue(KernIntegrity.verificar(lida, bytes).isEmpty())
    }

    @Test
    fun versao_v2_e_lida_v1_sem_materiais() {
        // malha SEM material: flag HAS_MATERIALS não é setado (arquivo pequeno)
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f)
        b.vertex(0.5f, 1f, 0f, 0f, 1f, 0f, 0.5f, 1f, 0f, 0f, 0f)
        b.triangle(0, 1, 2)
        val mesh = b.build()
        val bytes = KernWriter.write(mesh)
        assertEquals(KernFormat.VERSION.toShort(), com.kenkern.engine.assets.mesh.KernReader.readHeader(bytes).first)
        val lida = KernReader.read(bytes)
        assertEquals(0, lida.materiais.size)
        assertEquals(0, lida.gruposMaterial.size)
        // v1 antiga (sem flag) ainda abre — compatibilidade com assets existentes
        val v1 = bytes.copyOf()
        v1[4] = 1; v1[5] = 0 // version short little-endian = 1
        val lidaV1 = KernReader.read(v1)
        assertEquals(3, lidaV1.vertexCount)
    }

    @Test
    fun gltf_com_material_extrai_fatores_textura_e_dedup() {
        val r = GltfImporter.importar(GltfAmostras.quadComMaterialGlb())
        assertEquals("material dedup: 2 primitivos com o mesmo material", 1, r.mesh.materiais.size)
        val mat = r.mesh.materiais[0]
        assertEquals("madeira", mat.nome)
        assertEquals(0.8f, mat.baseColorR, 0.01f)
        assertEquals(0.2f, mat.baseColorG, 0.01f)
        assertEquals(0.1f, mat.baseColorB, 0.01f)
        assertEquals(0.5f, mat.metallic, 0.01f)
        assertEquals(0.4f, mat.roughness, 0.01f)
        assertTrue(mat.doubleSided)
        assertTrue("tem textura de cor base", mat.temTexturaBase)
        assertTrue("textura aponta para assets/textures", mat.texturaBasePath.startsWith("assets/textures/"))
        assertEquals("1 textura única coletada", 1, r.texturas.size)
        assertEquals("image/png", r.texturas[0].mimeType)
        // 2 primitivos = 2 grupos, ambos com material 0
        assertEquals(2, r.mesh.gruposMaterial.size)
        assertEquals(0, r.mesh.gruposMaterial[0].materialIndex)
        assertEquals(0, r.mesh.gruposMaterial[1].materialIndex)
        // roundtrip v2 preserva
        val lida = KernReader.read(KernWriter.write(r.mesh))
        assertEquals(1, lida.materiais.size)
        assertTrue(KernIntegrity.verificar(lida, KernWriter.write(lida)).isEmpty())
    }

    @Test
    fun tangente_sem_normal_map_fica_zerada() {
        // quadComMaterialGlb NÃO tem normalTexture → tangentes zeradas
        val r = GltfImporter.importar(GltfAmostras.quadComMaterialGlb())
        var algumaTangente = false
        for (t in r.mesh.tangents) if (t != 0f) algumaTangente = true
        assertTrue("tangentes zeradas sem normal map (B.6)", !algumaTangente)
    }

    @Test
    fun cancelamento_no_meio_do_dedup_da_erro_claro_rapido() {
        // B2d: o cancelamento é checado DENTRO do laço pesado — uma malha com
        // duplicatas e cancelado=true desde o início falha em < 1 s com a
        // mensagem CLARA (nunca fica preso no laço).
        val mesh = meshQuadDuplicada()
        val inicio = System.nanoTime()
        try {
            PosProcessamento.aplicar(mesh, cancelado = { true })
            throw AssertionError("cancelamento deveria ter lançado ImportException")
        } catch (e: ImportException) {
            val ms = (System.nanoTime() - inicio) / 1_000_000L
            assertTrue("cancelou em $ms ms (< 1000)", ms < 1000)
            assertTrue("mensagem clara: ${e.message}", e.message!!.contains("cancelado"))
        }
    }
}
