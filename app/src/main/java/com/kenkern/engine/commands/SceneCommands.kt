package com.kenkern.engine.commands

import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot

/** Cria um Nick do TIPO (kind) informado (filho do pai informado ou raiz),
 *  aplicando as CLASSES PADRÃO do tipo (T0.3 — ex.: body.collider → Colisor). */
class CreateNickCommand(
    private val scene: Scene,
    val parentId: ReferenceId?,
    private val kind: String,
    private val name: String? = null,
    private val classesPadrao: List<String> = emptyList(),
    private val newId: ReferenceId = ReferenceId.generate()
) : Command {
    override val label: String get() = "Criar ${NickKindRegistry.labelOf(kind, "pt")}"
    private var created: Nick? = null
    private var detached: Scene.DetachedSubtree? = null

    /** Id do nick criado (para selecionar após o execute); null se ainda não rodou. */
    fun createdNickId(): ReferenceId? = created?.id

    override fun execute() {
        val existing = created
        if (existing != null) {
            // Redo: reinsere a mesma subárvore e os seus dados LINKERF.
            val state = detached ?: return
            scene.attachSubtree(state)
            detached = null
            return
        }
        val parent = parentId?.let { scene.findNick(it) }
        val nick = scene.createNick(name, parent, newId, kind = kind)
        // Classes padrão do TIPO (preset de dados — DECISIONS #24).
        for (classe in classesPadrao) {
            NickClassRegistry.createInstance(classe)?.let { nick.addClassInstance(it) }
        }
        created = nick
    }

    override fun undo() {
        created?.let { nick -> scene.detachSubtree(nick)?.let { detached = it } }
    }
}

/** Remove um Nick (e subárvore); o undo recoloca a mesma subárvore no mesmo lugar. */
class DeleteNickCommand(
    private val scene: Scene,
    val nickId: ReferenceId
) : Command {
    override val label: String get() = "Apagar nick"
    private var detached: Scene.DetachedSubtree? = null

    override fun execute() {
        val nick = detached?.nick ?: scene.findNick(nickId) ?: return
        scene.detachSubtree(nick)?.let { detached = it }
    }

    override fun undo() {
        val state = detached ?: return
        scene.attachSubtree(state)
        detached = null
    }
}

/** Renomeia um Nick (texto VAZIO = volta a exibir o rótulo do tipo, DECISIONS #26). */
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
        nick.name = newName.trim().ifEmpty { null }
    }

    override fun undo() {
        val nick = scene.findNick(nickId) ?: return
        nick.name = oldName
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

/**
 * UM Command por ARRASTO do gizmo: guarda o estado inicial (capturado no DOWN)
 * e o final (no UP). O undo/redo volta tudo de uma vez — não um comando por frame.
 */
class TransformDragCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val startSnapshot: TransformSnapshot,
    private val endSnapshot: TransformSnapshot
) : Command {
    override val label: String get() = "Transformar (arrasto)"

    override fun execute() {
        scene.findNick(nickId)?.transform?.apply(endSnapshot)
    }

    override fun undo() {
        scene.findNick(nickId)?.transform?.apply(startSnapshot)
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

/**
 * T0.3 (adendo v4): troca a FORMA do colisor em UM Command — a forma nova se
 * AJUSTA À AABB ATUAL do Nick (centro/tamanho da classe "Colisor" assumem o
 * centro e a meia-extensão×2 da malha/cubo), e o UNDO devolve os 3 campos.
 */
class SetColliderShapeCommand(
    private val scene: Scene,
    private val nickId: ReferenceId,
    private val novaForma: String
) : Command {
    override val label: String get() = "Forma do colisor"

    private var antigaForma: String? = null
    private var antigoCentro: NickValue.Vec3Value? = null
    private var antigoTamanho: NickValue.Vec3Value? = null

    // Rascunhos (sem alocação relevante por execute).
    private val meia = FloatArray(3)
    private val centro = FloatArray(3)

    override fun execute() {
        val nick = scene.findNick(nickId) ?: return
        val inst = nick.findClassInstance(MalhaNick.CLASSE_COLISOR) ?: return
        if (antigaForma == null) {
            antigaForma = inst.getString("forma", "box")
            (inst.fields["centro"] as? NickValue.Vec3Value)?.let { antigoCentro = it.copyValue() as NickValue.Vec3Value }
            (inst.fields["tamanho"] as? NickValue.Vec3Value)?.let { antigoTamanho = it.copyValue() as NickValue.Vec3Value }
        }
        // Ajuste à AABB atual: a forma nova envolve o que o Nick já é.
        MalhaNick.meiaExtensaoLocal(nick, meia)
        MalhaNick.centroLocal(nick, centro)
        inst.fields["forma"] = NickValue.StringValue(novaForma)
        inst.fields["centro"] = NickValue.Vec3Value(centro[0], centro[1], centro[2])
        inst.fields["tamanho"] = NickValue.Vec3Value(meia[0] * 2f, meia[1] * 2f, meia[2] * 2f)
    }

    override fun undo() {
        val inst = scene.findNick(nickId)?.findClassInstance(MalhaNick.CLASSE_COLISOR) ?: return
        antigaForma?.let { inst.fields["forma"] = NickValue.StringValue(it) }
        antigoCentro?.let { inst.fields["centro"] = it.copyValue() as NickValue.Vec3Value }
        antigoTamanho?.let { inst.fields["tamanho"] = it.copyValue() as NickValue.Vec3Value }
    }
}
