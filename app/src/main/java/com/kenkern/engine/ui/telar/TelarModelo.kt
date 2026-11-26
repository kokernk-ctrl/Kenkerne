package com.kenkern.engine.ui.telar

/**
 * U1 (v0.1.27) — TELAR: MODELO PURO DO EDITOR DE UI (sem Android/Compose —
 * JVM-testável e usado pela checagem "ui" do Diagnóstico).
 *
 * A UI vive numa árvore 2D PRÓPRIA (separada da Hierarquia 3D), desenhada na
 * resolução de referência 1920x1080 paisagem e salva em `ui · .kui` (texto
 * estruturado, versionado KUI1). As âncoras (9 posições + esticar) e margens
 * resolvem a posição em QUALQUER proporção (16:9, 18:9, 20:9) — a pré-
 * visualização e o Play usam a MESMA resolução do modelo.
 *
 * Undo/redo POR COMMAND ([UiCommand] com executar/desfazer sobre o documento).
 */

// ------------------------------------------------------------------ ELEMENTOS

/** Os 8 elementos do TELAR (U1.2). */
enum class ElementoUi(val key: String) {
    PAINEL("ui.painel"),
    TEXTO("ui.texto"),
    IMAGEM("ui.imagem"),
    BOTAO("ui.botao"),
    BARRA("ui.barra"),
    SLIDER("ui.slider"),
    JOYSTICK("ui.joystick"),
    AREA_TOQUE("ui.area_toque");

    companion object {
        fun de(key: String): ElementoUi? = entries.firstOrNull { it.key == key }
    }
}

/** Nove âncoras + esticar (U1.2). */
enum class AncoraUi(val key: String) {
    TOPO_ESQUERDA("topo_esquerda"),
    TOPO_CENTRO("topo_centro"),
    TOPO_DIREITA("topo_direita"),
    CENTRO_ESQUERDA("centro_esquerda"),
    CENTRO("centro"),
    CENTRO_DIREITA("centro_direita"),
    BAIXO_ESQUERDA("baixo_esquerda"),
    BAIXO_CENTRO("baixo_centro"),
    BAIXO_DIREITA("baixo_direita"),
    ESTICAR("esticar");

    companion object {
        fun de(key: String): AncoraUi? = entries.firstOrNull { it.key == key }
    }
}

/** Estilo de texto (U1.3): fontes livres (OFL, embutidas — LICENSES.md). */
class EstiloTextoUi(
    var conteudo: String = "Texto",
    var fonte: String = "Roboto", // nome da fonte (ver FONTES_OFL)
    var tamanho: Int = 32,        // px na resolução 1920x1080
    var peso: String = "normal",  // normal | bold
    var alinhamento: String = "esquerda", // esquerda | centro | direita
    var cor: String = "#FFFFFF",
    var contorno: String = "",    // cor do contorno ("" = sem)
    var sombra: Boolean = false
)

/** Estilo de superfície (U1.3). */
class EstiloUi(
    var cor: String = "#2A2118",       // hex ou "#RRGGBBAA"
    var gradiente: String = "",        // hex final ("" = sem; gradiente cor→gradiente)
    var cantos: Int = 12,              // raio px
    var contornoCor: String = "",      // "" = sem
    var contornoEspessura: Int = 1,    // px
    var opacidade: Float = 1f
)

/** Um elemento da árvore de UI. */
class ElementoUiInstancia(
    val id: Int,
    val tipo: ElementoUi,
    var nome: String,
    var x: Int, var y: Int,       // px na resolução de referência (topo-esquerda)
    var largura: Int, var altura: Int,
    var ancora: AncoraUi = AncoraUi.TOPO_ESQUERDA,
    var margemX: Int = 0, var margemY: Int = 0, // dp do canto ancorado (área segura)
    var oculto: Boolean = false,
    var travado: Boolean = false,
    val estilo: EstiloUi = EstiloUi(),
    val texto: EstiloTextoUi = EstiloTextoUi(),
    // U1.5: LIGAÇÕES — botão chama função Lua; texto/barra lidas de variável
    var aoClicar: String = "",     // nome da função Lua (on_click)
    var ligaTexto: String = "",    // variável da UI de jogo (ui.contagem/texto)
    var ligaValor: String = "",    // 0..1 da barra/slider
    // U1.4: joystick com as propriedades do adendo v4 (migração sem perder dados)
    var joystickTipo: String = "fixo",
    var joystickTamanho: Int = 112,
    var joystickOpacidade: Float = 0.9f,
    var joystickZonaMorta: Float = 0.15f,
    var joystickOitoDirecoes: Boolean = false,
    var joystickControla: String = "",
    var joystickSeguirNick: Boolean = false
)

