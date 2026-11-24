package com.kenkern.engine.engine

import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2 (v0.1.24) — GRAVIDADE E FÍSICA em Play, SEM GL:
 *  - chão = Static Body Nick COM classe Colisor (volume 14×2×14 → topo 1,0);
 *  - corpo dinâmico a 5 m, 120 ticks (2 s a 60 Hz);
 *  - deve POUSAR e PARAR: |pé − topo| < 0,01.
 *
 * REPRODUÇÃO (motivo de "no aparelho nada cai"):
 *  (1) o chão Static Body + Colisor: getBlockingBounds do runtime IGNORAVA a
 *      classe Colisor do body.static (colidia como cubo unitário) → topo real
 *      1,0 vira 0,5 no runtime;
 *  (2) SÓ o primeiro body.player entrava na simulação — Rigid Body,
 *      Character Body e Collider Body NUNCA caem (não entram na simulação
 *      de Play; classes padrão sem corpo/gravidade).
 */
class S2GravidadeFisicaTest {

    private val ticks = 120
    private val topo = 1.0f

    @Before
    fun preparar() {
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    /** Chão = Static Body Nick + classe Colisor (caixa 14×2×14) — topo em y=1,0. */
    private fun chaoComColisor(scene: Scene) {
        val chao = scene.createRootNick("chão", kind = "body.static")
        chao.transform.position.set(0f, 0f, 0f)
        val colisor = NickClassRegistry.createInstance("Colisor")!!
        colisor.fields["forma"] = NickValue.StringValue("box")
        colisor.fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
        colisor.fields["tamanho"] = NickValue.Vec3Value(14f, 2f, 14f)
        chao.addClassInstance(colisor)
    }

    private fun colisorUnitario(): NickClassInstance =
        NickClassRegistry.createInstance("Colisor")!!.apply {
            fields["forma"] = NickValue.StringValue("box")
            fields["centro"] = NickValue.Vec3Value(0f, 0f, 0f)
            fields["tamanho"] = NickValue.Vec3Value(1f, 1f, 1f)
        }

    /**
     * Solta o kind pedido a 5 m e roda 120 ticks; o PÉ do corpo (y − 0,5 do
     * cubo padrão) deve parar no TOPO do chão com |pé − topo| < 0,01.
     */
    private fun pousa(kind: String): Float {
        val scene = Scene("s2-$kind")
        chaoComColisor(scene)
        val corpo = scene.createRootNick("corpo", kind = kind)
        corpo.transform.position.set(0f, 5f, 0f)
        if (kind == "body.collider") corpo.addClassInstance(colisorUnitario())
        scene.updateWorldTransforms()
        val runtime = NickPlayRuntime(scene)
        repeat(ticks) { runtime.step(1f / 60f, 0f, 0f) }
        return corpo.transform.position.y - 0.5f // pé
    }

    @Test
    fun `player body a 5 m pousa e para no chao Static Body com Colisor`() {
        val pe = pousa("body.player")
        assertTrue(
            "Player Body pousou com pé em $pe (esperado $topo ± 0,01)",
            kotlin.math.abs(pe - topo) < 0.01f
        )
    }

    @Test
    fun `rigid body a 5 m pousa e para`() {
        val pe = pousa("body.rigid")
        assertTrue(
            "Rigid Body pousou com pé em $pe (esperado $topo ± 0,01)",
            kotlin.math.abs(pe - topo) < 0.01f
        )
    }

    @Test
    fun `character body a 5 m pousa e para`() {
        val pe = pousa("body.character")
        assertTrue(
            "Character Body pousou com pé em $pe (esperado $topo ± 0,01)",
            kotlin.math.abs(pe - topo) < 0.01f
        )
    }

    @Test
    fun `collider body a 5 m pousa e para`() {
        val pe = pousa("body.collider")
        assertTrue(
            "Collider Body pousou com pé em $pe (esperado $topo ± 0,01)",
            kotlin.math.abs(pe - topo) < 0.01f
        )
    }

    @Test
    fun `quais nicks entram na simulacao de play — corpos dinâmicos, malha não`() {
        val scene = Scene("s2-quais")
        chaoComColisor(scene)
        scene.createRootNick("p", kind = "body.player").transform.position.set(0f, 5f, 0f)
        scene.createRootNick("r", kind = "body.rigid").transform.position.set(1f, 5f, 0f)
        scene.createRootNick("c", kind = "body.character").transform.position.set(2f, 5f, 0f)
        scene.createRootNick("m", kind = "nick.mesh").transform.position.set(3f, 5f, 0f)
        scene.updateWorldTransforms()
        val runtime = NickPlayRuntime(scene)
        assertEquals(
            "corpos simulados: player + rigid + character (malha NÃO)",
            3,
            runtime.corposSimulados.size
        )
        repeat(ticks) { runtime.step(1f / 60f, 0f, 0f) }
        val malha = scene.findNickByNameAnywhere("m")!!
        assertEquals("nick.mesh não entra na simulação (fica onde está)", 5f, malha.transform.position.y, 1e-4f)
    }

    @Test
    fun `overlay de debug mostra corpo simulado, velocidade, no chao e colisao`() {
        val scene = Scene("s2-debug")
        chaoComColisor(scene)
        val corpo = scene.createRootNick("Herói", kind = "body.player")
        corpo.transform.position.set(0f, 5f, 0f)
        scene.updateWorldTransforms()
        val runtime = NickPlayRuntime(scene)
        repeat(ticks) { runtime.step(1f / 60f, 0f, 0f) }
        val linhas = ArrayList<String>()
        runtime.snapshotDebug(linhas)
        val junto = linhas.joinToString(" | ")
        assertTrue("tem o nome do corpo: $junto", junto.contains("Herói"))
        assertTrue("tem o kind: $junto", junto.contains("body.player"))
        assertTrue("tem velocidade: $junto", junto.contains("vel") || junto.contains("vy"))
        assertTrue("tem estado de chão: $junto", junto.contains("chão") || junto.contains("apoiado"))
    }
}
