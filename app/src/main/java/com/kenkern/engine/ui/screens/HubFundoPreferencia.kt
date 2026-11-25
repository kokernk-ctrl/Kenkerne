package com.kenkern.engine.ui.screens

import android.content.Context

/**
 * Preferência persistida da qualidade do fundo do Hub (Alta / Baixa — C1).
 */
object HubFundoPreferencia {
    private const val PREFS = "kenkern_hub_prefs"
    private const val KEY_QUALIDADE = "fundo_qualidade"

    fun ler(context: Context): QualidadeFundoHub {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return QualidadeFundoHub.fromCodigo(sp.getString(KEY_QUALIDADE, QualidadeFundoHub.ALTA.codigo))
    }

    fun salvar(context: Context, qualidade: QualidadeFundoHub) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALIDADE, qualidade.codigo)
            .apply()
    }
}
