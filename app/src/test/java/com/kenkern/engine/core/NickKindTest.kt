package com.kenkern.engine.core

import com.kenkern.engine.core.serialization.CkFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T0.2 (adendo v4 — DECISIONS #24/#25/#26): NickKindRegistry + CK2 + migração CK1.
 *
 *  1. catálogo inicial registrado: 17 tipos, chaves únicas, rótulos en/pt e
 *     descrições NÃO VAZIOS, grupos coerentes (T0.2 exige rótulo nos 2 idiomas);
 *  2. CK2 ida e volta: kind e name nulo/custom preservados;
 *  3. MIGRAÇÃO CK1→CK2: classe Malha → nick.mesh; senão nick.empty; nomes
 *     automáticos ("Mundo", "Cubo", "Nick N") viram NULOS; nome do usuário fica;
 *  4. displayName: name nulo = rótulo do tipo no idioma; custom nunca traduz;
 *  5. kind desconhecido nunca vira "???" (a própria chave é devolvida).
 */
class NickKindTest {

    @Before
    fun registrar() {
        NickKindRegistry.clear()
        NickKindCatalog.registerAll()
        NickClassRegistry.clear()
        KenkernBuiltInClasses.registerAll()
    }

    @Test
    fun `catalogo tem os 19 tipos com rotulos nos dois idiomas`() {
        val esperados = setOf(
            "nick.empty", "nick.group", "nick.mesh",
            "light.directional", "light.point", "light.spot",
            "camera.basic", "camera.follow",
            "body.static", "body.rigid", "body.collider", "body.character",
            "body.player", "body.enemy", "body.npc",
            "area.volume", "area.signal", "area.spawn",
            "ui.joystick"
        )
        val registrados = NickKindRegistry.all().map { it.key }.toSet()
        assertEquals(esperados, registrados)
        for (kind in NickKindRegistry.all()) {
            assertTrue("labelEn vazio: ${kind.key}", kind.labelEn.isNotBlank())
            assertTrue("labelPt vazio: ${kind.key}", kind.labelPt.isNotBlank())
            assertTrue("descEn vazio: ${kind.key}", kind.descEn.isNotBlank())
            assertTrue("descPt vazio: ${kind.key}", kind.descPt.isNotBlank())
            assertNotNull("sem grupo: ${kind.key}", kind.group)
        }
    }

    @Test
    fun `rotulo por idioma e fallback de chave desconhecida`() {
        assertEquals("Nick de Corpo de Jogador", NickKindRegistry.labelOf("body.player", "pt"))
        assertEquals("Player Body Nick", NickKindRegistry.labelOf("body.player", "en"))
        // Chave de versão mais nova: a própria chave (nunca "???").
        assertEquals("futuro.tipo", NickKindRegistry.labelOf("futuro.tipo", "pt"))
    }

    @Test
    fun `displayName usa texto do usuario ou rotulo do tipo no idioma`() {
        val comNome = Nick(name = "Herói Principal", kind = "body.player")
        assertEquals("Herói Principal", comNome.displayName("en"))
        assertEquals("Herói Principal", comNome.displayName("pt"))

        val semNome = Nick(name = null, kind = "body.player")
        assertEquals("Nick de Corpo de Jogador", semNome.displayName("pt"))
        assertEquals("Player Body Nick", semNome.displayName("en"))
    }

    @Test
    fun `ck2 ida e volta preserva kind e name nulo e custom`() {
        val scene = Scene("Teste CK2")
        val raiz = scene.createRootNick(null, kind = NickKindRegistry.NICK_GROUP)
        val jogador = scene.createNick("Herói", raiz, kind = "body.player")
        scene.createNick(null, jogador, kind = "body.enemy")

        val texto = CkFormat.writeToString(scene)
        assertTrue("grava CK2", texto.startsWith("CK2"))
        assertTrue("kind gravado", texto.contains("kind=body.player"))
        assertTrue("name custom gravado", texto.contains("name=\"Herói\""))

        val lida = CkFormat.parseToNewScene(texto, "x")!!
        assertEquals(3, MalhaNick.contarNicks(lida))
        val raizLida = lida.roots[0]
        assertEquals(NickKindRegistry.NICK_GROUP, raizLida.kind)
        assertNull("name nulo permanece nulo", raizLida.name)
        val jogadorLido = raizLida.children[0]
        assertEquals("body.player", jogadorLido.kind)
        assertEquals("Herói", jogadorLido.name)
        // Filho sem nome: rótulo do TIPO no idioma (DECISIONS #26).
        assertEquals("Nick de Corpo Inimigo", jogadorLido.children[0].displayName("pt"))
        assertEquals("Enemy Body Nick", jogadorLido.children[0].displayName("en"))
        assertEquals("body.enemy", jogadorLido.children[0].kind)
        assertNull(jogadorLido.children[0].name)
    }

    @Test
    fun `ck1 migra malha para nick mesh e nomes automaticos viram nulos`() {
        // CK1 legado com o TEMPLATE antigo: Mundo > Cubo(Spin) + Malha importada
        // renomeada pelo usuário + "Nick 3" automático.
        val ck1 = """
            CK1
            SCENE name="Antigo"
            NICK id=11111111-1111-1111-1111-111111111111 name="Mundo" active=true visible=true parent=NONE
            TRANSFORM px=0.0 py=0.0 pz=0.0 qx=0.0 qy=0.0 qz=0.0 qw=1.0 sx=1.0 sy=1.0 sz=1.0
            NICK id=22222222-2222-2222-2222-222222222222 name="Cubo" active=true visible=true parent=11111111-1111-1111-1111-111111111111
            TRANSFORM px=0.0 py=0.5 pz=0.0 qx=0.0 qy=0.0 qz=0.0 qw=1.0 sx=1.0 sy=1.0 sz=1.0
            CLASS type="Spin"
            FIELD name="speed" type=FLOAT value="45.0"
            ENDCLASS
            ENDNICK
            NICK id=33333333-3333-3333-3333-333333333333 name="Meu Carro" active=true visible=true parent=11111111-1111-1111-1111-111111111111
            TRANSFORM px=2.0 py=0.0 pz=0.0 qx=0.0 qy=0.0 qz=0.0 qw=1.0 sx=1.0 sy=1.0 sz=1.0
            CLASS type="Malha"
            FIELD name="path" type=STRING value="assets/models/carro.KERN"
            ENDCLASS
            ENDNICK
            NICK id=44444444-4444-4444-4444-444444444444 name="Nick 3" active=true visible=true parent=NONE
            TRANSFORM px=0.0 py=0.0 pz=0.0 qx=0.0 qy=0.0 qz=0.0 qw=1.0 sx=1.0 sy=1.0 sz=1.0
            ENDNICK
            ENDNICK
            ENDCK
        """.trimIndent()

        val scene = CkFormat.parseToNewScene(ck1, "Antigo")!!
        val mundo = scene.findNick(ReferenceId.of("11111111-1111-1111-1111-111111111111"))!!
        val carro = scene.findNick(ReferenceId.of("33333333-3333-3333-3333-333333333333"))!!
        val nick3 = scene.findNick(ReferenceId.of("44444444-4444-4444-4444-444444444444"))!!

        // DECISIONS #25: sem Malha → nick.empty; COM classe Malha → nick.mesh.
        assertEquals(NickKindRegistry.NICK_EMPTY, mundo.kind)
        assertEquals(NickKindRegistry.NICK_EMPTY, mundo.children[0].kind) // Cubo (Spin, sem Malha)
        assertEquals(NickKindRegistry.NICK_MESH, carro.kind)

        // DECISIONS #26: nomes automáticos do template viram NULOS; o do usuário fica.
        assertNull("Mundo automático vira null", mundo.name)
        assertNull("Cubo automático vira null", mundo.children[0].name)
        assertNull("Nick 3 automático vira null", nick3.name)
        assertEquals("Meu Carro", carro.name)

        // Reagrupamento por parent= do CK1 preservado (Cubo e Carro filhos do Mundo).
        assertEquals(2, mundo.children.size)
    }

    @Test
    fun `ck2 com kind desconhecido carrega sem perder o tipo`() {
        val ck2 = """
            CK2
            SCENE name="Futuro"
            NICK id=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa kind=logic.timer active=true visible=true parent=NONE
            TRANSFORM px=0.0 py=0.0 pz=0.0 qx=0.0 qy=0.0 qz=0.0 qw=1.0 sx=1.0 sy=1.0 sz=1.0
            ENDNICK
            ENDCK
        """.trimIndent()
        val scene = CkFormat.parseToNewScene(ck2, "Futuro")!!
        val nick = scene.roots[0]
        assertEquals("logic.timer", nick.kind)
        // Tipo não registrado: displayName devolve a CHAVE (dado íntegro).
        assertEquals("logic.timer", nick.displayName("pt"))
    }

    @Test
    fun `classe Colisor registrada com forma padrao caixa`() {
        val inst = NickClassRegistry.createInstance("Colisor")
        assertNotNull("classe Colisor deve existir (T0.2)", inst)
        assertEquals("box", inst?.getString("forma", "box"))
        // body.collider aplica Colisor por padrão (defaultClasses).
        val collider = NickKindRegistry.find("body.collider")!!
        assertTrue(collider.defaultClasses.contains("Colisor"))
    }
}
