package com.kenkern.engine.editor

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.math.roundToInt

/** Estado de UI de um painel flutuante (posição, tamanho, visibilidade). */
class PanelUiState(
    val key: String,
    val title: String,
    var offsetX: Float,
    var offsetY: Float,
    var widthDp: Float,
    var heightDp: Float,
    val visible: MutableState<Boolean>
)

/**
 * Guarda posição/tamanho dos painéis do editor (Hierarquia e Inspetor).
 * Persistidos em SharedPreferences e restaurados na próxima sessão.
 */
class PanelStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val states = HashMap<String, PanelUiState>()

    fun get(
        key: String,
        title: String,
        defaultX: Float,
        defaultY: Float,
        defaultWidthDp: Float,
        defaultHeightDp: Float,
        screenWidthPx: Int
    ): PanelUiState {
        states[key]?.let { return it }
        val raw = prefs.getString(key, null)
        var x = defaultX
        var y = defaultY
        var w = defaultWidthDp
        var h = defaultHeightDp
        var visible = true
        if (raw != null) {
            val parts = raw.split(',')
            if (parts.size >= 5) {
                x = parts[0].toFloatOrNull() ?: x
                y = parts[1].toFloatOrNull() ?: y
                w = parts[2].toFloatOrNull() ?: w
                h = parts[3].toFloatOrNull() ?: h
                visible = parts[4] == "1"
            }
        }
        // Clampa posição salva para caber na tela atual.
        if (x > screenWidthPx - 80f) x = 0f
        if (y > 4000f) y = defaultY
        val state = PanelUiState(key, title, x, y, w, h, mutableStateOf(visible))
        states[key] = state
        return state
    }

    fun persist(state: PanelUiState) {
        prefs.edit().putString(
            state.key,
            "${state.offsetX.roundToInt()},${state.offsetY.roundToInt()}," +
                "${state.widthDp.roundToInt()},${state.heightDp.roundToInt()}," +
                if (state.visible.value) "1" else "0"
        ).apply()
    }

    companion object {
        private const val PREFS_NAME = "kenkern_panels"
    }
}
