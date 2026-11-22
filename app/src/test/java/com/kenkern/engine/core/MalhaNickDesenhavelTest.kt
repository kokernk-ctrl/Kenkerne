package com.kenkern.engine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REGRA "CENA = TELA" (Bug A, v0.1.17) — teste JVM EQUIVALENTE à checagem
 * "cena = tela" do Diagnóstico: a MESMA função pura (MalhaNick.desenhavel /
 * contarNicks / contarDesenhaveisVisiveis) decide o que o viewport desenha,
 * o que o picking aceita e o que o foco enquadra. O renderer conta M
 * (nicksDesenhados) com a mesma regra; M precisa ser igual ao número de
 * Nicks visíveis com malha.
 *
 * E5 (v0.1.18) — AUXILIARES DE EDITOR: Nicks de luz/câmera/área/sinal/
 * surgimento/vazio (SEM malha) NÃO desenham o cubo — desenham o auxiliar de
 * editor (ícone, cor por grupo, tamanho constante, só no editor) e NÃO contam
 * em M. Nicks COM malha (classe Malha com path) são SEMPRE geometria, mesmo
 * com kind=nick.empty (projetos antigos/importados continuam desenhando).
 */
class MalhaNickDesenhavelTest {

    private fun nickComMalha(scene: Scene, pai: Nick, nome: String, caminho: String): Nick {
        val nick = scene.createNick(nome, pai)
        val malha = NickClassInstance(MalhaNick.CLASSE)
        malha.fields["path"] = NickValue.StringValue(caminho)
        nick.addClassInstance(malha)
        return nick
    }

