package com.kenkern.engine.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L2 (v0.1.28) — 4 TESTES PENDENTES DO DIAGNÓSTICO, reproduzidos na JVM.
 *
 * FATOS da v0.1.27: o catálogo [Diagnostico.novaListaDeChecagens] tinha 30
 * checagens, mas [executar] agendava SÓ 26 — "play-sair", "area", "editor" e
 * "ui" existiam no catálogo (título próprio) e no `when` do despacho, mas
 * NÃO estavam em nenhuma lista da agenda. Na tela do aparelho elas ficavam
 * PENDENTES para sempre: a rodada acabava com 26 executadas e 4 pendentes —
 * as "provas" nunca aconteciam (aceite pendente de v0.1.25/26/27).
 *
 * Este teste NASCEU VERMELHO com a agenda de 26 ids: os 4 faltando são
 * exatamente os 4 testes pendentes. Fica VERDE com a agenda de 30 — toda
 * checagem do catálogo roda, nenhuma fica PENDENTE.
 */
class DiagAgendaTest {

    private val idsCatalogo = Diagnostico.novaListaDeChecagens().map { it.id }

    @Test
    fun `toda checagem do catalogo esta agendada (nada fica PENDENTE)`() {
        val agendadas = Diagnostico.agendaDeExecucao()
        val faltando = idsCatalogo - agendadas.toSet()
        assertTrue(
            "checagens do catálogo NÃO agendadas (ficam PENDENTES para sempre no " +
                "aparelho): $faltando — agenda tem ${agendadas.size} de ${idsCatalogo.size}",
            faltando.isEmpty()
        )
    }

    @Test
    fun `toda checagem agendada existe no catalogo (sem id fantasma)`() {
        val fantasmas = Diagnostico.agendaDeExecucao() - idsCatalogo.toSet()
        assertTrue("ids agendados sem checagem no catálogo (o when nunca roda): $fantasmas", fantasmas.isEmpty())
    }

    @Test
    fun `agenda sem duplicatas e catalogo com 30 checagens`() {
        val agenda = Diagnostico.agendaDeExecucao()
        assertEquals("agenda sem ids duplicados", agenda.size, agenda.toSet().size)
        assertEquals("catálogo completo (30 checagens — aceite 30/30)", 30, idsCatalogo.size)
    }

    @Test
    fun `as 4 checagens pendentes da v0_1_27 entraram na agenda`() {
        val agendadas = Diagnostico.agendaDeExecucao().toSet()
        for (id in listOf("play-sair", "area", "editor", "ui")) {
            assertTrue(
                "a checagem '$id' (pendente na v0.1.27) continua fora da agenda",
                id in agendadas
            )
        }
    }
}
