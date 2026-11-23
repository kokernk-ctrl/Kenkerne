package com.kenkern.engine.render

import com.kenkern.engine.core.math.Mat4

/** Payload pré-alocado de uma submissão de geometria (zero alocação por frame). */
class GeometrySubmission {
    var meshHandle: Int = 0
    var r: Float = 1f; var g: Float = 1f; var b: Float = 1f; var a: Float = 1f
    var outline: Boolean = false
    var grupoOffset: Int = -1   // ≥ 0 = sub-range com textura (grupo de material)
    var grupoCount: Int = 0
    var texturaHandle: Int = 0
    // T0.4 (v0.1.20): MATERIAL PBR (mapas + fatores — o shader usa o que existir)
    var normalHandle: Int = 0
    var normalScale: Float = 1f
    var mrHandle: Int = 0
    var metalFactor: Float = 0f
    var roughFactor: Float = 1f
    var aoHandle: Int = 0
    var aoStrength: Float = 1f
    var emissiveHandle: Int = 0
    var emissiveR: Float = 0f; var emissiveG: Float = 0f; var emissiveB: Float = 0f
    var emissiveIntensity: Float = 1f
    val model = Mat4()
}

/**
 * KENA (gancho de arquitetura): pipeline de geometria virtualizada estilo
 * Nanite adaptado a mobile (clusters, LOD contínuo, culling).
 * A implementação padrão submete as malhas normalmente; a KENA substituirá
 * esta interface pela seleção de clusters por tela.
 */
interface GeometryPipeline {
    val name: String

    fun reset()

    fun submit(meshHandle: Int, model: Mat4, r: Float, g: Float, b: Float, a: Float)

    /** Submissão de um GRUPO de material (sub-range com textura opcional — B.3/C.4). */
    fun submitGroup(
        meshHandle: Int, indexOffset: Int, indexCount: Int, textureHandle: Int,
        model: Mat4, r: Float, g: Float, b: Float, a: Float
    )

    /**
     * T0.4 (v0.1.20): grupo de material com o PBR COMPLETO — mapas (handle 0 =
     * usa o fator) + fatores; pré-alocado nos slots, zero alocação por frame.
     */
    fun submitGroupPBR(
        meshHandle: Int, indexOffset: Int, indexCount: Int, textureHandle: Int,
        model: Mat4, r: Float, g: Float, b: Float, a: Float,
        normalHandle: Int, normalScale: Float,
        mrHandle: Int, metalFactor: Float, roughFactor: Float,
        aoHandle: Int, aoStrength: Float,
        emissiveHandle: Int, emissiveR: Float, emissiveG: Float, emissiveB: Float,
        emissiveIntensity: Float
    ) {
        // Compatibilidade: backends que não implementam caem no grupo simples.
        submitGroup(meshHandle, indexOffset, indexCount, textureHandle, model, r, g, b, a)
    }

    /** Submissão de contorno de seleção (inverted hull) — desenhada após as opacas. */
    fun submitOutline(meshHandle: Int, model: Mat4, r: Float, g: Float, b: Float, a: Float)

    fun flush(backend: RenderBackend, viewProj: Mat4)
}

/** Implementação padrão: fila fixa de submissões pré-alocadas. */
class DefaultGeometryPipeline(private val capacity: Int = 1024) : GeometryPipeline {

    override val name: String = "DefaultGeometryPipeline"

    private val slots = Array(capacity) { GeometrySubmission() }
    private var count = 0

    /** Desenhos efetuados no último flush (estatística da barra de status). */
    @Volatile
    var lastFlushCount: Int = 0
        private set

    override fun reset() {
        count = 0
    }

    override fun submit(meshHandle: Int, model: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (count >= capacity || meshHandle == 0) return
        val slot = slots[count++]
        slot.meshHandle = meshHandle
        slot.model.copyFrom(model)
        slot.r = r; slot.g = g; slot.b = b; slot.a = a
        slot.outline = false
        slot.grupoOffset = -1
        slot.grupoCount = 0
        slot.texturaHandle = 0
    }

