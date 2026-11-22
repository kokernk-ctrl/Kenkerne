package com.kenkern.engine.editor

import android.content.Context

/**
 * T0.3 (adendo v4, seção 3): preferências da janela "Adicionar Nick" —
 * RECENTES (últimos tipos criados, no máximo 6) e FAVORITOS (estrela).
 * SharedPreferences simples (dados pequenos, leitura na composição).
 */
object AddNickStore {

    private const val PREFS = "kenkern_add_nick"
    private const val KEY_RECENTES = "recentes"
    private const val KEY_FAVORITOS = "favoritos"
    private const val MAX_RECENTES = 6

    fun recentes(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENTES, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun favoritos(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITOS, null)
            ?: emptySet()

    /** Registra a criação: o tipo vai ao TOPO dos recentes (máx. 6). */
    fun registrarCriacao(context: Context, kind: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nova = listOf(kind) + recentes(context).filter { it != kind }
        prefs.edit().putString(KEY_RECENTES, nova.take(MAX_RECENTES).joinToString(",")).apply()
    }

    /** Alterna a estrela do tipo; devolve o novo estado (true = favorito). */
    fun alternarFavorito(context: Context, kind: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val atuais = favoritos(context).toMutableSet()
        val agoraFavorito = kind !in atuais
        if (agoraFavorito) atuais.add(kind) else atuais.remove(kind)
        prefs.edit().putStringSet(KEY_FAVORITOS, atuais).apply()
        return agoraFavorito
    }
}
