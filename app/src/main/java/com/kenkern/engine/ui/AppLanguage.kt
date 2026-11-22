package com.kenkern.engine.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * IDIOMAS DO APP (T0.1, adendo v4 — DECISIONS #27): Inglês (en) e Português
 * (pt), recursos Android (`values/strings.xml` + `values-pt/strings.xml`).
 *
 *  - PADRÃO = idioma do aparelho (pt-BR/pt-PT → "pt"; qualquer outro → "en");
 *  - o seletor (Hub hoje; painel Configurações no futuro) grava a escolha em
 *    SharedPreferences e RECREIA a Activity — troca imediata;
 *  - o idioma é aplicado no `attachBaseContext` da MainActivity com
 *    `createConfigurationContext` (funciona em TODAS as versões suportadas,
 *    sem depender de per-app language do Android 13+).
 */
object AppLanguage {

    const val EN = "en"
    const val PT = "pt"
    const val DEFAULT = EN

    private const val PREFS = "kenkern_app"
    private const val KEY = "app_language"

    /** Idioma ESCOLHIDO pelo usuário (null = seguir o padrão do aparelho). */
    fun chosen(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** Idioma EFETIVO do app: escolha do usuário ou padrão do aparelho. */
    fun currentTag(context: Context): String {
        val escolhido = chosen(context)
        if (escolhido == EN || escolhido == PT) return escolhido
        return deviceDefault()
    }

    /** Padrão pelo idioma do APARELHO: português do Brasil/Portugal → pt; resto → en. */
    fun deviceDefault(): String {
        val tag = Locale.getDefault().language
        return if (tag.equals(PT, ignoreCase = true)) PT else DEFAULT
    }

    /**
     * Grava a escolha e RECREIA a Activity (troca imediata). Passar `null`
     * volta a seguir o idioma do aparelho. O contexto deve ser a Activity
     * visível (recreate recompõe a UI com o novo locale).
     */
    fun apply(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag).apply()
        (context as? Activity)?.recreate()
            ?: KenkernLogI18n.warn("AppLanguage.apply fora de Activity — idioma aplica só na próxima abertura")
    }

    /**
     * Contexto com o idioma do app (chamar no `attachBaseContext` da Activity).
     * Também fixa o `Locale.setDefault` para formatos de data/número seguirem
     * o idioma escolhido.
     */
    fun wrap(base: Context): Context {
        val tag = runCatching { currentTag(base) }
            .onFailure { android.util.Log.w("Kenkern/AppLanguage", "falha ao ler idioma — usando padrão do aparelho", it) }
            .getOrDefault(deviceDefault())
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

/** Console mínimo para o AppLanguage (evita dependência cíclica de pacotes). */
private object KenkernLogI18n {
    fun warn(msg: String) = android.util.Log.w("Kenkern/AppLanguage", msg)
}
