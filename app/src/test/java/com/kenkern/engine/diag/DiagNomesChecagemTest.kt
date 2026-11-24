package com.kenkern.engine.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * S1a (v0.1.24) — REPRODUÇÃO do bug dos títulos trocados:
 * "mensagens do joystick e de materiais aparecem sob '.ck (cena)'".
 *
 * CAUSA RAIZ: DiagnosticoScreen.nomeChecagem (v0.1.23, linhas 68-92) — o
 * `when` id→recurso NÃO tinha casos para "materiais", "luz", "layout" e
 * "joystick"; os quatro caíam no `else -> R.string.diag_check_ck` e
 * exibiam o título ".ck (cena)". O mapeamento agora é PURO em
 * [DiagNomesChecagem] e este teste FIXA o contrato:
 * todo id do catálogo tem título, e NENHUM título serve para dois ids.
 */
class DiagNomesChecagemTest {

    @Test
    fun `todo id do catálogo tem título localizado`() {
        val lista = Diagnostico.novaListaDeChecagens()
        assertTrue("catálogo vazio", lista.isNotEmpty())
        for (ch in lista) {
            try {
                DiagNomesChecagem.resource(ch.id)
            } catch (e: IllegalStateException) {
                fail("id '${ch.id}' sem título mapeado: ${e.message}")
            }
        }
    }

    @Test
    fun `todo id tem nome único — nenhum título serve para dois ids`() {
        val lista = Diagnostico.novaListaDeChecagens()
        val ids = lista.map { it.id }
        assertEquals("ids do catálogo únicos", ids.size, ids.distinct().size)

        val recursos = lista.map { DiagNomesChecagem.resource(it.id) }
        assertEquals(
            "cada checagem tem título PRÓPRIO (reprodução do S1a: " +
                "materiais/luz/layout/joystick compartilhavam o título '.ck (cena)')",
            recursos.size,
            recursos.distinct().size
        )
    }

    @Test
    fun `id desconhecido erra alto em vez de reutilizar título de outra checagem`() {
        try {
            DiagNomesChecagem.resource("inexistente")
            fail("id desconhecido deve ERRO, não cair num else silencioso")
        } catch (esperado: IllegalStateException) {
            assertTrue(esperado.message!!.contains("inexistente"))
        }
    }
}
