package com.kenkern.engine.editor

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.kenkern.engine.diag.KenkernLog
import kotlin.math.roundToInt

/**
 * Estado de UI de um painel do editor.
 *
 * Modelo DOCK-FIRST (o oposto do modelo antigo, que nascia solto sobre o viewport):
 *  - o painel nasce ACOPLADO à lateral (Hierarquia à esquerda, Inspetor à direita)
 *    com uma largura própria ajustável por divisor;
 *  - o modo flutuante é EXCEÇÃO, ativado apenas pelo menu do painel;
 *  - visibilidade é alternada pelo botão correspondente da TopBar.
 */
class PanelUiState(
    val key: String,
    val title: String,
    val minDockWidthDp: Float,
    val maxDockWidthDp: Float,
    val visible: MutableState<Boolean>,
    val docked: MutableState<Boolean>,
    val dockWidthDp: MutableState<Float>,
    // Estado do modo flutuante (exceção): posição/tamanho relativos ao viewport.
    var floatX: Float,
    var floatY: Float,
    var floatW: Float,
    var floatH: Float
)

/**
 * Persistência de posição/largura/visibilidade dos painéis.
 * Formato textual compacto "v,d,w,fx,fy,fw,fh"; qualquer valor corrompido
 * cai de volta no padrão profissional (visível + acoplado) sem lançar exceção.
 */
class PanelStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val states = HashMap<String, PanelUiState>()

    private companion object {
        const val TAG = "Kenkern/PanelStateStore"
    }

    fun get(
        key: String,
        title: String,
        defaultDockWidthDp: Float,
        minDockWidthDp: Float,
        maxDockWidthDp: Float,
        defaultFloatWidthDp: Float,
        defaultFloatHeightDp: Float
    ): PanelUiState {
        states[key]?.let { return it }

        // Padrões profissionais: painel visível e acoplado, largura padrão.
        var visible = true
        var docked = true
        var w = defaultDockWidthDp.coerceIn(minDockWidthDp, maxDockWidthDp)
        var fx = 16f
        var fy = 16f
        var fw = defaultFloatWidthDp
        var fh = defaultFloatHeightDp

        val raw = runCatching { prefs.getString(key, null) }
            .onFailure { KenkernLog.w(TAG, "prefs do painel '$key' ilegíveis — usando padrão", it) }
            .getOrNull()
        if (raw != null) {
            val p = raw.split(',')
            if (p.size >= 7) {
                visible = p[0] == "1"
                docked = p[1] == "1"
                p[2].toFloatOrNull()?.let { w = it.coerceIn(minDockWidthDp, maxDockWidthDp) }
                p[3].toFloatOrNull()?.let { fx = it }
                p[4].toFloatOrNull()?.let { fy = it }
                p[5].toFloatOrNull()?.let { fw = it.coerceAtLeast(MIN_FLOAT_W_DP) }
                p[6].toFloatOrNull()?.let { fh = it.coerceAtLeast(MIN_FLOAT_H_DP) }
            }
        }

        val state = PanelUiState(
            key, title, minDockWidthDp, maxDockWidthDp,
            mutableStateOf(visible),
            mutableStateOf(docked),
            mutableFloatStateOf(w),
            fx, fy, fw, fh
        )
        states[key] = state
        return state
    }

    fun persist(state: PanelUiState) {
        runCatching {
            prefs.edit().putString(
                state.key,
                listOf(
                    if (state.visible.value) "1" else "0",
                    if (state.docked.value) "1" else "0",
                    state.dockWidthDp.value.roundToInt().toString(),
                    state.floatX.roundToInt().toString(),
                    state.floatY.roundToInt().toString(),
                    state.floatW.roundToInt().toString(),
                    state.floatH.roundToInt().toString()
                ).joinToString(",")
            ).apply()
        }.onFailure { KenkernLog.w(TAG, "prefs do painel '${state.key}' não gravados", it) }
    }

    companion object {
        private const val PREFS_NAME = "kenkern_panels"
        const val MIN_FLOAT_W_DP = 220f
        const val MIN_FLOAT_H_DP = 200f
    }
}
