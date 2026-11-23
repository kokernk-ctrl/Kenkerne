package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.diag.KenkernLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import kotlin.math.sqrt

/**
 * Erro de importação FBX com motivo CLARO (regra: nada de engolir erro em
 * silêncio — DECISIONS #17; o importador LANÇA e o chamador registra no
 * Console `KenkernLog` + tela Diagnóstico).
 */
class FbxImportException(motivo: String) : Exception(motivo)

/**
 * IMPORTADOR FBX → .KERN (BACKLOG F2.8, depois do glTF/GLB — DECISIONS #11/#19).
 *
 * Suportado nesta fase:
 *  - FBX BINÁRIO 7.1–7.7 (header "Kaydara FBX Binary", versão u32); records de
 *    node com offsets u32 (versão < 7500) ou u64 (≥ 7500); footer ignorado;
 *  - propriedades i16/bool/i32/f32/f64/i64 + arrays 'd'/'i'/'f'/'l'/'b' com
 *    encoding 0 (raw) e 1 (zlib/Inflater) + strings (nomes "A::B" viram "B") + raw;
 *  - Geometry tipo "Mesh": Vertices + PolygonVertexIndex (fim de polígono =
 *    índice negativo, `idx.inv()`); triângulos E polígonos (fan de quad/ngon);
 *  - vértices EXPANDIDOS por canto de polígono (hard edges corretos nativamente;
 *    deduplicação fica para a pipeline de compressão — meta 100 MB → ≤ 20 MB);
 *  - LayerElementNormal e LayerElementUV (primeira camada): mapping
 *    ByPolygonVertex / ByVertice / ByPolygon com ReferenceInformationType
 *    Direct e IndexToDirect; ausente = normais acumuladas por face (mesma
 *    matemática do glTF) e UV zerada;
 *  - UV convertida para a convenção glTF (origem TOPO-esquerda): v' = 1 − v
 *    (DECISIONS #20 — importadores normalizam para uma convenção única);
 *  - Model/Properties70: Lcl Translation / Lcl Rotation (Euler XYZ em GRAUS) /
 *    Lcl Scaling + Geometric T/R/S; hierarquia via Connections OO;
 *  - geometria compartilhada por 2+ Models = instâncias fundidas em 1 KernMesh
 *    (offset de índices por base de vértice, como no glTF);
 *  - saída SEMPRE pelo KernWriter (quantização u16 + octaédrica 8:8) e
 *    verificável por KernIntegrity.
 *
 * Fora de escopo (erro claro ou aviso, nunca silêncio):
 *  - FBX ASCII/texto (erro claro — exportar como binário) e FBX 6.x/5.x;
 *  - skins/deformers/animações (Fase 8), materiais/texturas (Fase 4 TRAMA);
 *  - PreRotation/PostRotation presentes = AVISO no KenkernLog (aplicados via
 *    ordem Euler XYZ padrão; malha estática normalmente não os tem).
 */
object FbxImporter {

    // Header binário: "Kaydara FBX Binary  " (20 bytes) + 0x00 0x1A 0x00
    private val MAGIC = byteArrayOf(
        'K'.code.toByte(), 'a'.code.toByte(), 'y'.code.toByte(), 'd'.code.toByte(),
        'a'.code.toByte(), 'r'.code.toByte(), 'a'.code.toByte(), ' '.code.toByte(),
        'F'.code.toByte(), 'B'.code.toByte(), 'X'.code.toByte(), ' '.code.toByte(),
        'B'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 'a'.code.toByte(),
        'r'.code.toByte(), 'y'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
        0, 0x1A, 0
    )

    private const val VERSAO_MINIMA = 7100   // FBX 7.1 — mais antigo aceito
    private const val VERSAO_U64 = 7500      // ≥ 7500 usa offsets u64 no record
    private const val PROFUNDIDADE_MAXIMA = 64
    private const val TAG = "FbxImporter"

    // ---------------------------------------------------------------- API

