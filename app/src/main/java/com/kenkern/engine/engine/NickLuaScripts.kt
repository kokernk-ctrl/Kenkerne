package com.kenkern.engine.engine

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.script.ScriptRuntime
import com.kenkern.engine.diag.KenkernLog
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.LibFunction
import org.luaj.vm2.lib.VarArgFunction
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * M1.1 (v0.1.21) — LUA LIGADO AOS NICKS: ponte mínima da LUA_API.md
 * (self, transform, find, destroy, spawn de prefab simples, tempo, log e
 * eventos) sobre o ScriptRuntime (LuaJ) existente.
 *
 * O SCRIPT é uma CLASSE do Nick ("Script" com campo `fonte`); os DEMAIS
 * campos da classe são VARIÁVEIS PÚBLICAS — entram no script como
 * `params.<nome>` (números/booleanos/texto) e aparecem/edítam-se no Inspetor
 * (a cópia de Play leva os valores editados).
 *
 * Erros NUNCA derrubam o jogo: erro de sintaxe/runtime/orçamento/instruções
 * DESATIVA o script com a mensagem no Console (KenkernLog) — o Play segue.
 *
 * API MÍNIMA (subconjunto do contrato LUA_API.md, documentado):
 *   nick.set_position(x, y, z) · nick.position() → {x, y, z}
 *   nick.set_scale(x, y, z) · nick.set_active(bool) · nick.name() → texto
 *   nick.destroy() · nick.class() → nome? (via scene.find: handle igual)
 *   scene.find(nome) → handle ou nil · scene.destroy(handle)
 *   scene.spawn(x, y, z) → handle (prefab simples: cubo de 1 m)
 *   scene.log(msg) · scene.emit(evento, valor) · scene.on(evento, fn)
 *   engine.time() → segundos desde o início do Play
 */
class NickLuaScripts(private val scene: Scene) {

    /**
     * M1.3: estado do INPUT DE JOGO (setado pelo SurfaceView na abertura do
     * Play) — exposto ao Lua como `input.joystick()` e `input.acao(nome)`.
     */
    var estadoInput: EstadoInputJogo? = null

    /**
     * M1.5: INTERFACE DE JOGO mínima — textos/contadores escritos pelo Lua
     * (ui.texto/ui.contador/ui.limpar) e lidos pelo overlay do editor.
     * Sincronizado (escrita no thread GL, leitura na UI).
     */
    private val textosUi = LinkedHashMap<String, String>()

    /** M1.7: contadores ACUMULATIVOS por sessão (compartilhados entre scripts). */
    private val contagens = HashMap<String, Int>()

    @Synchronized
    fun snapshotUi(): Map<String, String> = LinkedHashMap(textosUi)

    class Instancia(
        val nick: Nick,
        val runtime: ScriptRuntime,
        val scripts: NickLuaScripts
    )

    /** Runtime iniciados por Nick (1º de cada Nick com classe Script ativa). */
    private val instancias = ArrayList<Instancia>()

    /** Callbacks registrados via scene.on(evento, fn) — eventos M1.1/M1.2. */
    private val eventos = HashMap<String, LuaValue>()

    /** Tempo acumulado desde o início do Play (engine.time()). */
    private var tempoSegundos = 0f

    val total: Int get() = instancias.size

    /** M1.5/M1.7: leitura do overlay — textos + contadores acumulados. */
    @Synchronized
    fun snapshotUiJogo(): Map<String, String> {
        val saida = LinkedHashMap(textosUi)
        synchronized(contagens) {
            for ((nome, valor) in contagens) saida[nome] = valor.toString()
        }
        return saida
    }

    /** Instância pelo nome de exibição do Nick (testes e diagnóstico). */
    fun instanciaDe(nomeNick: String): Instancia? =
        instancias.firstOrNull { (it.nick.name ?: it.nick.kind) == nomeNick }

