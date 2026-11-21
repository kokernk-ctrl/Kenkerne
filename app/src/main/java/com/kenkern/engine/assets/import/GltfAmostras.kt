package com.kenkern.engine.assets.import

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AMOSTRAS glTF SINTÉTICAS para o teste do importador (JVM) e para a checagem
 * do Diagnóstico. NÃO são fixtures mortas: cada amostra exercita um caminho
 * diferente do importador (GLB/BIN, .gltf/data URI, transform de nodes, merge
 * de primitivas, normais ausentes, índices u16/u32).
 *
 * Só dependem de java.* — rodam na JVM dos testes e no aparelho.
 */
object GltfAmostras {

    /**
     * QUAD com POSITION (f32), NORMAL (f32), TEXCOORD_0 (f32) e índices u16 —
     * GLB com chunk BIN. 2 triângulos.
     */
    fun quadGlb(): ByteArray {
        val pos = floatArrayOf(
            0f, 0f, 0f,  1f, 0f, 0f,  1f, 1f, 0f,  0f, 1f, 0f
        )
        val norm = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f
        )
        val uv = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)

        val bin = ByteArrayOutputStream()
        val posOff = 0
        val normOff = pos.size * 4
        val uvOff = normOff + norm.size * 4
        val idxOff = uvOff + uv.size * 4
        bin.write(vetorF32(pos))
        bin.write(vetorF32(norm))
        bin.write(vetorF32(uv))
        bin.write(vetorU16(idx))
        val dados = bin.toByteArray()

        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": $posOff, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": $normOff, "byteLength": ${norm.size * 4}},
            {"buffer": 0, "byteOffset": $uvOff, "byteLength": ${uv.size * 4}},
            {"buffer": 0, "byteOffset": $idxOff, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": 4, "type": "VEC2"},
            {"bufferView": 3, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{
              "attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2},
              "indices": 3, "mode": 4
          }]}],
          "nodes": [{"mesh": 0, "name": "Quad"}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados)
    }

    /**
     * O MESMO quad como .gltf (JSON) com buffer em data URI base64 — caminho
     * do JSON puro + decodificação de data URI.
     */
    fun quadGltfDataUri(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val bin = ByteArrayOutputStream()
        bin.write(vetorF32(pos))
        bin.write(vetorU16(idx))
        val dados = bin.toByteArray()
        val b64 = java.util.Base64.getEncoder().encodeToString(dados)
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size}, "uri": "data:application/octet-stream;base64,$b64"}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return json.toByteArray(Charsets.UTF_8)
    }

    /**
     * O MESMO quad como .gltf com buffer em ARQUIVO EXTERNO ("quad.bin") —
     * caminho do resolvedor de pasta/zip (ETAPA A/B.1). Use com
     * [quadBinExterno] (o conteúdo do quad.bin) num ResolvedorZip/pasta.
     */
    fun quadGltfExterno(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${pos.size * 4 + idx.size * 2}, "uri": "quad.bin"}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return json.toByteArray(Charsets.UTF_8)
    }

    /** Conteúdo do quad.bin externo (positions f32 + índices u16, sem padding). */
    fun quadBinExterno(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val bin = ByteArrayOutputStream()
        bin.write(vetorF32(pos))
        bin.write(vetorU16(idx))
        return bin.toByteArray()
    }

    /**
     * QUAD com MATERIAL (ETAPA B.3): GLB com material (baseColorFactor 0.8/0.2/0.1,
     * metallic 0.5, roughness 0.4) + baseColorTexture apontando para uma imagem
     * EMBUTIDA no chunk BIN (bytes de "PNG" sintético — não decodifica nada).
     * Dois primitivos com o MESMO material (dedup de materiais deve deixar 1).
     */
    fun quadComMaterialGlb(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val pngFalso = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
        val bin = ByteArrayOutputStream()
        bin.write(vetorF32(pos))
        bin.write(vetorU16(idx))
        val posOff = 0
        val idxOff = pos.size * 4
        val imgOff = idxOff + idx.size * 2
        val dados = bin.size() + pngFalso.size // total depois de escrever a imagem
        bin.write(pngFalso)
        val bytes = bin.toByteArray()
        val json = """
        {
          "asset": {"version": "2.0"},
          "materials": [{
            "name": "madeira",
            "pbrMetallicRoughness": {
              "baseColorFactor": [0.8, 0.2, 0.1, 1.0],
              "metallicFactor": 0.5,
              "roughnessFactor": 0.4,
              "baseColorTexture": {"index": 0}
            },
            "doubleSided": true
          }],
          "textures": [{"source": 0}],
          "images": [{"bufferView": 3, "mimeType": "image/png"}],
          "buffers": [{"byteLength": $dados}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": $posOff, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": $idxOff, "byteLength": ${idx.size * 2}},
            {"buffer": 0, "byteOffset": 0, "byteLength": 0},
            {"buffer": 0, "byteOffset": $imgOff, "byteLength": ${pngFalso.size}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [
            {"attributes": {"POSITION": 0}, "indices": 1, "material": 0, "mode": 4},
            {"attributes": {"POSITION": 0}, "indices": 1, "material": 0, "mode": 4}
          ]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, bytes)
    }

    /**
     * CUBO em 2 NODES com transform (node 0 com quaternion+escala no mesh;
     * node 1 filho com TRANSLATION duplicando a mesma mesh) — valida matriz
     * TRS/quaternion, herança de pai e MERGE de primitivas (2 instâncias).
     * Sem NORMAL e sem UV (caminho de normais acumuladas + uv zerado).
     */
    fun cuboComTransformGlb(): ByteArray {
        // 8 cantos + 36 índices de cubo (faces fixas)
        val pos = floatArrayOf(
            -0.5f, -0.5f, 0.5f,  0.5f, -0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, 0.5f,   // frente
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, 0.5f, -0.5f,  -0.5f, 0.5f, -0.5f // trás
        )
        val idx = shortArrayOf(
            0, 1, 2, 0, 2, 3,       // frente
            5, 4, 7, 5, 7, 6,       // trás
            4, 0, 3, 4, 3, 7,       // esquerda
            1, 5, 6, 1, 6, 2,       // direita
            3, 2, 6, 3, 6, 7,       // topo
            4, 5, 1, 4, 1, 0        // base
        )
        val bin = ByteArrayOutputStream()
        bin.write(vetorF32(pos))
        bin.write(vetorU16(idx))
        val dados = bin.toByteArray()

        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 8, "type": "VEC3", "min": [-0.5,-0.5,-0.5], "max": [0.5,0.5,0.5]},
            {"bufferView": 1, "componentType": 5123, "count": 36, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4}]}],
          "nodes": [
            {"mesh": 0, "rotation": [0.0, 0.3826834, 0.0, 0.9238795], "scale": [2.0, 1.0, 1.0]},
            {"mesh": 0, "translation": [0.0, 3.0, 0.0], "children": []}
          ],
          "scenes": [{"nodes": [0, 1]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados)
    }

    /**
     * MALHA DENSA para a meta de compressão: N vértices numa espiral 3D
     * (POSITION f32 + NORMAL f32 + TEXCOORD_0 f32, índices u32 quando > 65535
     * vértices) — o .KERN quantizado precisa ficar menor que o glTF de origem.
     */
    fun densoGlb(vertices: Int): ByteArray {
        val pos = FloatArray(vertices * 3)
        val norm = FloatArray(vertices * 3)
        val uv = FloatArray(vertices * 2)
        for (v in 0 until vertices) {
            val t = v * 0.37f
            val ang = t * 0.11f
            pos[v * 3] = kotlin.math.cos(ang) * (1f + t * 0.01f)
            pos[v * 3 + 1] = kotlin.math.sin(t * 0.21f) * 0.5f
            pos[v * 3 + 2] = kotlin.math.sin(ang) * (1f + t * 0.01f)
            // normal "apontando para fora" (não unitária de propósito — importador renormaliza no .KERN via oct)
            norm[v * 3] = pos[v * 3]; norm[v * 3 + 1] = 0.7f; norm[v * 3 + 2] = pos[v * 3 + 2]
            uv[v * 2] = (v % 16) / 16f
            uv[v * 2 + 1] = (v / 16 % 16) / 16f
        }
        // grade de triângulos fechada em anel (cada vértice conecta aos vizinhos)
        val usados = minOf(vertices, 65536)
        val idxCount = if (usados >= 3) (usados / 3) * 3 else 3
        val usarU32 = idxCount / 3 + 1 > 65535
        val bin = ByteArrayOutputStream()
        val posOff = 0
        val normOff = posOff + pos.size * 4
        val uvOff = normOff + norm.size * 4
        val idxOff = uvOff + uv.size * 4
        bin.write(vetorF32(pos))
        bin.write(vetorF32(norm))
        bin.write(vetorF32(uv))
        if (usarU32) {
            val idx = IntArray(idxCount) { (it * 7) % usados }
            bin.write(vetorU32(idx))
        } else {
            val idx = ShortArray(idxCount) { ((it * 7) % usados).toShort() }
            bin.write(vetorU16(idx))
        }
        val dados = bin.toByteArray()
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${dados.size}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": $posOff, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": $normOff, "byteLength": ${norm.size * 4}},
            {"buffer": 0, "byteOffset": $uvOff, "byteLength": ${uv.size * 4}},
            {"buffer": 0, "byteOffset": $idxOff, "byteLength": ${if (usarU32) idxCount * 4 else idxCount * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": $vertices, "type": "VEC3"},
            {"bufferView": 1, "componentType": 5126, "count": $vertices, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": $vertices, "type": "VEC2"},
            {"bufferView": 3, "componentType": ${if (usarU32) 5125 else 5123}, "count": $idxCount, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{
              "attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2},
              "indices": 3, "mode": 4
          }]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados)
    }

    /** GLB que NÃO é glTF (magic errado) — para o teste de erro claro. */
    fun magicQuebrado(): ByteArray = "XXXXarquivo-não-glTF".toByteArray(Charsets.US_ASCII)

    /**
     * QUAD com coisas que esta fase IGNORA (auditoria V3): extensionsUsed,
     * skins, animations e material com emissive/occlusion/metallicRoughness
     * texture. O importador DEVE avisar cada ignorância no ImportReport.
     */
    fun quadComIgnoradosGlb(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val dados = ByteArrayOutputStream()
        dados.write(vetorF32(pos))
        dados.write(vetorU16(idx))
        val json = """
        {
          "asset": {"version": "2.0"},
          "extensionsUsed": ["KHR_materials_transmission"],
          "buffers": [{"byteLength": ${dados.size()}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4,
            "material": 0}]}],
          "materials": [{"name": "com-extras", "pbrMetallicRoughness": {"metallicRoughnessTexture": {"index": 0}},
            "emissiveTexture": {"index": 0}, "occlusionTexture": {"index": 0}}],
          "skins": [{}],
          "animations": [{}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados.toByteArray())
    }

    /**
     * QUAD com extensionsRequired (auditoria V3): o importador NÃO suporta
     * nenhuma extensão obrigatória — deve dar ERRO CLARO com o nome dela
     * (Draco/Meshopt comprimem os buffers: importar "por cima" mentiria).
     */
    fun quadExigindoExtensaoGlb(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        val dados = ByteArrayOutputStream()
        dados.write(vetorF32(pos))
        dados.write(vetorU16(idx))
        val json = """
        {
          "asset": {"version": "2.0"},
          "extensionsRequired": ["KHR_draco_mesh_compression"],
          "extensionsUsed": ["KHR_draco_mesh_compression"],
          "buffers": [{"byteLength": ${dados.size()}}],
          "bufferViews": [
            {"buffer": 0, "byteOffset": 0, "byteLength": ${pos.size * 4}},
            {"buffer": 0, "byteOffset": ${pos.size * 4}, "byteLength": ${idx.size * 2}}
          ],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
            {"bufferView": 1, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1, "mode": 4}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, dados.toByteArray())
    }

    /** GLB válido no container mas com acessor que não existe — erro claro. */
    fun acessorFaltando(): ByteArray {
        val pos = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val bin = vetorF32(pos)
        val json = """
        {
          "asset": {"version": "2.0"},
          "buffers": [{"byteLength": ${bin.size}}],
          "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": ${bin.size}}],
          "accessors": [{"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3"}],
          "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "NORMAL": 9}, "mode": 4}]}],
          "nodes": [{"mesh": 0}],
          "scenes": [{"nodes": [0]}],
          "scene": 0
        }
        """.trimIndent()
        return GltfImporter.empacotarGlb(json, bin)
    }

    /** .gltf com buffer em ARQUIVO EXTERNO — deve dar erro claro (não silencioso). */
    fun bufferExterno(): ByteArray = """
    {
      "asset": {"version": "2.0"},
      "buffers": [{"byteLength": 100, "uri": "malha_externa.bin"}],
      "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "mode": 4}]}],
      "nodes": [{"mesh": 0}],
      "scenes": [{"nodes": [0]}],
      "scene": 0
    }
    """.trimIndent().toByteArray(Charsets.UTF_8)

    // ---------- helpers de empacotamento ----------

    private fun vetorF32(dados: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(dados.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in dados) buf.putFloat(v)
        return buf.array()
    }

    private fun vetorU16(dados: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(dados.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in dados) buf.putShort(v)
        return buf.array()
    }

    private fun vetorU32(dados: IntArray): ByteArray {
        val buf = ByteBuffer.allocate(dados.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in dados) buf.putInt(v)
        return buf.array()
    }
}
