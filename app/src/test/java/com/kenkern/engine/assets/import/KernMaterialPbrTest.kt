package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernFormat
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T0.4 (v0.1.20) — FIDELIDADE DE IMPORTAÇÃO: material PBR COMO ASSET no
 * .KERN v3 (cor base, normal, metálico/rugosidade, oclusão, emissivo e
 * fatores), compatibilidade v2 e detecção honesta de skinning.
 */
class KernMaterialPbrTest {

    @Test
    fun `material PBR com os 5 mapas sobrevive ao roundtrip v3`() {
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.triangle(0, 1, 2)
        b.material(
            KernMaterial(
                "pbr-completo", 0.8f, 0.6f, 0.4f, 1f, 0.35f, 0.7f,
                true, true, true, "assets/textures/base.kimg", "assets/textures/normal.kimg",
                "assets/textures/mr.kimg", "assets/textures/ao.kimg", "assets/textures/emiss.kimg",
                0.9f, 0.1f, 0.05f, 2.5f, 1.2f, 0.8f
            )
        )
        b.materialGroup(0, 3, 0)
        val bytes = KernWriter.write(b.build())
        val (versao, _, _) = KernReader.readHeader(bytes)
        assertEquals("PBR completo grava v3", KernFormat.VERSION.toShort(), versao)
        val lida = KernReader.read(bytes)
        val m = lida.materiais.firstOrNull()
        assertNotNull("tabela de materiais", m)
        m!!
        assertEquals("assets/textures/base.kimg", m.texturaBasePath)
        assertEquals("assets/textures/normal.kimg", m.texturaNormalPath)
        assertEquals("assets/textures/mr.kimg", m.texturaMrPath)
        assertEquals("assets/textures/ao.kimg", m.texturaOclusaoPath)
        assertEquals("assets/textures/emiss.kimg", m.texturaEmissivaPath)
        assertEquals(0.9f, m.emissiveR, 1e-3f)
        assertEquals(0.1f, m.emissiveG, 1e-3f)
        assertEquals(0.05f, m.emissiveB, 1e-3f)
        assertEquals(2.5f, m.emissiveIntensity, 1e-3f)
        assertEquals(1.2f, m.normalScale, 1e-3f)
        assertEquals(0.8f, m.oclusaoStrength, 1e-3f)
        assertEquals(0.35f, m.metallic, 1e-3f)
        assertEquals(0.7f, m.roughness, 1e-3f)
        // grupos preservados
        assertEquals(1, lida.gruposMaterial.size)
        assertEquals(0, lida.gruposMaterial[0].materialIndex)
    }

    @Test
    fun `material sem extensão PBR segue gravando v2 compatibilidade`() {
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.triangle(0, 1, 2)
        b.material(KernMaterial("v2", 1f, 1f, 1f, 1f, 0f, 1f, true, false, false, "assets/textures/b.kimg", ""))
        b.materialGroup(0, 3, 0)
        val bytes = KernWriter.write(b.build())
        val (versao, _, _) = KernReader.readHeader(bytes)
        assertEquals("sem PBR = v2 byte-compatível", KernFormat.VERSION_V2.toShort(), versao)
        val lida = KernReader.read(bytes)
        assertEquals("assets/textures/b.kimg", lida.materiais.first().texturaBasePath)
    }

    @Test
    fun `glTF importa todos os mapas e fatores que o KERN v3 preserva`() {
        val glb = quadPbrGlb()
        val r = GltfImporter.importar(glb)
        val m = r.mesh.materiais.firstOrNull() ?: throw AssertionError("sem material")
        assertTrue("MR map importado", m.texturaMrPath.isNotEmpty())
        assertTrue("oclusão importada", m.texturaOclusaoPath.isNotEmpty())
        assertTrue("emissiva importada", m.texturaEmissivaPath.isNotEmpty())
        assertTrue("normal importada", m.temTexturaNormal)
        assertEquals(0.25f, m.metallic, 1e-3f)
        assertEquals(0.5f, m.roughness, 1e-3f)
        assertEquals(1.5f, m.emissiveIntensity, 1e-3f)
        assertEquals(0.6f, m.normalScale, 1e-3f)
        assertEquals(0.7f, m.oclusaoStrength, 1e-3f)
        // O modelo chega COMO FOI BAIXADO: o .KERN v3 preserva tudo
        val lida = KernReader.read(KernWriter.write(r.mesh))
        val m2 = lida.materiais.first()
        assertEquals(m.texturaMrPath, m2.texturaMrPath)
        assertEquals(m.texturaOclusaoPath, m2.texturaOclusaoPath)
        assertEquals(m.texturaEmissivaPath, m2.texturaEmissivaPath)
        assertEquals(0.25f, m2.metallic, 1e-3f)
        assertEquals(1.5f, m2.emissiveIntensity, 1e-3f)
    }

