package com.kenkern.engine.render

import com.kenkern.engine.core.LuzNick
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.cos

/**
 * H2 (v0.1.20) — LUME: coleta das luzes dos Nicks e conjunto do frame.
 * Regras: direcional substitui o sol (e sem nenhuma, mantém); pontuais/spot
 * ≤ 4 por maior contribuição para a câmera; ligado=false/inativa nunca
 * ilumina; arrays primitivos reaproveitados; atenuação suave.
 */
class LumeCenaTest {

    private lateinit var frame: LumeCena.Frame

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
        frame = LumeCena.Frame()
    }

    /** Aplica uma instância da classe Luz com os valores pedidos. */
    private fun aplicarLuz(
        nick: com.kenkern.engine.core.Nick,
        cor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        intensidade: Float = 1f,
        alcance: Float = LuzNick.DEF_ALCANCE,
        ligado: Boolean = true,
        interno: Float = LuzNick.DEF_ANGULO_INTERNO,
        externo: Float = LuzNick.DEF_ANGULO_EXTERNO
    ) {
        val inst = NickClassRegistry.createInstance(LuzNick.CLASSE)!!
        inst.fields[LuzNick.CAMPO_COR] = NickValue.Vec3Value(cor.first, cor.second, cor.third)
        inst.fields[LuzNick.CAMPO_INTENSIDADE] = NickValue.FloatValue(intensidade)
        inst.fields[LuzNick.CAMPO_ALCANCE] = NickValue.FloatValue(alcance)
        inst.fields[LuzNick.CAMPO_ANGULO_INTERNO] = NickValue.FloatValue(interno)
        inst.fields[LuzNick.CAMPO_ANGULO_EXTERNO] = NickValue.FloatValue(externo)
        inst.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(ligado)
        nick.addClassInstance(inst)
    }

    @Test
    fun cenaSemLuzMantemOSolPadrao() {
        val scene = Scene("t")
        scene.createRootNick("Mundo")
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 6f, frame)
        assertFalse("sem direcional na cena, o sol padrão continua", frame.temDirecional)
        assertEquals(0, frame.nPontuais)
    }

    @Test
    fun direcionalLigadaSubstituiOSol() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val sol = scene.createNick("Sol", mundo, kind = "light.directional")
        aplicarLuz(sol, cor = Triple(1f, 0.5f, 0.25f), intensidade = 2f)
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 6f, frame)
        assertTrue(frame.temDirecional)
        // rotação identidade: −Z local = −Z mundial (direção de viagem)
        assertEquals(0f, frame.dir[0], 1e-4f)
        assertEquals(0f, frame.dir[1], 1e-4f)
        assertEquals(-1f, frame.dir[2], 1e-4f)
        assertEquals(1f, frame.cor[0], 1e-4f)
        assertEquals(0.5f, frame.cor[1], 1e-4f)
        assertEquals(0.25f, frame.cor[2], 1e-4f)
        assertEquals(2f, frame.intensidadeDirecional, 1e-4f)
    }

    @Test
    fun direcionalDesligadaOuInativaMantemOSol() {
        // desligada pelo campo ligado
        val s1 = Scene("t")
        val m1 = s1.createRootNick("Mundo")
        val desligada = s1.createNick("Sol", m1, kind = "light.directional")
        aplicarLuz(desligada, ligado = false)
        s1.updateWorldTransforms()
        LumeCena.coletar(s1, 0f, 0f, 6f, frame)
        assertFalse(frame.temDirecional)
        // inativa (nick.active=false)
        val s2 = Scene("t")
        val m2 = s2.createRootNick("Mundo")
        val inativa = s2.createNick("Sol", m2, kind = "light.directional")
        aplicarLuz(inativa)
        inativa.active = false
        s2.updateWorldTransforms()
        LumeCena.coletar(s2, 0f, 0f, 6f, frame)
        assertFalse(frame.temDirecional)
        // inativa POR PAI (subárvore podada)
        val s3 = Scene("t")
        val m3 = s3.createRootNick("Mundo")
        m3.active = false
        val filha = s3.createNick("Sol", m3, kind = "light.directional")
        aplicarLuz(filha)
        s3.updateWorldTransforms()
        LumeCena.coletar(s3, 0f, 0f, 6f, frame)
        assertFalse(frame.temDirecional)
    }

    @Test
    fun primeiraDirecionalVence() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val a = scene.createNick("Sol A", mundo, kind = "light.directional")
        aplicarLuz(a, cor = Triple(1f, 0f, 0f))
        val b = scene.createNick("Sol B", mundo, kind = "light.directional")
        aplicarLuz(b, cor = Triple(0f, 1f, 0f))
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 6f, frame)
        assertTrue(frame.temDirecional)
        assertEquals(1f, frame.cor[0], 1e-4f)
        assertEquals(0f, frame.cor[1], 1e-4f)
    }

    @Test
    fun pontuais_ateQuatroPorMaiorContribuicao() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        // 5 pontuais; a mais distante (L5, d=50) tem a MENOR contribuição
        val nomes = listOf("L1", "L2", "L3", "L4", "L5")
        val posicoes = listOf(
            Triple(0f, 0f, 0f), Triple(1f, 0f, 0f), Triple(2f, 0f, 0f),
            Triple(3f, 0f, 0f), Triple(50f, 0f, 0f)
        )
        for (i in nomes.indices) {
            val l = scene.createNick(nomes[i], mundo, kind = "light.point")
            aplicarLuz(l, intensidade = 1f)
            l.transform.setLocalPosition(posicoes[i].first, posicoes[i].second, posicoes[i].third)
        }
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        assertEquals("teto de 4 luzes por frame", LumeCena.MAX_PONTUAIS_SPOTS, frame.nPontuais)
        // a luz em (50,0,0) NÃO entrou: contribuição 1/(1+2500) perde de todas
        for (i in 0 until frame.nPontuais) {
            assertTrue(frame.pos[i * 3] < 10f)
        }
    }

    @Test
    fun pontualDesligadaOuInativaNaoIlumina() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val desligada = scene.createNick("D", mundo, kind = "light.point")
        aplicarLuz(desligada, ligado = false)
        val inativa = scene.createNick("I", mundo, kind = "light.point")
        aplicarLuz(inativa)
        inativa.active = false
        val ativa = scene.createNick("A", mundo, kind = "light.point")
        aplicarLuz(ativa, cor = Triple(1f, 0f, 0f), intensidade = 2f)
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 1f, 2f, frame)
        assertEquals(1, frame.nPontuais)
        assertEquals(2f, frame.corLs[0], 1e-4f) // cor × intensidade = 1 × 2
        assertEquals(0f, frame.corLs[1], 1e-4f)
        assertEquals(0f, frame.corLs[2], 1e-4f)
    }

    @Test
    fun luzSemClasseUsaOsDefaultsDoTipo() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val l = scene.createNick("Antiga", mundo, kind = "light.point") // Nick antigo, sem classe Luz
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        assertEquals(1, frame.nPontuais)
        assertEquals(1f, frame.corLs[0], 1e-4f) // cor branca × intensidade 1
        assertEquals(LuzNick.DEF_ALCANCE, frame.alcance[0], 1e-4f)
        assertEquals(LumeCena.CONE_PONTUAL, frame.cone[1], 1e-4f)
    }

    @Test
    fun spotPreencheConeComOsAngulosReais() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val s = scene.createNick("Foco", mundo, kind = "light.spot")
        aplicarLuz(s, interno = 25f, externo = 40f)
        s.transform.setLocalPosition(0f, 3f, 0f)
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        assertEquals(1, frame.nPontuais)
        assertEquals(cos(Math.toRadians(25.0)).toFloat(), frame.cone[0], 1e-4f)
        assertEquals(cos(Math.toRadians(40.0)).toFloat(), frame.cone[1], 1e-4f)
        // direção de viagem = −Z (identidade)
        assertEquals(-1f, frame.dirLs[2], 1e-4f)
    }

    @Test
    fun arraysSaoReaproveitadosEntreFrames() {
        val scene = Scene("t")
        val mundo = scene.createRootNick("Mundo")
        val l = scene.createNick("P", mundo, kind = "light.point")
        aplicarLuz(l)
        scene.updateWorldTransforms()
        LumeCena.coletar(scene, 0f, 0f, 0f, frame)
        val pos = frame.pos
        val corLs = frame.corLs
        LumeCena.coletar(scene, 1f, 1f, 1f, frame)
        assertSame("zero alocação por frame: mesmos arrays", pos, frame.pos)
        assertSame(corLs, frame.corLs)
    }

    @Test
    fun atenuacaoSuaveZeroNoAlcanceEDecrescente() {
        val alcance = 8f
        // zero NO alcance e além
        assertEquals(0f, LumeCena.atenuacao(alcance, alcance), 1e-6f)
        assertEquals(0f, LumeCena.atenuacao(alcance + 1f, alcance), 1e-6f)
        // decrescente e contínua (cada passo reduz)
        var anterior = LumeCena.atenuacao(0.01f, alcance)
        assertTrue(anterior > 0f)
        var d = 0.5f
        while (d < alcance) {
            val atual = LumeCena.atenuacao(d, alcance)
            assertTrue("atenuação decrescente em d=$d", atual < anterior)
            anterior = atual
            d += 0.5f
        }
    }

    @Test
    fun direcaoViagemZeroCaiParaMenosY() {
        val out = FloatArray(3)
        LumeCena.direcaoViagem(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), out)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(-1f, out[1], 1e-6f)
        assertEquals(0f, out[2], 1e-6f)
    }
}
