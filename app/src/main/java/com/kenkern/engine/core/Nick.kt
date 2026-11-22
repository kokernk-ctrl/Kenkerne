package com.kenkern.engine.core

/**
 * NICK: unidade básica da cena da KENKERN.
 * Tem id estável (ReferenceId), TIPO (chave estável do NickKindRegistry —
 * DECISIONS #24/#25), nome OPCIONAL (null = exibe o rótulo do tipo no idioma
 * atual — DECISIONS #26; renomeação do usuário nunca é traduzida),
 * ativo/inativo, pai e filhos, transform e uma lista de CLASSES (dados + funções).
 */
class Nick(
    val id: ReferenceId = ReferenceId.generate(),
    name: String? = null,
    active: Boolean = true,
    kind: String = NickKindRegistry.NICK_EMPTY
) {
    /** Nome do USUÁRIO (null = rótulo do tipo no idioma atual). */
    var name: String? = name

    /** Chave estável do tipo (ex.: "body.player"); o .ck CK2 guarda isto. */
    var kind: String = kind

    var active: Boolean = active
    var visible: Boolean = true

    var parent: Nick? = null
        internal set

    val children = ArrayList<Nick>()
    val transform = Transform()
    val classes = ArrayList<NickClassInstance>()

    /**
     * Nome EXIBIDO: texto do usuário se houver; senão o rótulo do TIPO no
     * idioma pedido ("en"/"pt"). Tipo desconhecido devolve a própria chave
     * (projeto de versão mais nova nunca mostra "???").
     */
    fun displayName(language: String): String =
        name ?: NickKindRegistry.labelOf(kind, language)

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

    override fun toString(): String = displayName("en")
}
