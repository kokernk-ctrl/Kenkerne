package com.kenkern.engine.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1 (v0.1.20): "Mostrar auxiliares" é preferência do usuário — Play/Pausa
 * NUNCA a altera; só SUPRIMEM o desenho/picking durante a sessão, e o Stop
 * restaura exatamente o que o usuário deixou.
 *
 * Cenários exigidos pela tarefa:
 *  - ligar → Play → Stop: continua ligado (desenha/pega no editor);
 *  - desligar → Play → Stop: continua desligado;
 *  - Play/Pausa suprimem mesmo com a preferência ligada.
 *
 * REGRESSÃO do H1: a condição do picking no E5 usava `playState != STOPPED`
 * (KenkernSurfaceView.kt:784) — o INVERSO da regra: pegáveis EM Play (quando
 * invisíveis) e não pegáveis no editor depois do Stop. devePegar reproduz a
 * regra CORRETA e este teste reprova a semântica invertida antiga.
 */
class AuxiliaresPreferenciaTest {

    @Test
    fun ligar_play_stop_continuaLigado() {
        var ligado = true // preferência do usuário (TopBar, padrão LIGADO)
        // Play: suprimido em PLAYING…
        assertFalse(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.PLAYING))
        // …e a preferência NÃO é escrita pelo play (nada aqui altera `ligado`).
        assertEquals(true, ligado)
        // Stop: volta a desenhar exatamente como estava.
        assertTrue(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.STOPPED))
        assertTrue(AuxiliaresPreferencia.devePegar(ligado, PlayState.STOPPED))
    }

    @Test
    fun desligar_play_stop_continuaDesligado() {
        var ligado = false // usuário desligou
        assertFalse(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.PLAYING))
        assertFalse(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.STOPPED))
        assertFalse(AuxiliaresPreferencia.devePegar(ligado, PlayState.STOPPED))
        assertEquals(false, ligado) // Play/Stop nunca religam por conta própria
    }

    @Test
    fun pausa_tambem_suprime_o_desenho() {
        val ligado = true
        assertTrue(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.STOPPED))
        assertFalse(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.PAUSED))
        assertFalse(AuxiliaresPreferencia.deveDesenhar(ligado, PlayState.PLAYING))
    }

    @Test
    fun picking_tem_a_mesma_regra_do_desenho() {
        for (ligado in listOf(true, false)) {
            for (estado in PlayState.values()) {
                assertEquals(
                    "devePegar deve IGUALAR deveDesenhar (ligado=$ligado, $estado)",
                    AuxiliaresPreferencia.deveDesenhar(ligado, estado),
                    AuxiliaresPreferencia.devePegar(ligado, estado)
                )
            }
        }
    }

    @Test
    fun regressao_condicao_invertida_do_e5_reprovada() {
        // Semântica ANTIGA (bug): pegar quando playState != STOPPED.
        fun antigo(ligado: Boolean, estado: PlayState) = ligado && estado != PlayState.STOPPED
        // Nos três estados, a regra NOVA difere da antiga em STOPPED e PLAYING
        // com preferência ligada — exatamente os sintomas do H1:
        assertTrue(antigo(true, PlayState.PLAYING) != AuxiliaresPreferencia.devePegar(true, PlayState.PLAYING))
        assertTrue(antigo(true, PlayState.STOPPED) != AuxiliaresPreferencia.devePegar(true, PlayState.STOPPED))
    }
}
