package com.kenkern.engine.diag

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Console de erros da KENKERN (regra do realinhamento — DECISIONS #17):
 * nenhum `runCatching` em caminho de render/formato/carregamento engole erro
 * em silêncio. Todo erro registrado passa aqui e alcança 3 destinos:
 *
 * 1. **Log** (logcat) — como sempre;
 * 2. **Console** — anel em memória de 200 entradas, consultável por qualquer
 *    thread (`registros()`);
 * 3. **Tela Diagnóstico** — a seção CONSOLE lista os registros; o overlay do
 *    editor mostra a contagem e as últimas linhas.
 *
 * INFRA-DIAG: nunca lança — uma falha do próprio console não pode derrubar o
 * app (mesma política do BootTrace). JVM-safe: o log de Android pode ser
 * desligado nos testes unitários ([androidLogging]).
 */
object KenkernLog {

    enum class Nivel { E, W, I }

    /** Uma entrada do console (imutável, thread-safe por cópia). */
    data class Registro(
        val nivel: Nivel,
        val tag: String,
        val mensagem: String,
        val detalhe: String?,   // exception.message ou causa curta
        val quando: Long
    ) {
        fun linha(hora: SimpleDateFormat): String = buildString {
            append(hora.format(Date(quando)))
            append(' ').append(
                when (nivel) {
                    Nivel.E -> "ERRO"
                    Nivel.W -> "AVISO"
                    Nivel.I -> "INFO"
                }
            )
            append(" [").append(tag).append("] ").append(mensagem)
            if (!detalhe.isNullOrBlank()) append(" — ").append(detalhe)
        }
    }

    /** Desligado nos testes JVM (android.util.Log não é mockável sem robolectric). */
    @Volatile
    var androidLogging: Boolean = true

    private const val CAPACIDADE = 200

    private val trava = Any()
    private val anel = ArrayDeque<Registro>(CAPACIDADE)
    private val hora = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Registra ERRO: logcat `Log.e` + anel do console. */
    fun e(tag: String, mensagem: String, erro: Throwable? = null) {
        registrar(Nivel.E, tag, mensagem, erro)
    }

    /** Registra AVISO: logcat `Log.w` + anel do console. */
    fun w(tag: String, mensagem: String, erro: Throwable? = null) {
        registrar(Nivel.W, tag, mensagem, erro)
    }

    /** L1 (v0.1.28): registra INFO (instrumentação — ex.: uniforms da luz) no console. */
    fun i(tag: String, mensagem: String) {
        registrar(Nivel.I, tag, mensagem, null)
    }

    private fun registrar(nivel: Nivel, tag: String, mensagem: String, erro: Throwable?) {
        if (androidLogging) {
            try {
                val msg = if (erro != null) "$mensagem: ${erro.message ?: erro.javaClass.simpleName}" else mensagem
                when (nivel) {
                    Nivel.E -> Log.e(tag, msg, erro)
                    Nivel.W -> Log.w(tag, msg, erro)
                    Nivel.I -> Log.i(tag, msg)
                }
            } catch (_: Throwable) {
                // INFRA-DIAG: logcat indisponível não pode derrubar o chamador.
            }
        }
        try {
            val reg = Registro(
                nivel = nivel,
                tag = tag,
                mensagem = mensagem,
                detalhe = erro?.let { it.message ?: it.javaClass.simpleName },
                quando = System.currentTimeMillis()
            )
            synchronized(trava) {
                if (anel.size >= CAPACIDADE) anel.removeFirst()
                anel.addLast(reg)
            }
        } catch (_: Throwable) {
            // INFRA-DIAG: o console jamais lança (mesma política do BootTrace).
        }
    }

    /** Cópia do anel (mais recente por último) — para as telas. */
    fun registros(): List<Registro> = synchronized(trava) { anel.toList() }

    /** Quantidade de ERROS no anel (para o overlay sem custo de string). */
    fun totalErros(): Int = synchronized(trava) {
        var n = 0
        for (r in anel) if (r.nivel == Nivel.E) n++
        n
    }

    /** Limpa o console (botão do Diagnóstico). */
    fun limpar() = synchronized(trava) { anel.clear() }
}
