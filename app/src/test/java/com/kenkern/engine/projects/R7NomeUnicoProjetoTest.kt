package com.kenkern.engine.projects

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * R7 (v0.1.29) — NOME DE PROJETO ÚNICO no Hub: criar "mm" quando "mm" já
 * existe tem que criar "mm 2" (sufixo " 2" SÓ na colisão) — na v0.1.28 a
 * pasta virava "mm (2)" mas o name= do project.kenkern continuava "mm" e o
 * Hub listava DOIS projetos chamados "mm" (print do aparelho).
 */
class R7NomeUnicoProjetoTest {

    private fun dirTemp(): File =
        File.createTempFile("kenkern-r7", "").let { f -> f.delete(); f.mkdirs(); f }

    @Test
    fun `sem colisão o nome sai exatamente igual (sem sufixo)`() {
        val dir = dirTemp()
        try {
            assertEquals("mm", ProjectManager.nomeUnico(dir, "mm"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `com colisão o sufixo é ' 2' (e NÃO '(cópia)' ou nada)`() {
        val dir = dirTemp()
        try {
            File(dir, "mm").mkdirs()
            assertEquals("mm 2", ProjectManager.nomeUnico(dir, "mm"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `colisão em cadeia — mm, mm 2 e mm 3 ocupados → mm 4`() {
        val dir = dirTemp()
        try {
            File(dir, "mm").mkdirs()
            File(dir, "mm 2").mkdirs()
            File(dir, "mm 3").mkdirs()
            assertEquals("mm 4", ProjectManager.nomeUnico(dir, "mm"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `nome que já tem sufixo numérico não confunde a cadeia`() {
        val dir = dirTemp()
        try {
            File(dir, "cena").mkdirs()
            File(dir, "cena 2").mkdirs()
            // criar "cena 2" de novo (o usuário digitou igual) → "cena 2 2"
            assertEquals("cena 2 2", ProjectManager.nomeUnico(dir, "cena 2"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
