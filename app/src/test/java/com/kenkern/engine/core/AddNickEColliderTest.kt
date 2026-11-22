package com.kenkern.engine.core

import com.kenkern.engine.commands.CreateNickCommand
import com.kenkern.engine.commands.SetColliderShapeCommand
import com.kenkern.engine.render.PickingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T0.3 (adendo v4, seções 3 e 4): janela "Adicionar Nick" (CRIAÇÃO com classes
 * padrão + ponto de surgimento) e SELETOR DE FORMA do collider.
 *
 *  1. CreateNickCommand aplica as CLASSES PADRÃO do tipo (body.collider →
 *     Colisor com forma box) e o undo/remove + redo funcionam;
 *  2. SetColliderShapeCommand troca a forma AJUSTANDO À AABB ATUAL (centro e
 *     tamanho = AABB do Nick) em UM Command; o undo devolve os 3 campos;
 *  3. interseccaoChao: ponto de surgimento = centro da tela projetado no chão
 *     (raio para baixo acerta; horizontal/para cima = sem interseção);
 *  4. classes agrupadas por categoria para o menu do Inspetor.
 */
class AddNickEColliderTest {

    @Before
    fun registrar() {
        NickKindRegistry.clear()
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    @Test
    fun `criar body collider aplica a classe Colisor por padrao`() {
        val scene = Scene("T")
        val cmd = CreateNickCommand(
            scene, null, "body.collider",
            classesPadrao = NickKindRegistry.find("body.collider")!!.defaultClasses
        )
        cmd.execute()
        val id = cmd.createdNickId()!!
        val nick = scene.findNick(id)!!
        assertEquals("body.collider", nick.kind)
        assertNull("name nulo = rótulo do tipo", nick.name)
        assertEquals("Nick de Corpo Colisor", nick.displayName("pt"))
        val colisor = nick.findClassInstance("Colisor")
        assertNotNull("classe Colisor aplicada por padrão", colisor)
        assertEquals("box", colisor!!.getString("forma", ""))

        // Undo remove o Nick inteiro (com a classe); redo devolve o MESMO id.
        cmd.undo()
        assertNull(scene.findNick(id))
        cmd.execute()
        assertEquals("body.collider", scene.findNick(id)!!.kind)
        assertNotNull(scene.findNick(id)!!.findClassInstance("Colisor"))
    }

    @Test
    fun `trocar forma do colisor ajusta a AABB atual e o undo devolve`() {
        val scene = Scene("T")
        val raiz = scene.createRootNick(null, kind = NickKindRegistry.NICK_GROUP)
        // Malha com AABB 2×4×6 centrada em (0, 1, 0): min(-1,-1,-3) max(1,3,3).
        val nick = scene.createNick("Placa", raiz, kind = NickKindRegistry.NICK_MESH)
        val malha = NickClassInstance("Malha")
        malha.fields["path"] = NickValue.StringValue("assets/models/placa.KERN")
        malha.fields["aabbMinX"] = NickValue.FloatValue(-1f)
        malha.fields["aabbMinY"] = NickValue.FloatValue(-1f)
        malha.fields["aabbMinZ"] = NickValue.FloatValue(-3f)
        malha.fields["aabbMaxX"] = NickValue.FloatValue(1f)
        malha.fields["aabbMaxY"] = NickValue.FloatValue(3f)
        malha.fields["aabbMaxZ"] = NickValue.FloatValue(3f)
        nick.addClassInstance(malha)
        val colisor = NickClassInstance("Colisor")
        colisor.fields["forma"] = NickValue.StringValue("box")
        colisor.fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
        colisor.fields["tamanho"] = NickValue.Vec3Value(1f, 1f, 1f)
        nick.addClassInstance(colisor)

        // Troca para ESFERA: forma + centro (centro da AABB) + tamanho (AABB inteira).
        val cmd = SetColliderShapeCommand(scene, nick.id, "sphere")
        cmd.execute()
        val depois = nick.findClassInstance("Colisor")!!
        assertEquals("sphere", depois.getString("forma", ""))
        val centro = depois.fields["centro"] as NickValue.Vec3Value
        val tamanho = depois.fields["tamanho"] as NickValue.Vec3Value
        assertEquals(0f, centro.x, 1e-4f)
        assertEquals(1f, centro.y, 1e-4f) // (min.y+max.y)/2 = (-1+3)/2
        assertEquals(0f, centro.z, 1e-4f)
        assertEquals(2f, tamanho.x, 1e-4f) // max-min por eixo
        assertEquals(4f, tamanho.y, 1e-4f)
        assertEquals(6f, tamanho.z, 1e-4f)

        // Undo devolve os 3 campos de uma vez (UM Command).
        cmd.undo()
        val devolvido = nick.findClassInstance("Colisor")!!
        assertEquals("box", devolvido.getString("forma", ""))
        assertEquals(0f, (devolvido.fields["centro"] as NickValue.Vec3Value).y, 1e-4f)
        assertEquals(1f, (devolvido.fields["tamanho"] as NickValue.Vec3Value).x, 1e-4f)

        // Redo reaplica a esfera ajustada.
        cmd.execute()
        assertEquals("sphere", nick.findClassInstance("Colisor")!!.getString("forma", ""))
    }

    @Test
    fun `interseccao com o chao do centro da tela`() {
        val out = FloatArray(2)

        // Câmera em (0, 10, 10) olhando reto para baixo: acerta (0, 0).
        val t = PickingMath.interseccaoChao(0f, 10f, 10f, 0f, -1f, 0f, out)
        assertTrue(t > 0f)
        assertEquals(0f, out[0], 1e-4f)
        assertEquals(10f, out[1], 1e-4f)

        // Diagonal 45° para baixo à frente: y=5, dy=-0.707 → t ≈ 7,07.
        val t2 = PickingMath.interseccaoChao(0f, 5f, 0f, 0.70710678f, -0.70710678f, 0f, out)
        assertTrue(t2 > 0f)
        assertEquals(5f, out[0], 1e-2f)

        // Paralelo ao chão: SEM interseção (fallback do chamador).
        assertFalse(PickingMath.interseccaoChao(0f, 5f, 0f, 1f, 0f, 0f, out) >= 0f)

        // Olhando para CIMA: interseção atrás do olho → sem interseção à frente.
        assertFalse(PickingMath.interseccaoChao(0f, 5f, 0f, 0f, 0.5f, 0f, out) >= 0f)
    }

    @Test
    fun `classes agrupadas por categoria para o menu do inspetor`() {
        val porCategoria = NickClassRegistry.porCategoria()
        assertTrue("categoria movimento", porCategoria["movimento"]!!.any { it.name == "Spin" })
        assertTrue("categoria movimento com Bob", porCategoria["movimento"]!!.any { it.name == "Bob" })
        assertTrue("categoria fisica com Colisor", porCategoria["fisica"]!!.any { it.name == "Colisor" })
        assertTrue("categoria geometria com Malha", porCategoria["geometria"]!!.any { it.name == "Malha" })
        // Toda classe registrada aparece em exatamente uma categoria.
        val total = porCategoria.values.sumOf { it.size }
        assertEquals(NickClassRegistry.all().size, total)
    }
}