    @Test
    fun folhaMeshEDesenhavelComoCuboPadrao() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        assertTrue("folha nick.mesh desenha (cubo padrão da Fase 1)", MalhaNick.desenhavel(cubo))
    }

    @Test
    fun luzEhAuxiliarNaoDesenhaCubo() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val luz = scene.createNick("Luz", mundo, kind = "light.directional")
        assertTrue("luz é AUXILIAR de editor (ícone, não cubo)", MalhaNick.ehAuxiliar(luz))
        assertFalse("auxiliar NÃO conta como malha (M da StatusBar)", MalhaNick.desenhavel(luz))
    }

    @Test
    fun tiposSemMalhaSaoAuxiliaresEOutrosNao() {
        // E5: classificação PURA por chave — luz/câmera/área/vazio = auxiliares.
        for (k in listOf(
            "nick.empty", "light.directional", "light.point", "light.spot",
            "camera.basic", "area.volume", "area.signal", "area.spawn"
        )) {
            assertTrue("'$k' é auxiliar de editor", MalhaNick.ehKindAuxiliar(k))
        }
        // corpos/containers/malha NÃO são auxiliares: body.* é geometria
        // placeholder (cubo) até o M1/FORJA darem forma própria.
        for (k in listOf("nick.group", "nick.mesh", "body.static", "body.rigid",
            "body.collider", "body.character", "body.player", "body.enemy", "body.npc")) {
            assertFalse("'$k' NÃO é auxiliar", MalhaNick.ehKindAuxiliar(k))
        }
    }

    @Test
    fun malhaImportadaVenceOTipoVazio() {
        // Projeto antigo/CK1: nick.empty COM classe Malha+path continua desenhando.
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val modelo = scene.createNick("Modelo", mundo) // kind=nick.empty por padrão
        modelo.addClassInstance(NickClassInstance(MalhaNick.CLASSE).also {
            it.fields["path"] = NickValue.StringValue("assets/models/velho.KERN")
        })
        assertFalse("temMalha vence: não é auxiliar", MalhaNick.ehAuxiliar(modelo))
        assertTrue("nick.empty COM malha importada desenha", MalhaNick.desenhavel(modelo))
    }

    @Test
    fun containerSemMalhaNaoEDesenhavel() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        assertFalse(
            "raiz 'Mundo' (container com filhos, sem malha) NÃO pode desenhar — era o cubo fantasma do Bug A",
            MalhaNick.desenhavel(mundo)
        )
    }

    @Test
    fun nickComMalhaEDesenhavelAindaQueTenhaFilhos() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val modelo = nickComMalha(scene, mundo, "Modelo", "assets/models/carro.KERN")
        scene.createNick("Filho", modelo)
        assertTrue("Nick com classe Malha desenha mesmo com filhos", MalhaNick.desenhavel(modelo))
    }

    @Test
    fun classeMalhaComPathVazioNoMeshFolhaAindaDesenhaCubo() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        // classe Malha PRESENTE mas path vazio: leitura defensiva do formato —
        // nick.mesh folha volta a ser cubo (E5: nick.empty folha seria auxiliar).
        val semPath = scene.createNick("SemPath", mundo, kind = NickKindRegistry.NICK_MESH)
        val inst = NickClassInstance(MalhaNick.CLASSE)
        inst.fields["path"] = NickValue.StringValue("")
        semPath.addClassInstance(inst)
        assertFalse(MalhaNick.ehAuxiliar(semPath))
        assertTrue("classe Malha com path vazio em nick.mesh = folha → cubo padrão", MalhaNick.desenhavel(semPath))
    }

    @Test
    fun contagemCenaPadraoN5M2() {
        // A MESMA cena da checagem "cena = tela" do Diagnóstico: Mundo >
        // [Cubo, Oculto(invisível), Grupo > Cubo3] → N=5, M esperado=2.
        val scene = Scene("cena=tela")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        val oculto = scene.createNick("Oculto", mundo, kind = NickKindRegistry.NICK_MESH)
        oculto.visible = false
        val grupo = scene.createNick("Grupo", mundo, kind = NickKindRegistry.NICK_GROUP)
        val cubo3 = scene.createNick("Cubo3", grupo, kind = NickKindRegistry.NICK_MESH)
        cubo3.transform.setLocalPosition(3f, 0.5f, 0f)

        assertEquals(5, MalhaNick.contarNicks(scene))
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun contagemReageAVisibleEActive() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val a = scene.createNick("A", mundo, kind = NickKindRegistry.NICK_MESH)
        val b = scene.createNick("B", mundo, kind = NickKindRegistry.NICK_MESH)
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))

        b.visible = false
        assertEquals("visible=false tira o Nick da contagem (e da tela)", 1, MalhaNick.contarDesenhaveisVisiveis(scene))

        a.active = false
        assertEquals("active=false também tira (o render pula nick inativo)", 0, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun auxiliarNaoContaEmM() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        scene.createNick("Luz", mundo, kind = "light.point")
        scene.createNick("Cam", mundo, kind = "camera.basic")
        scene.createNick("Area", mundo, kind = "area.volume")
        // E5/E6: M = MALHAS (2 auxiliares visíveis não entram na contagem).
        assertEquals(5, MalhaNick.contarNicks(scene))
        assertEquals(1, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun contagemMalhasImportadasVisiveis() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val m1 = nickComMalha(scene, mundo, "M1", "assets/models/a.KERN")
        val m2 = nickComMalha(scene, m1, "M2", "assets/models/b.KERN") // filho de malha: desenha
        m2.visible = false
        val grupo = scene.createNick("Grupo", mundo)
        scene.createNick("Cubo", grupo, kind = NickKindRegistry.NICK_MESH)
        // desenhaveis visíveis: M1 (malha) + Cubo (folha) = 2; M2 invisível não conta.
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))
        assertEquals(5, MalhaNick.contarNicks(scene))
    }

    @Test
    fun projetoNovoEMundoMaisCuboComUmDesenhavel() {
        // A3: projeto novo = Mundo (nick.group) + Cubo (nick.mesh) — o viewport
        // mostra SÓ o Cubo (template real do ProjectManager).
        val scene = Scene("Novo")
        val mundo = scene.createRootNick(null, kind = NickKindRegistry.NICK_GROUP)
        val cubo = scene.createNick(null, mundo, kind = NickKindRegistry.NICK_MESH)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        NickClassRegistry.createInstance("Spin")?.let { cubo.addClassInstance(it) }

        assertEquals(2, MalhaNick.contarNicks(scene))
        assertEquals("M = 1: o viewport do projeto novo mostra 1 cubo (o Cubo), não 2 nem torus", 1, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun mundoSemFilhosViraAuxiliarVisivelESelecionavel() {
        // E5: usuário apagou o Cubo → a raiz (nick.empty) é AUXILIAR agora
        // (losango, tamanho constante), não cubo — mas CONTINUA coerente com a
        // regra A5: visível no viewport, na Hierarquia e selecionável por toque.
        val scene = Scene("t")
        val mundo = scene.createRootNick(null, kind = NickKindRegistry.NICK_GROUP)
        scene.createNick(null, mundo, kind = NickKindRegistry.NICK_MESH).let { scene.removeNick(it) }
        // Um nick.group sem filhos não é auxiliar (group é container)…
        assertFalse(MalhaNick.ehAuxiliar(mundo))
        // …e um nick.empty sem filhos É auxiliar (ícone) — visível e selecionável
        // no editor, mas não é "malha".
        val vazio = scene.createNick("Vazio", null, kind = NickKindRegistry.NICK_EMPTY)
        assertTrue(MalhaNick.ehAuxiliar(vazio))
        assertFalse(MalhaNick.desenhavel(vazio))
    }
}
