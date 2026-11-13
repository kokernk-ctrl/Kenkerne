package com.kenkern.engine.engine

/** Estado de execução do editor (botões Play/Pause/Stop). */
enum class PlayState {
    STOPPED, PLAYING, PAUSED
}

/**
 * Loop de update com TIMESTEP FIXO (60 Hz) e acumulador.
 * Garante ZERO alocações por frame: nenhuma coleção ou objeto é criado em tick().
 */
class FixedStepLoop(
    val fixedDeltaTime: Float = 1f / 60f,
    @PublishedApi internal val maxStepsPerFrame: Int = 5
) {
    var engineTime: Float = 0f

    var playState: PlayState = PlayState.STOPPED

    @PublishedApi internal var lastNanos: Long = 0L
    @PublishedApi internal var accumulator: Float = 0f

    fun requestPlayState(state: PlayState) {
        playState = state
        if (state == PlayState.STOPPED) {
            reset()
        }
    }

    fun reset() {
        engineTime = 0f
        accumulator = 0f
        lastNanos = 0L
    }

    /**
     * Chamado a cada frame de render. [update] roda em passos fixos quando
     * o estado é PLAYING. Interpolação visual entra em fases futuras.
     */
    inline fun tick(nowNanos: Long, update: (Float) -> Unit) {
        if (lastNanos == 0L) {
            lastNanos = nowNanos
            return
        }
        var frameDelta = (nowNanos - lastNanos) / 1_000_000_000f
        lastNanos = nowNanos
        if (frameDelta > 0.25f) frameDelta = 0.25f // proteção contra espiral da morte

        if (playState != PlayState.PLAYING) return

        accumulator += frameDelta
        var steps = 0
        while (accumulator >= fixedDeltaTime && steps < maxStepsPerFrame) {
            update(fixedDeltaTime)
            engineTime += fixedDeltaTime
            accumulator -= fixedDeltaTime
            steps++
        }
        if (accumulator > fixedDeltaTime * maxStepsPerFrame) {
            accumulator = 0f
        }
    }
}
