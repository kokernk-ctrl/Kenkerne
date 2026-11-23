package com.kenkern.engine.core.linkerf

import com.kenkern.engine.core.ReferenceId

/**
 * LINKERF: sistema que liga Nicks, eventos e referências sem código.
 * Nesta fase existe apenas o MODELO DE DADOS e as interfaces — a UI chega na Fase 4.
 */

enum class LinkerfPortKind {
    EVENT_IN, EVENT_OUT, DATA_IN, DATA_OUT
}

enum class LinkerfPortType {
    TRIGGER, FLOAT, INT, BOOL, STRING, VEC3
}

/** Porta de um dono (Nick ou asset) que pode ser conectada por um LinkerfLink. */
class LinkerfPort(
    val id: ReferenceId,
    val ownerId: ReferenceId,
    val name: String,
    val kind: LinkerfPortKind,
    val type: LinkerfPortType
)

/** Conexão dirigida entre duas portas (saída -> entrada). */
class LinkerfLink(
    val id: ReferenceId,
    val fromPortId: ReferenceId,
    val toPortId: ReferenceId
)

/** Interface para quem expõe portas ao Linkerf (implementada por Nicks/assets no futuro). */
interface LinkerfPortProvider {
    fun ports(): List<LinkerfPort>
}

/** Política de validação de conexões (extensível na Fase 4). */
interface LinkerfConnectionPolicy {
    fun canConnect(from: LinkerfPort, to: LinkerfPort): Boolean
}

/** Política padrão: saída -> entrada, mesmo tipo, donos distintos, sem duplicidade. */
class DefaultLinkerfConnectionPolicy : LinkerfConnectionPolicy {
    override fun canConnect(from: LinkerfPort, to: LinkerfPort): Boolean {
        val fromIsOut = from.kind == LinkerfPortKind.EVENT_OUT || from.kind == LinkerfPortKind.DATA_OUT
        val toIsIn = to.kind == LinkerfPortKind.EVENT_IN || to.kind == LinkerfPortKind.DATA_IN
        if (!fromIsOut || !toIsIn) return false
        if (from.ownerId == to.ownerId) return false
        if (from.type != to.type) return false
        return true
    }
}

/** Grafo do Linkerf de uma cena: portas + links. */
class LinkerfGraph(private val policy: LinkerfConnectionPolicy = DefaultLinkerfConnectionPolicy()) {

    private val ports = LinkedHashMap<String, LinkerfPort>()
    private val links = LinkedHashMap<String, LinkerfLink>()

    /**
     * Dados retirados temporariamente junto com uma subárvore. Os índices
     * originais mantêm a ordem serializada ao desfazer a exclusão.
     */
    class Snapshot internal constructor(
        internal val ports: List<Pair<Int, LinkerfPort>>,
        internal val links: List<Pair<Int, LinkerfLink>>
    )

    fun addPort(port: LinkerfPort) {
        ports[port.id.value] = port
    }

    fun removePort(portId: ReferenceId) {
        ports.remove(portId.value)
        links.values.removeAll { it.fromPortId == portId || it.toPortId == portId }
    }

    fun findPort(portId: ReferenceId): LinkerfPort? = ports[portId.value]

    /** Cria um link respeitando a política. Retorna o link criado ou null. */
    fun connect(fromPortId: ReferenceId, toPortId: ReferenceId): LinkerfLink? {
        val from = ports[fromPortId.value] ?: return null
        val to = ports[toPortId.value] ?: return null
        if (!policy.canConnect(from, to)) return null
        for (existing in links.values) {
            if (existing.fromPortId == fromPortId && existing.toPortId == toPortId) return null
        }
        val link = LinkerfLink(ReferenceId.generate(), fromPortId, toPortId)
        links[link.id.value] = link
        return link
    }

    fun removeLink(linkId: ReferenceId) {
        links.remove(linkId.value)
    }

    /** Insere um link diretamente (desserialização .ck, sem validação de política). */
    fun addLinkDirect(link: LinkerfLink) {
        links[link.id.value] = link
    }

    fun linksOfOwner(ownerId: ReferenceId): List<LinkerfLink> {
        val out = ArrayList<LinkerfLink>()
        for (link in links.values) {
            val from = ports[link.fromPortId.value]
            val to = ports[link.toPortId.value]
            if (from?.ownerId == ownerId || to?.ownerId == ownerId) out.add(link)
        }
        return out
    }

    fun allPorts(): List<LinkerfPort> = ports.values.toList()

    fun allLinks(): List<LinkerfLink> = links.values.toList()

    /** Remove portas e links de um dono (usado ao apagar Nicks). */
    fun removeOwner(ownerId: ReferenceId) {
        removeOwners(setOf(ownerId))
    }

    /**
     * Retira todos os dados dos owners indicados e links que ficariam pendurados.
     * O snapshot é reversível por [restore], inclusive para links entre a
     * subárvore e owners externos.
     */
    fun removeOwners(ownerIds: Set<ReferenceId>): Snapshot {
        val removedPorts = ArrayList<Pair<Int, LinkerfPort>>()
        val deadPortIds = HashSet<String>()
        var portIndex = 0
        for (port in ports.values) {
            if (port.ownerId in ownerIds) {
                removedPorts.add(portIndex to port)
                deadPortIds.add(port.id.value)
            }
            portIndex++
        }

        val removedLinks = ArrayList<Pair<Int, LinkerfLink>>()
        var linkIndex = 0
        for (link in links.values) {
            val fromMissing = link.fromPortId.value !in ports
            val toMissing = link.toPortId.value !in ports
            if (link.fromPortId.value in deadPortIds || link.toPortId.value in deadPortIds ||
                fromMissing || toMissing
            ) {
                removedLinks.add(linkIndex to link)
            }
            linkIndex++
        }

        for ((_, port) in removedPorts) ports.remove(port.id.value)
        for ((_, link) in removedLinks) links.remove(link.id.value)
        return Snapshot(removedPorts, removedLinks)
    }

    /** Restaura exatamente os dados capturados por [removeOwners]. */
    fun restore(snapshot: Snapshot) {
        restoreInOriginalOrder(ports, snapshot.ports) { it.id.value }
        restoreInOriginalOrder(links, snapshot.links) { it.id.value }
    }

    private fun <T> restoreInOriginalOrder(
        map: LinkedHashMap<String, T>,
        removed: List<Pair<Int, T>>,
        key: (T) -> String
    ) {
        if (removed.isEmpty()) return
        val remaining = map.values.toList()
        val removedAtIndex = HashMap<Int, T>(removed.size)
        for ((index, value) in removed) removedAtIndex[index] = value

        val merged = ArrayList<T>(remaining.size + removed.size)
        var remainingIndex = 0
        val originalSize = remaining.size + removed.size
        for (index in 0 until originalSize) {
            val restored = removedAtIndex[index]
            if (restored != null) {
                merged.add(restored)
            } else if (remainingIndex < remaining.size) {
                merged.add(remaining[remainingIndex++])
            }
        }
        while (remainingIndex < remaining.size) merged.add(remaining[remainingIndex++])

        map.clear()
        for (value in merged) map[key(value)] = value
    }

    fun clear() {
        ports.clear()
        links.clear()
    }
}