    override fun submitGroup(
        meshHandle: Int, indexOffset: Int, indexCount: Int, textureHandle: Int,
        model: Mat4, r: Float, g: Float, b: Float, a: Float
    ) {
        if (count >= capacity || meshHandle == 0) return
        val slot = slots[count++]
        slot.meshHandle = meshHandle
        slot.model.copyFrom(model)
        slot.r = r; slot.g = g; slot.b = b; slot.a = a
        slot.outline = false
        slot.grupoOffset = indexOffset
        slot.grupoCount = indexCount
        slot.texturaHandle = textureHandle
        slot.normalHandle = 0; slot.mrHandle = 0; slot.aoHandle = 0; slot.emissiveHandle = 0
        slot.normalScale = 1f; slot.metalFactor = 0f; slot.roughFactor = 1f
        slot.aoStrength = 1f; slot.emissiveR = 0f; slot.emissiveG = 0f; slot.emissiveB = 0f
        slot.emissiveIntensity = 1f
    }

    override fun submitGroupPBR(
        meshHandle: Int, indexOffset: Int, indexCount: Int, textureHandle: Int,
        model: Mat4, r: Float, g: Float, b: Float, a: Float,
        normalHandle: Int, normalScale: Float,
        mrHandle: Int, metalFactor: Float, roughFactor: Float,
        aoHandle: Int, aoStrength: Float,
        emissiveHandle: Int, emissiveR: Float, emissiveG: Float, emissiveB: Float,
        emissiveIntensity: Float
    ) {
        if (count >= capacity || meshHandle == 0) return
        val slot = slots[count++]
        slot.meshHandle = meshHandle
        slot.model.copyFrom(model)
        slot.r = r; slot.g = g; slot.b = b; slot.a = a
        slot.outline = false
        slot.grupoOffset = indexOffset
        slot.grupoCount = indexCount
        slot.texturaHandle = textureHandle
        slot.normalHandle = normalHandle
        slot.normalScale = normalScale
        slot.mrHandle = mrHandle
        slot.metalFactor = metalFactor
        slot.roughFactor = roughFactor
        slot.aoHandle = aoHandle
        slot.aoStrength = aoStrength
        slot.emissiveHandle = emissiveHandle
        slot.emissiveR = emissiveR; slot.emissiveG = emissiveG; slot.emissiveB = emissiveB
        slot.emissiveIntensity = emissiveIntensity
    }

    override fun submitOutline(meshHandle: Int, model: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (count >= capacity || meshHandle == 0) return
        val slot = slots[count++]
        slot.meshHandle = meshHandle
        slot.model.copyFrom(model)
        slot.r = r; slot.g = g; slot.b = b; slot.a = a
        slot.outline = true
        slot.grupoOffset = -1
        slot.grupoCount = 0
        slot.texturaHandle = 0
    }

    override fun flush(backend: RenderBackend, viewProj: Mat4) {
        lastFlushCount = count
        for (i in 0 until count) {
            val s = slots[i]
            if (s.outline) {
                backend.drawMeshOutline(s.meshHandle, s.model, viewProj, s.r, s.g, s.b, s.a)
            } else if (s.grupoOffset >= 0) {
                // T0.4: material PBR do grupo antes do draw (o shader usa o que existir)
                backend.setMaterialPBR(
                    s.normalHandle, s.normalScale,
                    s.mrHandle, s.metalFactor, s.roughFactor,
                    s.aoHandle, s.aoStrength,
                    s.emissiveHandle, s.emissiveR, s.emissiveG, s.emissiveB,
                    s.emissiveIntensity
                )
                backend.drawMeshRange(
                    s.meshHandle, s.grupoOffset, s.grupoCount, s.texturaHandle,
                    s.model, viewProj, s.r, s.g, s.b, s.a
                )
            } else {
                backend.drawMesh(s.meshHandle, s.model, viewProj, s.r, s.g, s.b, s.a)
            }
        }
    }
}
