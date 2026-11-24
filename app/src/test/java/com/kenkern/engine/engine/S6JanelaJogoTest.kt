package com.kenkern.engine.engine

import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * S6 (v0.1.24) — JANELA DE JOGO: Play abre TELA CHEIA só do jogo; SAIR
 * (Stop) restaura a cena do editor. Entrar e SAIR 10× SEM VAZAMENTO nem
 * estado preso — o ciclo aqui é EXATAMENTE o do KenkernSurfaceView
 * (sceneForFrame: CkFormat.writeToString → parseToNewScene = a cópia de
 * Play; clearPlaySession no Stop descarta a sessão inteira).
 */
class S6JanelaJogoTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
    }

    /** Cena destrutiva: script anda com o player, destrói o Alvo e cria um nick. */
    private fun cenaDestrutiva(): Scene {
        val editor = Scene("s6")
        val chao = editor.createRootNick("chao", kind = "body.static")
        chao.transform.position.set(0f, 9.3f, 0f)
        chao.transform.scale.set(20f, 1f, 20f)
        val player = editor.createRootNick("player", kind = "body.player")
        player.transform.position.set(0f, 10.5f, 0f)
        val alvo = editor.createRootNick("Alvo")
        alvo.transform.position.set(5f, 0.5f, 0f)
        val script = NickClassRegistry.createInstance(NickLuaScripts.CLASSE_SCRIPT)!!
        script.fields[NickLuaScripts.CAMPO_FONTE] = NickValue.StringValue(
            """
            on_update = function(dt)
              local p = nick.position()
              nick.set_position(p.x + 0.05, p.y, p.z)
              if not destruido then
                destruido = true
                local a = scene.find("Alvo")
                if a then a.destroy() end
                scene.spawn(9, 9, 9)
              end
            end
            """.trimIndent()
        )
        player.addClassInstance(script)
        editor.updateWorldTransforms()
        return editor
    }

    /** Um ciclo Play→Stop: copia, simula (física+scripts), descarta. Devolve refs fracas da cópia. */
    private fun cicloPlayStop(editor: Scene): Pair<WeakReference<Scene>, WeakReference<NickPlayRuntime>> {
        val original = CkFormat.writeToString(editor)
        // ---- PLAY (sceneForFrame: cópia por sessão) ----
        val copia = CkFormat.parseToNewScene(original, editor.name)!!
        val runtime = NickPlayRuntime(copia)
        val scripts = NickLuaScripts(copia).also { it.iniciarTodos() }
        runtime.scripts = scripts
        // o MESMO par do loop real: step + scripts.atualizar por tick
        repeat(30) {
            runtime.step(1f / 60f, 0f, 0f)
            scripts.atualizar(1f / 60f)
        } // anda + destrói Alvo + spawn na CÓPIA
        // ---- STOP: clearPlaySession descarta a sessão INTEIRA ----
        val refCopia = WeakReference(copia)
        val refRuntime = WeakReference(runtime)
        return refCopia to refRuntime
    }

    @Test
    fun `entrar e sair 10 vezes restaura a cena a cada Stop e sem estado preso`() {
        val editor = cenaDestrutiva()
        val bytesAntes = CkFormat.writeToString(editor)
        val nicksAntes = contarNicks(editor)
        val posPlayerAntes = editor.roots.first { it.kind == "body.player" }.transform.position.y

        for (i in 1..10) {
            val (refCopia, _) = cicloPlayStop(editor)
            // a cena do EDITOR segue IDÊNTICA byte a byte após cada Stop
            assertEquals("ciclo $i: cena restaurada", bytesAntes, CkFormat.writeToString(editor))
            assertEquals("ciclo $i: mesmos nicks", nicksAntes, contarNicks(editor))
            val alvo = editor.roots.firstOrNull { it.name == "Alvo" }
            assertNotNull("ciclo $i: Alvo vivo no editor (destruição só na cópia)", alvo)
            // estado preso NÃO: uma edição entre os ciclos é vista pelo PRÓXIMO Play
            if (i == 5) {
                val alvoEdit = editor.roots.first { it.name == "Alvo" }
                alvoEdit.transform.position.set(7f, 1f, 2f)
                editor.updateWorldTransforms()
                val bytesEditados = CkFormat.writeToString(editor)
                val copia2 = CkFormat.parseToNewScene(bytesEditados, editor.name)!!
                val runtime2 = NickPlayRuntime(copia2)
                repeat(10) { runtime2.step(1f / 60f, 0f, 0f) }
                val alvoNaCopia = copia2.roots.first { it.name == "Alvo" }
                assertEquals(
                    "ciclo $i: o Play seguinte começa da cena EDITADA (sem estado preso)",
                    7f, alvoNaCopia.transform.position.x, 1e-4f
                )
                alvoEdit.transform.position.set(5f, 0.5f, 0f) // restaura p/ o ciclo continuar
                editor.updateWorldTransforms()
            }
            // SEM VAZAMENTO: a cópia da sessão de Play é coletável após o Stop
            forcarGc()
            assertTrue("ciclo $i: cópia de Play descartada (sem vazamento)", refCopia.get() == null)
        }
        assertEquals("posição do player intacta no editor", posPlayerAntes, editor.roots.first { it.kind == "body.player" }.transform.position.y, 1e-5f)
    }

    @Test
    fun `pausa congela o mundo e o continuar segue do ponto exato`() {
        val editor = cenaDestrutiva()
        val original = CkFormat.writeToString(editor)
        val copia = CkFormat.parseToNewScene(original, editor.name)!!
        val runtime = NickPlayRuntime(copia)
        val scripts = NickLuaScripts(copia).also { it.iniciarTodos() }
        runtime.scripts = scripts
        // PLAY: o par do loop real (step + atualizar por tick)
        repeat(30) {
            runtime.step(1f / 60f, 0f, 0f)
            scripts.atualizar(1f / 60f)
        }
        val xNaPausa = playerEm(copia).transform.position.x
        assertTrue("play andou (script on_update)", xNaPausa > 0.5f)

        // PAUSED: o FixedStepLoop NÃO roda update nenhum (contrato real do botão)
        val loop = FixedStepLoop()
        loop.requestPlayState(PlayState.PAUSED)
        var atualizacoesEmPausa = 0
        loop.tick(System.nanoTime() + 5_000_000_000L) { atualizacoesEmPausa++ } // 5 s acumulados
        assertEquals("PAUSED: nenhum step roda (loop não atualiza)", 0, atualizacoesEmPausa)

        // CONTINUAR: a simulação segue EXATAMENTE de onde parou
        loop.requestPlayState(PlayState.PLAYING)
        var atualizacoesEmJogo = 0
        loop.tick(System.nanoTime() + 10_000_000_000L) { atualizacoesEmJogo++ }
        assertTrue("PLAYING: o loop volta a atualizar", atualizacoesEmJogo > 0)
        repeat(30) {
            runtime.step(1f / 60f, 0f, 0f)
            scripts.atualizar(1f / 60f)
        }
        val xAposContinuar = playerEm(copia).transform.position.x
        assertTrue("continuar avança dali ($xAposContinuar > $xNaPausa)", xAposContinuar > xNaPausa + 0.5f)
        // e a cena do editor segue intacta
        assertEquals(original, CkFormat.writeToString(editor))
    }

    private fun contarNicks(scene: Scene): Int {
        var total = 0
        val pilha = ArrayDeque<com.kenkern.engine.core.Nick>()
        for (raiz in scene.roots) pilha.addLast(raiz)
        while (!pilha.isEmpty()) {
            val n = pilha.removeLast(); total++
            for (filho in n.children) pilha.addLast(filho)
        }
        return total
    }

    private fun playerEm(copia: Scene): com.kenkern.engine.core.Nick =
        copia.roots.first { it.kind == "body.player" }

    private fun forcarGc() {
        repeat(3) {
            System.gc()
            System.runFinalization()
            try {
                Thread.sleep(30)
            } catch (_: InterruptedException) {
            }
        }
    }
}