// ------------------------------------------------------------------ DOCUMENTO

/** Resolução de referência do TELAR (U1.1). */
const val LARGURA_REF = 1920
const val ALTURA_REF = 1080

/**
 * Documento de UI: árvore plana com ORDEM Z (a lista É a ordem de desenho —
 * índice 0 fica atrás; o painel de camadas reordena).
 */
class DocumentoUi(val nome: String = "ui") {

    val elementos = ArrayList<ElementoUiInstancia>()
    private val desfazer = ArrayList<UiCommand>()
    private val refazer = ArrayList<UiCommand>()

    /** Próximo id (monotônico, nunca reutiliza). */
    fun proximoId(): Int = (elementos.maxOfOrNull { it.id } ?: 0) + 1

    /** Executa um Command (undo/redo POR COMMAND — U1.7). */
    fun executar(comando: UiCommand) {
        comando.executar(this)
        desfazer.add(comando)
        if (desfazer.size > 100) desfazer.removeAt(0)
        refazer.clear()
    }

    fun desfazer(): Boolean {
        val c = desfazer.removeLastOrNull() ?: return false
        c.desfazer(this)
        refazer.add(c)
        return true
    }

    fun refazer(): Boolean {
        val c = refazer.removeLastOrNull() ?: return false
        c.executar(this)
        desfazer.add(c)
        return true
    }

    /** Seleção por id (o editor de UI mantém UMA seleção). */
    var selecaoId: Int = -1

    fun selecionado(): ElementoUiInstancia? = elementos.firstOrNull { it.id == selecaoId }

    /** Reordena (painel de camadas: sobe/desce na ordem Z). */
    fun moverNaOrdemZ(id: Int, delta: Int): Boolean {
        val i = elementos.indexOfFirst { it.id == id }
        if (i < 0) return false
        val j = (i + delta).coerceIn(0, elementos.size - 1)
        if (i == j) return false
        val e = elementos.removeAt(i)
        elementos.add(j, e)
        return true
    }
}

/** Command do TELAR (U1.7) — executa/desfaz sobre o documento. */
interface UiCommand {
    fun executar(doc: DocumentoUi)
    fun desfazer(doc: DocumentoUi)
}

/** Adiciona um elemento (undo = remove). */
class ComandoAdicionar(val elemento: ElementoUiInstancia) : UiCommand {
    override fun executar(doc: DocumentoUi) {
        doc.elementos.add(elemento)
        doc.selecaoId = elemento.id
    }
    override fun desfazer(doc: DocumentoUi) {
        doc.elementos.remove(elemento)
        if (doc.selecaoId == elemento.id) doc.selecaoId = -1
    }
}

/** Remove um elemento (undo = devolve na MESMA posição Z). */
class ComandoRemover(val id: Int) : UiCommand {
    private var indice = -1
    private var elemento: ElementoUiInstancia? = null
    override fun executar(doc: DocumentoUi) {
        indice = doc.elementos.indexOfFirst { it.id == id }
        if (indice >= 0) elemento = doc.elementos.removeAt(indice)
        if (doc.selecaoId == id) doc.selecaoId = -1
    }
    override fun desfazer(doc: DocumentoUi) {
        val e = elemento ?: return
        if (indice < 0 || indice > doc.elementos.size) doc.elementos.add(e) else doc.elementos.add(indice, e)
    }
}

