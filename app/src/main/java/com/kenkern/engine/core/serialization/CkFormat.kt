package com.kenkern.engine.core.serialization

import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.diag.KenkernLog
import java.io.StringReader
import java.io.StringWriter

/**
 * Formato .CK (arquivo de cena da KENKERN): texto estruturado, legível e versionável.
 *
 * CK2 (T0.2, adendo v4 — DECISIONS #25): o NICK ganha o campo `kind` com a
 * CHAVE ESTÁVEL do tipo em inglês (ex.: `body.player`), NUNCA o rótulo;
 * `name` passa a ser OPCIONAL (ausente = rótulo do tipo no idioma atual —
 * DECISIONS #26; o texto do usuário é gravado e nunca traduzido).
 *
 * Estrutura:
 *   CK2
 *   SCENE name="Cena"
 *   NICK id=<uuid> kind=nick.mesh name="Opcional" active=true visible=true parent=NONE
 *   TRANSFORM px=0 py=0 pz=0 qx=0 qy=0 qz=0 qw=1 sx=1 sy=1 sz=1
 *   CLASS type="Spin"
 *   FIELD name="speed" type=FLOAT value="45.0"
 *   ENDCLASS
 *   ENDNICK
 *   LINKERF
 *   PORT id=.. owner=.. name=.. kind=EVENT_OUT type=TRIGGER
 *   LINK id=.. from=.. to=..
 *   ENDLINKERF
 *   ENDCK
 *
 * CK1 (legado, ainda lido — o parser antigo continua funcionando): sem kind;
 * na carga, kind = nick.empty, ou nick.mesh se o Nick tem a classe Malha;
 * nomes automáticos antigos ("Mundo", "Cubo", "Nick N") viram NULOS quando
 * exatamente iguais ao padrão do template (DECISIONS #26).
 */
object CkFormat {

    const val MAGIC = "CK2"

    /** Magic legado (Fase 1): aceito na leitura com MIGRAÇÃO para CK2 em memória. */
    const val MAGIC_LEGADO = "CK1"
    private const val PARENT_NONE = "NONE"
    private const val TAG = "Kenkern/CkFormat"

    // ---------- Escrita ----------

    fun writeToString(scene: Scene): String {
        val sw = StringWriter()
        write(scene, sw)
        return sw.toString()
    }

    fun write(scene: Scene, out: Appendable) {
        out.appendLine(MAGIC)
        out.appendLine("SCENE name=${quote(scene.name)}")
        for (root in scene.roots) writeNick(root, out)
        writeLinkerf(scene, out)
        out.appendLine("ENDCK")
    }

    private fun writeNick(nick: com.kenkern.engine.core.Nick, out: Appendable) {
        val parentId = nick.parent?.id?.value ?: PARENT_NONE
        // CK2: kind SEMPRE; name só quando o usuário renomeou (null = rótulo do tipo).
        val namePart = nick.name?.let { " name=${quote(it)}" } ?: ""
        out.appendLine(
            "NICK id=${nick.id.value} kind=${nick.kind}$namePart active=${nick.active} visible=${nick.visible} parent=$parentId"
        )
        val t = nick.transform
        out.appendLine(
            "TRANSFORM px=${t.position.x} py=${t.position.y} pz=${t.position.z}" +
                " qx=${t.rotation.x} qy=${t.rotation.y} qz=${t.rotation.z} qw=${t.rotation.w}" +
                " sx=${t.scale.x} sy=${t.scale.y} sz=${t.scale.z}"
        )
        for (inst in nick.classes) {
            out.appendLine("CLASS type=${quote(inst.typeName)}")
            for ((key, value) in inst.fields) {
                out.appendLine("FIELD name=${quote(key)} type=${value.typeName} value=${quote(value.serializeValue())}")
            }
            out.appendLine("ENDCLASS")
        }
        for (child in nick.children) writeNick(child, out)
        out.appendLine("ENDNICK")
    }

