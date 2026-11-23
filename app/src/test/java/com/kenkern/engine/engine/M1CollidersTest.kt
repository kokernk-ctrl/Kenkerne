package com.kenkern.engine.engine

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.script.LuaJScriptRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M1.2 (v0.1.21) — COLISORES + GATILHOS + EVENTOS no Lua:
 *  - esfera/cápsula do Colisor PARAM o jogador (AABB da forma, convenção do
 *    wireframe do editor);
 *  - Area Nick (area.volume) dispara on_trigger UMA VEZ na ENTRADA (borda);
 *  - parada contra corpo dispara on_collision;
 *  - gravidade básica e sem solver completo (documentado).
 */
class M1CollidersTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    private fun colisor(shape: String, tamanho: Float = 1f): NickClassInstance =
        NickClassInstance("Colisor").apply {
            fields["forma"] = NickValue.StringValue(shape)
            fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
            fields["tamanho"] = NickValue.Vec3Value(tamanho, tamanho, tamanho)
        }

    private fun nickComScript(scene: Scene, nome: String, fonte: String) =
        scene.createRootNick(nome, kind = "nick.empty").also {
            val inst = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
            inst.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(fonte)
            it.addClassInstance(inst)
        }

    /** Cena padrão: chão + jogador que anda para a direita. */
    private fun cenaComChao(): Triple<Scene, com.kenkern.engine.core.Nick, NickPlayRuntime> {
        val scene = Scene()
        val chao = scene.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = scene.createRootNick("player", kind = "body.player")
        // ACIMA do topo do chão (topo 9,8): minY = 10,0 — o cliente NÃO nasce
        // dentro do chão (nascimento dentro = atravessa, sem solver completo).
        player.transform.position.set(0f, 10.5f, 0f)
        scene.updateWorldTransforms()
        val runtime = NickPlayRuntime(scene)
        return Triple(scene, player, runtime)
    }

    @Test
    fun `parada contra a parede dispara on_collision com o nome do corpo`() {
        val (scene, player, runtime) = cenaComChao()
        val parede = scene.createRootNick("parede", kind = "body.static")
        parede.transform.position.set(3f, 10f, 0f)
        parede.transform.scale.set(1f, 2f, 8f)
        val instP = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        instP.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            "batidas = 0\non_collision = function(outro) batidas = batidas + 1 end"
        )
        player.addClassInstance(instP)
        scene.updateWorldTransforms()
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        repeat(6) { runtime.step(0.25f, 1f, 0f) }
        // parede AABB x: 2,5..3,5; jogador para com maxX = 2,5 → x = 2,0
        assertEquals(2f, player.transform.position.x, 1e-3f)
        val globals = (scripts.instanciaDe("player")?.runtime as? LuaJScriptRuntime)?.ambiente()
        assertTrue("on_collision disparou ao menos 1×", globals!!.get("batidas").toint() >= 1)
    }

    @Test
    fun `area dispara on_trigger na entrada e de novo ao voltar`() {
        val (scene, player, runtime) = cenaComChao()
        val area = scene.createRootNick("Portal", kind = "area.volume")
        area.transform.position.set(2f, 10f, 0f) // cubo de 1 m: x 1,5..2,5
        val instP = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        instP.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            "entradas = 0\non_trigger = function(outro) entradas = entradas + 1 end"
        )
        player.addClassInstance(instP)
        scene.updateWorldTransforms()
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        // caminha até DENTRO da área (2,0) e depois para DENTRO dela
        runtime.step(0.25f, 1f, 0f) // x=1,0 (fora, mas maxX=1,5 toca a borda? AABB 1,5..2,5: maxX=1,5 → overlap de face? overlaps exige >: maxX 1,5 < 2,5 e 1,5 > 1,5 falso → FORA)
        runtime.step(0.25f, 1f, 0f) // x=2,0 → DENTRO (1,5..2,5) → 1 gatilho
        val globals = (scripts.instanciaDe("player")?.runtime as? LuaJScriptRuntime)?.ambiente()
        assertEquals("1 disparo na entrada", 1, globals!!.get("entradas").toint())
        // continua dentro: NÃO dispara de novo
        runtime.step(0.25f, 0f, 0f)
        assertEquals(1, globals.get("entradas").toint())
        // sai e volta: dispara DE NOVO (borda)
        runtime.step(0.25f, -1f, 0f) // x=1,0: fora
        runtime.step(0.25f, 1f, 0f) // x=2,0: dentro de novo
        assertEquals("sair e voltar dispara de novo", 2, globals.get("entradas").toint())
    }

    @Test
    fun `area com colisor maior dispara pelo volume do colisor`() {
        val (scene, player, runtime) = cenaComChao()
        val area = scene.createRootNick("Zona", kind = "area.volume")
        area.transform.position.set(2f, 10f, 0f)
        area.addClassInstance(colisor("box", 3f)) // x 0,5..3,5
        val instP = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        instP.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            "n = 0\non_trigger = function(outro) n = n + 1 end"
        )
        player.addClassInstance(instP)
        scene.updateWorldTransforms()
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        runtime.step(0.25f, 0.6f, 0f) // x=0,6 → dentro do volume 0,5..3,5? maxX=1,1>0,5 ✓ e minX=0,1<0,5 → overlap ✓
        val globals = (scripts.instanciaDe("player")?.runtime as? LuaJScriptRuntime)?.ambiente()
        assertEquals("disparou já no primeiro passo (volume 3 m)", 1, globals!!.get("n").toint())
    }

    @Test
    fun `area inativa nao dispara`() {
        val (scene, player, runtime) = cenaComChao()
        val area = scene.createRootNick("Portal", kind = "area.volume", active = false)
        area.transform.position.set(2f, 10f, 0f)
        val instP = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        instP.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            "n = 0\non_trigger = function(outro) n = n + 1 end"
        )
        player.addClassInstance(instP)
        scene.updateWorldTransforms()
        val scripts = NickLuaScripts(scene).also { it.iniciarTodos() }
        runtime.scripts = scripts
        repeat(4) { runtime.step(0.25f, 1f, 0f) }
        val globals = (scripts.instanciaDe("player")?.runtime as? LuaJScriptRuntime)?.ambiente()
        assertEquals(0, globals!!.get("n").toint())
    }

    @Test
    fun `gravidade basica para no chao e o jogador fica apoiado`() {
        val (scene, player, runtime) = cenaComChao()
        repeat(40) { runtime.step(1f / 60f, 0f, 0f) }
        // chão: topo em 9,8; jogador apoiado com minY = 9,8 → y = 10,3
        assertEquals(10.3f, player.transform.position.y, 0.05f)
    }
}
