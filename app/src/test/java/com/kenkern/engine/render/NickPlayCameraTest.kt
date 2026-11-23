package com.kenkern.engine.render

import com.kenkern.engine.core.LenteNick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NickPlayCameraTest {
    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    @Test
    fun `seguidora preserva o deslocamento inicial ao mover o jogador`() {
        val scene = Scene()
        val player = scene.createRootNick("Player")
        val follow = scene.createRootNick("Follow")
        player.transform.position.set(1f, 1f, 2f)
        follow.transform.position.set(2f, 4f, 8f)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, player, follow, null)
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
        val game = NickPlayCamera(scene, null, null, basic)
        val v = Vec3()
        game.camera.eyePoint(v)
        assertEquals(3f, v.y, 0.001f)
        game.camera.forwardVector(v)
        assertEquals(-1f, v.x, 0.001f)
        assertEquals(0f, v.z, 0.001f)
        assertFalse(NickPlayCamera(scene, null, null, null).active)
    }

    // ---------------- H3 (v0.1.20) ----------------

    private fun classeLente(
        nick: com.kenkern.engine.core.Nick,
        fov: Float = LenteNick.DEF_FOV,
        principal: Boolean = false,
        ortografica: Boolean = false,
        alvo: String = "",
        distancia: Float = LenteNick.DEF_DISTANCIA,
        altura: Float = LenteNick.DEF_ALTURA
    ) {
        val inst = NickClassRegistry.createInstance(LenteNick.CLASSE)!!
        inst.fields[LenteNick.CAMPO_FOV] = NickValue.FloatValue(fov)
        inst.fields[LenteNick.CAMPO_PRINCIPAL] = NickValue.BoolValue(principal)
        inst.fields[LenteNick.CAMPO_ORTOGRAFICA] = NickValue.BoolValue(ortografica)
        inst.fields[LenteNick.CAMPO_ALVO] = NickValue.StringValue(alvo)
        inst.fields[LenteNick.CAMPO_DISTANCIA] = NickValue.FloatValue(distancia)
        inst.fields[LenteNick.CAMPO_ALTURA] = NickValue.FloatValue(altura)
        nick.addClassInstance(inst)
    }

    @Test
    fun principalVenceFollowEBasicaEUmaMatrizPropria() {
        val scene = Scene()
        val player = scene.createRootNick("Player")
        val follow = scene.createRootNick("Follow")
        follow.kind = "camera.follow"
        val principal = scene.createRootNick("Principal")
        principal.kind = "camera.basic"
        principal.transform.position.set(0f, 2f, 7f)
        classeLente(follow)
        classeLente(principal, fov = 75f, principal = true)
        scene.updateWorldTransforms()

        val game = NickPlayCamera(scene, player, follow, null)
        assertTrue("principal ativa a matriz própria", game.usaMatrizPropria)
        game.prepararProjecaoPropria(1.5f)
        assertEquals("fov do Nick vence", 75f, game.fovPropria, 0.001f)
        // view da principal (world→camera): translação = −eye para rotação identidade
        game.prepararProjecaoPropria(1.5f)
        assertEquals(0f, game.viewPropria.m[12], 0.001f)
        assertEquals(-2f, game.viewPropria.m[13], 0.001f)
        assertEquals(-7f, game.viewPropria.m[14], 0.001f)
        // identidade → olhando pelo −Z: o centro do mundo aparece na frente
        val p = Mat4().setPerspective(60f, 1.5f, 0.1f, 500f)
        val vp = Mat4().multiply(game.projPropria, game.viewPropria)
        val x = vp.m[0] * 0f + vp.m[4] * 0f + vp.m[8] * -3f + vp.m[12]
        val w = vp.m[3] * 0f + vp.m[7] * 0f + vp.m[11] * -3f + vp.m[15]
        assertEquals("ponto à frente fica no eixo X (centro)", 0f, x / w, 0.001f)
    }

    @Test
    fun principalOrtograficaUsaOTamanhoComoMeiaAltura() {
        val scene = Scene()
        val principal = scene.createRootNick("Orto")
        principal.kind = "camera.basic"
        classeLente(principal, principal = true, ortografica = true)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, null, null, null)
        assertTrue(game.usaMatrizPropria)
        game.prepararProjecaoPropria(1f)
        // ortográfica: m[11] = 0 (sem divisão por w) e m[5] = 1/meiaAltura
        assertEquals(0f, game.projPropria.m[11], 1e-6f)
        assertEquals(1f / LenteNick.DEF_TAMANHO_ORTO, game.projPropria.m[5], 1e-4f)
    }

    @Test
    fun followUsaDistanciaEAlturaDaClasse() {
        val scene = Scene()
        val player = scene.createRootNick("Player")
        player.transform.position.set(0f, 0.5f, 0f)
        val follow = scene.createRootNick("Follow")
        follow.kind = "camera.follow"
        classeLente(follow, distancia = 8f, altura = 5f)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, player, follow, null)
        val eye = Vec3()
        game.camera.eyePoint(eye)
        assertEquals("câmera atrás do alvo (distância 8)", 0f, eye.x, 0.001f)
        assertEquals("câmera acima do alvo (altura 5)", 5.5f, eye.y, 0.001f)
        assertEquals(8f, eye.z, 0.001f)
    }

    @Test
    fun followSegueOAlvoExplicitoDoCampoAlvo() {
        val scene = Scene()
        val player = scene.createRootNick("Player")
        val carro = scene.createRootNick("Carro")
        carro.transform.position.set(10f, 1f, 4f)
        val follow = scene.createRootNick("Follow")
        follow.kind = "camera.follow"
        classeLente(follow, alvo = carro.id.value, distancia = 3f, altura = 2f)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, player, follow, null)
        val eye = Vec3()
        game.camera.eyePoint(eye)
        assertEquals("segue o carro, NÃO o jogador", 10f, eye.x, 0.001f)
        assertEquals(3f, eye.y, 0.001f)
        assertEquals(7f, eye.z, 0.001f)
    }

    @Test
    fun fovDoNickViraAProjecaoDaCameraEmPlay() {
        val scene = Scene()
        val basic = scene.createRootNick("Camera")
        basic.kind = "camera.basic"
        classeLente(basic, fov = 90f)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, null, null, basic)
        assertEquals("fov do Nick aplicado na OrbitCamera do jogo", 90f, game.camera.fovDegrees, 0.001f)
    }

    @Test
    fun `câmera seguidora não atravessa o chão M1_4`() {
        // M1.4 (v0.1.22): o OLHO fica sempre acima dos pés do alvo (+0,35 m):
        // altura negativa/pequena não enfia a câmera dentro do chão.
        val scene = Scene()
        val player = scene.createRootNick("Player")
        player.transform.position.set(0f, 1f, 0f)
        val follow = scene.createRootNick("Follow")
        follow.kind = "camera.follow"
        val inst = NickClassRegistry.createInstance(LenteNick.CLASSE)!!
        inst.fields[LenteNick.CAMPO_ALTURA] = NickValue.FloatValue(-5f) // abaixo do chão!
        follow.addClassInstance(inst)
        scene.updateWorldTransforms()
        val game = NickPlayCamera(scene, player, follow, null)
        val eye = Vec3()
        game.camera.eyePoint(eye)
        val pe = 1f - 0.5f // pé do alvo (cubo 1 m)
        assertTrue("olho acima dos pés + folga (${eye.y} ≥ ${pe + LenteNickMatriz.FOLGA_CHAO})", eye.y >= pe + LenteNickMatriz.FOLGA_CHAO - 1e-3f)
    }
}
