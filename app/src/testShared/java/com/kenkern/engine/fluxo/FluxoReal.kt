package com.kenkern.engine.fluxo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kenkern.engine.MainActivity
import com.kenkern.engine.R
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.Diagnostico
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.ui.editor.ModelosScript
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * HARNESS §3.2 — corpo COMPARTILHADO do Portão 2.
 *
 * O mesmo arquivo compila no androidTest (emulador) e no teste JVM com
 * Robolectric (alternativa quando o runner não tem KVM). Só o runner muda;
 * o conteúdo é o mesmo: abrir o app de verdade, na MainActivity, e percorrer
 * o caminho da interface (Hub → criar projeto → editor → abas → Play).
 */
object FluxoReal {

    const val NOME_PROJETO = "CI Fluxo Real"
    const val TEXTO_DIGITADO = "-- digitado pelo Portao 2"

    /** Checagens que dependem de GPU real: no emulador viram AVISO. */
    val SOMENTE_APARELHO = setOf("etc2-nativo", "etc2", "tex-cena", "luz", "astc", "glcaps")

    /** Pastas obrigatórias de todo projeto novo (F2). */
    val ARVORE = listOf(
        "assets/models", "assets/textures", "assets/materials", "assets/audio",
        "assets/fonts", "assets/video", "scenes", "nicks", "scripts", "animations",
        "shaders", "ui", "worlds", "dialogues", "build", ".kenkern"
    )

    fun vm(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>): EditorViewModel =
        androidx.lifecycle.ViewModelProvider(rule.activity)[EditorViewModel::class.java]

    fun contexto(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** a) cria o projeto PELO CAMINHO DA INTERFACE e devolve o handle. */
    fun criarProjetoPelaUi(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>, vm: EditorViewModel, comTemplate: Boolean): ProjectHandle {
        val ctx = contexto()
        // Cold start do app no emulador (swiftshader, Hub com buraco negro
        // animado) pode passar de 10 s na primeira composição — espera LONGA
        // aqui é espera pelo COMEÇO do app, não por funcionalidade.
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText(ctx.getString(R.string.hub_new_project)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(ctx.getString(R.string.hub_new_project)).onFirst().performClick()
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText(ctx.getString(R.string.hub_new_title)).fetchSemanticsNodes().isNotEmpty()
        }
        if (comTemplate) {
            rule.onNodeWithText(ctx.getString(R.string.hub_template_moedas)).performClick()
        }
        val campo = try {
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        } catch (e: AssertionError) {
            // Fallback: em temas sem a semântica de diálogo, o único campo de
            // texto da tela é o do nome (o Hub não tem outro).
            rule.onAllNodes(hasSetTextAction()).onFirst()
        }
        campo.performTextInput(NOME_PROJETO)
        rule.onNodeWithText(ctx.getString(R.string.hub_create)).performClick()
        rule.waitUntil(30_000) { vm.project.value != null }
        return vm.project.value!!
    }

    /** a) confere a árvore completa + project.kenkern. */
    fun conferirArvore(handle: ProjectHandle): List<String> {
        val faltas = ArrayList<String>()
        for (rel in ARVORE) {
            if (!File(handle.root, rel).isDirectory) faltas.add(rel)
        }
        if (!File(handle.root, ProjectManager.PROJECT_FILE).isFile) {
            faltas.add(ProjectManager.PROJECT_FILE)
        }
        return faltas
    }

    /** b) cria um script de CADA modelo pelo mesmo caminho do diálogo e abre no editor. */
    fun criarScriptsDeTodosOsModelos(vm: EditorViewModel): Map<String, File> {
        val raiz = vm.project.value?.root ?: error("projeto aberto")
        val saida = LinkedHashMap<String, File>()
        for (modelo in ModelosScript.NOMES) {
            val nome = vm.criarScriptComNome("script_$modelo.lua", modelo)
            check(nome.isNotBlank()) { "modelo '$modelo' não criou arquivo" }
            saida[modelo] = File(File(raiz, "scripts"), nome)
        }
        return saida
    }

    /** b) clica na aba Código e digita texto no editor. */
    fun abrirEditorCodigoEDigitar(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        val ctx = contexto()
        rule.onAllNodesWithText(ctx.getString(R.string.editor_aba_codigo)).onFirst().performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(TEXTO_DIGITADO)
    }

