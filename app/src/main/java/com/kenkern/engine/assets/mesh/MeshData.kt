package com.kenkern.engine.assets.mesh

import com.kenkern.engine.core.math.Vec3

/** Entrada da tabela de LODs. */
class KernLod(
    val indexOffset: Int,
    val indexCount: Int,
    val boundsMin: Vec3,
    val boundsMax: Vec3
)

/** Osso (reservado para skinning). */
class KernBone(
    val name: String,
    val parentIndex: Int,
    val inverseBind: FloatArray
)

/**
 * Malha no formato KERN completo: geometria + LODs + dados reservados de
 * skinning/esqueleto + bloco opcional de clusters (futura KENA).
 * v2 (ETAPA B): + tabela de MATERIAIS e GRUPOS por submalha (ambos opcionais;
 * malha sem material = listas vazias — o render usa a cor padrão).
 */
class KernMesh(
    val positions: FloatArray,      // xyz por vértice (dequantizado)
    val normals: FloatArray,        // xyz por vértice
    val uvs: FloatArray,            // uv por vértice
    val tangents: FloatArray,       // xyz por vértice (pode estar zerado)
    val indices: IntArray,
    val boundsMin: Vec3,
    val boundsMax: Vec3,
    val lods: List<KernLod>,
    val boneIndices: ByteArray,     // 4 por vértice (reservado)
    val boneWeights: ByteArray,     // 4 por vértice (reservado)
    val bones: List<KernBone>,      // esqueleto (reservado; vazio nesta fase)
    val clusters: ByteArray?,       // bloco KENA (reservado)
    val gruposMaterial: List<KernGrupoMaterial> = emptyList(), // v2 (B.3)
    val materiais: List<KernMaterial> = emptyList()            // v2 (B.3)
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * Malha pronta para render (floats dequantizados, índices em Int).
 */
class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val boundsMin: Vec3,
    val boundsMax: Vec3
) {
    val vertexCount: Int get() = positions.size / 3

    companion object {
        /** Converte um KernMesh em malha de render (dequantiza posições). */
        fun fromKern(mesh: KernMesh): MeshData {
            val count = mesh.vertexCount
            val positions = FloatArray(count * 3)
            for (i in 0 until count) {
                positions[i * 3] = mesh.positions[i * 3]
                positions[i * 3 + 1] = mesh.positions[i * 3 + 1]
                positions[i * 3 + 2] = mesh.positions[i * 3 + 2]
            }
            return MeshData(
                positions,
                mesh.normals,
                mesh.uvs,
                mesh.indices,
                mesh.boundsMin,
                mesh.boundsMax
            )
        }
    }
}