    /** Importa um arquivo FBX binário completo e devolve a malha .KERN + meta. */
    fun importar(bytes: ByteArray): ImportResult {
        texturasEmbutidas.clear() // por importação: sem estado de uma chamada anterior
        avisos.clear() // HONESTIDADE (V3): avisos do ImportReport, por importação
        if (bytes.isEmpty()) throw FbxImportException("arquivo vazio")
        if (!magicValido(bytes)) {
            // Erro CLARO e distinto para FBX ASCII (texto).
            val inicio = String(bytes, 0, minOf(bytes.size, 64), Charsets.US_ASCII)
            if (inicio.startsWith("FBXHeaderExtension") || inicio.contains("FBXHeaderExtension")) {
                return importarAscii(bytes) // ETAPA B.2: ASCII agora é PARSEADO (mesma saída do binário)
            }
            throw FbxImportException("magic do FBX binário inválido (não é FBX ou está corrompido)")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(MAGIC.size)
        val versao = buf.int
        if (versao < VERSAO_MINIMA) {
            throw FbxImportException(
                "FBX versão $versao (6.x/5.x) não suportada — converter para FBX binário 7.x"
            )
        }
        val raizes = lerLista(buf, bytes.size.toLong(), versao, PROFUNDIDADE_MAXIMA)
        return montarMalha(raizes, bytes.size)
    }

    // ---------------------------------------------------------------- FBX ASCII (ETAPA B.2)

    /**
     * PARSER DE FBX ASCII (texto) com a MESMA saída do binário: produz a mesma
     * árvore de [FbxNode] e chama [montarMalha]. Formato suportado: 7.x
     * (FBXHeaderVersion 1000+). Versão não suportada = erro CLARO com a versão
     * encontrada (regra B.2). Estrutura aceita:
     *
     *     Nome: prop, prop {         ← node com filhos
     *         Filho: valor
     *         Array: *N { a: 1,2,3 } ← array com contagem + bloco 'a:'
     *     }
     *     Nome: "string", 123, 1.5, T  ← node sem filhos
     *
     * Valores: "string", inteiro (Long), decimal (Double), T/F/Y/N (Boolean).
     * Comentários ';' até o fim da linha são ignorados.
     */
    private fun importarAscii(bytes: ByteArray): ImportResult {
        val texto = String(bytes, Charsets.UTF_8)
        val p = AsciiCursor(texto, bytes.size)
        val raizes = p.lerListaTopo()
        if (raizes.isEmpty()) {
            throw FbxImportException("FBX ASCII vazio ou sem nodes — nada para importar")
        }
        val header = raizes.firstOrNull { it.nome == "FBXHeaderExtension" }
        val fbxVersao = header?.filho("FBXVersion")?.props?.firstOrNull()
        val headerVersao = header?.filho("FBXHeaderVersion")?.props?.firstOrNull()
        val versaoNum = (fbxVersao as? Long) ?: (headerVersao as? Long) ?: -1L
        if (versaoNum > 0 && versaoNum < VERSAO_MINIMA) {
            throw FbxImportException(
                "FBX ASCII versão $versaoNum (6.x/5.x) não suportada — converter para FBX 7.x (ASCII ou binário)"
            )
        }
        return try {
            montarMalha(raizes, bytes.size)
        } catch (e: FbxImportException) {
            throw e
        } catch (e: Exception) {
            throw FbxImportException(
                "FBX ASCII (versão encontrada: ${if (versaoNum > 0) versaoNum else "não informada"}) " +
                    "falhou no parse: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Cursor de leitura do ASCII: um nível de estado (posição) + helpers de
     * pular whitespace/comentários e ler valores. NÃO usa regex — arquivo pode
     * ter centenas de MB de arrays.
     */
    private class AsciiCursor(val s: String, val tamanhoBytes: Int) {
        var i = 0

        fun fim(): Boolean = i >= s.length

        fun pularEspacoEComentarios() {
            while (i < s.length) {
                val c = s[i]
                when {
                    c == ';' -> { while (i < s.length && s[i] != '\n') i++ }
                    c.isWhitespace() -> i++
                    else -> return
                }
            }
        }

        /** Nome do node: lê até ':' (sem espaços finais). */
        fun lerNome(): String {
            val inicio = i
            while (i < s.length && s[i] != ':' && s[i] != '\n') i++
            if (i >= s.length || s[i] != ':') {
                throw FbxImportException(
                    "FBX ASCII: node sem ':' após '${s.substring(inicio, minOf(inicio + 40, s.length))}'"
                )
            }
            val nome = s.substring(inicio, i).trim()
            i++ // ':'
            return nome
        }

        /** Um valor: string "...", número (Long/Double) ou booleano T/F/Y/N. */
        fun lerValor(): Any {
            pularEspacoEComentarios()
            if (i >= s.length) throw FbxImportException("FBX ASCII: fim inesperado procurando valor")
            val c = s[i]
            return when {
                c == '"' -> {
                    i++
                    val sb = StringBuilder()
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length) {
                            // escape simples: \" e \\ e \n (o resto mantém a barra)
                            when (s[i + 1]) {
                                '"' -> { sb.append('"'); i += 2 }
                                '\\' -> { sb.append('\\'); i += 2 }
                                'n' -> { sb.append('\n'); i += 2 }
                                else -> { sb.append(s[i]); i++ }
                            }
                        } else {
                            sb.append(s[i]); i++
                        }
                    }
                    if (i >= s.length) throw FbxImportException("FBX ASCII: string sem fechar")
                    i++
                    // nome amigável: "Material::vermelho" → "vermelho" (mesma regra
                    // do binário, onde o separador é \u0000\u0001)
                    val str = sb.toString()
                    val sep = str.indexOf("::")
                    return if (sep > 0) str.substring(sep + 2) else str
                }
                else -> {
                    val inicio = i
                    while (i < s.length && s[i] != ',' && s[i] != '\n' && s[i] != '}' && s[i] != '{') i++
                    val bruto = s.substring(inicio, i).trim()
                    when {
                        bruto.isEmpty() -> throw FbxImportException("FBX ASCII: valor vazio")
                        bruto == "T" || bruto == "Y" -> true
                        bruto == "F" || bruto == "N" -> false
                        bruto.any { it == '.' || it == 'e' || it == 'E' } ->
                            bruto.toDoubleOrNull() ?: throw FbxImportException("FBX ASCII: número inválido '$bruto'")
                        else -> bruto.toLongOrNull()
                            ?: throw FbxImportException("FBX ASCII: inteiro inválido '$bruto'")
                    }
                }
            }
        }

        /** Pula vírgulas/espaços antes do próximo valor ou marcador de fim. */
        fun pularSeparador() {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',' || s[i] == ';')) {
                if (s[i] == ';') while (i < s.length && s[i] != '\n') i++ else i++
            }
        }

        /**
         * Lê a lista de nodes de um nível (topo ou dentro de '{'). Para em '}'
         * (sem consumir) ou no fim do arquivo.
         */
        fun lerListaTopo(): MutableList<FbxNode> {
            val lista = ArrayList<FbxNode>(16)
            while (true) {
                pularEspacoEComentarios()
                if (i >= s.length || s[i] == '}') return lista
                lista.add(lerNode(64))
            }
        }

        /** Lê UM node: nome, props (vírgulas) e filhos opcionais entre '{ }'. */
        fun lerNode(profundidade: Int): FbxNode {
            if (profundidade <= 0) {
                throw FbxImportException("FBX ASCII: aninhamento excessivo de nodes")
            }
            val nome = lerNome()
            val node = FbxNode(nome)

            // Contagem de array: `Nome: *N { a: ... }` — o '*' marca o tamanho.
            pularEspacoEComentarios()
            var contagemArray = -1L
            if (i < s.length && s[i] == '*') {
                i++
                val inicio = i
                while (i < s.length && s[i].isDigit()) i++
                contagemArray = s.substring(inicio, i).toLongOrNull()
                    ?: throw FbxImportException("FBX ASCII: contagem de array inválida em '$nome'")
            }

            // Props: valores separados por vírgula NA MESMA LINHA; a lista acaba no
            // fim da linha, a menos que depois venha '{' (filhos) na linha seguinte.
            while (true) {
                pularEspacoEComentarios()
                if (i >= s.length) break
                val c = s[i]
                if (c == '{' || c == '}') break
                if (c == ',') { i++; continue }
                node.props.add(lerValor())
                // fim da linha? (resto da linha é branco ou comentário)
                var j = i
                while (j < s.length && s[j] != '\n') j++
                val restoLinha = s.substring(i, j).trimStart()
                if (restoLinha.isEmpty() || restoLinha.startsWith(";")) {
                    var k = j
                    while (k < s.length && (s[k] == '\n' || s[k].isWhitespace() || s[k] == ';')) {
                        if (s[k] == ';') { while (k < s.length && s[k] != '\n') k++ } else k++
                    }
                    if (k >= s.length || s[k] != '{') break // próximo node ou fim do arquivo
                    i = j // '{' vem depois: deixa o bloco de filhos pegar
                }
            }

            // Formato de ARRAY: `*N { a: v1,v2,... }` → 1 prop = FbxArray com os 'a'
            if (contagemArray >= 0) {
                pularEspacoEComentarios()
                if (i >= s.length || s[i] != '{') {
                    throw FbxImportException("FBX ASCII: array '$nome' sem '{' após a contagem")
                }
                i++
                pularEspacoEComentarios()
                // bloco interno: 'a: valores' (nome pode ser 'a' com espaço 'a: ')
                while (i < s.length && s[i] != '}') {
                    pularEspacoEComentarios()
                    if (i < s.length && s[i] == '}') break
                    if (s[i] == 'a' || s[i] == 'A') {
                        // pula até ':'
                        while (i < s.length && s[i] != ':') i++
                        if (i >= s.length) throw FbxImportException("FBX ASCII: bloco 'a:' sem ':'")
                        i++
                    }
                    // lê valores separados por vírgula até '}'
                    val longos = ArrayList<Long>(256)
                    val decimais = ArrayList<Double>(256)
                    var teveDecimal = false
                    while (true) {
                        pularSeparador()
                        if (i >= s.length || s[i] == '}') break
                        val v = lerValor()
                        when (v) {
                            is Long -> longos.add(v)
                            is Double -> { teveDecimal = true; decimais.add(v) }
                            else -> throw FbxImportException("FBX ASCII: array '$nome' com valor não numérico")
                        }
                    }
                    if (i >= s.length || s[i] != '}') {
                        throw FbxImportException("FBX ASCII: array '$nome' sem '}' (truncado)")
                    }
                    i++ // fecha '}' do bloco de valores
                    val fbx = if (teveDecimal) {
                        FbxArray(
                            null,
                            DoubleArray(decimais.size) { decimais[it] },
                            if (longos.isEmpty()) null else IntArray(longos.size) { longos[it].toInt() },
                            null
                        )
                    } else {
                        // índices: IntArray; se não couber em Int (raro), mantém Long
                        FbxArray(
                            null, null,
                            if (longos.all { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() })
                                IntArray(longos.size) { longos[it].toInt() } else null,
                            if (longos.all { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }) null
                            else LongArray(longos.size) { it -> longos[it] }
                        )
                    }
                    node.props.add(fbx)
                    pularEspacoEComentarios()
                    break // só um bloco 'a' por array
                }
                return node
            }

            // Filhos: após os props, um '{' começa a lista de filhos
            pularEspacoEComentarios()
            if (i < s.length && s[i] == '{') {
                i++
                for (f in lerListaTopo()) node.filhos.add(f)
                pularEspacoEComentarios()
                if (i >= s.length || s[i] != '}') {
                    throw FbxImportException("FBX ASCII: node '$nome' sem '}' de fechamento")
                }
                i++
            }
            return node
        }
    }

