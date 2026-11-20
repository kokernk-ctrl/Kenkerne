package com.kenkern.engine.core.serialization

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.linkerf.LinkerfLink
import com.kenkern.engine.core.linkerf.LinkerfPort
import com.kenkern.engine.core.linkerf.LinkerfPortKind
import com.kenkern.engine.core.linkerf.LinkerfPortType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste JVM de IDA E VOLTA do formato .ck (CK1) — Fase 2, F2.1.
 *
 * O .ck é o único formato de cena da engine: se a ida e volta quebrar, o usuário
 * perde trabalho. Cobre:
 *  1. cena completa (hierarquia 3 níveis, transforms, classes com os 5 tipos de
 *     campo, LINKERF com portas e links, ids preservados);
 *  2. escapes de string (aspas, barra invertida, quebras de linha, unicode);
 *  3. arquivo PARCIAL/DEGRADADO (linhas perdidas): parse não falha, defaults
 *     aplicados — recuperação, não perda total;
 *  4. arquivo sem a mágica CK1: parse retorna false (é lixo, não é .ck);
 *  5. pai ausente: nick vira raiz em vez de sumir ou crashar.
 */
class CkFormatRoundtripTest {

    /** Cena de referência com tudo que o formato suporta. */
    private fun cenaCheia(): Scene {
        val scene = Scene("Cena Teste \"A\"")
        val mundo = scene.createRootNick("Mundo")
        mundo.transform.setLocalPosition(1.25f, -3.5f, 0.75f)

        val cubo = scene.createNick("Cubo", mundo)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        cubo.transform.rotation.set(0.3826834f, 0f, 0f, 0.9238795f) // 45° X
        cubo.transform.scale.set(2f, 2f, 2f)
        cubo.active = false
        cubo.visible = false
        val spin = NickClassInstance("Spin")
        spin.fields["speed"] = NickValue.FloatValue(90.5f)
        spin.fields["axis"] = NickValue.StringValue("Y")
        cubo.addClassInstance(spin)

        val grupo = scene.createNick("Grupo", mundo)
        val filho = scene.createNick("Filho com \"aspas\" e \\ barra", grupo)
        filho.transform.setLocalPosition(-10f, 99.75f, 0.001f)
        val dados = NickClassInstance("Dados")
        dados.fields["contador"] = NickValue.IntValue(-7)
        dados.fields["ligado"] = NickValue.BoolValue(true)
        dados.fields["vazio"] = NickValue.StringValue("")
        dados.fields["cor"] = NickValue.Vec3Value(0.25f, 0.5f, 0.75f)
        dados.fields["quebra\nlinha"] = NickValue.StringValue("linha1\nlinha2\ttab")
        filho.addClassInstance(dados)

        // LINKERF: porta de saída no cubo ligada à entrada do filho
        val portaSaida = LinkerfPort(
            ReferenceId.of("aaaaaaaa-0000-0000-0000-000000000001"),
            ReferenceId.of("aaaaaaaa-0000-0000-0000-0000000000c1"),
            "ao girar", LinkerfPortKind.EVENT_OUT, LinkerfPortType.TRIGGER
        )
        val portaEntrada = LinkerfPort(
            ReferenceId.of("aaaaaaaa-0000-0000-0000-000000000002"),
            ReferenceId.of("aaaaaaaa-0000-0000-0000-0000000000f1"),
            "ao receber", LinkerfPortKind.EVENT_IN, LinkerfPortType.TRIGGER
        )
        scene.linkerf.addPort(portaSaida)
        scene.linkerf.addPort(portaEntrada)
        scene.linkerf.addLinkDirect(
            LinkerfLink(
                ReferenceId.of("aaaaaaaa-0000-0000-0000-000000000003"),
                portaSaida.id, portaEntrada.id
            )
        )
        return scene
    }

    private fun roundtrip(scene: Scene): Scene {
        val text = CkFormat.writeToString(scene)
        val loaded = CkFormat.parseToNewScene(text, "fallback")
        assertNotNull("ida e volta deve produzir cena válida", loaded)
        return loaded!!
    }

    // ---------------------------------------------------------------- 1. cena cheia

