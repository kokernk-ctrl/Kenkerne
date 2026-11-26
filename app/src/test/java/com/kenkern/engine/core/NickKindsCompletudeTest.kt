package com.kenkern.engine.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 (v0.1.25) — REGRA DE NICK COMPLETO (contrato de Nicks).
 *
 * Um Nick só é COMPLETO (e portanto CRIÁVEL na janela "Adicionar Nick") com
 * as 5 exigências: (1) representação no viewport; (2) classes padrão +
 * Inspetor editável com efeito ao vivo; (3) comportamento em Play; (4) API
 * Lua ou porta LINKERF; (5) teste e checagem no Diagnóstico.
 *
 * O teste nasceu VERMELHO contra a v0.1.24: area.volume e area.spawn eram
 * FUNCIONAL (criáveis) sem on_enter/exit/stay e sem respawn/checkpoint — a
 * regra exige que fiquem PARCIAIS ("Em breve") até a Parada 2 (C2).
 */
class NickKindsCompletudeTest {

    @Test
    fun nenhumKindParcialOuSemFuncaoECriavel() {
        for (kind in NickKindRegistry.all()) {
            if (kind.status != NickKindStatus.FUNCIONAL) {
                assertFalse(
                    "kind '${kind.key}' com status ${kind.status} NÃO pode ser criável (P3)",
                    kind.criavel()
                )
            }
        }
    }

    @Test
    fun todosOsFuncionaisSaoCriaveis() {
        for (kind in NickKindRegistry.all()) {
            if (kind.status == NickKindStatus.FUNCIONAL) {
                assertTrue(
                    "kind '${kind.key}' FUNCIONAL deve ser criável",
                    kind.criavel()
                )
            }
        }
    }

    @Test
    fun areasFicamParciaisAteAParada2() {
        // Área, Sinal, Spawn e Checkpoint ficam completos na PARADA 2 (C2) —
        // na v0.1.25 o volume e o spawn são PARCIAIS ("Em breve").
        assertEquals(
            NickKindStatus.PARCIAL,
            NickKindRegistry.find("area.volume")?.status
        )
        assertEquals(
            NickKindStatus.PARCIAL,
            NickKindRegistry.find("area.spawn")?.status
        )
        assertFalse(NickKindRegistry.find("area.volume")!!.criavel())
        assertFalse(NickKindRegistry.find("area.spawn")!!.criavel())
    }

    @Test
    fun placeholdersFicamForaDaJanela() {
        // SEM_FUNCAO (placeholder) = fora de Adicionar Nick (visivel=false),
        // mas permanecem no registro para projetos antigos.
        for (kind in NickKindRegistry.all()) {
            if (kind.status == NickKindStatus.SEM_FUNCAO) {
                assertFalse(
                    "placeholder '${kind.key}' deve ficar fora de Adicionar Nick",
                    kind.visivel
                )
            } else {
                assertTrue(
                    "kind '${kind.key}' (${kind.status}) deve aparecer na janela (com selo se PARCIAL)",
                    kind.visivel
                )
            }
        }
    }

    @Test
    fun kindsFuncionaisTemClassesEViewport() {
        // Exigências (1) e (2) na prática: todo FUNCIONAL declara ícone no
        // catálogo (representação no viewport) e classes padrão quando o kind
        // as usa — aqui garante que NENHUM FUNCIONAL está sem ícone (a
        // representação é obrigatória).
        for (kind in NickKindRegistry.all()) {
            if (kind.status == NickKindStatus.FUNCIONAL) {
                assertTrue(
                    "kind '${kind.key}' FUNCIONAL sem iconKey (representação no viewport)",
                    kind.iconKey.isNotBlank()
                )
            }
        }
    }
}