    // ---------------------------------------------------------------- parser binário

    /** Node do FBX: nome + propriedades tipadas + filhos. */
    private class FbxNode(val nome: String) {
        val props = ArrayList<Any>(4)
        val filhos = ArrayList<FbxNode>(4)

        fun filho(nome: String): FbxNode? {
            for (f in filhos) if (f.nome == nome) return f
            return null
        }

        fun propLong(i: Int): Long {
            val p = props.getOrNull(i) ?: throw FbxImportException(
                "node '$nome' sem propriedade $i (corrompido)"
            )
            return p as? Long ?: (p as? Int)?.toLong() ?: throw FbxImportException(
                "node '$nome': propriedade $i não é inteiro"
            )
        }

        fun propString(i: Int): String {
            val p = props.getOrNull(i) ?: throw FbxImportException(
                "node '$nome' sem propriedade $i (corrompido)"
            )
            return p as? String ?: throw FbxImportException(
                "node '$nome': propriedade $i não é texto"
            )
        }

        fun arrayInt(nome: String): IntArray {
            val n = filho(nome) ?: throw FbxImportException(
                "Geometry sem '$nome' (obrigatório para malha)"
            )
            val a = n.props.firstOrNull() as? FbxArray ?: throw FbxImportException(
                "'$nome' sem array (corrompido)"
            )
            return a.ints()
        }

        fun arrayDouble(nome: String): DoubleArray {
            val n = filho(nome) ?: throw FbxImportException(
                "Geometry sem '$nome' (obrigatório para malha)"
            )
            val a = n.props.firstOrNull() as? FbxArray ?: throw FbxImportException(
                "'$nome' sem array (corrompido)"
            )
            return a.doubles()
        }

        fun arrayDoubleOpcional(nome: String): DoubleArray? {
            val n = filho(nome) ?: return null
            val a = n.props.firstOrNull() as? FbxArray ?: return null
            return a.doubles()
        }

        fun arrayIntOpcional(nome: String): IntArray? {
            val n = filho(nome) ?: return null
            val a = n.props.firstOrNull() as? FbxArray ?: return null
            return a.ints()
        }

        fun texto(nome: String): String? {
            val n = filho(nome) ?: return null
            return n.props.firstOrNull() as? String
        }
    }

    /** Array decodificado (encoding 0 raw ou 1 zlib já inflado). */
    private class FbxArray(
        val floats: FloatArray?,
        val doubles: DoubleArray?,
        val ints: IntArray?,
        val longs: LongArray?
    ) {
        fun doubles(): DoubleArray = when {
            doubles != null -> doubles
            floats != null -> DoubleArray(floats.size) { floats[it].toDouble() }
            ints != null -> DoubleArray(ints.size) { ints[it].toDouble() }
            longs != null -> DoubleArray(longs.size) { longs[it].toDouble() }
            else -> throw FbxImportException("array sem dados numéricos (corrompido)")
        }

        fun ints(): IntArray = when {
            ints != null -> ints
            longs != null -> IntArray(longs.size) { longs[it].toInt() }
            else -> throw FbxImportException("array de índices não é inteiro (corrompido)")
        }
    }

    private fun magicValido(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
        return true
    }

