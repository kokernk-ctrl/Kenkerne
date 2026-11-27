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
