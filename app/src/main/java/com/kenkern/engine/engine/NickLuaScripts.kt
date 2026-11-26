package com.kenkern.engine.engine

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.script.ScriptRuntime
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.KenkernLog
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.LibFunction
import org.luaj.vm2.lib.VarArgFunction
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * L2: Ponte completa de scripts Lua sobre [ScriptRuntime] (LuaJ).
 *
 * API documentada em LUA_API.md e espelhada em LuaApiCatalog:
 *  - eventos globais (`scene.emit`/`scene.on`/`scene.off` com múltiplos ouvintes + dados)
 *  - timers determinísticos (`timer.after`/`timer.every`/`timer.cancel`)
 *  - callbacks de área (`on_enter`/`on_exit`/`on_stay` + legado `on_trigger` + `on_collision`)
 *  - busca por nome e tag (`scene.find`/`find_by_tag`/`find_all_by_tag`, `nick.has_tag`/`add_tag`/`remove_tag`/`tags`)
 *  - spawn de prefab e destruição (`scene.spawn`/`spawn_prefab`/`destroy`, `nick.destroy`)
 *  - leitura/escrita de classes do Nick (`nick.get`/`set`/`class_data`, `params.*`)
 *  - input por ação e joystick (`input.action`/`acao`/`joystick`)
 *  - UI (`ui.text`/`texto`/`counter`/`contador`/`count`/`contagem`/`get_count`/`clear`/`limpar`)
 *  - persistência (`storage.save`/`load`/`has`/`delete`/`clear`)
 *  - log (`scene.log`/`engine.log`/`log`) e tempo (`engine.time`/`engine.dt`)
 */
