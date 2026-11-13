package com.kenkern.engine.render

/**
 * KELU (gancho de arquitetura): iluminação global estilo Lumen adaptada a
 * mobile (probes, SSAO, reflexos). A implementação padrão oferece apenas luz
 * direcional + ambiente; a KELU substituirá esta interface.
 */
interface LightingPipeline {
    val name: String

    /** Direção da luz (aponta PARA onde a luz viaja). Escreve em [outDir] sem alocar. */
    fun fillDirectional(outDir: FloatArray)

    /** Ambiente (R, G, B). */
    fun fillAmbient(outAmbient: FloatArray)

    /** Cor da luz direcional + intensidade. */
    fun fillLightColor(outColor: FloatArray): Float
}

/** Implementação padrão: sol fixo + ambiente neutro. */
class DefaultLightingPipeline : LightingPipeline {

    override val name: String = "DefaultLightingPipeline"

    var dirX: Float = -0.5f
    var dirY: Float = -1f
    var dirZ: Float = -0.35f
    var ambientR: Float = 0.30f
    var ambientG: Float = 0.30f
    var ambientB: Float = 0.32f
    var lightR: Float = 1f
    var lightG: Float = 0.97f
    var lightB: Float = 0.9f
    var intensity: Float = 1f

    override fun fillDirectional(outDir: FloatArray) {
        outDir[0] = dirX; outDir[1] = dirY; outDir[2] = dirZ
    }

    override fun fillAmbient(outAmbient: FloatArray) {
        outAmbient[0] = ambientR; outAmbient[1] = ambientG; outAmbient[2] = ambientB
    }

    override fun fillLightColor(outColor: FloatArray): Float {
        outColor[0] = lightR; outColor[1] = lightG; outColor[2] = lightB
        return intensity
    }
}
