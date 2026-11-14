package com.kenkern.engine.render

import com.kenkern.engine.core.math.Mat4

/** Payload pré-alocado de uma submissão de geometria (zero alocação por frame). */
class GeometrySubmission {
    var meshHandle: Int = 0
    var r: Float = 1f; var g: Float = 1f; var b: Float = 1f; var a: Float = 1f
    var outline: Boolean = false
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
    }

    override fun submitOutline(meshHandle: Int, model: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (count >= capacity || meshHandle == 0) return
        val slot = slots[count++]
        slot.meshHandle = meshHandle
        slot.model.copyFrom(model)
        slot.r = r; slot.g = g; slot.b = b; slot.a = a
        slot.outline = true
    }

    override fun flush(backend: RenderBackend, viewProj: Mat4) {
        lastFlushCount = count
        for (i in 0 until count) {
            val s = slots[i]
            if (s.outline) {
                backend.drawMeshOutline(s.meshHandle, s.model, viewProj, s.r, s.g, s.b, s.a)
            } else {
                backend.drawMesh(s.meshHandle, s.model, viewProj, s.r, s.g, s.b, s.a)
            }
        }
    }
}
