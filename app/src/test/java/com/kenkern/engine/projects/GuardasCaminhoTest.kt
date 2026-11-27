package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F1/F2 (v0.1.30) — GUARDAS DE CAMINHO + ESTRUTURA VISÍVEL DE PROJETO.
 *
 * F1: caminho SEMPRE dentro da raiz (bloqueio de ".."), nomes válidos,
 * espaço livre e CÓPIA VERIFICADA (o "mover para o Aparelho" mantém o
 * original até confirmação — copiaVerificada é o que decide se a cópia
 * presta).
 *
 * F2: árvore completa ao criar (assets/{models,textures,materials,audio,
 * fonts,video}, scenes, nicks, scripts, animations, shaders, ui, worlds,
 * dialogues, build, .kenkern) e PROJETOS ANTIGOS recebem o que FALTA na
 * abertura — sem apagar NADA.
 */
class GuardasCaminhoTest {

    private fun dirTemp(): File =
        File.createTempFile("kenkern-f2", "").let { f -> f.delete(); f.mkdirs(); f }

    // ------------------------------ F2: árvore ------------------------------

    @Test
    fun `árvore completa ao criar tem as pastas exigidas`() {
        val raiz = dirTemp()
        try {
            GuardasCaminho.garantirArvore(raiz)
            for (pasta in GuardasCaminho.ARVORE_PROJETO) {
                assertTrue("pasta '$pasta' não existe", File(raiz, pasta).isDirectory)
            }
            // as subpastas de assets exigidas pela tarefa
            for (sub in listOf("models", "textures", "materials", "audio", "fonts", "video")) {
                assertTrue("assets/$sub", File(raiz, "assets/$sub").isDirectory)
            }
            // o .kenkern (cache e lixeira) existe
            assertTrue(File(raiz, ".kenkern").isDirectory)
            assertTrue(GuardasCaminho.arvoreCompleta(raiz))
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `projeto ANTIGO ganha as pastas que faltam sem apagar nada`() {
        val raiz = dirTemp()
        try {
            // estrutura de um projeto v0.1.29 (sem dialogues, sem .kenkern,
            // sem assets/models…, com um arquivo DO USUÁRIO dentro)
            File(raiz, "assets").mkdirs()
            File(raiz, "scenes").mkdirs()
            val cena = File(raiz, "scenes/cena.ck")
            cena.writeText("# cena antiga")
            File(raiz, "minhapasta").mkdirs()
            File(raiz, "minhaposta.txt").writeText("conteúdo do usuário")

            GuardasCaminho.garantirArvore(raiz) // migração (ensureStructure)

            assertTrue("assets/models criada", File(raiz, "assets/models").isDirectory)
            assertTrue("dialogues criada", File(raiz, "dialogues").isDirectory)
            assertTrue(".kenkern criada", File(raiz, ".kenkern").isDirectory)
            // NADA apagado
            assertEquals("# cena antiga", cena.readText())
            assertTrue(File(raiz, "minhapasta").isDirectory)
            assertEquals("conteúdo do usuário", File(raiz, "minhaposta.txt").readText())
            assertTrue(GuardasCaminho.arvoreCompleta(raiz))
        } finally {
            raiz.deleteRecursively()
        }
    }

    // ------------------------------ F1: guardas ------------------------------

    @Test
    fun `caminhoDentro aceita o que é dentro e bloqueia 'ponto ponto' e absolutos`() {
        val raiz = dirTemp()
        try {
            assertTrue(GuardasCaminho.caminhoDentro(raiz, "scripts"))
            assertTrue(GuardasCaminho.caminhoDentro(raiz, "assets/models"))
            assertTrue(GuardasCaminho.caminhoDentro(raiz, "assets/models/../audio"))
            assertFalse("vazio", GuardasCaminho.caminhoDentro(raiz, ""))
            assertFalse("absoluto", GuardasCaminho.caminhoDentro(raiz, "/etc/passwd"))
            assertFalse("escape ..", GuardasCaminho.caminhoDentro(raiz, "../outro"))
            assertFalse("escape no meio", GuardasCaminho.caminhoDentro(raiz, "assets/../../outro"))
            assertFalse("backslash escape", GuardasCaminho.caminhoDentro(raiz, "assets\\..\\..\\x"))
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `nomeValido aceita os nomes sanidos e rejeita os inválidos`() {
        assertTrue(GuardasCaminho.nomeValido("mm"))
        assertTrue(GuardasCaminho.nomeValido("Meu Jogo 3D"))
        assertTrue(GuardasCaminho.nomeValido("projeto_teste-2"))
        assertFalse("vazio", GuardasCaminho.nomeValido("   "))
        assertFalse("ponto", GuardasCaminho.nomeValido("."))
        assertFalse("ponto ponto", GuardasCaminho.nomeValido(".."))
        assertFalse("barra no nome", GuardasCaminho.nomeValido("a/b"))
        assertFalse("maior que 60", GuardasCaminho.nomeValido("a".repeat(61)))
    }

    @Test
    fun `espaço livre checa o volume`() {
        val raiz = dirTemp()
        try {
            assertTrue(GuardasCaminho.espacoLivre(raiz, 0L))
            assertFalse(GuardasCaminho.espacoLivre(raiz, Long.MAX_VALUE / 2))
        } finally {
            raiz.deleteRecursively()
        }
    }

    // --------------------------- F1: cópia verificada ---------------------------

    @Test
    fun `cópia verificada confere caminhos e tamanhos — e DETECTA divergência`() {
        val origem = dirTemp()
        val destino = dirTemp()
        try {
            File(origem, "project.kenkern").writeText("meta")
            File(origem, "assets/models/cubo.kern").apply { parentFile.mkdirs() }.writeText("12345")
            File(origem, "scripts/main.lua").apply { parentFile.mkdirs() }.writeText("print(1)")

            origem.copyRecursively(destino, overwrite = true)
            assertTrue(GuardasCaminho.copiaVerificada(origem, destino))

            // arquivo a menos → falha
            File(destino, "scripts/main.lua").delete()
            assertFalse(GuardasCaminho.copiaVerificada(origem, destino))

            // tamanho divergente → falha
            origem.copyRecursively(destino, overwrite = true)
            File(destino, "assets/models/cubo.kern").writeText("123") // menor
            assertFalse(GuardasCaminho.copiaVerificada(origem, destino))

            // pasta inexistente → falha
            assertFalse(GuardasCaminho.copiaVerificada(origem, dirTemp()))
        } finally {
            origem.deleteRecursively(); destino.deleteRecursively()
        }
    }

    @Test
    fun `pastaDe mapeia as extensões da tarefa F4`() {
        assertEquals("assets/models", GuardasCaminho.pastaDe("glb"))
        assertEquals("assets/models", GuardasCaminho.pastaDe("GLB"))
        assertEquals("assets/models", GuardasCaminho.pastaDe("fbx"))
        assertEquals("assets/textures", GuardasCaminho.pastaDe("png"))
        assertEquals("assets/fonts", GuardasCaminho.pastaDe("ttf"))
        assertEquals("assets/audio", GuardasCaminho.pastaDe("ogg"))
        assertEquals("assets/video", GuardasCaminho.pastaDe("mp4"))
        assertEquals("scripts", GuardasCaminho.pastaDe("lua"))
        assertEquals("ui", GuardasCaminho.pastaDe("kui"))
        assertEquals("scenes", GuardasCaminho.pastaDe("ck"))
    }
}
