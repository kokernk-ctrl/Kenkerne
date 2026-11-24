package com.kenkern.engine.core

import com.kenkern.engine.engine.NickPlayRuntime
import com.kenkern.engine.render.LumeCena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S4 (v0.1.24) — TABELA DE TODO KIND IMPLEMENTADO: cria um Nick de CADA tipo
 * registrado e confere (a) a REPRESENTAÇÃO no viewport, (b) as CLASSES
 * PADRÃO e (c) o que FAZ EM PLAY — [KindEmPlay] é a fonte única; NICKS.md
 * espelha as mesmas chaves.
 *
 * REPRODUÇÃO S4b: o Joystick Nick (ui.*) desenhava o CUBO 3D no viewport
 * (MalhaNick.desenhavel devolvia true para folha ui.* — ehKindAuxiliar não
 * cobria ui.*). Corrigido: ui.* = ELEMENTO 2D — nunca malha 3D, nunca cubo,
 * nunca auxiliar 3D; no editor vira MOLDURA 2D de tela.
 */
class NickKindTabelaTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    /** Cria um Nick de CADA kind registrado, numa única cena. */
    private fun cenaComTodosOsKinds(): Scene {
        val scene = Scene("tabela")
        for (def in NickKindRegistry.all()) {
            val nick = scene.createRootNick("n-${def.key}", kind = def.key)
            // aplica as classes padrão exatamente como o CreateNickCommand
            for (classe in def.defaultClasses) {
                val inst = NickClassRegistry.createInstance(classe)
                if (inst != null) nick.addClassInstance(inst)
            }
        }
        scene.updateWorldTransforms()
        return scene
    }

    @Test
    fun `todo kind registrado tem representacao, classes padrao e comportamento em play`() {
        val kinds = NickKindRegistry.all()
        assertTrue("catálogo vazio", kinds.isNotEmpty())
        for (def in kinds) {
            val representacao = KindEmPlay.representacaoViewport(def.key)
            assertNotEquals(
                "kind '${def.key}' sem classificação de viewport", "—", representacao
            )
            // toda classificação é uma das 4 conhecidas
            assertTrue(
                "representação desconhecida para '${def.key}': $representacao",
                representacao == "malha-cubo" || representacao == "auxiliar" ||
                    representacao == "elemento-2d" || representacao == "container"
            )
            assertNotEquals(
                "kind '${def.key}' sem comportamento de Play documentado",
                "—", KindEmPlay.comportamentoEmPlay(def.key)
            )
            assertEquals(
                "classes padrão da tabela = registro ('${def.key}')",
                def.defaultClasses, KindEmPlay.classesPadrao(def.key)
            )
        }
    }

    @Test
    fun `joystick ui nunca desenha malha 3d nem cubo nem auxiliar 3d`() {
        val scene = cenaComTodosOsKinds()
        val joystick = scene.roots.first { it.kind == "ui.joystick" }
        // REPRODUÇÃO do bug S4b: antes da correção desenhavel(ui.joystick)=true
        // → o viewport desenhava o CUBO 3D para um controle de TELA.
        assertFalse("ui.joystick NÃO é desenhável (nunca malha 3D/cubo)", MalhaNick.desenhavel(joystick))
        assertFalse("ui.joystick NÃO é auxiliar 3D", MalhaNick.ehAuxiliar(joystick))
        assertTrue("ui.joystick é ELEMENTO 2D de tela", MalhaNick.ehKindElemento2D(joystick.kind))
        assertEquals("elemento-2d", KindEmPlay.representacaoViewport("ui.joystick"))
        // classe padrão Joystick aplicada (tamanho/zona morta editáveis)
        assertTrue(
            "ui.joystick nasce com classe Joystick",
            KindEmPlay.classesPadrao("ui.joystick").contains("Joystick")
        )
    }

    @Test
    fun `kinds de luz camera area vazio sao auxiliares de editor sem cubo`() {
        val scene = cenaComTodosOsKinds()
        for (kind in listOf("light.point", "light.spot", "light.directional", "camera.basic", "camera.follow", "area.volume", "area.spawn", "nick.empty")) {
            val nick = scene.roots.first { it.kind == kind }
            assertTrue("$kind é auxiliar", MalhaNick.ehAuxiliar(nick))
            assertFalse("$kind não desenha cubo", MalhaNick.desenhavel(nick))
            assertEquals("auxiliar", KindEmPlay.representacaoViewport(kind))
        }
    }

    @Test
    fun `kinds com geometria desenham malha ou cubo`() {
        val scene = cenaComTodosOsKinds()
        for (kind in listOf("nick.mesh", "body.player", "body.rigid", "body.character", "body.collider", "body.static", "body.enemy", "body.npc")) {
            val nick = scene.roots.first { it.kind == kind }
            assertTrue("$kind desenha (malha importada ou cubo)", MalhaNick.desenhavel(nick))
            assertEquals("malha-cubo", KindEmPlay.representacaoViewport(kind))
        }
        // container não desenha
        val grupo = scene.roots.first { it.kind == NickKindRegistry.NICK_GROUP }
        assertFalse("nick.group não desenha", MalhaNick.desenhavel(grupo))
    }

    @Test
    fun `classes padrao — corpos ganham Colisor, luzes Luz, camaras Lente`() {
        assertEquals(listOf("Colisor"), KindEmPlay.classesPadrao("body.player"))
        assertEquals(listOf("Colisor"), KindEmPlay.classesPadrao("body.rigid"))
        assertEquals(listOf("Colisor"), KindEmPlay.classesPadrao("body.character"))
        assertEquals(listOf("Colisor"), KindEmPlay.classesPadrao("body.collider"))
        assertEquals(listOf("Colisor"), KindEmPlay.classesPadrao("body.static"))
        assertEquals(listOf("Luz"), KindEmPlay.classesPadrao("light.point"))
        assertEquals(listOf("Luz"), KindEmPlay.classesPadrao("light.directional"))
        assertEquals(listOf("Luz"), KindEmPlay.classesPadrao("light.spot"))
        assertEquals(listOf("Lente"), KindEmPlay.classesPadrao("camera.basic"))
        assertEquals(listOf("Lente"), KindEmPlay.classesPadrao("camera.follow"))
        assertEquals(listOf("Joystick"), KindEmPlay.classesPadrao("ui.joystick"))
        assertTrue(KindEmPlay.classesPadrao("nick.mesh").isEmpty())
        assertTrue(KindEmPlay.classesPadrao("nick.group").isEmpty())
    }

    @Test
    fun `quem entra na simulacao de play — os 4 corpos dinâmicos, os outros não`() {
        val scene = cenaComTodosOsKinds()
        val runtime = NickPlayRuntime(scene)
        val simulados = runtime.corposSimulados.map { it.kind }.toSet()
        assertEquals(
            "exatamente os corpos dinâmicos entram na simulação",
            KindEmPlay.corposDinamicos, simulados
        )
        // corpos NÃO dinâmicos existem na cena e não caem
        assertTrue(scene.roots.any { it.kind == "body.enemy" })
        assertTrue(scene.roots.any { it.kind == "body.npc" })
    }

    @Test
    fun `luz do kind light point coletada pelo mesmo caminho do viewport`() {
        // S4c: a luz do Nick ILUMINA O EDITOR — o caminho é o MESMO
        // renderFrame usado pelo viewport (LumeCena.coletar por frame, sem
        // guarda de emJogo; a checagem 'luz' do Diagnóstico usa o MESMO
        // renderer.renderFrame offscreen). Se a checagem passa no aparelho,
        // a luz do editor funciona — mesma código-caminho.
        val scene = cenaComTodosOsKinds()
        val luz = scene.roots.first { it.kind == "light.point" }
        val inst = luz.findClassInstance("Luz")!!
        inst.fields["intensidade"] = NickValue.FloatValue(2f)
        inst.fields["cor"] = NickValue.Vec3Value(1f, 0f, 0f)
        scene.updateWorldTransforms()
        val frame = LumeCena.Frame()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        // a cena tem light.point E light.spot — as DUAS pontuais/spot coletadas
        assertEquals("point + spot coletadas no frame", 2, frame.nPontuais)
        // a luz que editamos (vermelha × 2) está no conjunto do frame
        var achouVermelha = false
        for (i in 0 until frame.nPontuais) {
            if (frame.corLs[i * 3] == 2f && frame.corLs[i * 3 + 1] == 0f && frame.corLs[i * 3 + 2] == 0f) {
                achouVermelha = true
            }
        }
        assertTrue("cor vermelha × intensidade 2 no frame do MESMO caminho do viewport", achouVermelha)
    }
}
