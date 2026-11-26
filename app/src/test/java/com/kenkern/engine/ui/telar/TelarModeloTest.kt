package com.kenkern.engine.ui.telar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1 (v0.1.27) — TESTES DO TELAR: modelo (elementos, ordem Z, ocultar/travar),
 * ÂNCORAS nas 3 proporções (16:9, 18:9, 20:9) com margens e área segura,
 * .kui (ida e volta completa), MIGRAÇÃO do Joystick Nick (sem perder dados)
 * e undo/redo POR COMMAND.
 */
class TelarModeloTest {

    private fun elementoBotao(): ElementoUiInstancia = ElementoUiInstancia(
        1, ElementoUi.BOTAO, "botão", 0, 0, 200, 90,
        AncoraUi.TOPO_DIREITA, 16, 16,
        estilo = EstiloUi(cor = "#14100C", cantos = 16, contornoCor = "#D98040"),
        texto = EstiloTextoUi(conteudo = "pausar", tamanho = 36, cor = "#CCCCCC"),
        aoClicar = "pausar"
    )

    private fun ElementoUiInstancia.copyId(novoId: Int): ElementoUiInstancia =
        ElementoUiInstancia(
            novoId, tipo, nome, x, y, largura, altura, ancora, margemX, margemY,
            oculto, travado, estilo, texto, aoClicar, ligaTexto, ligaValor,
            joystickTipo, joystickTamanho, joystickOpacidade, joystickZonaMorta,
            joystickOitoDirecoes, joystickControla, joystickSeguirNick
        )

    // ---------- ELEMENTOS E ORDEM Z ----------

    @Test
    fun `os 8 elementos existem no catálogo`() {
        assertEquals(8, ElementoUi.entries.size)
        for (tipo in ElementoUi.entries) {
            assertEquals(tipo, ElementoUi.de(tipo.key))
        }
    }

    @Test
    fun `ordem z é a ordem da lista e sobe e desce`() {
        val doc = DocumentoUi()
        doc.executar(ComandoAdicionar(elementoBotao().copyId(1)))
        doc.executar(ComandoAdicionar(elementoBotao().copyId(2)))
        doc.executar(ComandoAdicionar(elementoBotao().copyId(3)))
        assertEquals(listOf(1, 2, 3), doc.elementos.map { it.id })
        doc.moverNaOrdemZ(3, -2)
        assertEquals(listOf(3, 1, 2), doc.elementos.map { it.id })
        doc.moverNaOrdemZ(3, 1)
        assertEquals(listOf(1, 3, 2), doc.elementos.map { it.id })
    }

    @Test
    fun `ocultar e travar`() {
        val doc = DocumentoUi()
        val e = elementoBotao().copyId(1)
        doc.executar(ComandoAdicionar(e))
        doc.executar(ComandoPropriedade(1, "oculto", false, true))
        doc.executar(ComandoPropriedade(1, "travado", false, true))
        assertTrue(e.oculto)
        assertTrue(e.travado)
        doc.desfazer()
        doc.desfazer()
        assertFalse(e.oculto)
        assertFalse(e.travado)
    }

    // ---------- ÂNCORAS NAS 3 PROPORÇÕES (U1.7) ----------

    @Test
    fun `ancora topo direita fica no canto nas 3 proporções`() {
        val e = elementoBotao()
        for (par in listOf(1920 to 1080, 2400 to 1080, 2400 to 1200)) {
            val r = resolverRect(e, par.first, par.second)
            assertTrue("direita @${par.first}x${par.second}", r.x + r.largura <= par.first - 8)
            assertTrue("topo @${par.first}x${par.second}", r.y >= 8)
            assertTrue("dentro da tela @${par.first}x${par.second}", r.x >= 0 && r.y >= 0)
        }
    }

    @Test
    fun `ancora centro centra em qualquer proporção`() {
        val e = elementoBotao().copyId(1).apply {
            ancora = AncoraUi.CENTRO
            margemX = 0; margemY = 0
        }
        for (par in listOf(1920 to 1080, 2400 to 1080, 2400 to 1200)) {
            val r = resolverRect(e, par.first, par.second)
            assertEquals("centro x @${par.first}x${par.second}", (par.first - r.largura) / 2, r.x)
            assertEquals("centro y @${par.first}x${par.second}", (par.second - r.altura) / 2, r.y)
        }
    }

    @Test
    fun `esticar preenche com as margens`() {
        val e = elementoBotao().copyId(1).apply {
            ancora = AncoraUi.ESTICAR
            margemX = 0; margemY = 0
        }
        val r = resolverRect(e, 1920, 1080)
        assertTrue("largura esticada", r.largura >= 1900)
        assertTrue("altura esticada", r.altura >= 1060)
    }

    @Test
    fun `area segura nunca deixa fora da tela`() {
        val e = elementoBotao().copyId(1).apply { margemX = 0; margemY = 0 }
        val r = resolverRect(e, 1920, 1080)
        assertTrue(r.x >= 8)
        assertTrue(r.y >= 8)
    }

    // ---------- .KUI (U1.5) ----------