    /** Lê uma lista de nodes até [limite] (posição absoluta) ou record nulo. */
    private fun lerLista(
        buf: ByteBuffer,
        limite: Long,
        versao: Int,
        profundidade: Int
    ): MutableList<FbxNode> {
        val lista = ArrayList<FbxNode>(16)
        while (true) {
            val restante = limite - buf.position()
            if (restante <= 0) break
            if (restante < tamanhoHeader(versao)) break // padding/footer
            val node = lerNode(buf, versao, profundidade) ?: break // record nulo
            lista.add(node)
        }
        return lista
    }

    private fun tamanhoHeader(versao: Int): Int = if (versao >= VERSAO_U64) 25 else 13

    /** Lê UM node; devolve null se for o record nulo (terminador de lista). */
    private fun lerNode(buf: ByteBuffer, versao: Int, profundidade: Int): FbxNode? {
        if (profundidade <= 0) {
            throw FbxImportException("aninhamento de nodes excessivo (arquivo corrompido)")
        }
        val posInicial = buf.position()
        val u64 = versao >= VERSAO_U64
        val endOffset: Long = if (u64) buf.long else (buf.int.toLong() and 0xFFFFFFFFL)
        val numProps: Long = if (u64) buf.long else (buf.int.toLong() and 0xFFFFFFFFL)
        val propsLen: Long = if (u64) buf.long else (buf.int.toLong() and 0xFFFFFFFFL)
        val headerSize = if (u64) 25 else 13

        if (endOffset == 0L && numProps == 0L && propsLen == 0L) return null // terminador

        if (numProps < 0 || numProps > 1_000_000) {
            throw FbxImportException("node com $numProps propriedades — implausível (corrompido)")
        }
        if (endOffset < posInicial + headerSize || endOffset > buf.limit()) {
            throw FbxImportException(
                "endOffset do node fora do arquivo (end=$endOffset — corrompido)"
            )
        }
        val nameLen = buf.get().toInt() and 0xFF
        if (nameLen == 0) throw FbxImportException("node sem nome (corrompido)")
        val nomeBytes = ByteArray(nameLen)
        buf.get(nomeBytes)
        val node = FbxNode(String(nomeBytes, Charsets.US_ASCII))

        // Propriedades — o comprimento declarado deve bater com o consumido.
        val inicioProps = buf.position()
        for (i in 0 until numProps) {
            node.props.add(lerPropriedade(buf, node.nome))
        }
        val consumidoProps = buf.position() - inicioProps
        if (consumidoProps.toLong() != propsLen) {
            throw FbxImportException(
                "node '${node.nome}': propriedades ocupam $consumidoProps bytes, " +
                    "declarado $propsLen (corrompido)"
            )
        }

        // Filhos — presentes se sobrou espaço dentro de endOffset.
        if (buf.position().toLong() < endOffset) {
            for (f in lerLista(buf, endOffset, versao, profundidade - 1)) {
                node.filhos.add(f)
            }
        }
        buf.position(endOffset.toInt())
        return node
    }

    /** Lê UMA propriedade tipada e devolve o valor (Long/Double/Boolean/String/FbxArray/ByteArray). */
    private fun lerPropriedade(buf: ByteBuffer, dono: String): Any {
        val tipo = buf.get().toInt().toChar()
        when (tipo) {
            'Y' -> return buf.short.toLong()                // i16
            'C' -> return buf.get().toInt() != 0            // bool
            'I' -> return buf.int.toLong()                  // i32
            'F' -> return buf.float.toDouble()              // f32
            'D' -> return buf.double                        // f64
            'L' -> return buf.long                          // i64
            'f', 'd', 'i', 'l', 'b' -> return lerArrayTipado(buf, tipo, dono)
            'S' -> {
                val len = buf.int
                if (len < 0 || buf.position() + len > buf.limit()) {
                    throw FbxImportException("string de $len bytes estoura o arquivo (corrompido)")
                }
                val b = ByteArray(len)
                buf.get(b)
                return limparNome(String(b, Charsets.UTF_8))
            }
            'R' -> {
                val len = buf.int
                if (len < 0 || buf.position() + len > buf.limit()) {
                    throw FbxImportException("raw de $len bytes estoura o arquivo (corrompido)")
                }
                val b = ByteArray(len)
                buf.get(b)
                return b
            }
            else -> throw FbxImportException(
                "tipo de propriedade '$tipo' desconhecido em '$dono' (corrompido)"
            )
        }
    }

    /** Lê um array ('f','d','i','l','b'): count + encoding + lenComprimido + dados. */
    private fun lerArrayTipado(buf: ByteBuffer, tipo: Char, dono: String): FbxArray {
        val count = buf.int.toLong() and 0xFFFFFFFFL
        val encoding = buf.int
        val lenComprimido = buf.int.toLong() and 0xFFFFFFFFL
        if (count > 100_000_000) {
            throw FbxImportException("array de $count elementos — implausível (corrompido)")
        }
        val tamanhoElemento = when (tipo) {
            'f' -> 4; 'd' -> 8; 'i' -> 4; 'l' -> 8; else -> 1 // 'b'
        }
        val bytesDesejados = count * tamanhoElemento
        if (encoding == 0) {
            if (lenComprimido != bytesDesejados) {
                throw FbxImportException(
                    "array em '$dono': encoding 0 com len $lenComprimido ≠ esperado $bytesDesejados (corrompido)"
                )
            }
            if (buf.position() + bytesDesejados > buf.limit()) {
                throw FbxImportException("array em '$dono' estoura o arquivo (corrompido)")
            }
        } else if (encoding == 1) {
            if (lenComprimido <= 0 || buf.position() + lenComprimido > buf.limit()) {
                throw FbxImportException("array zlib em '$dono' estoura o arquivo (corrompido)")
            }
        } else {
            throw FbxImportException(
                "array em '$dono' com encoding $encoding desconhecido (corrompido)"
            )
        }

        val comprimido = ByteArray(lenComprimido.toInt())
        buf.get(comprimido)
        val dados = if (encoding == 0) comprimido else inflar(comprimido, bytesDesejados, dono)

        val b = ByteBuffer.wrap(dados).order(ByteOrder.LITTLE_ENDIAN)
        return when (tipo) {
            'f' -> FbxArray(FloatArray(count.toInt()) { b.float }, null, null, null)
            'd' -> FbxArray(null, DoubleArray(count.toInt()) { b.double }, null, null)
            'i' -> FbxArray(null, null, IntArray(count.toInt()) { b.int }, null)
            'l' -> FbxArray(null, null, null, LongArray(count.toInt()) { b.long })
            else -> FbxArray( // 'b': bytes 0/1 — como não usamos bools, guardo em longs
                null, null, null, LongArray(count.toInt()) { if (b.get().toInt() != 0) 1L else 0L }
            )
        }
    }