/** Move/redimensiona um elemento (arrasto no canvas; undo devolve x/y/w/h). */
class ComandoTransformar(val id: Int, val novoX: Int, val novoY: Int, val novoW: Int, val novoH: Int) : UiCommand {
    private var antigoX = 0
    private var antigoY = 0
    private var antigoW = 0
    private var antigoH = 0
    private var capturado = false
    override fun executar(doc: DocumentoUi) {
        val e = doc.elementos.firstOrNull { it.id == id } ?: return
        if (!capturado) {
            antigoX = e.x; antigoY = e.y; antigoW = e.largura; antigoH = e.altura
            capturado = true
        }
        e.x = novoX; e.y = novoY; e.largura = novoW; e.altura = novoH
    }
    override fun desfazer(doc: DocumentoUi) {
        val e = doc.elementos.firstOrNull { it.id == id } ?: return
        e.x = antigoX; e.y = antigoY; e.largura = antigoW; e.altura = antigoH
    }
}

/** Edita uma propriedade (Inspetor de UI; undo devolve o valor anterior). */
class ComandoPropriedade(val id: Int, val campo: String, val antes: Any?, val depois: Any?) : UiCommand {
    private fun aplicar(doc: DocumentoUi, valor: Any?) {
        val e = doc.elementos.firstOrNull { it.id == id } ?: return
        when (campo) {
            "nome" -> e.nome = valor as String
            "x" -> e.x = valor as Int
            "y" -> e.y = valor as Int
            "largura" -> e.largura = valor as Int
            "altura" -> e.altura = valor as Int
            "ancora" -> e.ancora = AncoraUi.de(valor as String) ?: e.ancora
            "margemX" -> e.margemX = valor as Int
            "margemY" -> e.margemY = valor as Int
            "oculto" -> e.oculto = valor as Boolean
            "travado" -> e.travado = valor as Boolean
            "cor" -> e.estilo.cor = valor as String
            "gradiente" -> e.estilo.gradiente = valor as String
            "cantos" -> e.estilo.cantos = valor as Int
            "contorno" -> e.estilo.contornoCor = valor as String
            "opacidade" -> e.estilo.opacidade = valor as Float
            "texto" -> e.texto.conteudo = valor as String
            "fonte" -> e.texto.fonte = valor as String
            "tamanhoTexto" -> e.texto.tamanho = valor as Int
            "peso" -> e.texto.peso = valor as String
            "alinhamento" -> e.texto.alinhamento = valor as String
            "corTexto" -> e.texto.cor = valor as String
            "sombraTexto" -> e.texto.sombra = valor as Boolean
            "aoClicar" -> e.aoClicar = valor as String
            "ligaTexto" -> e.ligaTexto = valor as String
            "ligaValor" -> e.ligaValor = valor as String
            "joystickTipo" -> e.joystickTipo = valor as String
            "joystickTamanho" -> e.joystickTamanho = valor as Int
            "joystickOpacidade" -> e.joystickOpacidade = valor as Float
            "joystickZonaMorta" -> e.joystickZonaMorta = valor as Float
            "joystickOitoDirecoes" -> e.joystickOitoDirecoes = valor as Boolean
            "joystickControla" -> e.joystickControla = valor as String
            "joystickSeguirNick" -> e.joystickSeguirNick = valor as Boolean
        }
    }
    override fun executar(doc: DocumentoUi) = aplicar(doc, depois)
    override fun desfazer(doc: DocumentoUi) = aplicar(doc, antes)
}

/** Reordena na ordem Z (painel de camadas; undo desfaz a troca). */
class ComandoOrdemZ(val id: Int, val delta: Int) : UiCommand {
    override fun executar(doc: DocumentoUi) {
        doc.moverNaOrdemZ(id, delta)
    }
    override fun desfazer(doc: DocumentoUi) {
        doc.moverNaOrdemZ(id, -delta)
    }
}

// ------------------------------------------------------------------ ÂNCORAS

/** Retângulo resolvido em px de uma proporção alvo. */
class RectUi(val x: Int, val y: Int, val largura: Int, val altura: Int)

/**
 * U1.2: resolve a posição do elemento para uma proporção ALVO (16:9, 18:9,
 * 20:9…) a partir da âncora + margens (dp) na resolução de REFERÊNCIA
 * 1920x1080. ÁREA SEGURA: mínimo de 8px do canto (barra de gestos) somado à
 * margem, nunca além de um quarto da tela.
 */
