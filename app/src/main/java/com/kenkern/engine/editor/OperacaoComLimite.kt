package com.kenkern.engine.editor

import kotlinx.coroutines.CancellationException

/**
 * S5 (v0.1.24) — LIMITE para toda operação assíncrona do Hub/editor: nada
 * fica "carregando" PARA SEMPRE (spinner infinito). A CAUSA RAIZ do spinner
 * eterno: createAndEnterProject/openProject/loadIntoEditor/importarKernpack/
 * duplicate/export (EditorViewModel) rodavam em Dispatchers.IO SEM TIMEOUT —
 * um I/O travado (SAF pendurado, armazenamento lento) deixava isLoading=true
 * para sempre: o Hub desabilita TODOS os botões ("Carregando…" no lugar da
 * lista) e nada mais funciona.
 *
 * CORREÇÃO: withTimeoutOrNull de [LIMITE_MS] (15 s) — bloco pendurado vira
 * TempoEsgotado com a OPERAÇÃO e a ETAPA corrente como causa; o chamador
 * (EditorViewModel) mostra o estado de ERRO VISÍVEL (statusMessage) e registra
 * no Console (KenkernLog.e). Sucesso/Erro seguem o fluxo normal.
 */
object OperacaoComLimite {

    /** Limite da tarefa S5: 15 s por operação do Hub/editor. */
    const val LIMITE_MS = 15_000L

    sealed class Resultado<out T> {
        data class Sucesso<T>(val valor: T) : Resultado<T>()
        data class Erro(val causa: Throwable) : Resultado<Nothing>()

        /** Tempo esgotado: [operacao] + [etapa] corrente = a CAUSA da mensagem. */
        data class TempoEsgotado(val operacao: String, val etapa: String?, val limiteMs: Long) : Resultado<Nothing>()
    }

    /** Caixa interna: distingue "bloco devolveu null" de "estourou o tempo". */
    private class Caixa<out T>(val valor: T)

    /**
     * Executa [bloco] (suspend) com o limite: Sucesso / Erro (exceção com
     * mensagem e log no chamador) / TempoEsgotado (operações penduradas viram
     * estado de erro VISÍVEL, nunca spinner eterno).
     */
    suspend fun <T> executar(
        operacao: String,
        limiteMs: Long = LIMITE_MS,
        etapa: (() -> String?)? = null,
        bloco: suspend () -> T
    ): Resultado<T> = try {
        val caixa = kotlinx.coroutines.withTimeoutOrNull(limiteMs) { Caixa(bloco()) }
            ?: return Resultado.TempoEsgotado(operacao, etapa?.invoke(), limiteMs)
        Resultado.Sucesso(caixa.valor)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Resultado.Erro(e)
    }
}
