package com.kenkern.engine.commands

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot

/** Cria um Nick (filho do pai informado ou raiz). */
class CreateNickCommand(
    private val scene: Scene,
    val parentId: ReferenceId?,
    private val name: String,
    private val newId: ReferenceId = ReferenceId.generate()
) : Command {
    override val label: String get() = "Criar $name"
    private var created: Nick? = null
    private var storedParent: Nick? = null
    private var storedIndex: Int = 0

    override fun execute() {
        val existing = created
        if (existing != null) {
            // Redo: reinsere a mesma subárvore (mesmo id) no lugar original.
            scene.attachSubtree(existing, storedParent, storedIndex)
            return
        }
        val parent = parentId?.let { scene.findNick(it) }
        val nick = scene.createNick(name, parent, newId)
        created = nick
        storedParent = nick.parent
        storedIndex = nick.indexInParent().coerceAtLeast(0)
    }

    override fun undo() {
        created?.let { scene.removeNick(it) }
    }
}

/** Remove um Nick (e subárvore); o undo recoloca a mesma subárvore no mesmo lugar. */
class DeleteNickCommand(
    private val scene: Scene,
    val nickId: ReferenceId
) : Command {
    override val label: String get() = "Apagar nick"
    private var removed: Nick? = null
    private var oldParent: Nick? = null
    private var oldIndex: Int = -1

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (removed == null) {
            removed = nick
            oldParent = nick.parent
            oldIndex = nick.indexInParent().coerceAtLeast(0)
        }
        scene.removeNick(nick)
    }

    override fun undo() {
        val nick = removed ?: return
        scene.attachSubtree(nick, oldParent, oldIndex)
    }
}

/** Renomeia um Nick. */
class RenameCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val newName: String
) : Command {
    override val label: String get() = "Renomear"
    private var oldName: String? = null

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (oldName == null) oldName = nick.name
        nick.name = newName
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        nick.name = oldName ?: return
    }
}

/** Liga/desliga ativo ou visível. */
class SetFlagCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val flagField: FlagField,
    private val newValue: Boolean
) : Command {
    enum class FlagField { ACTIVE, VISIBLE }

    override val label: String get() = "Alterar ${flagField.name.lowercase()}"
    private var oldValue: Boolean? = null

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (oldValue == null) {
            oldValue = if (flagField == FlagField.ACTIVE) nick.active else nick.visible
        }
        if (flagField == FlagField.ACTIVE) nick.active = newValue else nick.visible = newValue
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        val value = oldValue ?: return
        if (flagField == FlagField.ACTIVE) nick.active = value else nick.visible = value
    }
}

/** Altera o transform completo do Nick (posição, rotação, escala). */
class TransformCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val newSnapshot: TransformSnapshot
) : Command {
    override val label: String get() = "Transformar"
    private var oldSnapshot: TransformSnapshot? = null

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (oldSnapshot == null) oldSnapshot = nick.transform.snapshot()
        nick.transform.apply(newSnapshot)
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        nick.transform.apply(oldSnapshot ?: return)
    }
}

/** Move o Nick para outro pai (ou raiz). */
class ReparentCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val newParentId: ReferenceId?
) : Command {
    override val label: String get() = "Reparentar"
    private var oldParent: Nick? = null
    private var oldIndex: Int = -1

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (oldParent == null && oldIndex < 0) {
            oldParent = nick.parent
            oldIndex = nick.indexInParent().coerceAtLeast(0)
        }
        val target = newParentId?.let { scene.findNick(it) }
        scene.reparent(nick, target)
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        scene.reparent(nick, oldParent)
        // Recoloca na posição original entre os irmãos.
        val list = nick.parent?.children ?: scene.roots
        val idx = list.indexOf(nick)
        if (idx >= 0 && oldIndex in list.indices && idx != oldIndex) {
            list.removeAt(idx)
            list.add(oldIndex, nick)
        }
    }
}

/** Reordena o Nick entre os irmãos (delta em posições). */
class ReorderCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val delta: Int
) : Command {
    override val label: String get() = "Reordenar"

    override fun execute() {
        scene.findNick(nickId)?.let { scene.reorder(it, delta) }
    }

    override fun undo() {
        scene.findNick(nickId)?.let { scene.reorder(it, -delta) }
    }
}

/** Adiciona uma instância de classe ao Nick. */
class AddClassCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val typeName: String
) : Command {
    override val label: String get() = "Adicionar classe $typeName"
    private var createdInstance: NickClassInstance? = null

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        if (createdInstance != null) {
            nick.addClassInstance(createdInstance!!)
            return
        }
        val inst = NickClassRegistry.createInstance(typeName)
        if (inst != null) {
            nick.addClassInstance(inst)
            createdInstance = inst
        }
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        nick.classes.remove(createdInstance ?: nick.findClassInstance(typeName))
    }
}

/** Remove uma instância de classe do Nick (preserva campos para undo). */
class RemoveClassCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val typeName: String
) : Command {
    override val label: String get() = "Remover classe $typeName"
    private var removedInstance: NickClassInstance? = null
    private var removedIndex: Int = -1

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        val inst = nick.findClassInstance(typeName) ?: return
        removedInstance = inst
        removedIndex = nick.classes.indexOf(inst)
        nick.classes.remove(inst)
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        val inst = removedInstance ?: return
        val at = if (removedIndex in 0..nick.classes.size) removedIndex else nick.classes.size
        nick.classes.add(at, inst)
    }
}

/** Altera um campo de uma instância de classe. */
class SetClassFieldCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val typeName: String,
    private val fieldName: String,
    private val newValue: NickValue
) : Command {
    override val label: String get() = "Campo $fieldName"
    private var oldValue: NickValue? = null

    override fun execute() {
        val inst = scene.findNick(nickId)?.findClassInstance(typeName) ?: return
        val current = inst.fields[fieldName]
        if (oldValue == null && current != null) oldValue = current.copyValue()
        inst.fields[fieldName] = newValue.copyValue()
    }

    override fun undo() {
        val inst = scene.findNick(nickId)?.findClassInstance(typeName) ?: return
        val value = oldValue ?: return
        inst.fields[fieldName] = value.copyValue()
    }
}
