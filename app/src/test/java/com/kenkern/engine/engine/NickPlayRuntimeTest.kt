package com.kenkern.engine.engine

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NickPlayRuntimeTest {

    @Test
    fun `seleciona primeiro player ativo em ordem da arvore e inicia no spawn mundial`() {
        val scene = Scene("runtime")
        val inactive = scene.createRootNick("inativo", active = false, kind = "nick.group")
        scene.createNick("player ignorado", inactive, kind = "body.player")
        scene.createNick("spawn ignorado", inactive, kind = "area.spawn")
        scene.createRootNick("camera ignorada", active = false, kind = "camera.follow")

        val group = scene.createRootNick("grupo", kind = "nick.group")
        group.transform.position.set(10f, 0f, 0f)
        val spawn = scene.createNick("spawn", group, kind = "area.spawn")
        spawn.transform.position.set(2f, 1f, -3f)
        val firstPlayer = scene.createNick("player 1", group, kind = "body.player")
        val secondPlayer = scene.createRootNick("player 2", kind = "body.player")
        val follow = scene.createRootNick("follow", kind = "camera.follow")
        val basic = scene.createRootNick("basic", kind = "camera.basic")

        val runtime = NickPlayRuntime(scene)

        assertSame(firstPlayer, runtime.player)
        assertEquals(12f, firstPlayer.transform.worldMatrix.m[12], 1e-5f)
        assertEquals(1f, firstPlayer.transform.worldMatrix.m[13], 1e-5f)
        assertEquals(-3f, firstPlayer.transform.worldMatrix.m[14], 1e-5f)
        assertNotNull(secondPlayer)
        assertSame(follow, runtime.followCamera)
        assertSame(basic, runtime.basicCamera)
    }

    @Test
    fun `movimento diagonal normaliza e gravidade move em y`() {
        val scene = Scene()
        val player = scene.createRootNick("player", kind = "body.player")
        player.transform.position.y = 10f
        val runtime = NickPlayRuntime(scene)

        runtime.step(0.1f, 1f, 1f)

        assertEquals(4f / kotlin.math.sqrt(2f) * 0.1f, player.transform.position.x, 1e-5f)
        assertEquals(4f / kotlin.math.sqrt(2f) * 0.1f, player.transform.position.z, 1e-5f)
        assertEquals(9.8f, player.transform.position.y, 1e-5f)
    }

    @Test
    fun `static em cubo escalado bloqueia parede e mantem player no chao`() {
        val scene = Scene()
        val player = scene.createRootNick("player", kind = "body.player")
        player.transform.position.y = 0.5f

        val floor = scene.createRootNick("floor", kind = "body.static")
        floor.transform.position.y = -0.5f
        floor.transform.scale.set(20f, 1f, 20f)

        val wall = scene.createRootNick("wall", kind = "body.static")
        wall.transform.position.set(1.5f, 1.5f, 0f)
        wall.transform.scale.set(1f, 3f, 4f)
        val runtime = NickPlayRuntime(scene)

        runtime.step(0.25f, 1f, 0f)

        assertEquals(0.5f, player.transform.position.x, 1e-4f)
        assertEquals(0.5f, player.transform.position.y, 1e-4f)
    }

    @Test
    fun `collider box respeita centro tamanho e escala de transform`() {
        val scene = Scene()
        val player = scene.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 5f, 0f)

        val blocker = scene.createRootNick("box", kind = "body.collider")
        blocker.transform.position.set(2f, 5f, 0f)
        blocker.transform.scale.set(2f, 1f, 1f)
        blocker.addClassInstance(boxCollider(sizeX = 1f, sizeY = 2f, sizeZ = 2f))

        val runtime = NickPlayRuntime(scene)
        runtime.step(0.25f, 1f, 0f)

        // A caixa ocupa x=[1,3]; o player de largura 1 para em x=0.5.
        assertEquals(0.5f, player.transform.position.x, 1e-4f)
    }

    @Test
    fun `ignora blockers inativos e formas de collider nao suportadas`() {
        val scene = Scene()
        val player = scene.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10f, 0f)

        val inactiveParent = scene.createRootNick("inativo", active = false, kind = "nick.group")
        val inactiveWall = scene.createNick("wall inativa", inactiveParent, kind = "body.static")
        inactiveWall.transform.position.set(1.5f, 10f, 0f)
        val sphere = scene.createRootNick("sphere", kind = "body.collider")
        sphere.transform.position.set(1.5f, 10f, 0f)
        sphere.addClassInstance(boxCollider(shape = "sphere"))
        val runtime = NickPlayRuntime(scene)

        repeat(3) { runtime.step(0.25f, 1f, 0f) }

        assertEquals(3f, player.transform.position.x, 1e-4f)
    }

    @Test
    fun `transforms de pai sao considerados para movimento em eixos mundiais`() {
        val scene = Scene()
        val parent = scene.createRootNick("parent", kind = "nick.group")
        parent.transform.position.x = 10f
        parent.transform.scale.set(2f, 1f, 1f)
        parent.transform.rotation.setFromAxisAngle(0f, 1f, 0f, (Math.PI / 2.0).toFloat())
        val player = scene.createNick("player", parent, kind = "body.player")
        player.transform.position.y = 5f
        val runtime = NickPlayRuntime(scene)

        runtime.step(0.1f, 1f, 0f)

        assertEquals(10.4f, player.transform.worldMatrix.m[12], 1e-4f)
        assertEquals(0f, player.transform.worldMatrix.m[14], 1e-4f)
    }

    @Test
    fun `consulta de area verifica sobreposicao e efetividade de active`() {
        val scene = Scene()
        val player = scene.createRootNick("player", kind = "body.player")
        val area = scene.createRootNick("area", kind = "area.volume")
        area.transform.position.x = 0.75f
        area.transform.scale.set(2f, 2f, 2f)
        val runtime = NickPlayRuntime(scene)

        assertTrue(runtime.isPlayerInside(area))
        area.active = false
        assertFalse(runtime.isPlayerInside(area))
        area.active = true
        area.transform.position.x = 5f
        assertFalse(runtime.isPlayerInside(area))
        assertFalse(runtime.isPlayerInside(player))
    }

    private fun boxCollider(
        shape: String = "box",
        sizeX: Float = 1f,
        sizeY: Float = 1f,
        sizeZ: Float = 1f
    ): NickClassInstance = NickClassInstance("Colisor").apply {
        fields["forma"] = NickValue.StringValue(shape)
        fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
        fields["tamanho"] = NickValue.Vec3Value(sizeX, sizeY, sizeZ)
    }
}
