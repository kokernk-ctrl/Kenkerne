package com.kenkern.engine.render

import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NickPlayCameraTest {
    @Test
    fun `seguidora preserva o deslocamento inicial ao mover o jogador`() {
        val scene = Scene()
        val player = scene.createRootNick("Player")
        val follow = scene.createRootNick("Follow")
        player.transform.position.set(1f, 1f, 2f)
        follow.transform.position.set(2f, 4f, 8f)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(player, follow, null)
        val eye = Vec3()
        game.camera.eyePoint(eye)
        assertEquals(2f, eye.x, 0.001f)
        assertEquals(4f, eye.y, 0.001f)
        assertEquals(8f, eye.z, 0.001f)
        player.transform.position.set(3f, 2f, 5f)
        scene.updateWorldTransforms()
        game.update()
        game.camera.eyePoint(eye)
        assertEquals(4f, eye.x, 0.001f)
        assertEquals(5f, eye.y, 0.001f)
        assertEquals(11f, eye.z, 0.001f)
    }

    @Test
    fun `camera basica utiliza posicao e orientacao da cena`() {
        val scene = Scene()
        val basic = scene.createRootNick("Camera")
        basic.transform.position.set(1f, 3f, 6f)
        basic.transform.rotation.setFromAxisAngle(0f, 1f, 0f, (Math.PI / 2).toFloat())
        scene.updateWorldTransforms()
        val game = NickPlayCamera(null, null, basic)
        val v = Vec3()
        game.camera.eyePoint(v)
        assertEquals(3f, v.y, 0.001f)
        game.camera.forwardVector(v)
        assertEquals(-1f, v.x, 0.001f)
        assertEquals(0f, v.z, 0.001f)
        assertFalse(NickPlayCamera(null, null, null).active)
    }
}