    @Test
    fun `kui ida e volta preserva tudo`() {
        val doc = DocumentoUi("teste-ui")
        doc.executar(ComandoAdicionar(elementoBotao()))
        val texto = KuiFormat.salvar(doc)
        assertTrue(texto.startsWith("KUI1 teste-ui"))
        val doc2 = KuiFormat.carregar(texto)!!
        assertEquals(1, doc2.elementos.size)
        val e = doc2.elementos.first()
        assertEquals(ElementoUi.BOTAO, e.tipo)
        assertEquals("botão", e.nome)
        assertEquals(AncoraUi.TOPO_DIREITA, e.ancora)
        assertEquals("pausar", e.aoClicar)
        assertEquals("#14100C", e.estilo.cor)
        assertEquals(16, e.estilo.cantos)
        assertEquals("pausar", e.texto.conteudo)
    }

    @Test
    fun `kui texto com quebra de linha volta igual`() {
        val doc = DocumentoUi("v")
        doc.executar(
            ComandoAdicionar(
                ElementoUiInstancia(
                    1, ElementoUi.TEXTO, "t", 0, 0, 100, 50,
                    texto = EstiloTextoUi(conteudo = "linha1\nlinha2")
                )
            )
        )
        val texto = KuiFormat.salvar(doc)
        val doc2 = KuiFormat.carregar(texto)!!
        assertEquals("linha1\nlinha2", doc2.elementos.first().texto.conteudo)
    }

    @Test
    fun `kui inválido devolve null`() {
        assertNull(KuiFormat.carregar("não é kui"))
        assertNull(KuiFormat.carregar(""))
    }

    // ---------- MIGRAÇÃO (U1.4) ----------

    @Test
    fun `migração do joystick não perde dados`() {
        val e = MigracaoUi.elementoDeJoystick(
            9, "Joystick", tipo = "flutuante", tamanho = 150, opacidade = 0.8f,
            zonaMorta = 0.25f, oitoDirecoes = true, controla = "Herói", seguirNick = true
        )
        assertEquals(ElementoUi.JOYSTICK, e.tipo)
        assertEquals("flutuante", e.joystickTipo)
        assertEquals(150, e.joystickTamanho)
        assertEquals(0.8f, e.joystickOpacidade)
        assertEquals(0.25f, e.joystickZonaMorta)
        assertTrue(e.joystickOitoDirecoes)
        assertEquals("Herói", e.joystickControla)
        assertTrue(e.joystickSeguirNick)
        val doc = DocumentoUi()
        doc.executar(ComandoAdicionar(e))
        val doc2 = KuiFormat.carregar(KuiFormat.salvar(doc))!!
        val e2 = doc2.elementos.first()
        assertEquals("flutuante", e2.joystickTipo)
        assertEquals(150, e2.joystickTamanho)
        assertTrue(e2.joystickOitoDirecoes)
    }

    // ---------- UNDO/REDO POR COMMAND (U1.7) ----------

    @Test
    fun `adicionar e remover desfazem`() {
        val doc = DocumentoUi()
        doc.executar(ComandoAdicionar(elementoBotao()))
        assertEquals(1, doc.elementos.size)
        doc.desfazer()
        assertEquals(0, doc.elementos.size)
        doc.refazer()
        assertEquals(1, doc.elementos.size)
    }

    @Test
    fun `transformar desfaz para a posição anterior`() {
        val doc = DocumentoUi()
        val e = elementoBotao()
        doc.executar(ComandoAdicionar(e))
        doc.executar(ComandoTransformar(1, 500, 300, 240, 100))
        assertEquals(500, e.x)
        doc.desfazer()
        assertEquals(0, e.x)
        assertEquals(200, e.largura)
        doc.refazer()
        assertEquals(500, e.x)
        assertEquals(240, e.largura)
    }

    @Test
    fun `propriedade desfaz o valor anterior`() {
        val doc = DocumentoUi()
        val e = elementoBotao()
        doc.executar(ComandoAdicionar(e))
        doc.executar(ComandoPropriedade(1, "texto", "pausar", "continuar"))
        assertEquals("continuar", e.texto.conteudo)
        doc.desfazer()
        assertEquals("pausar", e.texto.conteudo)
    }

    @Test
    fun `ordem z desfaz a troca`() {
        val doc = DocumentoUi()
        doc.executar(ComandoAdicionar(elementoBotao().copyId(1)))
        doc.executar(ComandoAdicionar(elementoBotao().copyId(2)))
        doc.executar(ComandoOrdemZ(2, -1))
        assertEquals(listOf(2, 1), doc.elementos.map { it.id })
        doc.desfazer()
        assertEquals(listOf(1, 2), doc.elementos.map { it.id })
    }

    // ---------- TEMPLATE UI (U1.8) ----------

    @Test
    fun `template da coleta tem contador botão pausar e joystick`() {
        val doc = TemplateUiMoedas.montar()
        assertEquals(4, doc.elementos.size)
        val contador = doc.elementos.first { it.tipo == ElementoUi.TEXTO }
        assertEquals("moedas", contador.ligaTexto)
        val botao = doc.elementos.first { it.tipo == ElementoUi.BOTAO }
        assertEquals("pausar", botao.aoClicar)
        assertNotNull(doc.elementos.firstOrNull { it.tipo == ElementoUi.JOYSTICK })
        val doc2 = KuiFormat.carregar(KuiFormat.salvar(doc))!!
        assertEquals(4, doc2.elementos.size)
    }
}
