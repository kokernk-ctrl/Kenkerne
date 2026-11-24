package com.kenkern.engine.diag

import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.Scene
import com.kenkern.engine.projects.ProjectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S7 (v0.1.24) — CHECAGEM "jogo" no Diagnóstico: carrega o template
 * "Coletar moedas" SEM GL, simula 300 ticks com INPUT SINTÉTICO até uma
 * moeda e exige CONTADOR ≥ 1 e JOGADOR NO CHÃO. A falha DIZ O PASSO
 * (caiu / atravessou o chão / não moveu / não colidiu).
 *
 * Este teste É o jogo do aparelho, em JVM: o MESMO caminho de Play
 * (cópia por sessão + física S2 + scripts por tick) — a reproducão do
 * problema reportado no aparelho ("o jogo não funciona") agora é
 * verificada por máquina; as variantes quebradas provam que a mensagem
 * de falha identifica o PASSO certo.
 */
class S7ChecagemJogoTest {

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    private fun template(): Scene {
        val cena = Scene("Coletar moedas")
        ProjectManager.montarTemplateColetarMoedas(cena)
        cena.updateWorldTransforms()
        return cena
    }

    @Test
    fun `o jogo real coleta moeda e o jogador fica no chao (300 ticks, input sintético)`() {
        val r = Diagnostico.avaliarJogoTemplate()
        assertNull("o jogo é jogável: ${r.falha}", r.falha)
        assertTrue("contador ≥ 1 (coletou moeda)", r.moedasColetadas >= 1)
        assertTrue("jogador NO CHÃO (pé ≈ 0): ${r.peDoJogador}", kotlin.math.abs(r.peDoJogador) < 0.05f)
        assertTrue("input sintético moveu o jogador (${r.metrosAndados} m)", r.metrosAndados > 1f)
    }

    @Test
    fun `sem chao o passo da falha e CAIU ou ATRAVESSOU O CHAO`() {
        val cena = template()
        // tira o chão: o jogador cai para sempre
        val chao = cena.roots.first { it.kind == "nick.group" }.children.first { it.kind == "body.static" }
        cena.removeNick(chao)
        val r = Diagnostico.avaliarJogo(cena, 300)
        assertNotNull("deve falhar", r.falha)
        assertTrue(
            "mensagem diz o passo (caiu/atravessou o chão): ${r.falha}",
            r.falha!!.contains("CAIU") || r.falha!!.contains("ATRAVESSOU O CHÃO")
        )
    }

    @Test
    fun `moeda sem script o passo da falha e NAO COLIDIU`() {
        val cena = template()
        // tira o Script das moedas: o jogador anda, entra no volume, mas o
        // gatilho não soma no contador (não colidiu de verdade)
        val mundo = cena.roots.first { it.kind == "nick.group" }
        for (moeda in mundo.children.filter { it.kind == "area.volume" }) {
            val script = moeda.findClassInstance("Script")
            assertNotNull("moeda com script no template", script)
            moeda.classes.remove(script)
        }
        val r = Diagnostico.avaliarJogo(cena, 300)
        assertNotNull("deve falhar", r.falha)
        assertTrue("mensagem diz o passo (não colidiu): ${r.falha}", r.falha!!.contains("NÃO COLIDIU"))
    }
}