    @Test
    fun `ida e volta preserva nome da cena hierarquia transforms e ids`() {
        val original = cenaCheia()
        val loaded = roundtrip(original)

        assertEquals("Cena Teste \"A\"", loaded.name)

        val roots = loaded.roots
        assertEquals(1, roots.size)
        val mundo = roots[0]
        assertEquals("Mundo", mundo.name)
        assertEquals(1.25f, mundo.transform.position.x, 1e-5f)
        assertEquals(-3.5f, mundo.transform.position.y, 1e-5f)
        assertEquals(0.75f, mundo.transform.position.z, 1e-5f)
        // ids preservados (base das referências do LINKERF)
        assertEquals(original.roots[0].id, mundo.id)

        assertEquals(2, mundo.children.size)
        val cubo = mundo.children.first { it.name == "Cubo" }
        val grupo = mundo.children.first { it.name == "Grupo" }
        assertFalse(cubo.active)
        assertFalse(cubo.visible)
        assertEquals(0.3826834f, cubo.transform.rotation.x, 1e-5f)
        assertEquals(0.9238795f, cubo.transform.rotation.w, 1e-5f)
        assertEquals(2f, cubo.transform.scale.y, 1e-5f)
        assertEquals(mundo, cubo.parent)

        assertEquals(1, grupo.children.size)
        val filho = grupo.children[0]
        assertEquals("Filho com \"aspas\" e \\ barra", filho.name)
        assertEquals(-10f, filho.transform.position.x, 1e-5f)
        assertEquals(99.75f, filho.transform.position.y, 1e-5f)
        assertEquals(0.001f, filho.transform.position.z, 1e-5f)
    }

    @Test
    fun `ida e volta preserva classes e os 5 tipos de campo`() {
        val loaded = roundtrip(cenaCheia())
        val cubo = loaded.roots[0].children.first { it.name == "Cubo" }
        val spin = cubo.findClassInstance("Spin")
        assertNotNull(spin)
        assertEquals(90.5f, (spin!!.fields["speed"] as NickValue.FloatValue).value, 1e-5f)
        assertEquals("Y", (spin.fields["axis"] as NickValue.StringValue).value)

        val filho = loaded.roots[0].children.first { it.name == "Grupo" }.children[0]
        val dados = filho.findClassInstance("Dados")
        assertNotNull(dados)
        assertEquals(-7, (dados!!.fields["contador"] as NickValue.IntValue).value)
        assertEquals(true, (dados.fields["ligado"] as NickValue.BoolValue).value)
        assertEquals("", (dados.fields["vazio"] as NickValue.StringValue).value)
        val cor = dados.fields["cor"] as NickValue.Vec3Value
        assertEquals(0.25f, cor.x, 1e-5f)
        assertEquals(0.5f, cor.y, 1e-5f)
        assertEquals(0.75f, cor.z, 1e-5f)
        // chave e valor com escapes (\n, \t) preservados
        assertEquals("linha1\nlinha2\ttab", (dados.fields["quebra\nlinha"] as NickValue.StringValue).value)
    }

    @Test
    fun `ida e volta preserva portas e links do LINKERF`() {
        val loaded = roundtrip(cenaCheia())
        val ports = loaded.linkerf.allPorts()
        val links = loaded.linkerf.allLinks()
        assertEquals(2, ports.size)
        assertEquals(1, links.size)
        val saida = ports.first { it.name == "ao girar" }
        val entrada = ports.first { it.name == "ao receber" }
        assertEquals(LinkerfPortKind.EVENT_OUT, saida.kind)
        assertEquals(LinkerfPortKind.EVENT_IN, entrada.kind)
        assertEquals(LinkerfPortType.TRIGGER, saida.type)
        assertEquals(saida.id, links[0].fromPortId)
        assertEquals(entrada.id, links[0].toPortId)
    }

    // ---------------------------------------------------------------- 2. escapes e tokenizador

    @Test
    fun `quote e unquote são inversos para textos hostis`() {
        val hostis = listOf(
            "texto simples",
            "com \"aspas\"",
            "barra \\ invertida",
            "quebra\nde linha e\ttab",
            "unicode çãé KENKERN já",
            "", " ", "=\" engana tokenizador"
        )
        for (h in hostis) {
            assertEquals("roundtrip de escape falhou para: $h", h, CkFormat.unquote(CkFormat.quote(h)))
        }
    }

