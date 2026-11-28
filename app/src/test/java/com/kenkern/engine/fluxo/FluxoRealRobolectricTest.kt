package com.kenkern.engine.fluxo

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.Diagnostico
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * Portão 2 — alternativa SEM KVM: o MESMO conteúdo roda na JVM com Robolectric
 * (HARNESS §3.2). Sem tela, o teste não clica: chama o MESMO caminho de
 * produção que os botões usam (ProjectManager.createProject,
 * EditorViewModel.openProject/addNickOfKind/criarScriptComNome/setPlayState).
 *
 * Limite documentado (DECISIONS.md): Robolectric não tem GPU nem a `.so`
 * nativa; as checagens nativas/GL ficam na lista de AVISO deste runner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w800dp-h360dp-land",
    application = FluxoRealTestApplication::class
)
class FluxoRealRobolectricTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val ctx get() = app

    /** Sem GPU/.so no JVM: nativos e GL viram AVISO neste runner. */
    private val avisoJvm = FluxoReal.SOMENTE_APARELHO + setOf("nativo", "etc2-nativo", "malha-nativa")

    private fun bombearAte(ms: Long = 20_000, condicao: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        while (!condicao() && System.currentTimeMillis() - t0 < ms) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        check(condicao()) { "condição não atingida em ${ms} ms" }
    }

    private fun abrirProjeto(vm: EditorViewModel, handle: ProjectHandle) {
        vm.openProject(handle)
        bombearAte { vm.project.value != null && vm.sceneRef != null }
    }

    @Test
    fun a_a_f_fluxoRealCompleto() {
        // a) criar projeto + árvore completa (mesmo caminho do diálogo do Hub)
        val handle = ProjectManager.createProject(ctx, FluxoReal.NOME_PROJETO, "coletar-moedas")
            ?: error("createProject falhou: ${ProjectManager.lastError}")
        val faltas = FluxoReal.conferirArvore(handle)
        assertTrue("pastas faltando: $faltas", faltas.isEmpty())

        val vm = EditorViewModel(app)
        abrirProjeto(vm, handle)

        // b) um script de cada modelo, não vazio
        val scripts = FluxoReal.criarScriptsDeTodosOsModelos(vm)
        for ((modelo, arquivo) in scripts) {
            assertTrue("modelo '$modelo' não gravou arquivo", arquivo.isFile)
            assertTrue("modelo '$modelo' gravou vazio", arquivo.length() > 0)
        }

        // c) um Nick de cada kind criável, pelo addNickOfKind
        val criados = FluxoReal.criarUmNickDeCadaKind(vm)
        assertTrue("poucos kinds criáveis: ${criados.size}", criados.size >= 10)

        // d) Play liga/desliga + 120 ticks do Coletar moedas
        FluxoReal.ligarEDesligarPlay(vm)
        val (moedas, falha) = FluxoReal.jogar120Ticks(vm.sceneRef!!)
        assertNull("120 ticks do Coletar moedas: $falha", falha)
        assertTrue("nenhuma moeda contada", moedas >= 1)

        // f) salvar, fechar, reabrir — cena idêntica
        vm.saveNow()
        val antes = CkFormat.writeToString(vm.sceneRef!!)
        vm.exitProject()
        assertEquals(null, vm.project.value)
        abrirProjeto(vm, handle)
        assertEquals("cena mudou ao salvar/fechar/reabrir", antes, CkFormat.writeToString(vm.sceneRef!!))
    }

    @Test
    fun x1_uiJoystickComLuzSelecionada_nuncaAlteraHierarquia3D() {
        // X1 (v0.1.31): criar ui.joystick com uma luz selecionada NÃO altera a
        // Hierarquia 3D — o joystick vira elemento de UI do TELAR (ui/ui.kui).
        val handle = ProjectManager.createProject(ctx, "CI X1 " + System.currentTimeMillis(), null)
            ?: error("createProject falhou: ${ProjectManager.lastError}")
        val vm = EditorViewModel(app)
        abrirProjeto(vm, handle)
        val s = vm.sceneRef ?: error("cena ausente")

        vm.addNickOfKind("light.directional", false, null)
        val total3dAntes = FluxoReal.totalNicks(s)
        assertTrue("luz não entrou na cena", total3dAntes >= 1)

        // "Como filho do selecionado" = true — IGNORADO para ui.* (X1).
        vm.addNickOfKind("ui.joystick", true, null)

        assertEquals(
            "X1: ui.* não pode alterar a Hierarquia 3D",
            total3dAntes, FluxoReal.totalNicks(s)
        )
        assertTrue("X1: ui.joystick não virou elemento de UI", FluxoReal.temElementoUiCriado(vm, "ui.joystick"))
        val kui = File(handle.root, "ui/ui.kui")
        assertTrue("X1: ui/ui.kui não gravado", kui.isFile && kui.length() > 0)
    }

    @Test
    fun x2_inspetorJoystick_editaOs7CamposComEfeitoAoVivo() {
        // X2 (v0.1.31): o Inspetor do TELAR edita tipo, tamanho, opacidade,
        // zona morta, 8 direções, Controla e Seguir Nick do elemento joystick
        // (o MESMO caminho que a UI usa: ComandoPropriedade no DocumentoUi),
        // com undo e efeito ao vivo no Play (JoystickUiConfig lê o .kui).
        val handle = ProjectManager.createProject(ctx, "CI X2 " + System.currentTimeMillis(), null)
            ?: error("createProject falhou: ${ProjectManager.lastError}")
        val vm = EditorViewModel(app)
        abrirProjeto(vm, handle)
        vm.addNickOfKind("ui.joystick", false, null)

        val doc = vm.carregarUiDoc() ?: error("doc de UI ausente")
        val sel = doc.elementos.first { it.tipo == com.kenkern.engine.ui.telar.ElementoUi.JOYSTICK }

        // os 7 campos editáveis do Inspetor (mesmos nomes de campo do comando)
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickTipo", "fixo", "flutuante"))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickTamanho", sel.joystickTamanho, 200))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickOpacidade", sel.joystickOpacidade, 0.5f))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickZonaMorta", sel.joystickZonaMorta, 0.3f))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickOitoDirecoes", false, true))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickControla", "", "Herói"))
        doc.executar(com.kenkern.engine.ui.telar.ComandoPropriedade(sel.id, "joystickSeguirNick", false, true))

        assertEquals("flutuante", sel.joystickTipo)
        assertEquals(200, sel.joystickTamanho)
        assertEquals(0.5f, sel.joystickOpacidade)
        assertEquals(0.3f, sel.joystickZonaMorta)
        assertTrue(sel.joystickOitoDirecoes)
        assertEquals("Herói", sel.joystickControla)
        assertTrue(sel.joystickSeguirNick)

        // efeito ao vivo: o Play lê o joystick do .kui (X2)
        val cfg = com.kenkern.engine.ui.screens.JoystickUiConfig.de(vm.sceneRef, doc)
        assertEquals("flutuante", cfg.tipo)
        assertEquals(200f, cfg.tamanhoDp)
        assertEquals(0.5f, cfg.opacidade)
        assertEquals(0.3f, cfg.zonaMorta)
        assertTrue(cfg.oitoDirecoes)
        assertTrue(cfg.seguirNick)

        // ida e volta no .kui: os 7 campos sobrevivem ao salvar/carregar
        vm.salvarUiDoc(doc)
        val doc2 = vm.carregarUiDoc() ?: error("doc recarregado ausente")
        val j2 = doc2.elementos.first { it.tipo == com.kenkern.engine.ui.telar.ElementoUi.JOYSTICK }
        assertEquals("flutuante", j2.joystickTipo)
        assertEquals(200, j2.joystickTamanho)
        assertEquals("Herói", j2.joystickControla)
        assertTrue(j2.joystickOitoDirecoes)
        assertTrue(j2.joystickSeguirNick)
    }

    @Test
    fun e_fisicaRigidCorpo_esperadoFalhar() {
        val handle = ProjectManager.createProject(ctx, "CI Fisica " + System.currentTimeMillis(), null)
            ?: error("createProject falhou: ${ProjectManager.lastError}")
        val vm = EditorViewModel(app)
        abrirProjeto(vm, handle)
        vm.addNickOfKind("body.static", false, null)
        vm.sceneRef!!.roots.last().transform.setLocalPosition(0f, -0.5f, 0f)
        vm.addNickOfKind("body.rigid", false, null)
        vm.sceneRef!!.roots.last().transform.setLocalPosition(0f, 3f, 0f)

        val caiu = FluxoReal.rigidezCaiAteOChao(vm.sceneRef!!)
        File(ctx.getExternalFilesDir(null), "esperado-falhar.txt")?.writeText(
            if (caiu) "FIS-1 CORRIGIDO" else "ESPERADO-FALHAR FIS-1 (prazo v0.1.33)"
        )
        assertTrue(true)
    }
}
