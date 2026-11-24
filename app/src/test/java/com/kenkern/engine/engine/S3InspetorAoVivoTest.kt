package com.kenkern.engine.engine

import com.kenkern.engine.commands.SetClassFieldCommand
import com.kenkern.engine.commands.TransformCommand
import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.render.LumeCena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S3 (v0.1.24) — INSPETOR AO VIVO: toda mudança de campo (transform, luz,
 * câmera, colisor, parâmetros do script) aplica no MESMO frame no viewport,
 * no editor E EM PLAY.
 *
 * CAUSA RAIZ (em Play): o viewport renderiza a CÓPIA de Play
 * (KenkernSurfaceView.sceneForFrame, v0.1.22: "Uma cópia por sessão"), mas
 * os comandos do Inspetor (EditorViewModel.run → TransformCommand/
 * SetClassFieldCommand…) escrevem SÓ na cena do EDITOR — a edição NUNCA
 * chega à cópia renderizada enquanto o Play roda (nada muda na tela).
 *
 * CORREÇÃO: EspelhoNick — depois de cada comando de campo/transform, o
 * ViewModel espelha o estado do Nick (transform + classes + flags + nome)
 * para a cópia de Play no MESMO frame (via queueEvent do GL thread).
 */
class S3InspetorAoVivoTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    private fun cenaComLuz(): Pair<Scene, com.kenkern.engine.core.Nick> {
        val scene = Scene("s3")
        val luz = scene.createRootNick("Luz", kind = "light.point")
        luz.addClassInstance(NickClassRegistry.createInstance("Luz")!!)
        scene.updateWorldTransforms()
        return scene to luz
    }

    /** O MESMO caminho do renderer por frame: world transforms + LumeCena.coletar. */
    private fun correnteDoFrame(scene: Scene): LumeCena.Frame {
        scene.updateWorldTransforms()
        val frame = LumeCena.Frame()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        return frame
    }

    @Test
    fun `editor - transform, escala e cor da luz chegam ao frame no mesmo instante`() {
        val (scene, luz) = cenaComLuz()
        val id = luz.id

        // Comandos EXATOS do ViewModel (Inspetor): posição + escala…
        TransformCommand(
            scene, id,
            TransformSnapshot(1f, 2f, 3f, 0f, 0f, 0f, 1f, 2f, 2f, 2f)
        ).execute()
        // …e cor da luz (campo da classe Luz) → vermelho, intensidade 2.
        SetClassFieldCommand(scene, id, "Luz", "cor", NickValue.Vec3Value(1f, 0f, 0f)).execute()
        SetClassFieldCommand(scene, id, "Luz", "intensidade", NickValue.FloatValue(2f)).execute()

        val frame = correnteDoFrame(scene)
        assertTrue("luz pontual coletada", frame.nPontuais >= 1)
        // posição MUNDIAL nova no MESMO frame
        assertEquals(1f, frame.pos[0], 1e-4f)
        assertEquals(2f, frame.pos[1], 1e-4f)
        assertEquals(3f, frame.pos[2], 1e-4f)
        // cor × intensidade (vermelho × 2)
        assertEquals(2f, frame.corLs[0], 1e-4f)
        assertEquals(0f, frame.corLs[1], 1e-4f)
        assertEquals(0f, frame.corLs[2], 1e-4f)
        // escala reflete na matriz de mundo (m[0] = 2)
        assertEquals(2f, luz.transform.worldMatrix.m[0], 1e-4f)
    }

    @Test
    fun `reproducao - em Play a edicao do Inspetor NAO chega a copia renderizada`() {
        val (editor, luz) = cenaComLuz()
        val id = luz.id

        // A cópia de Play EXATAMENTE como KenkernSurfaceView.sceneForFrame
        // cria (CkFormat.writeToString → parseToNewScene).
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!

        // O ViewModel de hoje: os comandos escrevem SÓ na cena do editor.
        TransformCommand(editor, id, TransformSnapshot(5f, 6f, 7f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)).execute()
        SetClassFieldCommand(editor, id, "Luz", "cor", NickValue.Vec3Value(0f, 0f, 1f)).execute()

        // A tela em Play renderiza A CÓPIA — o que o renderer realmente usa:
        val frameCopia = correnteDoFrame(copia)
        // REPRODUÇÃO: a posição e a cor na cópia continuam as ANTIGAS.
        val posAntiga = frameCopia.pos[0] == 0f && frameCopia.pos[1] == 0f && frameCopia.pos[2] == 0f
        assertTrue("REPRODUZ: a cópia renderizada não recebeu a edição (posição antiga)", posAntiga)
        assertTrue("REPRODUZ: cor antiga na cópia (branca × 1)", frameCopia.corLs[0] == 1f)
    }

    @Test
    fun `correcao - EspelhoNick aplica a edicao na copia no mesmo frame`() {
        val (editor, luz) = cenaComLuz()
        val id = luz.id
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!

        // comandos no EDITOR (persistência)…
        TransformCommand(editor, id, TransformSnapshot(5f, 6f, 7f, 0f, 0f, 0f, 1f, 2f, 1f, 1f)).execute()
        SetClassFieldCommand(editor, id, "Luz", "cor", NickValue.Vec3Value(0f, 0f, 1f)).execute()
        SetClassFieldCommand(editor, id, "Luz", "intensidade", NickValue.FloatValue(3f)).execute()

        // …e o ESPELHO para a cópia (o que o ViewModel agora faz depois do comando)
        assertTrue(EspelhoNick.aplicar(EspelhoNick.de(luz), copia))

        val frameCopia = correnteDoFrame(copia)
        assertEquals("posição nova NA CÓPIA (mesmo frame)", 5f, frameCopia.pos[0], 1e-4f)
        assertEquals(6f, frameCopia.pos[1], 1e-4f)
        assertEquals(7f, frameCopia.pos[2], 1e-4f)
        assertEquals("cor nova × intensidade nova", 0f, frameCopia.corLs[0], 1e-4f)
        assertEquals(0f, frameCopia.corLs[1], 1e-4f)
        assertEquals(3f, frameCopia.corLs[2], 1e-4f)
        // escala espelhada na cópia
        val luzCopia = copia.findNick(id)!!
        assertEquals(2f, luzCopia.transform.worldMatrix.m[0], 1e-4f)
    }

    @Test
    fun `espelho - nick ausente na copia nao crasha e devolve falso`() {
        val (editor, luz) = cenaComLuz()
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!
        // nick criado DEPOIS no editor não existe na cópia (nasceu antes do Play)
        val novo = editor.createRootNick("Novo", kind = "light.point")
        assertEquals(false, EspelhoNick.aplicar(EspelhoNick.de(novo), copia))
    }

    @Test
    fun `espelho - campo de classe ausente na copia nao crasha`() {
        val (editor, luz) = cenaComLuz()
        // cena com classe JOYSTICK que o editor tem e a cópia não (classe
        // adicionada depois do Play — o espelho simplesmente não acha e segue)
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!
        val inst = NickClassRegistry.createInstance("Joystick")!!
        luz.addClassInstance(inst)
        assertTrue(EspelhoNick.aplicar(EspelhoNick.de(luz), copia))
    }

    @Test
    fun `espelho - transform de player em play continua da nova posicao`() {
        // Edição de transform em Play deve afetar o CORPO que a física usa
        // (a cópia é o que o runtime de física também lê).
        val editor = Scene("s3fisica")
        val chao = editor.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = editor.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10.5f, 0f)
        editor.updateWorldTransforms()
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(editor), editor.name)!!

        // usuário edita a POSIÇÃO do player para longe do chão durante o Play
        player.transform.position.set(3f, 12f, 0f)
        assertTrue(EspelhoNick.aplicar(EspelhoNick.de(player), copia))
        copia.updateWorldTransforms()

        val runtime = NickPlayRuntime(copia)
        repeat(120) { runtime.step(1f / 60f, 0f, 0f) }
        assertEquals("player pousou a partir da posição EDITADA (3, 12)", 10.3f, playerEm(copia).transform.position.y, 0.05f)
    }

    private fun playerEm(copia: Scene): com.kenkern.engine.core.Nick =
        copia.roots.first { it.kind == "body.player" }
}
