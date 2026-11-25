package com.kenkern.engine.core

import com.kenkern.engine.core.linkerf.LinkerfGraph
import com.kenkern.engine.core.references.ReferenceSystem

/**
 * CENA: árvore de Nicks + sistema de referências + grafo Linkerf.
 * As travessias usam estruturas pré-alocadas (ZERO alocações no loop de update).
 */
class Scene(var name: String = "Cena") {

    val roots = ArrayList<Nick>()
    val references = ReferenceSystem()
    val linkerf = LinkerfGraph()

    fun createRootNick(
        name: String?,
        id: ReferenceId = ReferenceId.generate(),
        active: Boolean = true,
        kind: String = NickKindRegistry.NICK_EMPTY
    ): Nick {
        val nick = Nick(id, name, active, kind)
        roots.add(nick)
        references.registerNick(nick)
        return nick
    }

    fun createNick(
        name: String?,
        parent: Nick?,
        id: ReferenceId = ReferenceId.generate(),
        active: Boolean = true,
        kind: String = NickKindRegistry.NICK_EMPTY
    ): Nick {
        val nick = Nick(id, name, active, kind)
        if (parent == null) {
            roots.add(nick)
        } else {
            parent.children.add(nick)
            nick.parent = parent
        }
        references.registerNick(nick)
        return nick
    }

    fun findNickByNameAnywhere(target: String): Nick? {
        for (r in roots) {
            val found = r.findChildByName(target)
            if (found != null) return found
        }
        return null
    }

    fun findNickByTagAnywhere(tag: String): Nick? {
        val clean = tag.trim()
        if (clean.isEmpty()) return null
        val pilha = ArrayList<Nick>(16)
        for (r in roots) pilha.add(r)
        var i = 0
        while (i < pilha.size) {
            val n = pilha[i++]
            if (n.hasTag(clean)) return n
            for (c in n.children) pilha.add(c)
        }
        return null
    }

    fun findAllNicksByTag(tag: String): List<Nick> {
        val clean = tag.trim()
        if (clean.isEmpty()) return emptyList()
        val out = ArrayList<Nick>()
        val pilha = ArrayList<Nick>(16)
        for (r in roots) pilha.add(r)
        var i = 0
        while (i < pilha.size) {
            val n = pilha[i++]
            if (n.hasTag(clean)) out.add(n)
            for (c in n.children) pilha.add(c)
        }
        return out
    }

    fun findNick(id: ReferenceId): Nick? = references.resolve(id)

    /** Remove o nick e toda a sua subárvore. Retorna true se removeu. */
    fun removeNick(nick: Nick): Boolean {
        val parent = nick.parent
        val removed = if (parent != null) parent.children.remove(nick) else roots.remove(nick)
        if (!removed) return false
        nick.parent = null
        unregisterSubtree(nick)
        return true
    }

    private fun unregisterSubtree(nick: Nick) {
        references.unregisterNick(nick)
        linkerf.removeOwner(nick.id)
        for (c in nick.children) {
            c.parent = null
            unregisterSubtree(c)
        }
        nick.children.clear()
    }

    /** Reinsere uma subárvore previamente removida (usada pelo undo de exclusão). */
    fun attachSubtree(nick: Nick, parent: Nick?, index: Int) {
        if (parent == null) {
            val at = index.coerceIn(0, roots.size)
            roots.add(at, nick)
        } else {
            val at = index.coerceIn(0, parent.children.size)
            parent.children.add(at, nick)
        }
        nick.parent = parent
        references.registerNick(nick)
    }

    fun reparent(nick: Nick, newParent: Nick?): Boolean {
        if (newParent != null) {
            if (newParent === nick || newParent.isAncestorOf(nick) || nick.isAncestorOf(newParent)) return false
        }
        val oldParent = nick.parent
        if (oldParent === newParent) return false
        if (oldParent != null) oldParent.children.remove(nick) else roots.remove(nick)
        if (newParent == null) {
            roots.add(nick)
        } else {
            newParent.children.add(nick)
        }
        nick.parent = newParent
        return true
    }

    /** Move o nick entre os irmãos por delta (-1 = subir, +1 = descer). */
    fun reorder(nick: Nick, delta: Int): Boolean {
        val list = nick.parent?.children ?: roots
        val idx = list.indexOf(nick)
        if (idx < 0) return false
        val target = idx + delta
        if (target < 0 || target >= list.size) return false
        list.removeAt(idx)
        list.add(target, nick)
        return true
    }

    /** Atualiza as matrizes de mundo (BFS iterativo com pilha pré-alocada, sem alocação).
     *  Loops INDEXADOS: for-in sobre ArrayList instancia um Iterator por frame. */
    fun updateWorldTransforms() {
        sScratch.clear()
        for (ri in roots.indices) sScratch.add(roots[ri])
        var head = 0
        while (head < sScratch.size) {
            val current = sScratch[head++]
            current.transform.localMatrix.setTRS(
                current.transform.position, current.transform.rotation, current.transform.scale
            )
            val parent = current.parent
            if (parent == null) {
                current.transform.worldMatrix.copyFrom(current.transform.localMatrix)
            } else {
                current.transform.worldMatrix.multiply(parent.transform.worldMatrix, current.transform.localMatrix)
            }
            val children = current.children
            for (ci in children.indices) sScratch.add(children[ci])
        }
    }

    /** Executa as funções das classes dos nicks ativos (modo Play). Sem alocação.
     *  Loops INDEXADOS: for-in sobre ArrayList instancia um Iterator por passo fixo. */
    fun updateFunctions(deltaTime: Float, engineTime: Float) {
        sScratch.clear()
        for (ri in roots.indices) sScratch.add(roots[ri])
        var head = 0
        while (head < sScratch.size) {
            val nick = sScratch[head++]
            if (nick.active) {
                val classes = nick.classes
                for (ki in classes.indices) {
                    NickFunctionRegistry.find(classes[ki].typeName)?.onUpdate(nick, deltaTime, engineTime)
                }
                val children = nick.children
                for (ci in children.indices) sScratch.add(children[ci])
            }
        }
    }

    private val sScratch = ArrayList<Nick>()
}
