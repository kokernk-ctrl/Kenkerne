package com.kenkern.engine.core.script

/**
 * RUNTIME DE SCRIPT da KENKERN (Fase 3) — LUA atrás de interface.
 *
 * Contrato:
 *  - [carregar] COMPIA o fonte (erros de SINTAXE não lançam — retornam
 *    mensagem para o editor mostrar); nada roda ainda;
 *  - [iniciar] roda o script (top-level + on_start se existir) — depois de
 *    a ponte de engine já estar instalada;
 *  - [atualizar] chama on_update(dt) a cada tick de Play;
 *  - erro em qualquer ponto NUNCA lança para o chamador: o script é
 *    DESATIVADO e o motivo fica em [ultimoErro] (o jogo continua);
 *  - orçamento: [atualizar] mede o TEMPO da chamada; passou do limite,
 *    o script é desativado por orçamento estourado.
 *
 * Implementação atual: LuaJ (JVM puro, sem NDK — DECISIONS.md). A troca por
 * Lua 5.4 nativo (NDK/JNI) não muda nenhum chamador, só esta interface.
 */
interface ScriptRuntime {

    /** Estado de um script (o jogo continua em qualquer um deles). */
    enum class Estado { PRONTO, INICIADO, ERRO, DESATIVADO }

    /** Resultado de carregar: null = compilou; senão, mensagem de erro. */
    fun carregar(nome: String, fonte: String): String?

    /** Roda top-level + on_start(). False se não rodou (erro desativou). */
    fun iniciar(): Boolean

    /** Chama on_update(dt) — dt em segundos. False se desativado/erro. */
    fun atualizar(dt: Float): Boolean

    /** Estado atual do script. */
    fun estado(): Estado

    /** Último erro (sintaxe, runtime ou orçamento); null se nenhum. */
    fun ultimoErro(): String?

    /** Nome do script (para mensagens e o editor). */
    fun nome(): String

    /** Orçamento de tempo por chamada de atualizar (ms). */
    fun definirOrcamentoMs(ms: Long)

    companion object {
        /** Fábrica: a engine inteira instancia o runtime por AQUI (troca fácil). */
        fun criar(nome: String, fonte: String, orcamentoMs: Long = 4L): ScriptRuntime? {
            val rt = LuaJScriptRuntime(nome, orcamentoMs)
            return if (rt.carregar(nome, fonte) == null) rt else null
        }
    }
}