fun resolverRect(
    elemento: ElementoUiInstancia,
    larguraAlvo: Int,
    alturaAlvo: Int
): RectUi {
    val escalaX = larguraAlvo.toFloat() / LARGURA_REF
    val escalaY = alturaAlvo.toFloat() / ALTURA_REF
    val w = (elemento.largura * escalaX).toInt().coerceAtLeast(1)
    val h = (elemento.altura * escalaY).toInt().coerceAtLeast(1)
    val m = margemSegura(elemento.margemX, larguraAlvo)
    val mv = margemSegura(elemento.margemY, alturaAlvo)
    val x = when (elemento.ancora) {
        AncoraUi.TOPO_ESQUERDA, AncoraUi.CENTRO_ESQUERDA, AncoraUi.BAIXO_ESQUERDA -> m
        AncoraUi.TOPO_CENTRO, AncoraUi.CENTRO, AncoraUi.BAIXO_CENTRO -> (larguraAlvo - w) / 2
        AncoraUi.TOPO_DIREITA, AncoraUi.CENTRO_DIREITA, AncoraUi.BAIXO_DIREITA -> larguraAlvo - w - m
        AncoraUi.ESTICAR -> m
    }
    val y = when (elemento.ancora) {
        AncoraUi.TOPO_ESQUERDA, AncoraUi.TOPO_CENTRO, AncoraUi.TOPO_DIREITA -> mv
        AncoraUi.CENTRO_ESQUERDA, AncoraUi.CENTRO, AncoraUi.CENTRO_DIREITA -> (alturaAlvo - h) / 2
        AncoraUi.BAIXO_ESQUERDA, AncoraUi.BAIXO_CENTRO, AncoraUi.BAIXO_DIREITA -> alturaAlvo - h - mv
        AncoraUi.ESTICAR -> mv
    }
    val we = if (elemento.ancora == AncoraUi.ESTICAR) larguraAlvo - 2 * m else w
    val he = if (elemento.ancora == AncoraUi.ESTICAR) alturaAlvo - 2 * mv else h
    return RectUi(x, y, we, he)
}

/** Área segura: margem em "dp" (1dp ≈ 2,45px @1080p/440dpi) + 8px de piso. */
private fun margemSegura(margemDp: Int, dimensaoPx: Int): Int {
    val margem = (margemDp.coerceAtLeast(0) * 2.45f).toInt() + 8
    return margem.coerceAtMost(dimensaoPx / 4)
}

/** U1.3: as 8 fontes livres (OFL) embutidas — licenças em LICENSES.md; meta final 24. */
val FONTES_OFL = listOf(
    "Roboto", "RobotoMono", "NotoSans", "NotoSerif", "OpenSans",
    "Lato", "Montserrat", "JetBrainsMono"
)

// ------------------------------------------------------------------ .KUI (salvar/carregar)

/**
 * U1.5: formato .KUI — TEXTO estruturado e versionado (KUI1), uma linha por
 * campo (legível e com diff fácil).
 */
object KuiFormat {

