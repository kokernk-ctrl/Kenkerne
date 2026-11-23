package com.kenkern.engine.commands

import com.kenkern.engine.core.LenteNick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * H3 (v0.1.20): "Câmera principal — SÓ UMA por cena" — SetLentePrincipalCommand
 * marca a escolhida, desmarca as demais camera.* e o UNDO devolve TODOS os
 * flags de uma vez.
 */
class SetLentePrincipalCommandTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    private fun marcar(nick: com.kenkern.engine.core.Nick, principal: Boolean) {
        val inst = NickClassRegistry.createInstance(LenteNick.CLASSE)!!
        inst.fields[LenteNick.CAMPO_PRINCIPAL] = NickValue.BoolValue(principal)
        nick.addClassInstance(inst)
    }

    private fun lerPrincipal(nick: com.kenkern.engine.core.Nick): Boolean =
        nick.findClassInstance(LenteNick.CLASSE)?.getBool(LenteNick.CAMPO_PRINCIPAL, false) ?: false

    @Test
    fun `marca a escolhida e desmarca todas as outras`() {
        val scene = Scene()
        val a = scene.createRootNick("A")
        a.kind = "camera.basic"
        val b = scene.createRootNick("B")
        b.kind = "camera.basic"
        marcar(a, true)
        marcar(b, false)

        SetLentePrincipalCommand(scene, b.id, true).execute()
        assertTrue("B virou principal", lerPrincipal(b))
        assertFalse("A foi DESMARCADA (só uma por cena)", lerPrincipal(a))
    }

    @Test
    fun `undo devolve todos os flags de uma vez`() {
        val scene = Scene()
        val a = scene.createRootNick("A")
        a.kind = "camera.basic"
        val b = scene.createRootNick("B")
        b.kind = "camera.basic"
        marcar(a, true)
        marcar(b, false)

        val cmd = SetLentePrincipalCommand(scene, b.id, true)
        cmd.execute()
        cmd.undo()
        assertTrue("A volta a ser principal", lerPrincipal(a))
        assertFalse("B volta a não-principal", lerPrincipal(b))
    }

    @Test
    fun nickSemClasseLenteNuncaCrasha() {
        val scene = Scene()
        val a = scene.createRootNick("A") // nick.mesh comum — sem Lente
        a.kind = "nick.mesh"
        val b = scene.createRootNick("B")
        b.kind = "camera.basic"
        marcar(b, true)

        SetLentePrincipalCommand(scene, b.id, true).execute()
        assertTrue(lerPrincipal(b))
        assertEquals(1, b.classes.size)
    }
}
