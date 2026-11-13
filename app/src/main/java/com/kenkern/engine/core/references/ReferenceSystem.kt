package com.kenkern.engine.core.references

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.ReferenceId

/**
 * Sistema de REFERENCIAÇÃO da KENKERN: referência estável a um Nick ou asset por id,
 * que sobrevive a renomear e mover. A UI de referências chega na Fase 4 (Linkerf).
 */
class ReferenceSystem {

    private val nickById = HashMap<String, Nick>()
    private val assetPathById = HashMap<String, String>()

    fun registerNick(nick: Nick) {
        nickById[nick.id.value] = nick
        for (c in nick.children) registerNick(c)
    }

    fun unregisterNick(nick: Nick) {
        nickById.remove(nick.id.value)
        for (c in nick.children) unregisterNick(c)
    }

    /** Resolve uma referência para um Nick vivo (ou null se pendurada). */
    fun resolve(id: ReferenceId): Nick? = nickById[id.value]

    fun registerAsset(id: ReferenceId, path: String) {
        assetPathById[id.value] = path
    }

    fun resolveAsset(id: ReferenceId): String? = assetPathById[id.value]

    fun unregisterAsset(id: ReferenceId) {
        assetPathById.remove(id.value)
    }

    /** Remove referências a nicks que não estão mais na árvore (limpeza). */
    fun purgeUnreachable(rootNicks: List<Nick>) {
        val reachable = HashSet<String>()
        fun walk(n: Nick) {
            reachable.add(n.id.value)
            for (c in n.children) walk(c)
        }
        for (r in rootNicks) walk(r)
        nickById.keys.retainAll(reachable)
    }

    fun clear() {
        nickById.clear()
        assetPathById.clear()
    }
}
