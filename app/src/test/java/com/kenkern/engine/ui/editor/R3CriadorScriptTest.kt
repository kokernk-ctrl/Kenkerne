package com.kenkern.engine.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R3 (v0.1.29) — CRIAÇÃO DE SCRIPT: o modelo grava conteúdo ANTES de abrir
 * (arquivo de CADA modelo não é vazio) e o nome NÃO TEM sufixo aleatório
 * (o "script_movimento_34934.lua" da v0.1.28); sufixo "_2" SÓ em colisão,
 * padrão "script.lua".
 */
class R3CriadorScriptTest {

    @Test
    fun `arquivo criado de CADA modelo não é vazio (grava antes de abrir)`() {
        for (modelo in ModelosScript.NOMES) {
            val dir = File.createTempFile("kenkern-r3", "").let { f -> f.delete(); f.mkdirs(); f }
            try {
                val nome = CriadorScript.criar(dir, "script.lua", modelo)
                assertNotNull("modelo '$modelo' não criou arquivo", nome)
                val conteudo = File(dir, nome!!).readText()
                assertTrue(
                    "modelo '$modelo' gravou arquivo VAZIO (é o 'abre vazio' da v0.1.28)",
                    conteudo.isNotBlank()
                )
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun `nome padrão é script_lua e SEM colisão o nome sai exatamente igual`() {
        assertEquals("script.lua", CriadorScript.NOME_PADRAO)
        assertEquals("script.lua", CriadorScript.nomeUnico("script.lua", emptySet()))
        assertEquals(
            "script.lua",
            CriadorScript.nomeUnico("script.lua", setOf("outro.lua", "porta.lua"))
        )
    }

    @Test
    fun `sufixo _2 SÓ em colisão (nunca aleatório)`() {
        assertEquals("script_2.lua", CriadorScript.nomeUnico("script.lua", setOf("script.lua")))
        assertEquals(
            "script_3.lua",
            CriadorScript.nomeUnico("script.lua", setOf("script.lua", "script_2.lua"))
        )
        assertEquals(
            "porta_2.lua",
            CriadorScript.nomeUnico("porta.lua", setOf("porta.lua"))
        )
        // determinístico: duas chamadas com o MESMO cenário dão o MESMO nome
        // (o sufixo aleatório da v0.1.28 — script_movimento_34934.lua — não volta)
        val existentes = setOf("script.lua")
        assertEquals(
            CriadorScript.nomeUnico("script.lua", existentes),
            CriadorScript.nomeUnico("script.lua", existentes)
        )
    }

    @Test
    fun `criar com nome colidente grava em _2 com conteúdo do modelo`() {
        val dir = File.createTempFile("kenkern-r3", "").let { f -> f.delete(); f.mkdirs(); f }
        try {
            File(dir, "script.lua").writeText("existente")
            val nome = CriadorScript.criar(dir, "script.lua", "movimento")
            assertEquals("script_2.lua", nome)
            assertTrue(File(dir, "script_2.lua").readText().isNotBlank())
            assertEquals("existente", File(dir, "script.lua").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `nome vazio ou com barra é rejeitado (nada criado)`() {
        val dir = File.createTempFile("kenkern-r3", "").let { f -> f.delete(); f.mkdirs(); f }
        try {
            assertEquals(null, CriadorScript.criar(dir, "  ", "vazio"))
            assertEquals(null, CriadorScript.criar(dir, "../escapando.lua", "vazio"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