    /** c) cria UM Nick de cada kind criável pelo caminho da UI (addNickOfKind).
     * X1 (v0.1.31): kinds `ui.*` NÃO entram na cena 3D — o helper exige que a
     * cena fique INTACTA e que o elemento de UI apareça no .kui do TELAR. */
    fun criarUmNickDeCadaKind(vm: EditorViewModel): List<Pair<String, String>> {
        val s = vm.sceneRef ?: error("cena ausente")
        val criados = ArrayList<Pair<String, String>>()
        for (kind in NickKindRegistry.all().filter { it.criavel() }) {
            val antes = s.roots.size + contarDescendentes(s)
            vm.addNickOfKind(kind.key, false, null)
            if (kind.key.startsWith("ui.")) {
                // X1: cena 3D INTACTA + elemento de UI criado.
                val depois3d = s.roots.size + contarDescendentes(s)
                check(depois3d == antes) { "X1: kind '${kind.key}' ALTEROU a Hierarquia 3D ($antes → $depois3d)" }
                check(temElementoUiCriado(vm, kind.key)) {
                    "X1: kind '${kind.key}' não virou elemento de UI no .kui"
                }
                criados += kind.key to kind.label("en")
            } else {
                val depois = s.roots.size + contarDescendentes(s)
                check(depois == antes + 1) { "kind '${kind.key}' não entrou na cena ($antes → $depois)" }
                val ultimo = s.roots.lastOrNull()?.let { ultimoCriado(it) } ?: s.roots.last()
                criados += kind.key to (ultimo.name ?: kind.label("en"))
            }
        }
        return criados
    }

    /** X1: o .kui atual contém um elemento do tipo correspondente ao kind. */
    fun temElementoUiCriado(vm: EditorViewModel, kind: String): Boolean {
        val tipo = com.kenkern.engine.ui.telar.ElementoUi.de(kind) ?: return false
        val doc = vm.carregarUiDoc() ?: return false
        return doc.elementos.any { it.tipo == tipo }
    }

    /** X1: total de Nicks da árvore 3D (raízes + descendentes). */
    fun totalNicks(s: Scene): Int = s.roots.size + contarDescendentes(s)

    private fun contarDescendentes(s: Scene): Int {
        var n = 0
        for (r in s.roots) n += contarFilhos(r)
        return n
    }

    private fun contarFilhos(nick: com.kenkern.engine.core.Nick): Int {
        var n = 0
        for (f in nick.children) n += 1 + contarFilhos(f)
        return n
    }

    private fun ultimoCriado(raiz: com.kenkern.engine.core.Nick): com.kenkern.engine.core.Nick {
        var atual = raiz
        while (atual.children.isNotEmpty()) atual = atual.children.last()
        return atual
    }

