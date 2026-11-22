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
 * Causas raiz do Bug A cobertas aqui:
 *  1. torus de teste fora da cena (removido do render — a checagem GL
 *     "cena" desenha o torus SÓ offscreen);
 *  2. TODO Nick visível sem malha virava cubo — INCLUSIVE a raiz "Mundo"
 *     (container): agora containers não desenham nem são pegáveis.
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
    fun folhaSemMalhaEDesenhavelComoCuboPadrao() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo)
        assertTrue("folha sem filhos desenha (cubo padrão da Fase 1)", MalhaNick.desenhavel(cubo))
    }

    @Test
    fun containerSemMalhaNaoEDesenhavel() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        scene.createNick("Cubo", mundo)
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
    fun nickComClasseMalhaSemPathNaoEDesenhavelComoMalhaMasFolhaEhCubo() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val semPath = scene.createNick("SemPath", mundo)
        val inst = NickClassInstance(MalhaNick.CLASSE)
        // classe Malha PRESENTE mas path vazio: MalhaNick.path devolve null —
        // o Nick volta a ser folha-cubo (leitura defensiva do formato).
        inst.fields["path"] = NickValue.StringValue("")
        semPath.addClassInstance(inst)
        assertTrue("classe Malha com path vazio = folha → cubo padrão", MalhaNick.desenhavel(semPath))
    }

    @Test
    fun contagemCenaPadraoN5M2() {
        // A MESMA cena da checagem "cena = tela" do Diagnóstico: Mundo >
        // [Cubo, Oculto(invisível), Grupo > Cubo3] → N=5, M esperado=2.
        val scene = Scene("cena=tela")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        val oculto = scene.createNick("Oculto", mundo)
        oculto.visible = false
        val grupo = scene.createNick("Grupo", mundo)
        val cubo3 = scene.createNick("Cubo3", grupo)
        cubo3.transform.setLocalPosition(3f, 0.5f, 0f)

        assertEquals(5, MalhaNick.contarNicks(scene))
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun contagemReageAVisibleEActive() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val a = scene.createNick("A", mundo)
        val b = scene.createNick("B", mundo)
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))

        b.visible = false
        assertEquals("visible=false tira o Nick da contagem (e da tela)", 1, MalhaNick.contarDesenhaveisVisiveis(scene))

        a.active = false
        assertEquals("active=false também tira (o render pula nick inativo)", 0, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun contagemMalhasImportadasVisiveis() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val m1 = nickComMalha(scene, mundo, "M1", "assets/models/a.KERN")
        val m2 = nickComMalha(scene, m1, "M2", "assets/models/b.KERN") // filho de malha: desenha
        m2.visible = false
        val grupo = scene.createNick("Grupo", mundo)
        scene.createNick("Cubo", grupo)
        // desenhaveis visíveis: M1 (malha) + Cubo (folha) = 2; M2 invisível não conta.
        assertEquals(2, MalhaNick.contarDesenhaveisVisiveis(scene))
        assertEquals(5, MalhaNick.contarNicks(scene))
    }

    @Test
    fun projetoNovoEMundoMaisCuboComUmDesenhavel() {
        // A3: projeto novo = Mundo + Cubo — o viewport mostra SÓ o Cubo.
        val scene = Scene("Novo")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        NickClassRegistry.createInstance("Spin")?.let { cubo.addClassInstance(it) }

        assertEquals(2, MalhaNick.contarNicks(scene))
        assertEquals("M = 1: o viewport do projeto novo mostra 1 cubo (o Cubo), não 2 nem torus", 1, MalhaNick.contarDesenhaveisVisiveis(scene))
    }

    @Test
    fun mundoSemFilhosVoltaASerCuboConsistenteComHierarquia() {
        // Usuário apagou o Cubo: Mundo vira folha → desenha como cubo (continua
        // aparecendo na Hierarquia e selecionável — regra A5 sem exceções).
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        scene.createNick("Cubo", mundo).let { scene.removeNick(it) }
        assertTrue(MalhaNick.desenhavel(mundo))
        assertEquals(1, MalhaNick.contarDesenhaveisVisiveis(scene))
    }
}
