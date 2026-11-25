package com.kenkern.engine.engine

import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * L2: Persistência de dados do jogo para a API Lua (`storage.save`/`load`).
 * Valores suportados: string, número, booleano e mapa plano (tabela Lua).
 */
sealed class SavedValue {
    data class StrVal(val value: String) : SavedValue()
    data class NumVal(val value: Double) : SavedValue()
    data class BoolVal(val value: Boolean) : SavedValue()
    data class MapVal(val entries: Map<String, SavedValue>) : SavedValue()
}

interface GameSaveStore {
    fun save(key: String, value: SavedValue): Boolean
    fun load(key: String): SavedValue?
    fun has(key: String): Boolean
    fun delete(key: String): Boolean
    fun clear()
}

/** Implementação em memória para testes de JVM e checagem do Diagnóstico. */
class MemoryGameSaveStore : GameSaveStore {
    private val map = ConcurrentHashMap<String, SavedValue>()

    override fun save(key: String, value: SavedValue): Boolean {
        if (key.isBlank()) return false
        map[key] = value
        return true
    }

    override fun load(key: String): SavedValue? = map[key]
    override fun has(key: String): Boolean = map.containsKey(key)
    override fun delete(key: String): Boolean = map.remove(key) != null
    override fun clear() {
        map.clear()
    }
}

/**
 * Persistência em arquivo estruturado (`saves/gamedata.ck` dentro do projeto)
 * com escrita atômica e log explícito de falhas via [KenkernLog].
 */
class FileGameSaveStore(private val file: File) : GameSaveStore {
    private val mem = LinkedHashMap<String, SavedValue>()
    private val lock = Any()

    init {
        carregarDoDisco()
    }

    override fun save(key: String, value: SavedValue): Boolean {
        if (key.isBlank()) return false
        synchronized(lock) {
            mem[key] = value
            return gravarNoDisco()
        }
    }

    override fun load(key: String): SavedValue? = synchronized(lock) { mem[key] }

    override fun has(key: String): Boolean = synchronized(lock) { mem.containsKey(key) }

    override fun delete(key: String): Boolean = synchronized(lock) {
        val removed = mem.remove(key) != null
        if (removed) gravarNoDisco()
        removed
    }

    override fun clear() {
        synchronized(lock) {
            mem.clear()
            gravarNoDisco()
        }
    }

    private fun carregarDoDisco() {
        if (!file.isFile) return
        try {
            mem.clear()
            file.readLines(Charsets.UTF_8).forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || line == MAGIC) return@forEach
                val tokens = parseLine(line) ?: return@forEach
                if (tokens.size >= 3) {
                    val k = CkFormat.unescape(tokens[0])
                    val type = tokens[1]
                    val payload = CkFormat.unescape(tokens[2])
                    decodeValue(type, payload)?.let { mem[k] = it }
                }
            }
        } catch (t: Throwable) {
            KenkernLog.e(TAG, "falha ao ler save do jogo em '${file.path}'", t)
        }
    }

    private fun gravarNoDisco(): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val sb = StringBuilder()
            sb.appendLine(MAGIC)
            for ((k, v) in mem) {
                val (type, payload) = encodeValue(v)
                sb.appendLine("${CkFormat.quote(k)} $type ${CkFormat.quote(payload)}")
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(sb.toString(), Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                KenkernLog.w(TAG, "não foi possível substituir arquivo anterior '${file.path}' diretamente")
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            true
        } catch (t: Throwable) {
            KenkernLog.e(TAG, "falha ao gravar save do jogo em '${file.path}'", t)
            false
        }
    }

    companion object {
        private const val TAG = "Kenkern/GameSave"
        private const val MAGIC = "KENKERN_SAVE_1"

        internal fun encodeValue(v: SavedValue): Pair<String, String> = when (v) {
            is SavedValue.StrVal -> "STR" to v.value
            is SavedValue.NumVal -> "NUM" to v.value.toString()
            is SavedValue.BoolVal -> "BOOL" to v.value.toString()
            is SavedValue.MapVal -> {
                val packed = v.entries.entries.joinToString("\n") { (ek, ev) ->
                    val (t, p) = encodeValue(ev)
                    "${CkFormat.quote(ek)} $t ${CkFormat.quote(p)}"
                }
                "MAP" to packed
            }
        }

        internal fun decodeValue(type: String, payload: String): SavedValue? = when (type) {
            "STR" -> SavedValue.StrVal(payload)
            "NUM" -> payload.toDoubleOrNull()?.let { SavedValue.NumVal(it) }
            "BOOL" -> payload.toBooleanStrictOrNull()?.let { SavedValue.BoolVal(it) }
            "MAP" -> {
                val out = LinkedHashMap<String, SavedValue>()
                payload.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val parts = parseLine(trimmed) ?: return@forEach
                    if (parts.size >= 3) {
                        val ek = CkFormat.unescape(parts[0])
                        decodeValue(parts[1], CkFormat.unescape(parts[2]))?.let { out[ek] = it }
                    }
                }
                SavedValue.MapVal(out)
            }
            else -> null
        }

        private fun parseLine(line: String): List<String>? {
            val out = ArrayList<String>(3)
            var i = 0
            val n = line.length
            while (i < n) {
                while (i < n && line[i].isWhitespace()) i++
                if (i >= n) break
                if (line[i] == '"') {
                    i++
                    val sb = StringBuilder()
                    while (i < n) {
                        val c = line[i]
                        if (c == '\\' && i + 1 < n) {
                            sb.append(c).append(line[i + 1])
                            i += 2
                        } else if (c == '"') {
                            i++
                            break
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    out.add(sb.toString())
                } else {
                    val start = i
                    while (i < n && !line[i].isWhitespace()) i++
                    out.add(line.substring(start, i))
                }
            }
            return if (out.size >= 3) out else null
        }
    }
}