    /** Volta do EDITOR DE CÓDIGO para a aba CENA: o EditorLuaScreen é tela
     * cheia ("nada do editor de cena é composto", EditorScreen.kt) — a
     * Hierarquia NÃO existe enquanto ele está aberto e nem espera nem scroll
     * a encontram (causa raiz das execuções 6, 7 e 8 do Portão 2). A seta de
     * voltar do editor fecha SOMENTE a tela dele (alvoEditorLua = null);
     * na Cena a MESMA seta sai do projeto, então o clique é válido apenas
     * com o editor de código aberto. */
    fun voltarDaAbaCodigoParaACena(rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
        val ctx = contexto()
        rule.onNodeWithContentDescription(ctx.getString(R.string.editor_back_hub)).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(ctx.getString(R.string.editor_aba_cena)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** c) o Nick criado aparece na Hierarquia (painel montado da cena).
     * Duas causas raiz já provadas no CI:
     *   * execuções 6/7/8 — o editor de código é TELA CHEIA e esconde o
     *     painel: voltar à Cena é obrigatório (voltarDaAbaCodigoParaACena);
     *   * execução 10 — performScrollToNode na RAIZ do app falha com
     *     "Semantic Node has no parent layout with a Scroll SemanticsAction":
     *     a raiz NÃO está dentro de nenhum scrollable. O âncora do scroll é a
     *     PRIMEIRA LINHA da lista (primeira raiz da cena, visível na posição
     *     0 da LazyColumn), que tem o scrollable da Hierarquia como ancestral;
     *     com 17+ Nicks o último está fora da vista e esperar sozinho nunca
     *     o vê (nó fora da viewport não existe na semântica). */
    fun conferirNaHierarquia(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        vm: EditorViewModel,
        nome: String
    ) {
        // nickDisplayName é @Composable (LocalContext) — fora da composição o
        // MESMO texto vem de Nick.displayName + AppLanguage.currentTag.
        val primeiraLinha = vm.sceneRef?.roots?.firstOrNull()
            ?.let { it.displayName(com.kenkern.engine.ui.AppLanguage.currentTag(contexto())) }
        if (primeiraLinha != null) {
            val ancora = rule.onAllNodesWithText(primeiraLinha, substring = true)
            if (ancora.fetchSemanticsNodes().isNotEmpty()) {
                ancora.onFirst().performScrollToNode(hasText(nome, substring = true))
            }
        }
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText(nome, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** d) Play por 120 ticks no template Coletar moedas, sem GL. */
    fun jogar120Ticks(cena: Scene): Pair<Int, String?> {
        val r = Diagnostico.avaliarJogo(cena, 120)
        return r.moedasColetadas to r.falha
    }

    /** d) liga e desliga o Play pelo estado real (o mesmo que o botão usa). */
    fun ligarEDesligarPlay(vm: EditorViewModel) {
        vm.setPlayState(PlayState.PLAYING)
        check(vm.playState.value == PlayState.PLAYING) { "Play não entrou em PLAYING" }
        Thread.sleep(1_200)
        vm.setPlayState(PlayState.STOPPED)
        check(vm.playState.value == PlayState.STOPPED) { "Play não voltou a STOPPED" }
    }

    /** f) salva, fecha, reabrir e compara a cena serializada. */
    fun salvarFecharReabrir(vm: EditorViewModel): Pair<String, String> {
        val handle = vm.project.value ?: error("projeto ausente")
        vm.saveNow()
        val antes = CkFormat.writeToString(vm.sceneRef ?: error("cena ausente"))
        vm.exitProject()
        check(vm.project.value == null) { "projeto não fechou" }
        vm.openProject(handle)
        val t0 = System.currentTimeMillis()
        while (vm.project.value == null && System.currentTimeMillis() - t0 < 20_000) Thread.sleep(100)
        check(vm.project.value != null) { "projeto não reabriu" }
        return antes to CkFormat.writeToString(vm.sceneRef ?: error("cena ausente após reabrir"))
    }

    /** g) roda TODAS as checagens do Diagnóstico e devolve o status final de cada uma. */
    fun rodarDiagnostico(ctx: Context): Map<String, Pair<Diagnostico.Status, String>> {
        val saida = LinkedHashMap<String, Pair<Diagnostico.Status, String>>()
        runBlocking {
            Diagnostico(ctx).executar { c ->
                if (c.status != Diagnostico.Status.PENDENTE && c.status != Diagnostico.Status.RODANDO) {
                    saida[c.id] = c.status to c.detalhe
                }
            }
        }
        return saida
    }

    /** Captura de tela do emulador (no Robolectric não há tela: ignora). */
    fun capturarTela(ctx: Context, nome: String) {
        try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation ?: return
            val bmp: Bitmap = automation.takeScreenshot() ?: return
            val dir = File(ctx.getExternalFilesDir(null), "screenshots")
            dir.mkdirs()
            FileOutputStream(File(dir, "$nome.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            android.util.Log.w("Portao2", "captura '$nome' ignorada: ${e.message}")
        }
    }

    /** e) física: Rigid Body acima de um Static Body com colisor filho. */
    fun rigidezCaiAteOChao(cena: Scene): Boolean {
        val copia = CkFormat.parseToNewScene(CkFormat.writeToString(cena), cena.name) ?: return false
        val runtime = com.kenkern.engine.engine.NickPlayRuntime(copia)
        val rigido = copia.roots.firstOrNull { it.kind == "body.rigid" } ?: return false
        val y0 = rigido.transform.worldMatrix.m[13]
        repeat(120) { runtime.step(1f / 60f, 0f, 0f) }
        val y1 = rigido.transform.worldMatrix.m[13]
        return y1 < y0 - 0.01f
    }
}