    fun salvar(doc: DocumentoUi): String {
        val sb = StringBuilder(256 * (doc.elementos.size + 1))
        sb.append("KUI1 ").append(doc.nome).append('\n')
        for (e in doc.elementos) {
            sb.append("elem ").append(e.tipo.key).append(' ').append(e.id).append('\n')
            sb.append("nome ").append(e.nome).append('\n')
            sb.append("ancora ").append(e.ancora.key).append('\n')
            sb.append("x ").append(e.x).append('\n')
            sb.append("y ").append(e.y).append('\n')
            sb.append("w ").append(e.largura).append('\n')
            sb.append("h ").append(e.altura).append('\n')
            sb.append("margemx ").append(e.margemX).append('\n')
            sb.append("margemy ").append(e.margemY).append('\n')
            sb.append("oculto ").append(e.oculto).append('\n')
            sb.append("travado ").append(e.travado).append('\n')
            sb.append("cor ").append(e.estilo.cor).append('\n')
            sb.append("gradiente ").append(e.estilo.gradiente).append('\n')
            sb.append("cantos ").append(e.estilo.cantos).append('\n')
            sb.append("contorno ").append(e.estilo.contornoCor).append('\n')
            sb.append("opacidade ").append(e.estilo.opacidade).append('\n')
            sb.append("texto ").append(e.texto.conteudo.replace('\n', '\\')).append('\n')
            sb.append("fonte ").append(e.texto.fonte).append('\n')
            sb.append("tamanhotexto ").append(e.texto.tamanho).append('\n')
            sb.append("peso ").append(e.texto.peso).append('\n')
            sb.append("alinhamento ").append(e.texto.alinhamento).append('\n')
            sb.append("cortexto ").append(e.texto.cor).append('\n')
            sb.append("sombra ").append(e.texto.sombra).append('\n')
            sb.append("aoclicar ").append(e.aoClicar).append('\n')
            sb.append("ligatexto ").append(e.ligaTexto).append('\n')
            sb.append("ligavalor ").append(e.ligaValor).append('\n')
            sb.append("joysticktipo ").append(e.joystickTipo).append('\n')
            sb.append("joysticktamanho ").append(e.joystickTamanho).append('\n')
            sb.append("joystickopacidade ").append(e.joystickOpacidade).append('\n')
            sb.append("joystickzonamorta ").append(e.joystickZonaMorta).append('\n')
            sb.append("joystickoitodirecoes ").append(e.joystickOitoDirecoes).append('\n')
            sb.append("joystickcontrola ").append(e.joystickControla).append('\n')
            sb.append("joystickseguir ").append(e.joystickSeguirNick).append('\n')
            sb.append("fim\n")
        }
        return sb.toString()
    }

    fun carregar(texto: String): DocumentoUi? {
        val linhas = texto.split('\n')
        if (linhas.isEmpty() || !linhas[0].startsWith("KUI1 ")) return null
        val doc = DocumentoUi(linhas[0].substring(5).trim())
        var e: ElementoUiInstancia? = null
        for (bruta in linhas.subList(1, linhas.size)) {
            val linha = bruta.trim()
            if (linha.isEmpty()) continue
            val espaco = linha.indexOf(' ')
            val chave = if (espaco < 0) linha else linha.substring(0, espaco)
            val valor = if (espaco < 0) "" else linha.substring(espaco + 1)
            when (chave) {
                "elem" -> {
                    val partes = valor.split(' ')
                    val tipo = ElementoUi.de(partes[0]) ?: continue
                    val id = partes.getOrNull(1)?.toIntOrNull() ?: doc.proximoId()
                    e = ElementoUiInstancia(id, tipo, tipo.key, 0, 0, 200, 60)
                    doc.elementos.add(e)
                }
                "fim" -> e = null
                else -> {
                    val alvo = e ?: continue
                    when (chave) {
                        "nome" -> alvo.nome = valor
                        "ancora" -> alvo.ancora = AncoraUi.de(valor) ?: AncoraUi.TOPO_ESQUERDA
                        "x" -> alvo.x = valor.toIntOrNull() ?: 0
                        "y" -> alvo.y = valor.toIntOrNull() ?: 0
                        "w" -> alvo.largura = valor.toIntOrNull() ?: 200
                        "h" -> alvo.altura = valor.toIntOrNull() ?: 60
                        "margemx" -> alvo.margemX = valor.toIntOrNull() ?: 0
                        "margemy" -> alvo.margemY = valor.toIntOrNull() ?: 0
                        "oculto" -> alvo.oculto = valor == "true"
                        "travado" -> alvo.travado = valor == "true"
                        "cor" -> alvo.estilo.cor = valor
                        "gradiente" -> alvo.estilo.gradiente = valor
                        "cantos" -> alvo.estilo.cantos = valor.toIntOrNull() ?: 12
                        "contorno" -> alvo.estilo.contornoCor = valor
                        "opacidade" -> alvo.estilo.opacidade = valor.toFloatOrNull() ?: 1f
                        "texto" -> alvo.texto.conteudo = valor.replace('\\', '\n')
                        "fonte" -> alvo.texto.fonte = valor
                        "tamanhotexto" -> alvo.texto.tamanho = valor.toIntOrNull() ?: 32
                        "peso" -> alvo.texto.peso = valor
                        "alinhamento" -> alvo.texto.alinhamento = valor
                        "cortexto" -> alvo.texto.cor = valor
                        "sombra" -> alvo.texto.sombra = valor == "true"
                        "aoclicar" -> alvo.aoClicar = valor
                        "ligatexto" -> alvo.ligaTexto = valor
                        "ligavalor" -> alvo.ligaValor = valor
                        "joysticktipo" -> alvo.joystickTipo = valor
                        "joysticktamanho" -> alvo.joystickTamanho = valor.toIntOrNull() ?: 112
                        "joystickopacidade" -> alvo.joystickOpacidade = valor.toFloatOrNull() ?: 0.9f
                        "joystickzonamorta" -> alvo.joystickZonaMorta = valor.toFloatOrNull() ?: 0.15f
                        "joystickoitodirecoes" -> alvo.joystickOitoDirecoes = valor == "true"
                        "joystickcontrola" -> alvo.joystickControla = valor
                        "joystickseguir" -> alvo.joystickSeguirNick = valor == "true"
                    }
                }
            }
        }
        return doc
    }
}

