package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1.8 (v0.1.23) — EXPORTAR APK DO JOGO: montagem PURA da requisição
 * (URL/corpo/token) — a rede em si é um POST padrão do GitHub (204).
 * O token NUNCA é ecoado em mensagem.
 */
class ExportadorJogoTest {

    @Test
    fun `url do dispatch e montada do repositório e do workflow`() {
        assertEquals(
            "https://api.github.com/repos/kokernk-ctrl/Kenkerne/actions/workflows/exportar-jogo.yml/dispatches",
            ExportadorJogo.montarUrl("kokernk-ctrl/Kenkerne", "exportar-jogo.yml")
        )
        // URL de pasta colada também funciona (tira o prefixo)
        assertEquals(
            "https://api.github.com/repos/dono/repo/actions/workflows/exportar-jogo.yml/dispatches",
            ExportadorJogo.montarUrl("https://github.com/dono/repo", " exportar-jogo.yml ")
        )
        // workflow em branco → padrão
        assertEquals(
            "https://api.github.com/repos/dono/repo/actions/workflows/exportar-jogo.yml/dispatches",
            ExportadorJogo.montarUrl("dono/repo", "")
        )
    }

    @Test
    fun `repositório malformado é recusado com mensagem clara`() {
        val erro = try {
            ExportadorJogo.montarUrl("sem-barra", "x.yml"); null
        } catch (e: IllegalArgumentException) {
            e.message
        }
        assertNotNull(erro)
        assertTrue(erro!!.contains("dono/repo"))
    }

    @Test
    fun `corpo leva o projeto nos inputs e ref`() {
        assertEquals(
            "{\"ref\":\"main\",\"inputs\":{\"projeto\":\"Coletar moedas\"}}",
            ExportadorJogo.montarCorpo("Coletar moedas", "main")
        )
        // aspas do nome escapadas
        assertTrue(ExportadorJogo.montarCorpo("Meu \"jogo\"", "dev").contains("\\\"jogo\\\""))
    }

    @Test
    fun `token ausente ou curto é recusado ANTES da rede`() {
        assertNotNull(ExportadorJogo.validarToken(""))
        assertNotNull(ExportadorJogo.validarToken("abc"))
        assertNull(ExportadorJogo.validarToken("ghp_abc123def456ghi789jkl012"))
    }
}