    private fun inflar(comprimido: ByteArray, desejado: Long, dono: String): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(comprimido)
            val saida = ByteArray(desejado.toInt())
            val inflados = inflater.inflate(saida)
            if (inflados != desejado.toInt() || !inflater.finished()) {
                throw FbxImportException(
                    "array zlib em '$dono' inflou $inflados bytes, esperado $desejado (corrompido)"
                )
            }
            return saida
        } catch (e: DataFormatException) {
            throw FbxImportException("array zlib em '$dono' inválido: ${e.message}")
        } finally {
            inflater.end()
        }
    }

    /** "Model::cubo\u0000\u0001..." → parte depois de "::" (nome amigável). */
    private fun limparNome(s: String): String {
        val sep = s.indexOf("\u0000\u0001")
        return if (sep >= 0) s.substring(sep + 2) else s
    }

    // ---------------------------------------------------------------- cena → malha

    private fun montarMalha(raizes: List<FbxNode>, bytesOrigem: Int): ImportResult {
        val objetos = raizes.firstOrNull { it.nome == "Objects" }
            ?: throw FbxImportException("FBX sem 'Objects' — nada para importar")
        val conexoes = raizes.firstOrNull { it.nome == "Connections" }
            ?: throw FbxImportException("FBX sem 'Connections' — nada para importar")

        // 1) inventário de Objects: Geometry/Model tipo "Mesh" (LinkedHashMap = ordem do arquivo)
        val geometrias = LinkedHashMap<Long, FbxNode>()
        val modelos = LinkedHashMap<Long, FbxNode>()
        for (obj in objetos.filhos) {
            if (obj.props.size < 3) continue
            val id = obj.propLong(0)
            val tipo = obj.propString(2)
            when (obj.nome) {
                "Geometry" -> if (tipo == "Mesh") geometrias[id] = obj
                "Model" -> if (tipo == "Mesh") modelos[id] = obj
            }
        }
        if (geometrias.isEmpty()) {
            throw FbxImportException(
                "FBX sem Geometry tipo 'Mesh' (só skins/curvas/etc. não são suportados nesta fase)"
            )
        }

        // 1b) MATERIAIS (B.3): Material/Video + conexões para Models
        val materiaisPorId = LinkedHashMap<Long, FbxNode>()
        val videosPorId = LinkedHashMap<Long, FbxNode>()
        for (obj in objetos.filhos) {
            if (obj.props.size < 1) continue
            when (obj.nome) {
                "Material" -> materiaisPorId[obj.propLong(0)] = obj
                "Video" -> videosPorId[obj.propLong(0)] = obj
            }
        }
        val materiaisDoModelo = HashMap<Long, MutableList<Long>>()
        val videoDoMaterial = HashMap<Long, Long>()
        for (c in conexoes.filhos) {
            if (c.nome != "C" || c.props.size < 3) continue
            val tipo = c.props.getOrNull(0) as? String ?: continue
            if (tipo != "OO" && tipo != "OP") continue
            val filho = (c.props.getOrNull(1) as? Long) ?: continue
            val pai = (c.props.getOrNull(2) as? Long) ?: continue
            if (filho in materiaisPorId && pai in modelos) {
                materiaisDoModelo.getOrPut(pai) { ArrayList() }.add(filho)
            }
            if (filho in videosPorId && pai in materiaisPorId) {
                videoDoMaterial.putIfAbsent(pai, filho)
            }
        }
        // tabela DEDUPADA (B.4) por conteúdo + mapa id-FBX → índice
        val tabelaMateriais = ArrayList<KernMaterial>()
        val indiceMaterialPorId = HashMap<Long, Int>()
        for ((id, mat) in materiaisPorId) {
            val kern = kernMaterialDeFbx(mat, videosPorId[videoDoMaterial[id]])
            if (kern != null) {
                val existente = tabelaMateriais.indexOfFirst { it == kern }
                indiceMaterialPorId[id] = if (existente >= 0) existente else {
                    tabelaMateriais.add(kern); tabelaMateriais.size - 1
                }
            }
        }

        // 2) Connections OO: pai de cada Model; geometrias conectadas a cada Model
        val paiDoModelo = HashMap<Long, Long>()
        val geometriasDoModelo = HashMap<Long, MutableList<Long>>()
        for (c in conexoes.filhos) {
            if (c.nome != "C" || c.props.size < 3) continue
            val tipo = c.props.getOrNull(0) as? String ?: continue
            if (tipo != "OO" && tipo != "OP") continue
            val filho = (c.props.getOrNull(1) as? Long) ?: continue
            val pai = (c.props.getOrNull(2) as? Long) ?: continue
            if (filho in geometrias && pai in modelos) {
                geometriasDoModelo.getOrPut(pai) { ArrayList() }.add(filho)
            }
            if (filho in modelos) paiDoModelo[filho] = pai
        }

        // 3) percorre os Models (ordem do arquivo) e funde as instâncias;
        //    cada Model com material gera UM GRUPO de material (B.3)
        val builder = KernWriter.Builder()
        val cacheMundo = HashMap<Long, Mat4>()
        val emProgresso = HashSet<Long>()
        var modelosComMesh = 0
        var geometriasImportadas = 0
        var normaisCalculadas = false

        for ((modelId, modelo) in modelos) {
            val lista = geometriasDoModelo[modelId] ?: continue
            val mundo = mundoDoModelo(modelId, modelos, paiDoModelo, cacheMundo, emProgresso)
            val grupoOffset = builder.indices.size
            for (geoId in lista) {
                val geo = geometrias[geoId] ?: continue // ligado a objeto não-suportado
                normaisCalculadas = importarGeometria(geo, mundo, builder) || normaisCalculadas
                geometriasImportadas++
            }
            val matIdx = materiaisDoModelo[modelId]?.firstOrNull()?.let { indiceMaterialPorId[it] }
            if (matIdx != null && builder.indices.size > grupoOffset) {
                builder.materialGroup(grupoOffset, builder.indices.size - grupoOffset, matIdx)
            }
            modelosComMesh++
        }

        if (geometriasImportadas == 0 || builder.positions.isEmpty()) {
            throw FbxImportException("nenhuma Geometry conectada a um Model 'Mesh' — nada para importar")
        }

        for (m in tabelaMateriais) builder.material(m)

        val malha = builder.build()
        val bytesKern = KernWriter.write(malha)
        val report = ImportReport(
            bytesOrigem = bytesOrigem,
            bytesKern = bytesKern.size,
            vertices = malha.vertexCount,
            triangulos = malha.indices.size / 3,
            lods = malha.lods.size,
            primitivas = geometriasImportadas,
            nodesComMesh = modelosComMesh,
            normaisCalculadas = normaisCalculadas,
            materialCount = tabelaMateriais.size,
            textureCount = texturasEmbutidas.size
        )
        return ImportResult(malha, bytesKern, report, avisos.toList(), texturasEmbutidas.values.toList())
    }

    /**
     * MATERIAL FBX (B.3): lê DiffuseColor (cor base) + Opacity de Properties70;
     * textura só se o Video tiver Content EMBUTIDO (bytes) — arquivo externo
     * é AVISO (não dá para converter sem os bytes nesta fase).
     */
    private fun kernMaterialDeFbx(mat: FbxNode, video: FbxNode?): KernMaterial? {
        // nome amigável: prop 1 já vem limpa ("Material::vermelho" → "vermelho")
        val nomeAmigavel = (mat.props.getOrNull(1) as? String) ?: mat.nome
        var r = 1f; var g = 1f; var b = 1f; var a = 1f
        val p70 = mat.filho("Properties70")
        if (p70 != null) {
            for (p in p70.filhos) {
                if (p.nome != "P" || p.props.isEmpty()) continue
                when (p.propString(0)) {
                    "DiffuseColor" -> {
                        if (p.props.size >= 7) {
                            r = valor(p, 4); g = valor(p, 5); b = valor(p, 6)
                        }
                    }
                    "Opacity" -> {
                        if (p.props.size >= 5) a = valor(p, 4)
                    }
                }
            }
        }
        var temTexBase = false
        var texPath = ""
        if (video != null) {
            val nome = video.propString(1)
            val content = video.props.filterIsInstance<ByteArray>().firstOrNull()
            if (content != null && content.isNotEmpty()) {
                // bytes embutidos: hash vira o asset (conversão .KIMG na ETAPA C)
                val sha1 = AssetLayout.sha1Hex(content)
                temTexBase = true
                texPath = "${AssetLayout.DIR_TEXTURAS}/$sha1.kimg"
                // guarda os bytes para o ModelImporter coletar (acesso via nome)
                texturasEmbutidas[sha1] = TexturaImportada("image/png", content, nome, sha1)
            } else {
                // HONESTIDADE (V3): além do Console, o aviso entra no ImportReport
                // (textura procedural/externa sem embutido = cor sólida nesta fase).
                val motivo = "material '${mat.nome}': textura externa '$nome' sem conteúdo " +
                    "embutido (procedural ou caminho externo) — cor sólida nesta fase"
                avisos.add(motivo)
                KenkernLog.w(TAG, motivo)
            }
        }
        return KernMaterial(
            nomeAmigavel, r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f),
            0f, 1f, temTexBase, false, false, texPath, ""
        )
    }

    /** Texturas embutidas em Videos (sha1 → bytes), coletadas pelo ModelImporter. */
    val texturasEmbutidas = LinkedHashMap<String, TexturaImportada>()

    /** Avisos do ImportReport (V3): limitações reais encontradas na importação. */
    val avisos = ArrayList<String>(4)

    /** Mundo do Model = mundo(pai) × TRS local × TRS geométrico (com memo + ciclo). */
    private fun mundoDoModelo(
        id: Long,
        modelos: Map<Long, FbxNode>,
        paiDoModelo: Map<Long, Long>,
        cache: MutableMap<Long, Mat4>,
        emProgresso: MutableSet<Long>
    ): Mat4 {
        cache[id]?.let { return it }
        if (!emProgresso.add(id)) {
            throw FbxImportException("ciclo na hierarquia de Models (Connections corrompidas)")
        }
        val modelo = modelos[id] ?: throw FbxImportException("Model $id não existe")
        val local = matrizLocalDoModelo(modelo)
        val pai = paiDoModelo[id] ?: 0L
        val mundo = if (pai != 0L && pai in modelos) {
            Mat4().multiply(mundoDoModelo(pai, modelos, paiDoModelo, cache, emProgresso), local)
        } else {
            local
        }
        emProgresso.remove(id)
        cache[id] = mundo
        return mundo
    }

    /** TRS local do Model (Properties70) multiplicado pelo TRS geométrico. */
    private fun matrizLocalDoModelo(modelo: FbxNode): Mat4 {
        val p70 = modelo.filho("Properties70") ?: return Mat4().setIdentity()

        val pos = Vec3()
        val esc = Vec3(1f, 1f, 1f)
        val rot = Quaternion()
        var geoT: FloatArray? = null
        var geoR: FloatArray? = null
        var geoS: FloatArray? = null

        for (p in p70.filhos) {
            if (p.nome != "P" || p.props.isEmpty()) continue
            when (p.propString(0)) {
                "Lcl Translation" -> {
                    if (p.props.size < 7) throw FbxImportException("'Lcl Translation' sem 3 valores")
                    pos.set(valor(p, 4), valor(p, 5), valor(p, 6))
                }
                "Lcl Rotation" -> {
                    if (p.props.size < 7) throw FbxImportException("'Lcl Rotation' sem 3 valores (graus)")
                    rot.set(Quaternion().setFromEulerDegrees(valor(p, 4), valor(p, 5), valor(p, 6)))
                }
                "Lcl Scaling" -> {
                    if (p.props.size < 7) throw FbxImportException("'Lcl Scaling' sem 3 valores")
                    esc.set(valor(p, 4), valor(p, 5), valor(p, 6))
                }
                "GeometricTranslation" -> {
                    if (p.props.size < 7) throw FbxImportException("'GeometricTranslation' sem 3 valores")
                    geoT = floatArrayOf(valor(p, 4), valor(p, 5), valor(p, 6))
                }
                "GeometricRotation" -> {
                    if (p.props.size < 7) throw FbxImportException("'GeometricRotation' sem 3 valores")
                    geoR = floatArrayOf(valor(p, 4), valor(p, 5), valor(p, 6))
                }
                "GeometricScaling" -> {
                    if (p.props.size < 7) throw FbxImportException("'GeometricScaling' sem 3 valores")
                    geoS = floatArrayOf(valor(p, 4), valor(p, 5), valor(p, 6))
                }
                "PreRotation", "PostRotation" -> KenkernLog.w(
                    TAG,
                    "Model '${modelo.nome}': ${p.propString(0)} presente e IGNORADO nesta fase " +
                        "(importação de malha estática — rigs/animações são da Fase 8)"
                )
            }
        }

        val trs = Mat4().setTRS(pos, rot, esc)
        val geo = if (geoT == null && geoR == null && geoS == null) {
            Mat4().setIdentity()
        } else {
            Mat4().setTRS(
                geoT?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3(),
                geoR?.let { Quaternion().setFromEulerDegrees(it[0], it[1], it[2]) } ?: Quaternion(),
                geoS?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3(1f, 1f, 1f)
            )
        }
        return Mat4().multiply(trs, geo) // local = TRS × TRS geométrico
    }

    /** Valor numérico de um P (aceita Double 'D', Float 'F' e Long 'I'/'L'). */
    private fun valor(p: FbxNode, i: Int): Float {
        val v = p.props.getOrNull(i) ?: throw FbxImportException(
            "'${p.props.getOrNull(0)}' sem valor $i (corrompido)"
        )
        return when (v) {
            is Double -> v.toFloat()
            is Float -> v
            is Long -> v.toFloat()
            is Int -> v.toFloat()
            else -> throw FbxImportException(
                "'${p.props.getOrNull(0)}': valor $i não é numérico (corrompido)"
            )
        }
    }

    /**
     * Importa UMA Geometry com o mundo do Model aplicado. Vértices EXPANDIDOS
     * por canto; devolve true se as normais foram calculadas (acumuladas).
     */
    private fun importarGeometria(geo: FbxNode, mundo: Mat4, builder: KernWriter.Builder): Boolean {
        val vertices = geo.arrayDouble("Vertices")
        if (vertices.size % 3 != 0 || vertices.isEmpty()) {
            throw FbxImportException(
                "Geometry '${geo.nome}': Vertices com ${vertices.size} valores (precisa ser múltiplo de 3)"
            )
        }
        val cpCount = vertices.size / 3
        val pvi = geo.arrayInt("PolygonVertexIndex")
        if (pvi.isEmpty()) {
            throw FbxImportException("Geometry '${geo.nome}' sem polígonos (PolygonVertexIndex vazio)")
        }

        // range de control points válido (erro claro, nunca índice silencioso)
        for (bruto in pvi) {
            val cp = if (bruto < 0) bruto.inv() else bruto
            if (cp < 0 || cp >= cpCount) {
                throw FbxImportException(
                    "PolygonVertexIndex com control point $cp fora do range [0, $cpCount)"
                )
            }
        }

        // camadas de normal (3 componentes) e UV (2 componentes)
        val normal = camada(geo, "LayerElementNormal", "Normals", "NormalsIndex", 3)
        val uv = camada(geo, "LayerElementUV", "UV", "UVIndex", 2)
        avisarSeMultiplasCamadas(geo, "LayerElementNormal")
        avisarSeMultiplasCamadas(geo, "LayerElementUV")

        // normais calculadas: acumula por CONTROL POINT (mundo) antes da expansão
        val calculouNormais = normal == null
        val acumuladas = if (normal == null) DoubleArray(cpCount * 3) else null

        // 1ª passada: triangula (fan) acumulando normais por cp se preciso
        var indicePoligono = 0
        val base = builder.positions.size / 3
        var i = 0
        while (i < pvi.size) {
            val poligonoInicio = i
            // descobre o fim do polígono (índice negativo)
            while (i < pvi.size && pvi[i] >= 0) i++
            if (i >= pvi.size) {
                throw FbxImportException(
                    "polígono aberto no fim do PolygonVertexIndex (falta índice negativo de fechamento)"
                )
            }
            val cantos = i - poligonoInicio + 1 // incluindo o fechamento
            if (cantos < 3) {
                throw FbxImportException(
                    "polígono $indicePoligono com $cantos canto(s) — mínimo 3"
                )
            }
            if (acumuladas != null) {
                // fan sobre control points: (cp0, cpk, cpk+1)
                for (k in 1 until cantos - 1) {
                    val a = pvi[poligonoInicio]
                    val b = if (poligonoInicio + k == i) pvi[i].inv() else pvi[poligonoInicio + k]
                    val c = if (poligonoInicio + k + 1 == i) pvi[i].inv() else pvi[poligonoInicio + k + 1]
                    acumularFace(acumuladas, vertices, mundo, a, b, c)
                }
            }
            indicePoligono++
            i++
        }

        // 2ª passada: expande cada canto como vértice e emite os triângulos
        var canto = 0
        var poligono = 0
        i = 0
        while (i < pvi.size) {
            val poligonoInicio = i
            while (i < pvi.size && pvi[i] >= 0) i++
            val fim = i
            val cantos = fim - poligonoInicio + 1
            val baseCantos = canto

            for (k in 0 until cantos) {
                val bruto = if (poligonoInicio + k == fim) pvi[fim] else pvi[poligonoInicio + k]
                val cp = if (bruto < 0) bruto.inv() else bruto
                var nx = 0f; var ny = 0f; var nz = 0f
                if (normal != null) {
                    val n = amostraVetor(normal, cp, canto, poligono, cantos)
                    nx = n[0].toFloat(); ny = n[1].toFloat(); nz = n[2].toFloat()
                    // linear do mundo (3×3) + renormalização (mesma do glTF)
                    val tx = mundo.m[0] * nx + mundo.m[4] * ny + mundo.m[8] * nz
                    val ty = mundo.m[1] * nx + mundo.m[5] * ny + mundo.m[9] * nz
                    val tz = mundo.m[2] * nx + mundo.m[6] * ny + mundo.m[10] * nz
                    nx = tx; ny = ty; nz = tz
                } else {
                    nx = acumuladas!![cp * 3].toFloat()
                    ny = acumuladas[cp * 3 + 1].toFloat()
                    nz = acumuladas[cp * 3 + 2].toFloat()
                }

                var u = 0f; var v = 0f
                if (uv != null) {
                    val t = amostraVetor(uv, cp, canto, poligono, cantos)
                    u = t[0].toFloat()
                    v = 1f - t[1].toFloat() // FBX origem BAIXO → convenção glTF (DECISIONS #20)
                }

                // POSIÇÃO transformada pelo mundo do Model (column-major)
                val vx = vertices[cp * 3].toFloat()
                val vy = vertices[cp * 3 + 1].toFloat()
                val vz = vertices[cp * 3 + 2].toFloat()
                val px = mundo.m[0] * vx + mundo.m[4] * vy + mundo.m[8] * vz + mundo.m[12]
                val py = mundo.m[1] * vx + mundo.m[5] * vy + mundo.m[9] * vz + mundo.m[13]
                val pz = mundo.m[2] * vx + mundo.m[6] * vy + mundo.m[10] * vz + mundo.m[14]

                // renormaliza a normal deste vértice
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 1e-8f) { nx /= len; ny /= len; nz /= len }
                else if (normal != null) { nx = 0f; ny = 1f; nz = 0f }

                builder.vertex(px, py, pz, nx, ny, nz, u, v, 0f, 0f, 0f)
                canto++
            }

            // fan: (c0, ck, ck+1) nos vértices EXPANDIDOS recém-criados
            for (k in 1 until cantos - 1) {
                builder.triangle(base + baseCantos, base + baseCantos + k, base + baseCantos + k + 1)
            }
            poligono++
            i++
        }

        return calculouNormais
    }

    /** Camada de normal/UV: devolve (dados, índices|null, mapping) ou null se ausente. */
    private class Camada(
        val dados: DoubleArray,
        val indices: IntArray?,
        val componentes: Int,         // 3 = normal, 2 = uv
        val porVertice: Boolean,      // "ByVertice"
        val porPoligono: Boolean      // "ByPolygon"
    )

    private fun camada(
        geo: FbxNode,
        nomeCamada: String,
        nomeDados: String,
        nomeIndices: String,
        componentes: Int
    ): Camada? {
        val camada = geo.filho(nomeCamada) ?: return null
        val mapping = camada.texto("MappingInformationType") ?: "ByPolygonVertex"
        val referencia = camada.texto("ReferenceInformationType") ?: "Direct"
        val dados = camada.arrayDoubleOpcional(nomeDados)
            ?: throw FbxImportException(
                "Geometry '${geo.nome}': $nomeCamada sem array '$nomeDados'"
            )
        if (dados.size % componentes != 0) {
            throw FbxImportException(
                "Geometry '${geo.nome}': '$nomeDados' com ${dados.size} valores " +
                    "(múltiplo de $componentes esperado)"
            )
        }
        var indices = camada.arrayIntOpcional(nomeIndices)
        if (referencia == "IndexToDirect" && indices == null) {
            throw FbxImportException(
                "Geometry '${geo.nome}': $nomeCamada IndexToDirect sem '$nomeIndices'"
            )
        }
        if (referencia != "IndexToDirect") indices = null
        return Camada(
            dados = dados,
            indices = indices,
            componentes = componentes,
            porVertice = mapping == "ByVertice" || mapping == "ByControlPoint",
            porPoligono = mapping == "ByPolygon"
        )
    }

    /** Amostra o vetor (normal 3 / uv 2) para um canto, conforme o mapping/referência. */
    private fun amostraVetor(
        camada: Camada,
        cp: Int,
        canto: Int,
        poligono: Int,
        cantosDoPoligono: Int
    ): DoubleArray {
        val indice = when {
            camada.porVertice -> cp
            camada.porPoligono -> poligono
            else -> canto // ByPolygonVertex
        }
        val direto = camada.indices?.getOrNull(indice) ?: indice
        if (direto < 0 || (direto + 1) * camada.componentes > camada.dados.size) {
            throw FbxImportException(
                "camada normal/UV: índice $direto fora do range de dados " +
                    "(${camada.dados.size / camada.componentes})"
            )
        }
        val saida = DoubleArray(camada.componentes)
        for (i in 0 until camada.componentes) {
            saida[i] = camada.dados[direto * camada.componentes + i]
        }
        return saida
    }

    private fun avisarSeMultiplasCamadas(geo: FbxNode, nomeCamada: String) {
        var total = 0
        for (f in geo.filhos) if (f.nome == nomeCamada) total++
        if (total > 1) {
            KenkernLog.w(
                TAG,
                "Geometry '${geo.nome}': $total camadas de $nomeCamada — usando a PRIMEIRA (layer 0)"
            )
        }
    }

    // ---------------------------------------------------------------- helpers de matemática

    /** Normal de face em ESPAÇO DE MUNDO, acumulada nos 3 control points. */
    private fun acumularFace(
        acumuladas: DoubleArray,
        vertices: DoubleArray,
        mundo: Mat4,
        a: Int, b: Int, c: Int
    ) {
        val ax = aplicarLinha(mundo, 0, vertices, a); val ay = aplicarLinha(mundo, 1, vertices, a); val az = aplicarLinha(mundo, 2, vertices, a)
        val bx = aplicarLinha(mundo, 0, vertices, b); val by = aplicarLinha(mundo, 1, vertices, b); val bz = aplicarLinha(mundo, 2, vertices, b)
        val cx = aplicarLinha(mundo, 0, vertices, c); val cy = aplicarLinha(mundo, 1, vertices, c); val cz = aplicarLinha(mundo, 2, vertices, c)
        val e1x = bx - ax; val e1y = by - ay; val e1z = bz - az
        val e2x = cx - ax; val e2y = cy - ay; val e2z = cz - az
        val nx = e1y * e2z - e1z * e2y
        val ny = e1z * e2x - e1x * e2z
        val nz = e1x * e2y - e1y * e2x
        for (v in intArrayOf(a, b, c)) {
            acumuladas[v * 3] += nx
            acumuladas[v * 3 + 1] += ny
            acumuladas[v * 3 + 2] += nz
        }
    }

    /** Aplica a LINHA [linha] de M (column-major: m[col*4+row]) a vertices[v]. */
    private fun aplicarLinha(m: Mat4, linha: Int, vertices: DoubleArray, v: Int): Double =
        m.m[linha] * vertices[v * 3] +
            m.m[4 + linha] * vertices[v * 3 + 1] +
            m.m[8 + linha] * vertices[v * 3 + 2] +
            m.m[12 + linha]
}