    @Test
    fun `skinning detectado gera causa clara no relatório`() {
        val glb = quadComSkinGlb()
        val r = GltfImporter.importar(glb)
        assertEquals("4 vértices com JOINTS/WEIGHTS contados", 4, r.report.verticesComSkin)
        val avisos = r.avisos.joinToString(" | ")
        assertTrue(
            "causa SKIN ausente no relatório: $avisos",
            avisos.contains("SKIN: 4 vértices") && avisos.contains("pose de bind")
        )
    }

    @Test
    fun `tangentes do KERN chegam à malha de render`() {
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.triangle(0, 1, 2)
        val lida = KernReader.read(KernWriter.write(b.build()))
        val meshData = MeshData.fromKern(lida)
        assertEquals("tangentes por vértice na MeshData", lida.vertexCount * 3, meshData.tangents.size)
        assertEquals(1f, meshData.tangents[0], 1e-2f) // octaédrico (1,0,0) ida e volta
    }

    // ------------------------------------------------------------ helpers

    /** Imagem "qualquer" em data URI (o importador só coleta os bytes). */
    private fun dataUri(conteudo: String): String =
        "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(conteudo.toByteArray())

    /** Quad com material PBR COMPLETO (MR + oclusão + emissiva + normal + fatores). */
    private fun quadPbrGlb(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val norm = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val uv = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val tang = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val dados = java.io.ByteArrayOutputStream()
        dados.write(vetorF32(pos))
        dados.write(vetorF32(norm))
        dados.write(vetorF32(uv))
        dados.write(vetorF32(tang))
        dados.write(vetorU16(idx))
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size()}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${norm.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4 + norm.size * 4}, "byteLength": ${uv.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4 + norm.size * 4 + uv.size * 4}, "byteLength": ${tang.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4 + norm.size * 4 + uv.size * 4 + tang.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": 4, "type": "VEC2"},
            {"bufferView": 3, "componentType": 5126, "count": 4, "type": "VEC3"},
            {"bufferView": 4, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "images": [
            {"uri": "${dataUri("mr")}", "name": "mr"},
            {"uri": "${dataUri("ao")}", "name": "ao"},
            {"uri": "${dataUri("emiss")}", "name": "emiss"},
            {"uri": "${dataUri("normal")}", "name": "normal"}
          ],
          "textures": [{"source": 0}, {"source": 1}, {"source": 2}, {"source": 3}],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2, "TANGENT": 3}, "indices": 4, "mode": 4, "material": 0}]}],
          "materials": [{"name": "pbr", "pbrMetallicRoughness": {
              "baseColorFactor": [0.8, 0.6, 0.4, 1],
              "metallicFactor": 0.25, "roughnessFactor": 0.5,
              "metallicRoughnessTexture": {"index": 0},
              "baseColorTexture": {"index": 3}},
            "normalTexture": {"index": 3, "scale": 0.6},
            "occlusionTexture": {"index": 1, "strength": 0.7},
            "emissiveTexture": {"index": 2},
            "emissiveFactor": [0.9, 0.1, 0.05],
            "extensions": {"KHR_materials_emissive_strength": {"emissiveStrength": 1.5}}
          }],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados.toByteArray())
    }

    /** Quad com skinning (JOINTS_0/WEIGHTS_0) — skin NÃO aplicado (Fase 8). */
    private fun quadComSkinGlb(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val joints = shortArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3)
        val weights = shortArrayOf(255, 0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val dados = java.io.ByteArrayOutputStream()
        dados.write(vetorF32(pos))
        dados.write(vetorU16(joints))
        dados.write(vetorU16(weights))
        dados.write(vetorU16(idx))
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size()}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${joints.size * 2}},
            {"buffer": 0, "byteOffset": ${pos.size * 4 + joints.size * 2}, "byteLength": ${weights.size * 2}},
            {"buffer": 0, "byteOffset": ${pos.size * 4 + joints.size * 2 + weights.size * 2}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 4, "type": "VEC4"},
            {"bufferView": 2, "componentType": 5123, "count": 4, "type": "VEC4", "normalized": true},
            {"bufferView": 3, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "skins": [{}],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2}, "indices": 3, "mode": 4}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados.toByteArray())
    }

    private fun vetorF32(v: FloatArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val bb = java.nio.ByteBuffer.allocate(v.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (f in v) bb.putFloat(f)
        out.write(bb.array())
        return out.toByteArray()
    }

    private fun vetorU16(v: ShortArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val bb = java.nio.ByteBuffer.allocate(v.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in v) bb.putShort(s)
        out.write(bb.array())
        return out.toByteArray()
    }
}