    /** Cria e inicia TODOS os scripts da cena (chamado 1× na abertura do Play). */
    fun iniciarTodos() {
        instancias.clear()
        eventos.clear()
        tempoSegundos = 0f
        val pilha = ArrayList<Nick>(32)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val nick = pilha[head++]
            if (nick.active) {
                for (c in nick.children) pilha.add(c)
                val fonte = nick.findClassInstance(CLASSE_SCRIPT)
                    ?.getString("fonte", "") ?: continue
                if (fonte.isBlank()) continue
                val runtime = ScriptRuntime.criar(
                    "script:${nick.name ?: nick.kind}", fonte, orcamentoMs = ORCAMENTO_MS
                ) ?: continue // erro de sintaxe já registrado no Console pelo runtime
                instalarPonte(runtime as LuaJScriptRuntime, nick)
                instalarVariaveisPublicas(runtime, nick)
                if (!runtime.iniciar()) continue // desativado com motivo no Console
                instancias.add(Instancia(nick, runtime, this))
            }
        }
        KenkernLog.w(TAG, "Play: ${instancias.size} script(s) Lua iniciado(s)")
    }

    /** on_update(dt) de cada script (erro desativa UM script, não o jogo). */
    fun atualizar(dt: Float) {
        tempoSegundos += dt
        for (i in instancias.indices) {
            val inst = instancias[i]
            if (inst.nick.active) inst.runtime.atualizar(dt)
        }
    }

    /**
     * M1.2: evento on_collision(nomeDoOutro) — chamado quando o jogador/servo
     * dono do script PARA contra um corpo estático.
     */
    fun emitirColisao(nick: Nick, nomeOutro: String) {
        chamar(nick, "on_collision", nomeOutro)
    }

    /** M1.2: evento on_trigger(nomeDoOutro) — entrada de um Nick na Área. */
    fun emitirGatilho(nick: Nick, nomeOutro: String) {
        chamar(nick, "on_trigger", nomeOutro)
    }

    private fun chamar(nick: Nick, funcao: String, nomeOutro: String) {
        for (inst in instancias) {
            if (inst.nick !== nick) continue
            val globals = (inst.runtime as? LuaJScriptRuntime)?.ambiente() ?: continue
            val f = globals.get(funcao)
            if (f.isfunction()) {
                try {
                    (f as LuaFunction).call(LuaValue.valueOf(nomeOutro))
                } catch (e: Throwable) {
                    KenkernLog.e(TAG, "script '${inst.runtime.nome()}' falhou em $funcao: ${e.message}")
                    // erro em evento não desativa o script inteiro (mesmo espírito
                    // do orçamento): registrado e o jogo segue.
                }
            }
        }
    }

    /** Dispara callbacks registrados com scene.on(nome, fn). */
    fun dispararEvento(nome: String, valor: LuaValue) {
        val f = eventos[nome] ?: return
        if (f.isfunction()) {
            try {
                (f as LuaFunction).call(valor)
            } catch (e: Throwable) {
                KenkernLog.e(TAG, "evento '$nome' falhou no script: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------- ponte

    /**
     * Instala nick/scene/engine do Nick DONO (self). Handles de outros Nicks
     * são tables com as MESMAS funções (find/spawn devolvem handles).
     */
    private fun instalarPonte(runtime: LuaJScriptRuntime, dono: Nick) {
        // ---------- nick (self) ----------
        runtime.instalarTabela("nick", tabelaNick(dono))

        // ---------- scene ----------
        val cena = LuaTable()
        cena.set("find", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val achou = scene.findNickByNameAnywhere(nome) ?: return LuaValue.NIL
                return tabelaNick(achou)
            }
        })
        cena.set("spawn", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                // PREFAB SIMPLES: cubo de 1 m (nick.mesh padrão) na posição dada.
                val x = args.arg1().optdouble(0.0)
                val y = args.arg(2).optdouble(0.0)
                val z = args.arg(3).optdouble(0.0)
                val novo = scene.createNick(
                    null, null,
                    kind = NickKindRegistry.NICK_MESH
                )
                novo.transform.setLocalPosition(x.toFloat(), y.toFloat(), z.toFloat())
                return tabelaNick(novo)
            }
        })
        cena.set("destroy", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val alvo = nickDe(args.arg1()) ?: return LuaValue.NIL
                destruir(alvo)
                return LuaValue.NIL
            }
        })
        cena.set("log", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                KenkernLog.w(TAG, "lua: ${args.arg1().optjstring("")}")
                return LuaValue.NIL
            }
        })
        cena.set("emit", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                dispararEvento(args.arg1().optjstring(""), args.arg(2))
                return LuaValue.NIL
            }
        })
        cena.set("on", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                if (args.arg(2).isfunction()) eventos[nome] = args.arg(2)
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("scene", cena)

        // ---------- engine ----------
        val engine = LuaTable()
        engine.set("time", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(tempoSegundos.toDouble())
        })
        runtime.instalarTabela("engine", engine)

        // ---------- input (M1.3) ----------
        val input = LuaTable()
        input.set("joystick", object : LibFunction() {
            override fun call(): LuaValue {
                val e = estadoInput
                val t = LuaTable()
                t.set("x", LuaValue.valueOf(e?.moverX?.toDouble() ?: 0.0))
                t.set("y", LuaValue.valueOf(e?.moverY?.toDouble() ?: 0.0))
                t.set("ativo", LuaValue.valueOf(e != null && (abs(e.moverX) > 1e-4 || abs(e.moverY) > 1e-4)))
                return t
            }
        })
        input.set("acao", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val e = estadoInput ?: return LuaValue.FALSE
                return when (args.arg1().optjstring("")) {
                    "pular" -> LuaValue.valueOf(e.pularPressionado)
                    "atacar" -> LuaValue.valueOf(e.atacarPressionado)
                    else -> LuaValue.FALSE
                }
            }
        })
        runtime.instalarTabela("input", input)

        // ---------- ui (M1.5): texto e contador controlados por Lua ----------
        val ui = LuaTable()
        ui.set("texto", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val texto = args.arg(2).optjstring("")
                synchronized(textosUi) {
                    if (texto.isEmpty()) textosUi.remove(nome) else textosUi[nome] = texto
                }
                return LuaValue.NIL
            }
        })
        ui.set("contador", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val valor = args.arg(2).optint(0)
                synchronized(textosUi) { textosUi[nome] = valor.toString() }
                return LuaValue.NIL
            }
        })
        ui.set("limpar", object : LibFunction() {
            override fun call(): LuaValue {
                synchronized(textosUi) { textosUi.clear() }
                synchronized(contagens) { contagens.clear() }
                return LuaValue.NIL
            }
        })
        // M1.7: CONTADOR ACUMULATIVO compartilhado por TODOS os scripts da cena
        // (cada Nick tem um runtime próprio; a contagem mora na ponte).
        ui.set("contagem", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val delta = args.arg(2).optint(1)
                synchronized(contagens) { contagens[nome] = (contagens[nome] ?: 0) + delta }
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("ui", ui)
    }

    /**
     * VARIÁVEIS PÚBLICAS: campos da classe Script (exceto `fonte`) entram no
     * script como params.<nome> (FLOAT → número, BOOL → booleano, STRING →
     * texto, VEC3 → table {x, y, z}). Editáveis no Inspetor a qualquer momento.
     */
    private fun instalarVariaveisPublicas(runtime: LuaJScriptRuntime, nick: Nick) {
        val inst = nick.findClassInstance(CLASSE_SCRIPT) ?: return
        val params = LuaTable()
        for ((nome, valor) in inst.fields) {
            if (nome == CAMPO_FONTE) continue
            when (valor) {
                is NickValue.FloatValue -> params.set(nome, LuaValue.valueOf(valor.value.toDouble()))
                is NickValue.BoolValue -> params.set(nome, LuaValue.valueOf(valor.value))
                is NickValue.IntValue -> params.set(nome, LuaValue.valueOf(valor.value))
                is NickValue.StringValue -> params.set(nome, LuaValue.valueOf(valor.value))
                is NickValue.Vec3Value -> {
                    val v = LuaTable()
                    v.set("x", LuaValue.valueOf(valor.x.toDouble()))
                    v.set("y", LuaValue.valueOf(valor.y.toDouble()))
                    v.set("z", LuaValue.valueOf(valor.z.toDouble()))
                    params.set(nome, v)
                }
            }
        }
        runtime.instalarTabela("params", params)
    }

    /** Table Lua que envolve um Nick (todas as funções de transform/self). */
    private fun tabelaNick(nick: Nick): LuaTable {
        val t = LuaTable()
        t.set("__nick", LuaValue.valueOf(System.identityHashCode(nick)))
        t.set("name", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(nick.displayName("pt"))
        })
        t.set("set_position", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                nick.transform.setLocalPosition(
                    args.arg1().optdouble(0.0).toFloat(),
                    args.arg(2).optdouble(0.0).toFloat(),
                    args.arg(3).optdouble(0.0).toFloat()
                )
                return LuaValue.NIL
            }
        })
        t.set("position", object : LibFunction() {
            override fun call(): LuaValue {
                val p = nick.transform.position
                val v = LuaTable()
                v.set("x", LuaValue.valueOf(p.x.toDouble()))
                v.set("y", LuaValue.valueOf(p.y.toDouble()))
                v.set("z", LuaValue.valueOf(p.z.toDouble()))
                return v
            }
        })
        t.set("set_scale", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                nick.transform.scale.set(
                    args.arg1().optdouble(1.0).toFloat(),
                    args.arg(2).optdouble(1.0).toFloat(),
                    args.arg(3).optdouble(1.0).toFloat()
                )
                return LuaValue.NIL
            }
        })
        t.set("set_active", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                nick.active = args.arg1().optboolean(true)
                return LuaValue.NIL
            }
        })
        t.set("destroy", object : LibFunction() {
            override fun call(): LuaValue {
                destruir(nick)
                return LuaValue.NIL
            }
        })
        return t
    }

    /** Recupera o Nick de um handle (table com __nick) — nil se não for handle. */
    private fun nickDe(handle: LuaValue): Nick? {
        if (!handle.istable()) return null
        val id = handle.get("__nick")
        if (!id.isint()) return null
        val esperado = id.toint()
        val pilha = ArrayList<Nick>(32)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val n = pilha[head++]
            if (System.identityHashCode(n) == esperado) return n
            for (c in n.children) pilha.add(c)
        }
        return null
    }

    /** Destrói o Nick da cena de PLAY (a cena do editor NUNCA é tocada). */
    private fun destruir(nick: Nick) {
        // removeNick também DESREGISTRA da tabela de referências (o findNick
        // não devolve mais o Nick destruído).
        scene.removeNick(nick)
    }

    companion object {
        private const val TAG = "Kenkern/Lua"

        /** Orçamento de tempo por tick por script (ms). */
        const val ORCAMENTO_MS: Long = 4L

        /** Nome da CLASSE de script do Nick (campo `fonte` = código Lua). */
        const val CLASSE_SCRIPT = "Script"
        const val CAMPO_FONTE = "fonte"

        /** Distância usada pela detecção de proximidade (M1.2 testa AABB). */
        fun distanciaAprox(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
            sqrt(
                (ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz)
            )
    }
}