class NickLuaScripts(
    private val scene: Scene,
    private val projectRoot: File? = null,
    private val saveStore: GameSaveStore = defaultSaveStore(projectRoot),
    private val prefabProvider: ((String) -> Nick?)? = null,
    private val scriptResolver: ((String) -> String?)? = null
) {
    /** Estado do input de jogo (setado na abertura do Play). */
    var estadoInput: EstadoInputJogo? = null

    /**
     * C2 (v0.1.26): consultor de área/respawn fornecido pelo NickPlayRuntime —
     * bounds de MUNDO de qualquer Nick (para area.bodies()/contains()) e o
     * reaparecer do jogador (player.respawn()).
     */
    var consultorArea: ConsultorArea? = null

    /** Contrato mínimo que o runtime entrega à ponte Lua (sem ciclo de dependência). */
    interface ConsultorArea {
        fun boundsMundo(nick: Nick, out: FloatArray)
        fun corposCena(): List<Nick>
        fun respawnJogador(): Boolean
        fun boundsJogador(out: FloatArray)
    }

    /** Buffer de logs emitidos por Lua (útil para diagnósticos e testes). */
    private val logsRecentes = ArrayList<String>()

    private val textosUi = LinkedHashMap<String, String>()
    private val contagens = HashMap<String, Int>()

    /** C2.2: ponte interna para signal.emit → o barramento de eventos da cena. */
    internal var cenaEventosBridge: ((String, LuaValue) -> Unit)? = null
    /** C2.2: ponte interna para signal.on → registro no barramento de eventos da cena. */
    internal var cenaOnBridge: ((String, LuaFunction) -> Unit)? = null

    @Synchronized
    fun snapshotUi(): Map<String, String> = LinkedHashMap(textosUi)

    @Synchronized
    fun logsEmitidos(): List<String> = logsRecentes.toList()

    class Instancia(
        val nick: Nick,
        val runtime: ScriptRuntime,
        val scripts: NickLuaScripts,
        val origem: String
    )

    private data class TimerEntry(
        val id: Int,
        val interval: Float,
        var remaining: Float,
        val repeating: Boolean,
        val callback: LuaFunction,
        val dono: Nick,
        var cancelled: Boolean = false
    )

    private val instancias = ArrayList<Instancia>()
    private val eventos = HashMap<String, MutableList<LuaValue>>()
    private val timers = ArrayList<TimerEntry>()
    private var proximoTimerId = 1

    private var tempoSegundos = 0f
    private var ultimoDt = 0f

    val total: Int get() = instancias.size

    @Synchronized
    fun snapshotUiJogo(): Map<String, String> {
        val saida = LinkedHashMap(textosUi)
        synchronized(contagens) {
            for ((nome, valor) in contagens) saida[nome] = valor.toString()
        }
        return saida
    }

    @Synchronized
    fun lerContagem(nome: String): Int = contagens[nome] ?: 0

    fun instanciasPublicas(): List<Instancia> = instancias.toList()

    fun instanciaDe(nomeNick: String): Instancia? =
        instancias.firstOrNull { (it.nick.name ?: it.nick.kind) == nomeNick }

    /** Cria e inicia todos os scripts ativos da cena. */
    fun iniciarTodos() {
        instancias.clear()
        eventos.clear()
        timers.clear()
        logsRecentes.clear()
        tempoSegundos = 0f
        ultimoDt = 0f

        val pilha = ArrayList<Nick>(32)
        for (r in scene.roots) pilha.add(r)
        var head = 0
        while (head < pilha.size) {
            val nick = pilha[head++]
            if (nick.active) {
                for (c in nick.children) pilha.add(c)
                iniciarScriptDeNick(nick)
            }
        }
        KenkernLog.w(TAG, "Play: ${instancias.size} script(s) Lua iniciado(s)")
    }

    private fun iniciarScriptDeNick(nick: Nick) {
        val scriptInst = nick.findClassInstance(CLASSE_SCRIPT) ?: return
        val arquivo = scriptInst.getString(CAMPO_ARQUIVO, "").trim()
        val fonteInline = scriptInst.getString(CAMPO_FONTE, "")
        val fonteResolvida = if (arquivo.isNotEmpty()) {
            resolverFonteExterna(arquivo) ?: run {
                KenkernLog.e(TAG, "arquivo Lua '$arquivo' não encontrado no projeto")
                ""
            }
        } else {
            fonteInline
        }
        if (fonteResolvida.isBlank()) return

        val nomeChunk = if (arquivo.isNotEmpty()) arquivo else (nick.name ?: nick.kind)
        val runtime = ScriptRuntime.criar(
            nomeChunk, fonteResolvida, orcamentoMs = ORCAMENTO_MS
        ) as? LuaJScriptRuntime ?: return
        instalarPonte(runtime, nick)
        instalarVariaveisPublicas(runtime, nick)
        if (!runtime.iniciar()) return
        instancias.add(Instancia(nick, runtime, this, nomeChunk))
    }

    private fun resolverFonteExterna(caminho: String): String? {
        scriptResolver?.invoke(caminho)?.let { return it }
        val root = projectRoot ?: return null
        val direto = File(root, caminho)
        val candidato = if (direto.isFile) direto else File(root, "scripts/$caminho")
        if (!candidato.isFile) return null
        return try {
            val rootCan = root.canonicalPath
            val fileCan = candidato.canonicalPath
            if (!fileCan.startsWith(rootCan)) {
                KenkernLog.w(TAG, "caminho de script fora do projeto ignorado: '$caminho'")
                null
            } else {
                candidato.readText(Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            KenkernLog.e(TAG, "falha ao ler script '$caminho'", t)
            null
        }
    }

    /** Avança tempo, timers e on_update(dt) de cada script ativo. */
    fun atualizar(dt: Float) {
        if (dt < 0f || !dt.isFinite()) return
        tempoSegundos += dt
        ultimoDt = dt
        atualizarTimers(dt)
        var i = 0
        while (i < instancias.size) {
            val inst = instancias[i]
            if (inst.nick.active && scene.findNick(inst.nick.id) != null) {
                inst.runtime.atualizar(dt)
            }
            i++
        }
    }

    private fun atualizarTimers(dt: Float) {
        if (timers.isEmpty()) return
        val snapshot = timers.toList()
        for (entry in snapshot) {
            if (entry.cancelled || !entry.dono.active || scene.findNick(entry.dono.id) == null) continue
            entry.remaining -= dt
            var disparos = 0
            while (entry.remaining <= 0f && !entry.cancelled && disparos < 16) {
                disparos++
                try {
                    entry.callback.call()
                } catch (t: Throwable) {
                    KenkernLog.e(TAG, "timer #${entry.id} falhou no script: ${t.message}")
                    entry.cancelled = true
                    break
                }
                if (entry.repeating && !entry.cancelled) {
                    entry.remaining += entry.interval.coerceAtLeast(0.001f)
                } else {
                    entry.cancelled = true
                    break
                }
            }
        }
        timers.removeAll { it.cancelled }
    }

    fun emitirColisao(nick: Nick, nomeOutro: String) {
        chamarComString(nick, "on_collision", nomeOutro)
    }

    fun emitirGatilho(nick: Nick, nomeOutro: String) {
        chamarComString(nick, "on_trigger", nomeOutro)
    }

    fun emitirAreaEnter(area: Nick, outro: Nick) {
        chamarComHandle(area, "on_enter", outro)
        chamarComHandle(outro, "on_enter", area)
    }

    fun emitirAreaExit(area: Nick, outro: Nick) {
        chamarComHandle(area, "on_exit", outro)
        chamarComHandle(outro, "on_exit", area)
    }

    fun emitirAreaStay(area: Nick, outro: Nick, dt: Float) {
        chamarComHandleDt(area, "on_stay", outro, dt)
        chamarComHandleDt(outro, "on_stay", area, dt)
    }

    private fun chamarComString(nick: Nick, funcao: String, arg: String) {
        for (inst in instancias.toList()) {
            if (inst.nick !== nick) continue
            val globals = (inst.runtime as? LuaJScriptRuntime)?.ambiente() ?: continue
            val f = globals.get(funcao)
            if (f.isfunction()) {
                try {
                    (f as LuaFunction).call(LuaValue.valueOf(arg))
                } catch (e: Throwable) {
                    KenkernLog.e(TAG, "script '${inst.runtime.nome()}' falhou em $funcao: ${e.message}")
                }
            }
        }
    }

    private fun chamarComHandle(alvo: Nick, funcao: String, outro: Nick) {
        for (inst in instancias.toList()) {
            if (inst.nick !== alvo) continue
            val globals = (inst.runtime as? LuaJScriptRuntime)?.ambiente() ?: continue
            val f = globals.get(funcao)
            if (f.isfunction()) {
                try {
                    (f as LuaFunction).call(tabelaNick(outro))
                } catch (e: Throwable) {
                    KenkernLog.e(TAG, "script '${inst.runtime.nome()}' falhou em $funcao: ${e.message}")
                }
            }
        }
    }

    private fun chamarComHandleDt(alvo: Nick, funcao: String, outro: Nick, dt: Float) {
        for (inst in instancias.toList()) {
            if (inst.nick !== alvo) continue
            val globals = (inst.runtime as? LuaJScriptRuntime)?.ambiente() ?: continue
            val f = globals.get(funcao)
            if (f.isfunction()) {
                try {
                    (f as LuaFunction).call(tabelaNick(outro), LuaValue.valueOf(dt.toDouble()))
                } catch (e: Throwable) {
                    KenkernLog.e(TAG, "script '${inst.runtime.nome()}' falhou em $funcao: ${e.message}")
                }
            }
        }
    }

    /** Dispara TODOS os ouvintes registrados via scene.on(nome, fn) passando dados. */
    fun dispararEvento(nome: String, valor: LuaValue = LuaValue.NIL) {
        val lista = eventos[nome]?.toList() ?: return
        for (fn in lista) {
            if (!fn.isfunction()) continue
            try {
                (fn as LuaFunction).call(valor)
            } catch (e: Throwable) {
                KenkernLog.e(TAG, "evento '$nome' falhou no script: ${e.message}")
            }
        }
    }

    private fun registrarLog(mensagem: String) {
        KenkernLog.w(TAG, "lua: $mensagem")
        synchronized(logsRecentes) {
            if (logsRecentes.size >= 100) logsRecentes.removeAt(0)
            logsRecentes.add(mensagem)
        }
    }

    // ------------------------------------------------------------- ponte

    private fun instalarPonte(runtime: LuaJScriptRuntime, dono: Nick) {
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
        cena.set("find_by_tag", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val tag = args.arg1().optjstring("")
                val achou = scene.findNickByTagAnywhere(tag) ?: return LuaValue.NIL
                return tabelaNick(achou)
            }
        })
        cena.set("find_all_by_tag", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val tag = args.arg1().optjstring("")
                val lista = scene.findAllNicksByTag(tag)
                val tbl = LuaTable()
                for (idx in lista.indices) {
                    tbl.set(idx + 1, tabelaNick(lista[idx]))
                }
                return tbl
            }
        })
        cena.set("spawn", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val x = args.arg1().optdouble(0.0)
                val y = args.arg(2).optdouble(0.0)
                val z = args.arg(3).optdouble(0.0)
                val novo = scene.createNick(null, null, kind = NickKindRegistry.NICK_MESH)
                novo.transform.setLocalPosition(x.toFloat(), y.toFloat(), z.toFloat())
                scene.updateWorldTransforms()
                return tabelaNick(novo)
            }
        })
        cena.set("spawn_prefab", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val x = args.arg(2).optdouble(0.0).toFloat()
                val y = args.arg(3).optdouble(0.0).toFloat()
                val z = args.arg(4).optdouble(0.0).toFloat()
                val instanciado = instanciarPrefab(nome, x, y, z) ?: return LuaValue.NIL
                return tabelaNick(instanciado)
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
                registrarLog(args.arg1().optjstring(""))
                return LuaValue.NIL
            }
        })
        cena.set("emit", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                dispararEvento(nome, args.arg(2))
                return LuaValue.NIL
            }
        })
        cena.set("on", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val fn = args.arg(2)
                if (nome.isNotBlank() && fn.isfunction()) {
                    val lista = eventos.getOrPut(nome) { ArrayList() }
                    if (!lista.contains(fn)) lista.add(fn)
                }
                return LuaValue.NIL
            }
        })
        cena.set("off", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val fn = args.arg(2)
                if (fn.isnil()) {
                    eventos.remove(nome)
                } else {
                    eventos[nome]?.remove(fn)
                }
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("scene", cena)
        // C2.2: as pontes do signal usam O MESMO barramento (dispararEvento/
        // registro de ouvintes) das chamadas scene.emit/scene.on.
        cenaEventosBridge = { nome, dados -> dispararEvento(nome, dados) }
        cenaOnBridge = { nome, fn ->
            if (nome.isNotBlank()) {
                val lista = eventos.getOrPut(nome) { ArrayList() }
                if (!lista.contains(fn)) lista.add(fn)
            }
        }

        // ---------- C2.2 (v0.1.26): signal — SINAIS NOMEADOS do Signal Nick
        // (signal.emit / signal.on no MESMO barramento dos eventos da cena;
        // preparado para portas LINKERF na Parada 4).
        val signalTbl = LuaTable()
        signalTbl.set("emit", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                if (nome.isNotBlank()) cenaEventosBridge?.invoke(nome, args.arg(2))
                return LuaValue.NIL
            }
        })
        signalTbl.set("on", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val fn = args.arg(2) as? LuaFunction ?: return LuaValue.NIL
                if (nome.isNotBlank()) cenaOnBridge?.invoke(nome, fn)
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("signal", signalTbl)

        // ---------- timer ----------
        val timerTbl = LuaTable()
        timerTbl.set("after", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val seg = args.arg1().optdouble(0.0).toFloat().coerceAtLeast(0f)
                val fn = args.arg(2) as? LuaFunction ?: return LuaValue.NIL
                val id = proximoTimerId++
                timers.add(TimerEntry(id, seg, seg, false, fn, dono))
                return LuaValue.valueOf(id)
            }
        })
        timerTbl.set("every", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val seg = args.arg1().optdouble(0.1).toFloat().coerceAtLeast(0.001f)
                val fn = args.arg(2) as? LuaFunction ?: return LuaValue.NIL
                val id = proximoTimerId++
                timers.add(TimerEntry(id, seg, seg, true, fn, dono))
                return LuaValue.valueOf(id)
            }
        })
        timerTbl.set("cancel", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val id = args.arg1().optint(-1)
                var cancelou = false
                for (t in timers) {
                    if (t.id == id && !t.cancelled) {
                        t.cancelled = true
                        cancelou = true
                    }
                }
                return LuaValue.valueOf(cancelou)
            }
        })
        runtime.instalarTabela("timer", timerTbl)

        // ---------- engine ----------
        val engine = LuaTable()
        engine.set("time", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(tempoSegundos.toDouble())
        })
        engine.set("dt", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(ultimoDt.toDouble())
        })
        engine.set("log", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                registrarLog(args.arg1().optjstring(""))
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("engine", engine)

        // Global log(msg) de conveniência
        runtime.ambiente()?.set("log", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                registrarLog(args.arg1().optjstring(""))
                return LuaValue.NIL
            }
        })

        // ---------- input ----------
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
        val actionFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val e = estadoInput ?: return LuaValue.FALSE
                return LuaValue.valueOf(e.estaPressionada(args.arg1().optjstring("")))
            }
        }
        input.set("acao", actionFn)
        input.set("action", actionFn)
        runtime.instalarTabela("input", input)

        // ---------- ui ----------
        val ui = LuaTable()
        val textoFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val texto = args.arg(2).optjstring("")
                synchronized(textosUi) {
                    if (texto.isEmpty()) textosUi.remove(nome) else textosUi[nome] = texto
                }
                return LuaValue.NIL
            }
        }
        ui.set("texto", textoFn)
        ui.set("text", textoFn)

        val contadorFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val valor = args.arg(2).optint(0)
                synchronized(textosUi) { textosUi[nome] = valor.toString() }
                return LuaValue.NIL
            }
        }
        ui.set("contador", contadorFn)
        ui.set("counter", contadorFn)

        val limparFn = object : LibFunction() {
            override fun call(): LuaValue {
                synchronized(textosUi) { textosUi.clear() }
                synchronized(contagens) { contagens.clear() }
                return LuaValue.NIL
            }
        }
        ui.set("limpar", limparFn)
        ui.set("clear", limparFn)

        val contagemFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                val delta = args.arg(2).optint(1)
                synchronized(contagens) { contagens[nome] = (contagens[nome] ?: 0) + delta }
                return LuaValue.NIL
            }
        }
        ui.set("contagem", contagemFn)
        ui.set("count", contagemFn)
        ui.set("add_count", contagemFn)

        val getCountFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val nome = args.arg1().optjstring("")
                return LuaValue.valueOf(lerContagem(nome))
            }
        }
        ui.set("get_count", getCountFn)
        ui.set("contagem_atual", getCountFn)
        runtime.instalarTabela("ui", ui)

        // ---------- storage ----------
        val storage = LuaTable()
        storage.set("save", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val key = args.arg1().optjstring("")
                val saved = luaParaSavedValue(args.arg(2), 0) ?: return LuaValue.FALSE
                return LuaValue.valueOf(saveStore.save(key, saved))
            }
        })
        storage.set("load", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val key = args.arg1().optjstring("")
                val fallback = args.arg(2)
                val loaded = saveStore.load(key) ?: return fallback
                return savedValueParaLua(loaded)
            }
        })
        storage.set("has", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return LuaValue.valueOf(saveStore.has(args.arg1().optjstring("")))
            }
        })
        storage.set("delete", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                return LuaValue.valueOf(saveStore.delete(args.arg1().optjstring("")))
            }
        })
        storage.set("clear", object : LibFunction() {
            override fun call(): LuaValue {
                saveStore.clear()
                return LuaValue.NIL
            }
        })
        runtime.instalarTabela("storage", storage)
    }

    private fun instanciarPrefab(nome: String, x: Float, y: Float, z: Float): Nick? {
        val modelo = resolverModeloPrefab(nome) ?: return null
        val clone = clonarSubarvoreComNovosIds(modelo, null)
        clone.transform.setLocalPosition(x, y, z)
        scene.roots.add(clone)
        registrarSubarvoreNaCena(clone)
        scene.updateWorldTransforms()
        // Inicia eventuais scripts anexados ao prefab instanciado
        val pilha = ArrayList<Nick>(8)
        pilha.add(clone)
        var head = 0
        while (head < pilha.size) {
            val atual = pilha[head++]
            if (atual.active) {
                for (c in atual.children) pilha.add(c)
                iniciarScriptDeNick(atual)
            }
        }
        return clone
    }

    private fun resolverModeloPrefab(nome: String): Nick? {
        prefabProvider?.invoke(nome)?.let { return it }
        val root = projectRoot ?: return null
        val clean = nome.trim().removePrefix("prefabs/").removeSuffix(".ck")
        if (clean.isEmpty()) return null
        val prefabsDir = File(root, "prefabs")
        val candidate = listOf(
            File(prefabsDir, "$clean.ck"),
            File(prefabsDir, clean),
            File(root, nome)
        ).firstOrNull { it.isFile } ?: return null
        return try {
            val loaded = CkFormat.parseToNewScene(candidate.readText(Charsets.UTF_8), clean)
            loaded?.roots?.firstOrNull()
        } catch (t: Throwable) {
            KenkernLog.e(TAG, "falha ao carregar prefab '$nome'", t)
            null
        }
    }

    private fun clonarSubarvoreComNovosIds(orig: Nick, novoPai: Nick?): Nick {
        val copia = Nick(
            id = ReferenceId.generate(),
            name = orig.name,
            active = orig.active,
            kind = orig.kind
        )
        copia.visible = orig.visible
        copia.tags.addAll(orig.tags)
        copia.parent = novoPai
        copia.transform.apply(orig.transform.snapshot())
        for (c in orig.classes) {
            val classCopy = NickClassInstance(c.typeName)
            for ((k, v) in c.fields) {
                classCopy.fields[k] = when (v) {
                    is NickValue.Vec3Value -> v.copy()
                    else -> v
                }
            }
            copia.addClassInstance(classCopy)
        }
        for (child in orig.children) {
            copia.children.add(clonarSubarvoreComNovosIds(child, copia))
        }
        return copia
    }

    private fun registrarSubarvoreNaCena(nick: Nick) {
        scene.references.registerNick(nick)
        for (c in nick.children) registrarSubarvoreNaCena(c)
    }

    private fun instalarVariaveisPublicas(runtime: LuaJScriptRuntime, nick: Nick) {
        val inst = nick.findClassInstance(CLASSE_SCRIPT) ?: return
        val params = LuaTable()
        for ((nome, valor) in inst.fields) {
            if (nome == CAMPO_FONTE || nome == CAMPO_ARQUIVO) continue
            params.set(nome, nickValueParaLua(valor))
        }
        runtime.instalarTabela("params", params)
    }

    private fun nickValueParaLua(valor: NickValue): LuaValue = when (valor) {
        is NickValue.FloatValue -> LuaValue.valueOf(valor.value.toDouble())
        is NickValue.IntValue -> LuaValue.valueOf(valor.value)
        is NickValue.BoolValue -> LuaValue.valueOf(valor.value)
        is NickValue.StringValue -> LuaValue.valueOf(valor.value)
        is NickValue.Vec3Value -> LuaTable().apply {
            set("x", LuaValue.valueOf(valor.x.toDouble()))
            set("y", LuaValue.valueOf(valor.y.toDouble()))
            set("z", LuaValue.valueOf(valor.z.toDouble()))
        }
    }

    private fun luaParaNickValue(lua: LuaValue, existente: NickValue?): NickValue? {
        return when (existente) {
            is NickValue.BoolValue -> NickValue.BoolValue(lua.toboolean())
            is NickValue.IntValue -> NickValue.IntValue(lua.optint(existente.value))
            is NickValue.FloatValue -> NickValue.FloatValue(lua.optdouble(existente.value.toDouble()).toFloat())
            is NickValue.StringValue -> NickValue.StringValue(lua.optjstring(""))
            is NickValue.Vec3Value -> if (lua.istable()) {
                NickValue.Vec3Value(
                    lua.get("x").optdouble(existente.x.toDouble()).toFloat(),
                    lua.get("y").optdouble(existente.y.toDouble()).toFloat(),
                    lua.get("z").optdouble(existente.z.toDouble()).toFloat()
                )
            } else null
            null -> when {
                lua.isboolean() -> NickValue.BoolValue(lua.toboolean())
                lua.isint() -> NickValue.IntValue(lua.toint())
                lua.isnumber() -> NickValue.FloatValue(lua.todouble().toFloat())
                lua.istable() -> NickValue.Vec3Value(
                    lua.get("x").optdouble(0.0).toFloat(),
                    lua.get("y").optdouble(0.0).toFloat(),
                    lua.get("z").optdouble(0.0).toFloat()
                )
                else -> NickValue.StringValue(lua.optjstring(""))
            }
        }
    }

    private fun luaParaSavedValue(v: LuaValue, depth: Int): SavedValue? {
        if (v.isnil() || depth > 4) return null
        return when {
            v.isboolean() -> SavedValue.BoolVal(v.toboolean())
            v.isnumber() -> SavedValue.NumVal(v.todouble())
            v.isstring() -> SavedValue.StrVal(v.tojstring())
            v.istable() -> {
                val tbl = v.checktable()
                val out = LinkedHashMap<String, SavedValue>()
                var k = LuaValue.NIL
                while (true) {
                    val pair = tbl.next(k)
                    k = pair.arg1()
                    if (k.isnil()) break
                    val child = luaParaSavedValue(pair.arg(2), depth + 1) ?: continue
                    out[k.tojstring()] = child
                }
                SavedValue.MapVal(out)
            }
            else -> null
        }
    }

    private fun savedValueParaLua(v: SavedValue): LuaValue = when (v) {
        is SavedValue.StrVal -> LuaValue.valueOf(v.value)
        is SavedValue.NumVal -> LuaValue.valueOf(v.value)
        is SavedValue.BoolVal -> LuaValue.valueOf(v.value)
        is SavedValue.MapVal -> LuaTable().apply {
            for ((k, item) in v.entries) {
                set(k, savedValueParaLua(item))
            }
        }
    }

    /** Table Lua que envolve um Nick (transform, tags, classes e destroy). */
    /** C2: sobreposição AABB (mesma regra dos gatilhos do runtime). */
    private fun seSobrepoe(a: FloatArray, b: FloatArray): Boolean =
        a[0] <= b[3] && a[3] >= b[0] && a[1] <= b[4] && a[4] >= b[1] && a[2] <= b[5] && a[5] >= b[2]

    fun tabelaNick(nick: Nick): LuaTable {
        val t = LuaTable()
        t.set("__nick", LuaValue.valueOf(System.identityHashCode(nick)))
        t.set("id", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(nick.id.value)
        })
        t.set("kind", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(nick.kind)
        })
        t.set("name", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(nick.displayName("pt"))
        })
        t.set("set_name", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val n = args.arg1().optjstring("").trim()
                nick.name = n.ifEmpty { null }
                return LuaValue.NIL
            }
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
                return LuaTable().apply {
                    set("x", LuaValue.valueOf(p.x.toDouble()))
                    set("y", LuaValue.valueOf(p.y.toDouble()))
                    set("z", LuaValue.valueOf(p.z.toDouble()))
                }
            }
        })
        t.set("rotation", object : LibFunction() {
            override fun call(): LuaValue {
                val r = nick.transform.rotation
                return LuaTable().apply {
                    set("x", LuaValue.valueOf(r.x.toDouble()))
                    set("y", LuaValue.valueOf(r.y.toDouble()))
                    set("z", LuaValue.valueOf(r.z.toDouble()))
                    set("w", LuaValue.valueOf(r.w.toDouble()))
                }
            }
        })
        t.set("set_rotation", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                nick.transform.rotation.set(
                    args.arg1().optdouble(0.0).toFloat(),
                    args.arg(2).optdouble(0.0).toFloat(),
                    args.arg(3).optdouble(0.0).toFloat(),
                    args.arg(4).optdouble(1.0).toFloat()
                )
                return LuaValue.NIL
            }
        })
        t.set("scale", object : LibFunction() {
            override fun call(): LuaValue {
                val s = nick.transform.scale
                return LuaTable().apply {
                    set("x", LuaValue.valueOf(s.x.toDouble()))
                    set("y", LuaValue.valueOf(s.y.toDouble()))
                    set("z", LuaValue.valueOf(s.z.toDouble()))
                }
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
        t.set("active", object : LibFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(nick.active)
        })
        t.set("set_active", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                nick.active = args.arg1().toboolean()
                return LuaValue.NIL
            }
        })
        t.set("has_tag", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue =
                LuaValue.valueOf(nick.hasTag(args.arg1().optjstring("")))
        })
        t.set("add_tag", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue =
                LuaValue.valueOf(nick.addTag(args.arg1().optjstring("")))
        })
        t.set("remove_tag", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue =
                LuaValue.valueOf(nick.removeTag(args.arg1().optjstring("")))
        })
        t.set("tags", object : LibFunction() {
            override fun call(): LuaValue {
                val tbl = LuaTable()
                var idx = 1
                for (tag in nick.tags) tbl.set(idx++, LuaValue.valueOf(tag))
                return tbl
            }
        })
        // ---- C2 (v0.1.26): área — bodies()/contains() e player.respawn() ----
        t.set("bodies", object : LibFunction() {
            override fun call(): LuaValue {
                val consultor = consultorArea ?: return LuaTable()
                val areaBounds = FloatArray(6)
                consultor.boundsMundo(nick, areaBounds)
                val tbl = LuaTable()
                var idx = 1
                for (corpo in consultor.corposCena()) {
                    if (corpo === nick) continue
                    val b = FloatArray(6)
                    consultor.boundsMundo(corpo, b)
                    if (seSobrepoe(areaBounds, b)) tbl.set(idx++, tabelaNick(corpo))
                }
                return tbl
            }
        })
        t.set("contains", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val consultor = consultorArea ?: return LuaValue.FALSE
                val outro = nickDe(args.arg1()) ?: return LuaValue.FALSE
                val areaBounds = FloatArray(6)
                consultor.boundsMundo(nick, areaBounds)
                val b = FloatArray(6)
                consultor.boundsMundo(outro, b)
                return LuaValue.valueOf(seSobrepoe(areaBounds, b))
            }
        })
        t.set("respawn", object : LibFunction() {
            override fun call(): LuaValue {
                val ok = consultorArea?.respawnJogador() ?: false
                return LuaValue.valueOf(ok)
            }
        })
        t.set("get", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classe = args.arg1().optjstring("")
                val campo = args.arg(2).optjstring("")
                val inst = nick.findClassInstance(classe) ?: return LuaValue.NIL
                val v = inst.fields[campo] ?: return LuaValue.NIL
                return nickValueParaLua(v)
            }
        })
        t.set("set", object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classe = args.arg1().optjstring("")
                val campo = args.arg(2).optjstring("")
                val rawValor = args.arg(3)
                if (classe.isBlank() || campo.isBlank()) return LuaValue.FALSE
                val inst = nick.findClassInstance(classe) ?: run {
                    NickClassRegistry.createInstance(classe)?.also { nick.addClassInstance(it) }
                } ?: return LuaValue.FALSE
                val convertido = luaParaNickValue(rawValor, inst.fields[campo]) ?: return LuaValue.FALSE
                inst.fields[campo] = convertido
                return LuaValue.TRUE
            }
        })
        val classDataFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): LuaValue {
                val classe = args.arg1().optjstring("")
                val inst = nick.findClassInstance(classe) ?: return LuaValue.NIL
                val tbl = LuaTable()
                for ((k, v) in inst.fields) tbl.set(k, nickValueParaLua(v))
                return tbl
            }
        }
        t.set("class_data", classDataFn)
        t.set("class", classDataFn)

        t.set("destroy", object : LibFunction() {
            override fun call(): LuaValue {
                destruir(nick)
                return LuaValue.NIL
            }
        })
        return t
    }

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

    private fun destruir(nick: Nick) {
        timers.removeAll { it.dono === nick }
        scene.removeNick(nick)
    }

    companion object {
        private const val TAG = "Kenkern/Lua"
        const val ORCAMENTO_MS: Long = 4L
        const val CLASSE_SCRIPT = "Script"
        const val CAMPO_ARQUIVO = "arquivo"
        const val CAMPO_FONTE = "fonte"

        fun defaultSaveStore(projectRoot: File?): GameSaveStore =
            if (projectRoot != null) {
                FileGameSaveStore(File(projectRoot, "saves/gamedata.ck"))
            } else {
                MemoryGameSaveStore()
            }

        fun distanciaAprox(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float =
            sqrt(
                (ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz)
            )
    }
}
