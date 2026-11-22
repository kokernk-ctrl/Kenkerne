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
 *
 * E4 (v0.1.18): floatX/floatY/floatW/floatH são [MutableState] OBSERVÁVEIS.
 * Eram `var Float` comuns (PanelStateStore.kt:28-31, v0.1.17) — o
 * pointerInput do cabeçalho escrevia nelas, mas NADA recompunha/re-media
 * (Modifier.offset{} e width()/height() leem valores comuns): o painel
 * NUNCA movia nem redimensionava na tela, era o bug "painéis flutuantes
 * não movem". Posição/tamanho também são salvos POR ORIENTAÇÃO (retrato e
 * paisagem têm viewports diferentes).
 */
class PanelUiState(
    val key: String,
    val title: String,
    val minDockWidthDp: Float,
    val maxDockWidthDp: Float,
    /** E4: orientação em que este estado vale ("land"/"port") — persist usa a chave certa. */
    val orientacao: String,
    val defaultDockWidthDp: Float,
    val defaultFloatWidthDp: Float,
    val defaultFloatHeightDp: Float,
    val visible: MutableState<Boolean>,
    val docked: MutableState<Boolean>,
    val dockWidthDp: MutableState<Float>,
    // Estado do modo flutuante (exceção): posição/tamanho relativos ao viewport, OBSERVÁVEL.
    val floatX: MutableState<Float>,
    val floatY: MutableState<Float>,
    val floatW: MutableState<Float>,
    val floatH: MutableState<Float>
)

/** Valores lidos da persistência (parse puro, testável sem Context). */
data class EstadoPainel(
    val visible: Boolean,
    val docked: Boolean,
    val dockWidthDp: Float,
    val floatX: Float,
    val floatY: Float,
    val floatW: Float,
    val floatH: Float
)

/**
 * Persistência de posição/largura/visibilidade dos painéis.
 * Formato textual compacto "v,d,w,fx,fy,fw,fh"; qualquer valor corrompido
 * cai de volta no padrão profissional (visível + acoplado) sem lançar exceção.
 *
 * E4 (v0.1.18): a chave de prefs é "painel#orientação" — retrato e paisagem
 * guardam LAYOUT PRÓPRIO (posição e tamanho por painel e por orientação).
 */
class PanelStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val states = HashMap<String, PanelUiState>()

    fun get(
        key: String,
        orientacao: String,
        title: String,
        defaultDockWidthDp: Float,
        minDockWidthDp: Float,
        maxDockWidthDp: Float,
        defaultFloatWidthDp: Float,
        defaultFloatHeightDp: Float
    ): PanelUiState {
        val chave = chavePrefs(key, orientacao)
        states[chave]?.let { return it }

        val bruto = runCatching { prefs.getString(chave, null) }
            .onFailure { KenkernLog.w(TAG, "prefs do painel '$chave' ilegíveis — usando padrão", it) }
            .getOrNull()
        val e = parseLinha(bruto, minDockWidthDp, maxDockWidthDp)

        val state = PanelUiState(
            key, title, minDockWidthDp, maxDockWidthDp, orientacao,
            defaultDockWidthDp, defaultFloatWidthDp, defaultFloatHeightDp,
            mutableStateOf(e.visible),
            mutableStateOf(e.docked),
            mutableFloatStateOf(e.dockWidthDp),
            mutableFloatStateOf(e.floatX),
            mutableFloatStateOf(e.floatY),
            mutableFloatStateOf(e.floatW),
            mutableFloatStateOf(e.floatH)
        )
        states[chave] = state
        return state
    }

    fun persist(state: PanelUiState) {
        runCatching {
            prefs.edit().putString(
                chavePrefs(state.key, state.orientacao),
                formatarLinha(
                    state.visible.value, state.docked.value, state.dockWidthDp.value,
                    state.floatX.value, state.floatY.value, state.floatW.value, state.floatH.value
                )
            ).apply()
        }.onFailure { KenkernLog.w(TAG, "prefs do painel '${state.key}#${state.orientacao}' não gravados", it) }
    }

    /**
     * E4: "Resetar layout" — o painel volta ao padrão profissional (visível +
     * acoplado + largura/posição/tamanho padrão) e PERSISTE na hora.
     */
    fun resetar(state: PanelUiState) {
        state.visible.value = true
        state.docked.value = true
        state.dockWidthDp.value = state.defaultDockWidthDp
        state.floatX.value = 16f
        state.floatY.value = 16f
        state.floatW.value = state.defaultFloatWidthDp
        state.floatH.value = state.defaultFloatHeightDp
        persist(state)
    }

    companion object {
        private const val TAG = "Kenkern/PanelStateStore"
        private const val PREFS_NAME = "kenkern_panels"
        const val MIN_FLOAT_W_DP = 220f
        const val MIN_FLOAT_H_DP = 200f

        fun chavePrefs(key: String, orientacao: String): String = "$key#$orientacao"

        /**
         * Parse PURO (testável sem Context) da linha "v,d,w,fx,fy,fw,fh" —
         * corrompido/faltante = padrão profissional (visível + acoplado).
         */
        fun parseLinha(bruto: String?, minDockWidthDp: Float, maxDockWidthDp: Float): EstadoPainel {
            var e = EstadoPainel(
                visible = true, docked = true,
                dockWidthDp = 220f.coerceIn(minDockWidthDp, maxDockWidthDp),
                floatX = 16f, floatY = 16f, floatW = 280f, floatH = 340f
            )
            if (bruto == null) return e
            val p = bruto.split(',')
            if (p.size >= 7) {
                val v = p[0] == "1"
                val d = p[1] == "1"
                val w = p[2].toFloatOrNull()?.coerceIn(minDockWidthDp, maxDockWidthDp) ?: e.dockWidthDp
                val fx = p[3].toFloatOrNull() ?: e.floatX
                val fy = p[4].toFloatOrNull() ?: e.floatY
                val fw = p[5].toFloatOrNull()?.coerceAtLeast(MIN_FLOAT_W_DP) ?: e.floatW
                val fh = p[6].toFloatOrNull()?.coerceAtLeast(MIN_FLOAT_H_DP) ?: e.floatH
                e = EstadoPainel(v, d, w, fx, fy, fw, fh)
            }
            return e
        }

        fun formatarLinha(
            visible: Boolean, docked: Boolean, dockWidthDp: Float,
            floatX: Float, floatY: Float, floatW: Float, floatH: Float
        ): String {
            val r = { v: Float -> v.roundToInt().toString() }
            return "${if (visible) "1" else "0"},${if (docked) "1" else "0"}," +
                "${r(dockWidthDp)},${r(floatX)},${r(floatY)},${r(floatW)},${r(floatH)}"
        }

        /**
         * E4: CLAMP do painel flutuante — MANTÉM O PAINEL INTEIRO dentro do
         * viewport (posição e tamanho em PIXELS; bounds = viewport). Puro e
         * testável. Painel maior que o bounds fica encostado em 0.
         */
        fun clampDentro(
            x: Float, y: Float, w: Float, h: Float,
            boundsW: Float, boundsH: Float
        ): Pair<Float, Float> {
            val maxX = (boundsW - w).coerceAtLeast(0f)
            val maxY = (boundsH - h).coerceAtLeast(0f)
            return x.coerceIn(0f, maxX) to y.coerceIn(0f, maxY)
        }
    }
}
