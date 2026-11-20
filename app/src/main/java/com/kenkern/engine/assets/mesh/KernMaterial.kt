package com.kenkern.engine.assets.mesh

/**
 * MATERIAL do .KERN v2 (ETAPA B.3): dados básicos lidos de glTF/FBX.
 * O índice do material é referenciado pelos [KernGrupoMaterial] por submalha.
 *
 *  - baseColor RGBA (0–1, linear);
 *  - metallic/roughness como VALORES (texturas desses parâmetros ficam para a
 *    TRAMA/Fase 4 — aqui só o escalar, que é o que o shader básico usa);
 *  - texturas por CAMINHO RELATIVO do projeto ("assets/textures/<hash>.kimg") —
 *    o leitor de textura do render resolve contra a raiz do projeto; caminho
 *    vazio = sem textura (cor sólida);
 *  - flags: bit0 = tem textura de cor base, bit1 = tem textura normal,
 *    bit2 = dupla face (doubleSided).
 */
class KernMaterial(
    val nome: String,
    val baseColorR: Float,
    val baseColorG: Float,
    val baseColorB: Float,
    val baseColorA: Float,
    val metallic: Float,
    val roughness: Float,
    val temTexturaBase: Boolean,
    val temTexturaNormal: Boolean,
    val doubleSided: Boolean,
    val texturaBasePath: String,
    val texturaNormalPath: String
) {
    /** Igualdade de CONTEÚDO para dedup de materiais iguais (B.4). */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KernMaterial) return false
        return nome == other.nome &&
            baseColorR == other.baseColorR && baseColorG == other.baseColorG &&
            baseColorB == other.baseColorB && baseColorA == other.baseColorA &&
            metallic == other.metallic && roughness == other.roughness &&
            temTexturaBase == other.temTexturaBase && temTexturaNormal == other.temTexturaNormal &&
            doubleSided == other.doubleSided &&
            texturaBasePath == other.texturaBasePath && texturaNormalPath == other.texturaNormalPath
    }

    override fun hashCode(): Int {
        var h = nome.hashCode()
        h = h * 31 + baseColorR.hashCode(); h = h * 31 + baseColorG.hashCode()
        h = h * 31 + baseColorB.hashCode(); h = h * 31 + baseColorA.hashCode()
        h = h * 31 + metallic.hashCode(); h = h * 31 + roughness.hashCode()
        h = h * 31 + texturaBasePath.hashCode()
        return h
    }
}

/**
 * GRUPO de material por SUBMALHA (ETAPA B.3): um intervalo do buffer de índices
 * desenhado com UM material. Vários grupos em sequência = várias submalhas
 * (primitivas glTF / Models FBX) compartilhando a mesma malha de vértices.
 */
class KernGrupoMaterial(
    val indexOffset: Int,
    val indexCount: Int,
    val materialIndex: Int
)
