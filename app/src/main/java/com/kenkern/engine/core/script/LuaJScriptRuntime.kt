package com.kenkern.engine.core.script

import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.CoroutineLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.jse.JseBaseLib

/**
 * Implementação LUA com LuaJ (Fase 3, F3.1) — JVM puro, sem NDK.
 *
 * SANDBOX: o ambiente global recebe SÓ as bibliotecas seguras (base/math/
 * string/table/coroutine/bit32). NADA de os/io/luajava — script de jogo não
 * toca sistema de arquivos nem classes Java.
 *
 * Erros NUNCA vazam: qualquer LuaError desativa o script com a mensagem
 * (linha incluída pelo LuaJ). Orçamento de TEMPO por tick de update:
 * excedeu, o script é desativado por orçamento (o editor mostra o motivo).
 *
 * LIMITAÇÃO CONHECIDA (documentada em DECISIONS.md): LuaJ não tem limite de
 * INSTRUÇÕES — um `while true do end` sem syscalls trava o Play até o Stop;
 * a mitigação é o orçamento de tempo + desativação manual no editor.
 */
class LuaJScriptRuntime(
    private val nomeScript: String,
    private var orcamentoMs: Long = 4L
) : ScriptRuntime {

    private var estado: ScriptRuntime.Estado = ScriptRuntime.Estado.PRONTO
    private var erro: String? = null
    private var funcaoUpdate: LuaFunction? = null
    private var ambiente: Globals? = null
    private var chunkTopo: LuaValue? = null // top-level compilado; roda no iniciar()

    override fun carregar(nome: String, fonte: String): String? {
        return try {
            val globals = criarAmbiente()
            // COMPIA sem rodar: o top-level só roda no iniciar(), DEPOIS que a
            // ponte de engine (nick/scene/engine, F3.2) foi instalada.
            val chunk = globals.load(fonte, "=$nome")
            ambiente = globals
            chunkTopo = chunk
            null
        } catch (e: LuaError) {
            estado = ScriptRuntime.Estado.ERRO
            erro = e.message ?: "erro de sintaxe"
            erro
        } catch (e: Exception) {
            estado = ScriptRuntime.Estado.ERRO
            erro = "${e.javaClass.simpleName}: ${e.message}"
            erro
        }
    }

    override fun iniciar(): Boolean {
        val globals = ambiente ?: return falhou("script não carregado")
        val chunk = chunkTopo ?: return falhou("script não compilado")
        return runCatching {
            // 1) top-level (define funções e estado inicial do script)
            chunk.call()
            // 2) on_start (se existir)
            val onStart = globals.get("on_start")
            if (onStart.isfunction()) (onStart as LuaFunction).call()
            // 3) on_update é capturado AQUI (o top-level pode tê-lo definido)
            funcaoUpdate = globals.get("on_update").takeIf { it.isfunction() } as? LuaFunction
            estado = ScriptRuntime.Estado.INICIADO
            true
        }.getOrElse {
            falhou(it.message ?: "erro ao iniciar o script")
        }
    }

    override fun atualizar(dt: Float): Boolean {
        if (estado == ScriptRuntime.Estado.DESATIVADO) return false
        val update = funcaoUpdate ?: return true // script sem on_update: ok, não é erro
        val inicio = System.nanoTime()
        return runCatching {
            // Nota: LuaJ aloca internamente por chamada (impossível zero-alloc
            // com script) — registrado em DECISIONS.md; orçamento de tempo segura
            // o custo por tick.
            update.call(LuaValue.valueOf(dt.toDouble()))
            true
        }.getOrElse {
            falhou(it.message ?: "erro em on_update")
        }.also {
            val gastoMs = (System.nanoTime() - inicio) / 1_000_000L
            if (it && gastoMs > orcamentoMs) {
                estado = ScriptRuntime.Estado.DESATIVADO
                erro = "orçamento estourado: on_update levou ${gastoMs}ms (limite ${orcamentoMs}ms)"
                funcaoUpdate = null
            }
        }
    }

    override fun estado(): ScriptRuntime.Estado = estado

    override fun ultimoErro(): String? = erro

    override fun nome(): String = nomeScript

    override fun definirOrcamentoMs(ms: Long) {
        orcamentoMs = ms
    }

    // ---------------------------------------------------------------- sandbox

    /**
     * Ambiente SEGURO: base/math/string/table/coroutine/bit32. Sem os/io.
     * A ponte de engine (nick/scene/engine) é instalada pela F3.2 via
     * [instalarTabela] — mantida separada para o runtime ficar testável puro.
     */
    private fun criarAmbiente(): Globals = Globals().also { g ->
        // LuaJ 3.0.1: MathLib (e outras) indexam package.loaded ao se registrar.
        // Em vez de instalar o PackageLib (que daria `require` com busca em
        // disco/classpath), plantamos uma mesa "package" VAZIA: registro das
        // libs funciona e `require` não existe no sandbox.
        val pkg = LuaTable()
        pkg.set("loaded", LuaTable())
        g.set("package", pkg)

        g.load(JseBaseLib())
        g.load(MathLib())
        g.load(StringLib())
        g.load(TableLib())
        g.load(CoroutineLib())
        g.load(Bit32Lib())
        LoadState.install(g)
        LuaC.install(g)
    }

    /** Instala uma tabela global extra (ponte nick/scene/engine na F3.2). */
    fun instalarTabela(nome: String, tabela: LuaTable) {
        ambiente?.set(nome, tabela)
    }

    /** Acesso ao ambiente (F3.2 lê para montar a ponte antes do iniciar). */
    fun ambiente(): Globals? = ambiente

    private fun falhou(motivo: String): Boolean {
        estado = ScriptRuntime.Estado.DESATIVADO
        erro = motivo
        funcaoUpdate = null
        return false
    }
}
