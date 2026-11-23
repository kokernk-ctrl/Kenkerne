package com.kenkern.engine.assets.mesh

/**
 * MATERIAL do .KERN — v2 (ETAPA B.3) + v3 (T0.4, v0.1.20): MATERIAL PBR
 * COMPLETO como asset. O índice do material é referenciado pelos
 * [KernGrupoMaterial] por submalha.
 *
 *  - baseColor RGBA (0–1, linear) + textura de cor base;
 *  - metallic/roughness FATORES (0–1) e textura metallicRoughness (convenção
 *    glTF: canal G = rugosidade, canal B = metálico);
 *  - normal map com escala (normalScale);
 *  - oclusão (textura + strength);
 *  - emissivo (textura + cor RGB + intensidade);
 *  - dupla face (doubleSided).
 *  - texturas por CAMINHO RELATIVO do projeto ("assets/textures/<hash>.kimg") —
 *    o leitor de textura do render resolve contra a raiz do projeto; caminho
 *    vazio = sem textura (usa o FATOR/cor sólida).
 *
 * O shader da engine usa O QUE EXISTIR: fator quando não há textura, textura
 * quando há (um único programa de material, sem ramificação cara).
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
    val texturaNormalPath: String,
    // ---------- v3 (T0.4): PBR completo ----------
    val texturaMrPath: String = "",
    val texturaOclusaoPath: String = "",
    val texturaEmissivaPath: String = "",
    val emissiveR: Float = 0f,
    val emissiveG: Float = 0f,
    val emissiveB: Float = 0f,
    val emissiveIntensity: Float = 1f,
    val normalScale: Float = 1f,
    val oclusaoStrength: Float = 1f
) {
    /** Tem ALGUM dado da extensão v3 (o escritor só então grava versão 3). */
    val temExtensaoPbr: Boolean
        get() = texturaMrPath.isNotEmpty() || texturaOclusaoPath.isNotEmpty() ||
            texturaEmissivaPath.isNotEmpty() || emissiveR != 0f || emissiveG != 0f ||
            emissiveB != 0f || emissiveIntensity != 1f || normalScale != 1f ||
            oclusaoStrength != 1f

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
            texturaBasePath == other.texturaBasePath && texturaNormalPath == other.texturaNormalPath &&
            texturaMrPath == other.texturaMrPath && texturaOclusaoPath == other.texturaOclusaoPath &&
            texturaEmissivaPath == other.texturaEmissivaPath &&
            emissiveR == other.emissiveR && emissiveG == other.emissiveG && emissiveB == other.emissiveB &&
            emissiveIntensity == other.emissiveIntensity &&
            normalScale == other.normalScale && oclusaoStrength == other.oclusaoStrength
    }

    override fun hashCode(): Int {
        var h = nome.hashCode()
        h = h * 31 + baseColorR.hashCode(); h = h * 31 + baseColorG.hashCode()
        h = h * 31 + baseColorB.hashCode(); h = h * 31 + baseColorA.hashCode()
        h = h * 31 + metallic.hashCode(); h = h * 31 + roughness.hashCode()
        h = h * 31 + texturaBasePath.hashCode()
        h = h * 31 + texturaMrPath.hashCode()
        h = h * 31 + texturaOclusaoPath.hashCode()
        h = h * 31 + texturaEmissivaPath.hashCode()
        h = h * 31 + emissiveR.hashCode(); h = h * 31 + emissiveG.hashCode()
        h = h * 31 + emissiveB.hashCode(); h = h * 31 + emissiveIntensity.hashCode()
        h = h * 31 + normalScale.hashCode(); h = h * 31 + oclusaoStrength.hashCode()
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
