package com.kenkern.engine.core.serialization

import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import org.junit.Assert.*
import org.junit.Test

class CkHierarchyTest {
    @Test fun roundTripPreservaOrdemExplicitaDosIrmaos() {
        val original = Scene()
        val parent = original.createRootNick("root")
        val ids = listOf("z", "a", "n", "m", "b")
        ids.forEach { original.createNick(it, parent, id = ReferenceId.of(it)) }
        val copy = CkFormat.parseToNewScene(CkFormat.writeToString(original))!!
        assertEquals(ids, copy.roots.single().children.map { it.id.value })
        ids.forEach { assertSame(copy.roots.single(), copy.findNick(ReferenceId.of(it))!!.parent) }
    }

    @Test fun referenciasCircularesSaoRecusadasAntesDeCriarArvore() {
        assertNull(CkFormat.parseToNewScene("CK2\nSCENE name=x\nNICK id=a parent=b\nNICK id=b parent=a\nENDCK"))
    }
}