    private fun writeLinkerf(scene: Scene, out: Appendable) {
        val ports: List<com.kenkern.engine.core.linkerf.LinkerfPort> = scene.linkerf.allPorts()
        val links: List<com.kenkern.engine.core.linkerf.LinkerfLink> = scene.linkerf.allLinks()
        if (ports.isEmpty() && links.isEmpty()) return
        out.appendLine("LINKERF")
        ports.forEach { port ->
            out.appendLine(
                "PORT id=${port.id.value} owner=${port.ownerId.value} name=${quote(port.name)} kind=${port.kind.name} type=${port.type.name}"
            )
        }
        links.forEach { link ->
            out.appendLine("LINK id=${link.id.value} from=${link.fromPortId.value} to=${link.toPortId.value}")
        }
        out.appendLine("ENDLINKERF")
    }

    fun quote(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        sb.append('"')
        for (c in raw) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun unquote(raw: String): String {
        if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return raw
        return unescape(raw.substring(1, raw.length - 1))
    }

    /**
     * Remove os escapes (\\ \" \n \r \t) de um valor JÁ SEM aspas externas —
     * a saída do [tokenize]. O [unquote] antigo era aplicado direto ao token e
     * exigia aspas que o tokenizer já tinha tirado: strings com " ou quebra de
     * linha voltavam ESCAPADAS do arquivo (corrupção silenciosa — achado pelo
     * teste de ida e volta F2.1).
     */
    fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            if (c == '\\' && i + 1 < n) {
                when (val next = raw[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // ---------- Leitura ----------

    /** Preenche uma cena existente a partir de texto .ck. Retorna true em caso de sucesso. */
    fun parse(text: String, scene: Scene): Boolean = runCatching {
        scene.roots.clear()
        scene.references.clear()
        scene.linkerf.clear()

        val nicks = LinkedHashMap<String, com.kenkern.engine.core.Nick>()
        val parents = HashMap<String, String>()
        var currentNick: com.kenkern.engine.core.Nick? = null
        var currentClass: NickClassInstance? = null
        var inLinkerf = false
        var sawMagic = false
        var formatoLegado = false

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            // Linhas corruptas são ignoradas individualmente (arquivo parcial recuperável) —
            // mas o descarte É REGISTRADO (regra: runCatching não engole em silêncio).
            val tokens = runCatching { tokenize(line) }
                .onFailure { KenkernLog.w(TAG, "linha .ck corrupta descartada (parse segue parcial)", it) }
                .getOrNull() ?: return@forEach
            when (tokens.firstOrNull()?.first) {
                MAGIC -> sawMagic = true
                MAGIC_LEGADO -> {
                    sawMagic = true
                    formatoLegado = true
                }
                "SCENE" -> {
                    val name = tokenValue(tokens, "name")
                    if (name != null) {
                        scene.name = unescape(name)
                    }
                }
                "NICK" -> {
                    val id = tokenValue(tokens, "id") ?: return@forEach
                    // CK2: kind lido do arquivo; CK1: provisório até a migração abaixo.
                    val kind = tokenValue(tokens, "kind")?.let { unescape(it) }
                    // Name AUSENTE = null (rótulo do tipo no idioma atual).
                    val name = tokenValue(tokens, "name")?.let { unescape(it) }
                    val active = tokenValue(tokens, "active")?.toBooleanStrictOrNull() ?: true
                    val visible = tokenValue(tokens, "visible")?.toBooleanStrictOrNull() ?: true
                    val parent = tokenValue(tokens, "parent")
                    val nick = com.kenkern.engine.core.Nick(
                        ReferenceId.of(id), name, active,
                        kind ?: com.kenkern.engine.core.NickKindRegistry.NICK_EMPTY
                    )
                    nick.visible = visible
                    nicks[id] = nick
                    if (parent != null && parent != PARENT_NONE) parents[id] = parent
                    currentNick = nick
                    currentClass = null
                }
                "TRANSFORM" -> {
                    val nick = currentNick ?: return@forEach
                    val t = nick.transform
                    fun f(key: String, def: Float): Float = tokenValue(tokens, key)?.toFloatOrNull() ?: def
                    t.position.set(f("px", 0f), f("py", 0f), f("pz", 0f))
                    t.rotation.set(f("qx", 0f), f("qy", 0f), f("qz", 0f), f("qw", 1f))
                    t.scale.set(f("sx", 1f), f("sy", 1f), f("sz", 1f))
                }
                "CLASS" -> {
                    val nick = currentNick ?: return@forEach
                    val type = tokenValue(tokens, "type")?.let { unescape(it) } ?: return@forEach
                    val inst = NickClassInstance(type)
                    nick.addClassInstance(inst)
                    currentClass = inst
                }
                "FIELD" -> {
                    val inst = currentClass ?: return@forEach
                    val name = tokenValue(tokens, "name")?.let { unescape(it) } ?: return@forEach
                    val type = tokenValue(tokens, "type") ?: return@forEach
                    val value = tokenValue(tokens, "value")?.let { unescape(it) } ?: ""
                    NickValue.parse(type, value)?.let { inst.fields[name] = it }
                }
                "ENDCLASS" -> currentClass = null
                "ENDNICK" -> currentNick = null
                "LINKERF" -> inLinkerf = true
                "PORT" -> {
                    if (!inLinkerf) return@forEach
                    val id = tokenValue(tokens, "id") ?: return@forEach
                    val owner = tokenValue(tokens, "owner") ?: return@forEach
                    val name = tokenValue(tokens, "name")?.let { unescape(it) } ?: "porta"
                    val kind = tokenValue(tokens, "kind")?.let {
                        runCatching { com.kenkern.engine.core.linkerf.LinkerfPortKind.valueOf(it) }
                            .onFailure { e ->
                                KenkernLog.w(TAG, "PORT com kind desconhecido '$it' — usando DATA_IN", e)
                            }
                            .getOrNull()
                    } ?: com.kenkern.engine.core.linkerf.LinkerfPortKind.DATA_IN
                    val type = tokenValue(tokens, "type")?.let {
                        runCatching { com.kenkern.engine.core.linkerf.LinkerfPortType.valueOf(it) }
                            .onFailure { e ->
                                KenkernLog.w(TAG, "PORT com type desconhecido '$it' — usando TRIGGER", e)
                            }
                            .getOrNull()
                    } ?: com.kenkern.engine.core.linkerf.LinkerfPortType.TRIGGER
                    scene.linkerf.addPort(
                        com.kenkern.engine.core.linkerf.LinkerfPort(
                            ReferenceId.of(id), ReferenceId.of(owner), name, kind, type
                        )
                    )
                }
                "LINK" -> {
                    if (!inLinkerf) return@forEach
                    val id = tokenValue(tokens, "id") ?: return@forEach
                    val from = tokenValue(tokens, "from") ?: return@forEach
                    val to = tokenValue(tokens, "to") ?: return@forEach
                    scene.linkerf.addLinkDirect(
                        com.kenkern.engine.core.linkerf.LinkerfLink(
                            ReferenceId.of(id), ReferenceId.of(from), ReferenceId.of(to)
                        )
                    )
                }
                "ENDLINKERF" -> inLinkerf = false
            }
        }

        if (!sawMagic) {
            // Sem magic = não é um .ck (ou está truncado no início): recusar COM registro.
            KenkernLog.e(TAG, "arquivo .ck sem magic CK1/CK2 — parse recusado (cena fica vazia)")
            return@runCatching false
        }

        // MIGRAÇÃO CK1 → CK2 (DECISIONS #25/#26): kind = nick.mesh se o Nick tem
        // a classe Malha, senão nick.empty; nomes automáticos do template antigo
        // ("Mundo", "Cubo", "Nick N") viram NULOS quando exatamente iguais ao
        // padrão — o rótulo do tipo passa a ser exibido no idioma atual.
        if (formatoLegado) {
            for (nick in nicks.values) {
                if (nick.kind == com.kenkern.engine.core.NickKindRegistry.NICK_EMPTY &&
                    nick.findClassInstance(com.kenkern.engine.core.MalhaNick.CLASSE) != null
                ) {
                    nick.kind = com.kenkern.engine.core.NickKindRegistry.NICK_MESH
                }
                val nome = nick.name
                if (nome != null && ehNomeAutomaticoLegado(nome)) {
                    nick.name = null
                }
            }
        }

        // Valida as cadeias de pais antes de criar qualquer aresta. O mapa já pode
        // conter referências para frente; cada id é visitado no máximo duas vezes.
        val checked = HashSet<String>()
        val chain = LinkedHashSet<String>()
        for (id in nicks.keys) {
            if (id in checked) continue
            chain.clear()
            var next: String? = id
            while (next != null && next !in checked && next in nicks) {
                if (!chain.add(next)) {
                    KenkernLog.w(TAG, "ciclo de hierarquia no .ck — cena recusada")
                    return@runCatching false
                }
                next = parents[next]
            }
            checked.addAll(chain)
        }
        // Registrar ANTES de ligar: registerNick visita os filhos recursivamente.
        for (nick in nicks.values) scene.references.registerNick(nick)
        // A ordem do arquivo (não a de um HashMap de IDs) é a ordem dos irmãos.
        for ((id, nick) in nicks) {
            val parent = parents[id]?.let { nicks[it] }
            if (parent == null) scene.roots.add(nick) else {
                parent.children.add(nick)
                nick.parent = parent
            }
        }
        true
    }.onFailure {
        android.util.Log.e(TAG, "parse falhou", it)
        com.kenkern.engine.diag.KenkernLog.e(TAG, "parse do .ck falhou (cena fica vazia)", it)
    }.getOrDefault(false)

    fun parseToNewScene(text: String, fallbackName: String = "Cena"): Scene? {
        val scene = Scene(fallbackName)
        return if (parse(text, scene)) scene else null
    }

    /**
     * Nome automático do TEMPLATE antigo (CK1): "Mundo", "Cubo" ou "Nick N"
     * (N numérico). Nomes DIFERENTES destes são do usuário e permanecem
     * (DECISIONS #26 — "viram nulos quando iguais ao padrão").
     */
    private val nomeAutomaticoLegado = Regex("Nick \\d+")

    private fun ehNomeAutomaticoLegado(nome: String): Boolean =
        nome == "Mundo" || nome == "Cubo" || nomeAutomaticoLegado.matches(nome)

    private fun setSceneName(scene: Scene, value: String) {
        scene.name = value
    }

    /** Tokeniza "key=value key=\"valor com espaços\"". Retorna pares na ordem. */
    fun tokenize(line: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(8)
        var i = 0
        val n = line.length
        while (i < n) {
            if (line[i].isWhitespace()) {
                i++
                continue
            }
            var j = i
            while (j < n && !line[j].isWhitespace() && line[j] != '=') j++
            if (j >= n || line[j] != '=') {
                // Palavra solta (como marcadores ENDNICK): registra com valor vazio.
                out.add(line.substring(i, j) to "")
                i = if (j < n) j + 1 else j
                continue
            }
            val key = line.substring(i, j)
            j++ // pula '='
            if (j < n && line[j] == '"') {
                val sb = StringBuilder()
                j++
                while (j < n) {
                    val c = line[j]
                    if (c == '\\' && j + 1 < n) {
                        sb.append(c).append(line[j + 1])
                        j += 2
                    } else if (c == '"') {
                        j++
                        break
                    } else {
                        sb.append(c)
                        j++
                    }
                }
                out.add(key to sb.toString())
                i = j
            } else {
                var k = j
                while (k < n && !line[k].isWhitespace()) k++
                out.add(key to line.substring(j, k))
                i = k
            }
        }
        return out
    }

    private fun tokenValue(tokens: List<Pair<String, String>>, key: String): String? {
        for ((k, v) in tokens) if (k == key) return v
        return null
    }
}
