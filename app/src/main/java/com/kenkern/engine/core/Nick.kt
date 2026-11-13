package com.kenkern.engine.core

/**
 * NICK: unidade básica da cena da KENKERN.
 * Tem id estável (ReferenceId), nome, ativo/inativo, pai e filhos,
 * transform e uma lista de CLASSES (dados + funções).
 */
class Nick(
    val id: ReferenceId = ReferenceId.generate(),
    name: String = "Nick",
    active: Boolean = true
) {
    var name: String = name
    var active: Boolean = active
    var visible: Boolean = true

    var parent: Nick? = null
        internal set

    val children = ArrayList<Nick>()
    val transform = Transform()
    val classes = ArrayList<NickClassInstance>()

    /** Índice deste nick entre os filhos do pai (para reordenação). */
    fun indexInParent(): Int {
        val p = parent ?: return -1
        return p.children.indexOf(this)
    }

    fun isAncestorOf(other: Nick): Boolean {
        var p: Nick? = other
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    fun findChildById(id: ReferenceId): Nick? {
        if (id == this.id) return this
        for (c in children) {
            val found = c.findChildById(id)
            if (found != null) return found
        }
        return null
    }

    fun findChildByName(target: String): Nick? {
        if (name == target) return this
        for (c in children) {
            val found = c.findChildByName(target)
            if (found != null) return found
        }
        return null
    }

    fun addClassInstance(instance: NickClassInstance) {
        classes.add(instance)
    }

    fun findClassInstance(typeName: String): NickClassInstance? {
        for (c in classes) if (c.typeName == typeName) return c
        return null
    }

    override fun toString(): String = name
}
