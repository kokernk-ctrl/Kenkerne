package com.kenkern.engine.assets

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

/**
 * Onda 0 (v0.1.28, P5) — FONTES OFL EMBUTIDAS NO APK.
 *
 * As 8 fontes livres do TELAR (lista [NOMES] = a mesma `FONTES_OFL`) vivem em
 * `assets/fonts/<Nome>.ttf` DENTRO do APK e são carregadas por
 * [Typeface.createFromAsset] — NENHUM download em runtime (funciona em modo
 * avião; licenças no LICENSES.md).
 *
 * ANTES: os nomes eram só RÓTULOS — `familiaDe()` no TelarScreen devolvia os
 * 3 FontFamily GENÉRICOS do sistema (Default/Monospace/Serif) e nenhum
 * arquivo de fonte existia no APK.
 *
 * Cache: uma vez por nome (ConcurrentHashMap; carregar de novo após
 * INVALIDAÇÃO de assets não existe aqui — o asset não muda dentro de um
 * processo). FALHA NÃO SILENCIOSA: asset ausente registra no Console
 * (KenkernLog) e devolve o FontFamily padrão do sistema — a tela continua
 * legível e o Diagnóstico acusa (a checagem "ui" mostra a fonte carregada).
 *
 * PARTE PURA (JVM): [NOMES], [arquivoDe], [conhecida] — provadas em
 * FontesKenkernTest, junto com a EXISTÊNCIA dos 8 .ttf em
 * `app/src/main/assets/fonts/` (guarda: ninguém apaga o embedding sem o CI
 * ficar vermelho).
 */
object FontesKenkern {

    /** Os 8 nomes (chaves estáveis do .kui — mesmas de FONTES_OFL do TELAR). */
    val NOMES = listOf(
        "Roboto", "RobotoMono", "NotoSans", "NotoSerif",
        "OpenSans", "Lato", "Montserrat", "JetBrainsMono"
    )

    /** Caminho do asset para um nome ("Roboto" → "fonts/Roboto.ttf"). */
    fun arquivoDe(nome: String): String = "fonts/$nome.ttf"

    /** O nome está na lista das fontes embutidas? */
    fun conhecida(nome: String): Boolean = nome in NOMES

    /** Cache por processo — carrega cada asset UMA vez. */
    private val cache = ConcurrentHashMap<String, Typeface>()

    /**
     * Carrega a fonte do APK (cacheada). Nome desconhecido ou asset ausente
     * → FontFamily do SISTEMA + linha no Console (nada silencioso).
     */
    fun carregar(context: Context, nome: String): Typeface? {
        if (!conhecida(nome)) return null
        cache[nome]?.let { return it }
        return try {
            val tf = Typeface.createFromAsset(context.assets, arquivoDe(nome))
            cache[nome] = tf
            tf
        } catch (t: Throwable) {
            com.kenkern.engine.diag.KenkernLog.e(
                "Kenkern/Fontes",
                "fonte '$nome' não carregou do APK (${arquivoDe(nome)}) — usando a padrão do sistema",
                t
            )
            null
        }
    }

    /** Limpa o cache (testes). */
    fun limparCache() = cache.clear()
}
