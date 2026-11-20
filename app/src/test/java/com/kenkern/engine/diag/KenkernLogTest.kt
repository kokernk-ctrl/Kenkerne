package com.kenkern.engine.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testes JVM do Console da engine (regra do realinhamento: nenhum runCatching
 * engole erro em silêncio — KenkernLog é o Console; logcat desligado aqui).
 */
class KenkernLogTest {

    @Before
    fun preparar() {
        // JVM: android.util.Log não existe — o console segue funcionando sem logcat.
        KenkernLog.androidLogging = false
        KenkernLog.limpar()
    }

    @Test
    fun registro_e_e_w_entra_no_anel_com_nivel_tag_e_detalhe() {
        val erro = IllegalStateException("cabuloso")
        KenkernLog.e("Kenkern/Teste", "falha de render", erro)
        KenkernLog.w("Kenkern/Teste", "aviso de formato", null)

        val regs = KenkernLog.registros()
        assertEquals(2, regs.size)
        assertEquals(KenkernLog.Nivel.E, regs[0].nivel)
        assertEquals("Kenkern/Teste", regs[0].tag)
        assertEquals("falha de render", regs[0].mensagem)
        assertEquals("cabuloso", regs[0].detalhe)
        assertEquals(KenkernLog.Nivel.W, regs[1].nivel)
        assertEquals(null, regs[1].detalhe)
        // Linha pronta para as telas (não lança, contém tag e mensagem).
        val linha = regs[0].linha(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US))
        assertTrue(linha.contains("ERRO") && linha.contains("Kenkern/Teste") && linha.contains("cabuloso"))
    }

    @Test
    fun total_erros_conta_so_erros() {
        KenkernLog.e("T", "a", null)
        KenkernLog.w("T", "b", null)
        KenkernLog.e("T", "c", null)
        assertEquals(2, KenkernLog.totalErros())
        assertEquals(3, KenkernLog.registros().size)
    }

    @Test
    fun anel_respeita_a_capacidade_de_200_mais_recente_no_fim() {
        for (i in 1..250) KenkernLog.w("T", "msg $i", null)
        val regs = KenkernLog.registros()
        assertEquals(200, regs.size)
        assertEquals("msg 250", regs.last().mensagem)
        // As 50 primeiras foram descartadas (mais antigas).
        assertEquals("msg 51", regs.first().mensagem)
    }

    @Test
    fun limpar_zera_o_console() {
        KenkernLog.e("T", "algo", null)
        assertTrue(KenkernLog.registros().isNotEmpty())
        KenkernLog.limpar()
        assertEquals(0, KenkernLog.registros().size)
        assertEquals(0, KenkernLog.totalErros())
    }

    @Test
    fun exception_sem_mensagem_usa_o_nome_da_classe() {
        KenkernLog.e("T", "sem mensagem", NullPointerException())
        assertEquals("NullPointerException", KenkernLog.registros().single().detalhe)
    }
}