    @Test
    fun `tokenizador lê pares com aspas espaços e iguais dentro do valor`() {
        val linha = "NICK id=abc name=\"nome = com \\\"aspas\\\" e espaços\" active=true"
        val tokens = CkFormat.tokenize(linha)
        assertEquals("abc", tokens.first { it.first == "id" }.second)
        // Contrato do tokenize: valor com ESCAPES preservados e SEM aspas externas
        assertEquals("nome = com \\\"aspas\\\" e espaços", tokens.first { it.first == "name" }.second)
        assertEquals("true", tokens.first { it.first == "active" }.second)
        // unescape decodifica (é o que o parse aplica)
        assertEquals(
            "nome = com \"aspas\" e espaços",
            CkFormat.unescape(tokens.first { it.first == "name" }.second)
        )
    }

    @Test
    fun `unescape decodifica todos os escapes e tolera barra solta`() {
        assertEquals("a\"b\\c\nd\te\rf", CkFormat.unescape("a\\\"b\\\\c\\nd\\te\\rf"))
        assertEquals("sem escapes", CkFormat.unescape("sem escapes"))
        // barra no fim não engole caracteres nem lança
        assertEquals("barra solta \\", CkFormat.unescape("barra solta \\"))
        // escape desconhecido: perde a barra, mantém o caractere
        assertEquals("x", CkFormat.unescape("\\x"))
    }

    // ---------------------------------------------------------------- 3. arquivo degradado

    @Test
    fun `arquivo PARCIAL perde linhas mas não a cena - defaults aplicados`() {
        val text = CkFormat.writeToString(cenaCheia())
        // derruba TODAS as linhas TRANSFORM e uma linha CLASS: o nick sobrevive
        // com transform identidade e sem a classe perdida.
        val degradado = text.lineSequence()
            .filter { !it.startsWith("TRANSFORM") && !it.contains("type=\"Dados\"") }
            .joinToString("\n")
        val scene = Scene("tmp")
        assertTrue("parse de arquivo parcial deve retornar true (recuperação)", CkFormat.parse(degradado, scene))
        assertEquals(1, scene.roots.size)
        val mundo = scene.roots[0]
        // transform virou default (posição 0, escala 1, rotação identidade)
        assertEquals(0f, mundo.transform.position.x, 1e-6f)
        assertEquals(1f, mundo.transform.scale.x, 1e-6f)
        assertEquals(1f, mundo.transform.rotation.w, 1e-6f)
        val filho = mundo.children.first { it.name == "Grupo" }.children[0]
        assertNull(filho.findClassInstance("Dados"))
        // a classe Spin do cubo (linha CLASS intacta) sobreviveu
        val cubo = mundo.children.first { it.name == "Cubo" }
        assertNotNull(cubo.findClassInstance("Spin"))
    }

    @Test
    fun `arquivo truncado no meio da linha não derruba o parse`() {
        val text = CkFormat.writeToString(cenaCheia())
        val truncado = text.dropLast(30) // corta no meio da última linha
        val scene = Scene("tmp")
        assertTrue(CkFormat.parse(truncado, scene))
        // a cena tem ao menos a raiz
        assertTrue(scene.roots.isNotEmpty())
    }

    // ---------------------------------------------------------------- 4. lixo

    @Test
    fun `sem a mágica CK1 o parse falha com false (não é ck)`() {
        val scene = Scene("tmp")
        assertFalse(CkFormat.parse("SCENE name=\"x\"\nNICK id=a name=\"a\"\nENDCK\n", scene))
        assertFalse(CkFormat.parse("", scene))
    }

    // ---------------------------------------------------------------- 5. pai ausente

    @Test
    fun `nick com pai inexistente vira raiz em vez de sumir`() {
        val text = """
            CK1
            SCENE name="Orfãos"
            NICK id=1 name="Pai Perdido" active=true parent=999
            NICK id=2 name="Filho Órfão" active=true parent=1
            ENDCK
        """.trimIndent()
        val scene = Scene("tmp")
        assertTrue(CkFormat.parse(text, scene))
        // pai 999 não existe: o nick 1 vira raiz; o 2 continua filho dele
        assertEquals(1, scene.roots.size)
        assertEquals("Pai Perdido", scene.roots[0].name)
        assertEquals(1, scene.roots[0].children.size)
        assertEquals("Filho Órfão", scene.roots[0].children[0].name)
    }
}
