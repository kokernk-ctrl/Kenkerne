package com.kenkern.engine.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1c (v0.1.24) — botão "Copiar relatório": o TEXTO COMPLETO das checagens
 * (título + estado + tempo + detalhe inteiro) com as FALHAS NO TOPO.
 */
class DiagRelatorioTextoTest {

    private fun checagem(id: String, nome: String, status: Diagnostico.Status, detalhe: String): Diagnostico.Checagem {
        val c = Diagnostico.Checagem(id, nome)
        when (status) {
            Diagnostico.Status.APROVADO -> c.aprovado(detalhe)
            Diagnostico.Status.FALHOU -> c.falhou(detalhe)
            else -> {}
        }
        return c
    }

    @Test
    fun `falhas primeiro na ordem de exibição — resto na ordem do catálogo`() {
        val a = checagem("a", "A", Diagnostico.Status.APROVADO, "ok")
        val f = checagem("f", "F", Diagnostico.Status.FALHOU, "quebrou")
        val b = checagem("b", "B", Diagnostico.Status.PENDENTE, "—")
        val ordenados = DiagRelatorioTexto.ordenar(listOf(a, f, b))
        assertEquals("falha no topo", "f", ordenados[0].id)
        assertEquals("resto na ordem original", listOf("a", "b"), ordenados.drop(1).map { it.id })
    }

    @Test
    fun `relatório tem texto completo — estado, tempo e detalhe inteiro`() {
        val ok = checagem("cena", "Cena = tela", Diagnostico.Status.APROVADO, "6 malhas · 60 fps medidos")
        ok.registrarTempo(412L)
        val ruim = checagem("materiais", "Materiais PBR", Diagnostico.Status.FALHOU, "amostra quad.glb importou SEM tabela")
        ruim.registrarTempo(0L)
        val texto = DiagRelatorioTexto.montar(listOf(ok, ruim)) { id -> "Título[$id]" }

        assertTrue("resumo no cabeçalho: $texto", texto.contains("1 aprovadas · 1 falhas (de 2)"))
        assertTrue("estado FALHOU", texto.contains("FALHOU"))
        assertTrue("estado APROVADO", texto.contains("APROVADO"))
        assertTrue("tempo em ms", texto.contains("412 ms"))
        assertTrue("título localizado via lambda", texto.contains("Título[materiais]"))
        assertTrue("detalhe completo (sem cortar)", texto.contains("amostra quad.glb importou SEM tabela"))
        assertTrue("detalhe do aprovado completo", texto.contains("6 malhas · 60 fps medidos"))
    }

    @Test
    fun `falha aparece antes do aprovado no texto copiado`() {
        val ok = checagem("cena", "Cena", Diagnostico.Status.APROVADO, "ok")
        val ruim = checagem("gltf", "glTF", Diagnostico.Status.FALHOU, "erro grave")
        val texto = DiagRelatorioTexto.montar(listOf(ok, ruim)) { id -> "T[$id]" }
        val posFalha = texto.indexOf("erro grave")
        val posOk = texto.indexOf("ok")
        assertTrue("falha no topo do texto copiado", posFalha in 0 until posOk)
    }
}
