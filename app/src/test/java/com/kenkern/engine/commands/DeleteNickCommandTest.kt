package com.kenkern.engine.commands

import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.linkerf.LinkerfLink
import com.kenkern.engine.core.linkerf.LinkerfPort
import com.kenkern.engine.core.linkerf.LinkerfPortKind
import com.kenkern.engine.core.linkerf.LinkerfPortType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteNickCommandTest {

    @Test
    fun `delete undo redo preserva subarvore referencias ordem e linkerf`() {
        val scene = Scene("delete-undo")
        val firstRoot = scene.createRootNick("antes")
        val group = scene.createRootNick("grupo")
        val childA = scene.createNick("A", group)
        val grandchild = scene.createNick("A1", childA)
        val childB = scene.createNick("B", group)
        val lastRoot = scene.createRootNick("depois")
        val external = scene.createRootNick("externo")
        val outsideEnd = scene.createRootNick("destino")

        val outsideOut = port("outside-out", external.id, "out", LinkerfPortKind.DATA_OUT)
        val outsideIn = port("outside-in", outsideEnd.id, "in", LinkerfPortKind.DATA_IN)
        val aOut = port("a-out", childA.id, "out", LinkerfPortKind.DATA_OUT)
        val bIn = port("b-in", childB.id, "in", LinkerfPortKind.DATA_IN)
        val bOut = port("b-out", childB.id, "out", LinkerfPortKind.DATA_OUT)
        val externalIn = port("external-in", external.id, "in", LinkerfPortKind.DATA_IN)
        listOf(outsideOut, outsideIn, aOut, bIn, bOut, externalIn).forEach(scene.linkerf::addPort)
        val unrelated = scene.linkerf.connect(outsideOut.id, outsideIn.id)!!
        val internal = scene.linkerf.connect(aOut.id, bIn.id)!!
        val crossing = scene.linkerf.connect(bOut.id, externalIn.id)!!

        val rootsBefore = scene.roots.toList()
        val childrenBefore = group.children.toList()
        val portsBefore = scene.linkerf.allPorts().map(::portView)
        val linksBefore = scene.linkerf.allLinks().map(::linkView)
        val command = DeleteNickCommand(scene, group.id)

        command.execute()

        assertNull(scene.findNick(group.id))
        assertNull(scene.findNick(childA.id))
        assertNull(scene.findNick(grandchild.id))
        assertNull(scene.findNick(childB.id))
        assertEquals(listOf(unrelated.id), scene.linkerf.allLinks().map { it.id })
        assertEquals(
            listOf(outsideOut.id, outsideIn.id, externalIn.id),
            scene.linkerf.allPorts().map { it.id }
        )

        command.undo()

        assertSame(rootsBefore[1], scene.roots[1])
        assertEquals(rootsBefore, scene.roots)
        assertEquals(childrenBefore, group.children)
        assertSame(group, childA.parent)
        assertSame(childA, grandchild.parent)
        assertSame(group, childB.parent)
        assertSame(grandchild, scene.findNick(grandchild.id))
        assertEquals(portsBefore, scene.linkerf.allPorts().map(::portView))
        assertEquals(linksBefore, scene.linkerf.allLinks().map(::linkView))
        assertTrue(scene.linkerf.allLinks().any { it.id == internal.id })
        assertTrue(scene.linkerf.allLinks().any { it.id == crossing.id })

        command.execute()
        command.undo()
        assertEquals(rootsBefore, scene.roots)
        assertEquals(portsBefore, scene.linkerf.allPorts().map(::portView))
        assertEquals(linksBefore, scene.linkerf.allLinks().map(::linkView))
        assertSame(firstRoot, scene.roots.first())
        assertSame(lastRoot, scene.roots[2])
    }

    private fun port(
        id: String,
        ownerId: ReferenceId,
        name: String,
        kind: LinkerfPortKind
    ) = LinkerfPort(
        ReferenceId.of(id),
        ownerId,
        name,
        kind,
        LinkerfPortType.FLOAT
    )

    private data class PortView(
        val id: String,
        val owner: String,
        val name: String,
        val kind: LinkerfPortKind,
        val type: LinkerfPortType
    )

    private fun portView(port: LinkerfPort) =
        PortView(port.id.value, port.ownerId.value, port.name, port.kind, port.type)

    private data class LinkView(val id: String, val from: String, val to: String)

    private fun linkView(link: LinkerfLink) =
        LinkView(link.id.value, link.fromPortId.value, link.toPortId.value)
}