// ------------------------------------------------------------------ MIGRAÇÃO

/**
 * U1.4: MIGRAÇÃO — o Joystick Nick antigo (ui.joystick na árvore 3D, classe
 * Joystick do adendo v4) vira elemento de UI SEM PERDER DADOS: tipo, tamanho,
 * opacidade, zona morta, 8 direções, "Controla" e "Seguir Nick" são copiados.
 */
object MigracaoUi {

    fun elementoDeJoystick(
        id: Int,
        nome: String,
        tipo: String, tamanho: Int, opacidade: Float, zonaMorta: Float,
        oitoDirecoes: Boolean, controla: String, seguirNick: Boolean,
        ancora: AncoraUi = AncoraUi.BAIXO_ESQUERDA,
        margemX: Int = 12, margemY: Int = 12
    ): ElementoUiInstancia = ElementoUiInstancia(
        id, ElementoUi.JOYSTICK, nome,
        0, 0, tamanho * 4, tamanho * 4, // 112dp → ~448px na referência
        ancora, margemX, margemY,
        joystickTipo = tipo,
        joystickTamanho = tamanho,
        joystickOpacidade = opacidade,
        joystickZonaMorta = zonaMorta,
        joystickOitoDirecoes = oitoDirecoes,
        joystickControla = controla,
        joystickSeguirNick = seguirNick
    )
}

/** U1.8: UI DO TEMPLATE "Coletar moedas" (contador + botão de pausa + joystick). */
object TemplateUiMoedas {

    fun montar(): DocumentoUi {
        val doc = DocumentoUi("coletar-moedas")
        doc.executar(
            ComandoAdicionar(
                ElementoUiInstancia(
                    1, ElementoUi.PAINEL, "Painel topo",
                    460, 24, 1000, 120, AncoraUi.TOPO_CENTRO, 16, 16,
                    estilo = EstiloUi(cor = "#14100CAA", cantos = 24, contornoCor = "#D98040", contornoEspessura = 1)
                )
            )
        )
        doc.executar(
            ComandoAdicionar(
                ElementoUiInstancia(
                    2, ElementoUi.TEXTO, "Contador de moedas",
                    500, 48, 920, 72, AncoraUi.TOPO_CENTRO, 16, 16,
                    texto = EstiloTextoUi(conteudo = "moedas: 0", tamanho = 48, peso = "bold", cor = "#FFA940"),
                    ligaTexto = "moedas"
                )
            )
        )
        doc.executar(
            ComandoAdicionar(
                ElementoUiInstancia(
                    3, ElementoUi.BOTAO, "Botão pausar",
                    1680, 24, 220, 90, AncoraUi.TOPO_DIREITA, 16, 16,
                    estilo = EstiloUi(cor = "#14100CBB", cantos = 16, contornoCor = "#D98040"),
                    texto = EstiloTextoUi(conteudo = "pausar", tamanho = 36, cor = "#CCCCCC"),
                    aoClicar = "pausar"
                )
            )
        )
        doc.executar(
            ComandoAdicionar(
                ElementoUiInstancia(
                    4, ElementoUi.JOYSTICK, "Joystick",
                    24, 640, 448, 448, AncoraUi.BAIXO_ESQUERDA, 12, 12,
                    joystickTipo = "fixo", joystickTamanho = 112, joystickControla = "Herói"
                )
            )
        )
        return doc
    }
}
