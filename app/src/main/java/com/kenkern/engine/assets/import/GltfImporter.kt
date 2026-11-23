package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernMesh
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Erro de importação com motivo CLARO (regra: nada de engolir erro em silêncio —
 * toda falha do importador chega ao Console via KenkernLog e ao Diagnóstico).
 */
class GltfImportException(motivo: String) : Exception(motivo)

/**
 * META DE COMPRESSÃO (BACKLOG F2.7 / ROADMAP Fase 2): relatório da conversão
 * glTF/GLB → .KERN. A meta do roadmap é 100 MB → ≤ 20 MB (razão ≥ 5×); o
 * alcance real depende da pipeline completa (F2.8+: deduplicação, LODs
 * gerados, compressão de índices) — este relatório é a régua dessa meta.
 */
class ImportReport(
    val bytesOrigem: Int,
    val bytesKern: Int,
    val vertices: Int,
    val triangulos: Int,
    val lods: Int,
    val primitivas: Int,
    val nodesComMesh: Int,
    val normaisCalculadas: Boolean,
    // ETAPA B/C: materiais e texturas extraídos (0 até a ETAPA B implementar a
    // extração; ModelImporter usa esses contadores no ImportSummary).
    val materialCount: Int = 0,
    val textureCount: Int = 0
) {
    /** Razão origem/.KERN (ex.: 5.0 = 100 MB viraram 20 MB). */
    val razao: Float get() = if (bytesKern > 0) bytesOrigem / bytesKern.toFloat() else 0f

    /** Meta do ROADMAP (100 MB → ≤ 20 MB). */
    val metaRoadmap100Para20: Boolean get() = razao >= 5f

    fun resumo(): String =
        ("%.1f KB origem → %.1f KB .KERN (razão %.2f×; meta 100 MB→≤20 MB %s) · " +
            "%d vértices · %d triângulos · %d primitiva(s) · %d node(s) com mesh · %d LOD").format(
            java.util.Locale.US,
            bytesOrigem / 1024f, bytesKern / 1024f, razao,
            if (metaRoadmap100Para20) "ALCANÇADA" else "pendente (pipeline F2.8+)",
            vertices, triangulos, primitivas, nodesComMesh, lods
        )
}

/** Resultado do importador: malha .KERN pronta + bytes + meta + avisos + texturas. */
class ImportResult(
    val mesh: KernMesh,
    val bytesKern: ByteArray,
    val report: ImportReport,
    /** Avisos acumulados durante a importação (mostrados no ImportReport da UI). */
    val avisos: List<String> = emptyList(),
    /** Imagens de textura referenciadas pelos materiais (ETAPA B.3/C.4). */
    val texturas: List<TexturaImportada> = emptyList()
)

/**
 * TEXTURA extraída do arquivo de origem (ETAPA B.3/C.4): bytes originais
 * (PNG/JPEG) + tipo de arquivo. A conversão para .KIMG (ETC2) acontece na
 * ETAPA C, no aparelho, com hash de conteúdo como nome do asset.
 */
class TexturaImportada(
    val mimeType: String,
    val bytes: ByteArray,
    val nomeOrigem: String,
    val sha1: String
)

/**
 * IMPORTADOR glTF 2.0 (GLB binário e .gltf JSON) → .KERN (BACKLOG F2.7).
 *
 * Suportado nesta fase:
 *  - GLB: header 12B + chunk JSON + chunk BIN opcional (magic/limites validados);
 *  - .gltf: buffers embutidos como data URI (base64); buffer em ARQUIVO EXTERNO
 *    = erro claro (não silencioso);
 *  - primitivas TRIANGLES (mode 4) com POSITION (obrigatório), NORMAL e
 *    TEXCOORD_0 (opcionais) e índices (opcional — gerados em sequência);
 *  - componentType: i8/u8/i16/u16/u32/f32;
 *  - transform dos nodes (matriz column-major ou TRS/quaternion) aplicado aos
 *    vértices; atributos integer normalizados são convertidos ao intervalo
 *    glTF e NORMAL é transformada pela inversa-transposta e renormalizada; NORMAL
 *    ausente = acumulação suave das faces (flat→smooth no espaço de mundo);
 *  - todas as primitivas de todos os nodes da cena padrão são FUNDIDAS em 1
 *    KernMesh (offset de índices por base de vértice);
 *  - saída SEMPRE pelo KernWriter existente (quantização u16 + octaédrica 8:8).
 *
 * Fora de escopo (F2.8+/fases seguintes): skins, animações, materiais/texturas,
 * draco/meshopt, primitivas em strip/fan (erro claro, nunca silencioso).
 */
object GltfImporter {

    // componentTypes do glTF 2.0
    private const val CT_I8 = 5120
    private const val CT_U8 = 5121
    private const val CT_I16 = 5122
    private const val CT_U16 = 5123
    private const val CT_U32 = 5125
    private const val CT_F32 = 5126

    private const val MODE_TRIANGLES = 4

    // ---------------------------------------------------------------- API

    /** Importa um arquivo glTF/GLB completo e devolve a malha .KERN + meta. */
    fun importar(bytes: ByteArray): ImportResult = importar(bytes, null, "modelo.gltf", false)

    /**
     * Importação com RESOLVEDOR DE ARQUIVOS EXTERNOS (ETAPA A/B.1): buffers
     * (.bin) e imagens referenciados por URI relativa são buscados via
     * [resolutor] (pasta SAF ou conteúdo de .zip). Sem resolver, URI externa
     * continua dando erro CLARO (comportamento anterior, F2.7).
     * [manterTangentes] (B.6): lê TANGENT mesmo sem normal map (senão tangentes
     * ficam zeradas — não há uso para elas sem normal map no shader básico).
     */
    fun importar(
        bytes: ByteArray,
        resolutor: ResolvedorExterno?,
        nomeArquivo: String = "modelo.gltf",
        manterTangentes: Boolean = false
    ): ImportResult {
        if (bytes.isEmpty()) throw GltfImportException("arquivo vazio")
        val (json, binario) = separarJsonEBinario(bytes)
        val raiz = parseJson(json, bytes)
        val buffers = lerBuffers(raiz, binario, resolutor, nomeArquivo)
        return montarMalha(raiz, buffers, bytes.size, resolutor, nomeArquivo, manterTangentes)
    }

    // ---------------------------------------------------------------- container

    /** Separa o JSON do glTF do BIN binário (GLB = chunks; .gltf = JSON puro). */
    private fun separarJsonEBinario(bytes: ByteArray): Pair<ByteArray, ByteArray?> {
        // GLB: magic "glTF"
        if (bytes.size >= 12 &&
            bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(4)
            val versao = buf.int
            val totalLen = buf.int
            if (versao != 2) {
                throw GltfImportException("GLB versão $versao não suportada (esperado 2)")
            }
            if (totalLen < 12 || totalLen > bytes.size) {
                throw GltfImportException(
                    "GLB declara $totalLen bytes mas o arquivo tem ${bytes.size} (corrompido)"
                )
            }
            var pos = 12
            var json: ByteArray? = null
            var bin: ByteArray? = null
            while (pos + 8 <= totalLen) {
                buf.position(pos)
                val chunkLen = buf.int
                val tipo = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                if (chunkLen < 0 || pos + 8 + chunkLen > totalLen) {
                    throw GltfImportException("chunk GLB '$tipo' estoura o arquivo (len=$chunkLen)")
                }
                val dados = bytes.copyOfRange(pos + 8, pos + 8 + chunkLen)
                when (tipo) {
                    "JSON" -> json = dados
                    "BIN\u0000" -> bin = dados
                    else -> {} // chunks desconhecidos são ignorados por spec
                }
                pos += 8 + chunkLen
            }
            if (json == null) {
                throw GltfImportException("GLB sem chunk JSON (o primeiro chunk precisa ser JSON)")
            }
            return json to bin
        }
        // .gltf: JSON puro (tolera BOM/espaços no início).
        var start = 0
        while (start < bytes.size && bytes[start].toInt().toChar().isWhitespace()) start++
        if (start + 2 < bytes.size && bytes[start] == 0xEF.toByte() &&
            bytes[start + 1] == 0xBB.toByte() && bytes[start + 2] == 0xBF.toByte()
        ) start += 3
        while (start < bytes.size && bytes[start].toInt().toChar().isWhitespace()) start++
        if (start >= bytes.size || bytes[start] != '{'.code.toByte()) {
            throw GltfImportException("não é GLB (magic 'glTF') nem .gltf (JSON '{…')")
        }
        return bytes.copyOfRange(start, bytes.size) to null
    }

    private fun parseJson(json: ByteArray, origem: ByteArray): JSONObject {
        val texto = String(json, Charsets.UTF_8)
        try {
            return JSONObject(texto)
        } catch (e: Exception) {
            throw GltfImportException(
                "JSON do glTF inválido (${origem.size} bytes): ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    // ---------------------------------------------------------------- buffers

    /** Lê os buffers do glTF: BIN do GLB, data URIs (base64) do .gltf e arquivos externos via resolvedor. */
    private fun lerBuffers(
        raiz: JSONObject,
        binario: ByteArray?,
        resolutor: ResolvedorExterno?,
        nomeArquivo: String
    ): List<ByteArray> {
        val buffers = raiz.optJSONArray("buffers")
            ?: throw GltfImportException("glTF sem array 'buffers' (glTF 2.0 exige ao menos 1)")
        val lista = ArrayList<ByteArray>(buffers.length())
        for (i in 0 until buffers.length()) {
            val b = buffers.getJSONObject(i)
            val uri = b.optString("uri", "")
            val declared = b.optInt("byteLength", -1)
            val dados: ByteArray = when {
                uri.isEmpty() -> {
                    if (i == 0 && binario != null) binario
                    else throw GltfImportException(
                        "buffer $i sem URI e sem chunk BIN (o GLB só embute o buffer 0)"
                    )
                }
                uri.startsWith("data:") -> decodificarDataUri(i, uri)
                else -> {
                    // ETAPA B.1: URI relativa resolvida contra a pasta SAF ou o .zip.
                    val viaResolver = resolutor?.bytes(uri)
                    if (viaResolver == null) {
                        throw GltfImportException(
                            "buffer $i aponta para o arquivo externo '$uri' e ele não foi achado " +
                                "(importe pela opção Pasta/.zip que inclui o .bin, ou reexporte embutido)"
                        )
                    }
                    if (viaResolver.isEmpty()) {
                        throw GltfImportException("buffer externo '$uri' veio vazio (${nomeArquivo})")
                    }
                    viaResolver
                }
            }
            if (declared >= 0 && dados.size < declared) {
                throw GltfImportException(
                    "buffer $i declara $declared bytes mas tem ${dados.size} (truncado)"
                )
            }
            lista.add(dados)
        }
        return lista
    }

    private fun decodificarDataUri(indice: Int, uri: String): ByteArray {
        val virgula = uri.indexOf(',')
        if (virgula < 0) {
            throw GltfImportException("data URI do buffer $indice sem separador ','")
        }
        return try {
            Base64.getMimeDecoder().decode(uri.substring(virgula + 1))
        } catch (e: Exception) {
            throw GltfImportException("base64 do buffer $indice inválido: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------------------------------------------------------- acessores

    private fun tamanhoComponente(componentType: Int): Int = when (componentType) {
        CT_I8, CT_U8 -> 1
        CT_I16, CT_U16 -> 2
        CT_U32, CT_F32 -> 4
        else -> throw GltfImportException("componentType $componentType não suportado")
    }

    private fun componentesDoTipo(tipo: String): Int = when (tipo) {
        "SCALAR" -> 1
        "VEC2" -> 2
        "VEC3" -> 3
        "VEC4" -> 4
        else -> throw GltfImportException("tipo de acessor '$tipo' não suportado (SCALAR/VEC2/VEC3/VEC4)")
    }

    /**
     * Lê um acessor como floats (componentes intercalados). TODAS as validações
     * de limite com mensagem precisa — nada de ler lixo sem avisar.
     */
    private fun lerAcessorFloat(
        raiz: JSONObject,
        buffers: List<ByteArray>,
        indice: Int
    ): FloatArray {
        val acessores = raiz.optJSONArray("accessors")
            ?: throw GltfImportException("glTF sem array 'accessors'")
        if (indice < 0 || indice >= acessores.length()) {
            throw GltfImportException("acessor $indice não existe (${acessores.length()} definidos)")
        }
        val acc = acessores.getJSONObject(indice)
        val count = acc.getInt("count")
        if (count <= 0) throw GltfImportException("acessor $indice com count=$count")
        val tipo = acc.getString("type")
        val comps = componentesDoTipo(tipo)
        val componentType = acc.getInt("componentType")
        val tamComp = tamanhoComponente(componentType)
        val normalizado = acc.optBoolean("normalized", false)
        if (normalizado && componentType != CT_I8 && componentType != CT_U8 &&
            componentType != CT_I16 && componentType != CT_U16
        ) {
            throw GltfImportException(
                "acessor $indice usa normalized=true com componentType $componentType (só BYTE/UNSIGNED_BYTE/SHORT/UNSIGNED_SHORT)"
            )
        }

        val saida = FloatArray(count * comps)
        val viewIndex = acc.optInt("bufferView", -1)
        if (viewIndex < 0) {
            // Acessor denso sem bufferView (spec: só com count=1 e buffer default 0).
            if (count != 1) {
                throw GltfImportException("acessor $indice sem bufferView (só válido para count=1)")
            }
            val bufferIndex = acc.optInt("buffer", 0)
            if (bufferIndex < 0 || bufferIndex >= buffers.size) {
                throw GltfImportException("acessor $indice aponta para buffer $bufferIndex inexistente")
            }
            lerFloats(buffers[bufferIndex], 0, saida, 0, comps, componentType, tamComp, normalizado)
            return saida
        }

        val views = raiz.optJSONArray("bufferViews")
            ?: throw GltfImportException("glTF sem array 'bufferViews'")
        if (viewIndex >= views.length()) {
            throw GltfImportException("bufferView $viewIndex não existe (${views.length()} definidos)")
        }
        val view = views.getJSONObject(viewIndex)
        val bufferIndex = view.optInt("buffer", -1)
        if (bufferIndex < 0 || bufferIndex >= buffers.size) {
            throw GltfImportException("bufferView $viewIndex aponta para buffer $bufferIndex inexistente")
        }
        val dados = buffers[bufferIndex]
        val viewOffset = view.optInt("byteOffset", 0)
        val viewLen = view.optInt("byteLength", -1)
        if (viewLen < 0 || viewOffset < 0 || viewOffset + viewLen > dados.size) {
            throw GltfImportException(
                "bufferView $viewIndex estoura o buffer $bufferIndex (offset $viewOffset + $viewLen > ${dados.size})"
            )
        }
        val accOffset = acc.optInt("byteOffset", 0)
        val stride = if (view.has("byteStride")) view.getInt("byteStride") else comps * tamComp
        val passo = if (stride >= comps * tamComp) stride else comps * tamComp
        val necessario = accOffset + (count - 1) * passo + comps * tamComp
        if (necessario > viewLen) {
            throw GltfImportException(
                "acessor $indice precisa de $necessario bytes mas bufferView $viewIndex tem $viewLen"
            )
        }
        var base = viewOffset + accOffset
        for (i in 0 until count) {
            lerFloats(dados, base, saida, i * comps, comps, componentType, tamComp, normalizado)
            base += passo
        }
        return saida
    }

    private fun lerFloats(
        dados: ByteArray,
        offset: Int,
        saida: FloatArray,
        saidaBase: Int,
        comps: Int,
        componentType: Int,
        tamComp: Int,
        normalizado: Boolean
    ) {
        val buf = ByteBuffer.wrap(dados, offset, comps * tamComp).order(ByteOrder.LITTLE_ENDIAN)
        for (c in 0 until comps) {
            val bruto = when (componentType) {
                CT_F32 -> buf.float
                CT_I8 -> buf.get().toInt().toFloat()
                CT_U8 -> (buf.get().toInt() and 0xFF).toFloat()
                CT_I16 -> buf.short.toInt().toFloat()
                CT_U16 -> (buf.short.toInt() and 0xFFFF).toFloat()
                CT_U32 -> buf.int.toFloat()
                else -> throw GltfImportException("componentType $componentType não suportado")
            }
            saida[saidaBase + c] = if (!normalizado) bruto else when (componentType) {
                CT_I8 -> maxOf(-1f, bruto / 127f)
                CT_U8 -> bruto / 255f
                CT_I16 -> maxOf(-1f, bruto / 32767f)
                CT_U16 -> bruto / 65535f
                else -> throw GltfImportException("componentType $componentType inválido com normalized=true")
            }
        }
    }

    /** Lê um acessor SCALAR como índices (u8/i8/u16/i16/u32). */
    private fun lerAcessorIndices(
        raiz: JSONObject,
        buffers: List<ByteArray>,
        indice: Int
    ): IntArray {
        val acessores = raiz.getJSONArray("accessors")
        if (indice < 0 || indice >= acessores.length()) {
            throw GltfImportException("acessor de índices $indice não existe")
        }
        val acc = acessores.getJSONObject(indice)
        val count = acc.getInt("count")
        if (count <= 0) throw GltfImportException("acessor de índices $indice com count=$count")
        val tipo = acc.getString("type")
        if (tipo != "SCALAR") {
            throw GltfImportException("índices precisam de acessor SCALAR (recebido $tipo)")
        }
        val componentType = acc.getInt("componentType")
        val tamComp = tamanhoComponente(componentType)

        if (!acc.has("bufferView")) {
            if (count != 1) {
                throw GltfImportException("acessor de índices $indice sem bufferView (só válido para count=1)")
            }
            val bufferIndex = acc.optInt("buffer", 0)
            if (bufferIndex < 0 || bufferIndex >= buffers.size) {
                throw GltfImportException("acessor de índices $indice aponta para buffer $bufferIndex inexistente")
            }
            val saida = IntArray(1)
            lerIndicesDireto(buffers[bufferIndex], 0, saida, componentType)
            return saida
        }

        val views = raiz.getJSONArray("bufferViews")
        val viewIndex = acc.getInt("bufferView")
        if (viewIndex >= views.length()) {
            throw GltfImportException("bufferView $viewIndex não existe")
        }
        val view = views.getJSONObject(viewIndex)
        val bufferIndex = view.optInt("buffer", -1)
        if (bufferIndex < 0 || bufferIndex >= buffers.size) {
            throw GltfImportException("bufferView $viewIndex aponta para buffer $bufferIndex inexistente")
        }
        val dados = buffers[bufferIndex]
        val viewOffset = view.optInt("byteOffset", 0)
        val viewLen = view.optInt("byteLength", -1)
        val accOffset = acc.optInt("byteOffset", 0)
        if (accOffset + count.toLong() * tamComp > viewLen ||
            viewOffset + accOffset + count.toLong() * tamComp > dados.size
        ) {
            throw GltfImportException(
                "acessor de índices $indice estoura bufferView $viewIndex (necessário ${count * tamComp}, tem $viewLen)"
            )
        }
        val saida = IntArray(count)
        lerIndicesDireto(dados, viewOffset + accOffset, saida, componentType)
        return saida
    }

    private fun lerIndicesDireto(dados: ByteArray, offset: Int, saida: IntArray, componentType: Int) {
        val buf = ByteBuffer.wrap(dados, offset, saida.size * tamanhoComponente(componentType))
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in saida.indices) {
            saida[i] = when (componentType) {
                CT_U8, CT_I8 -> buf.get().toInt() and 0xFF
                CT_U16, CT_I16 -> buf.short.toInt() and 0xFFFF
                CT_U32 -> buf.int
                else -> throw GltfImportException("componentType $componentType inválido para índices")
            }
        }
    }

    // ---------------------------------------------------------------- nodes

    /** Constrói a matriz local do node (matriz column-major OU TRS/quaternion). */
    private fun matrizDoNode(node: JSONObject): Mat4 {
        val m = Mat4().setIdentity()
        if (node.has("matrix")) {
            val arr = node.getJSONArray("matrix")
            if (arr.length() != 16) {
                throw GltfImportException("node com 'matrix' de ${arr.length()} elementos (esperado 16)")
            }
            for (i in 0 until 16) m.m[i] = arr.getDouble(i).toFloat()
            return m
        }
        val pos = Vec3()
        if (node.has("translation")) {
            val t = node.getJSONArray("translation")
            if (t.length() != 3) throw GltfImportException("translation com ${t.length()} elementos (esperado 3)")
            pos.set(t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
        }
        val esc = Vec3(1f, 1f, 1f)
        if (node.has("scale")) {
            val s = node.getJSONArray("scale")
            if (s.length() != 3) throw GltfImportException("scale com ${s.length()} elementos (esperado 3)")
            esc.set(s.getDouble(0).toFloat(), s.getDouble(1).toFloat(), s.getDouble(2).toFloat())
        }
        val rot = Quaternion()
        if (node.has("rotation")) {
            val r = node.getJSONArray("rotation")
            if (r.length() != 4) throw GltfImportException("rotation com ${r.length()} elementos (esperado 4: xyzw)")
            rot.set(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat())
            // Normaliza (glTF não garante; a Mat4 do Motor assume quaternion unit).
            val len = sqrt(rot.x * rot.x + rot.y * rot.y + rot.z * rot.z + rot.w * rot.w)
            if (len > 1e-8f) rot.set(rot.x / len, rot.y / len, rot.z / len, rot.w / len)
        }
        return m.setTRS(pos, rot, esc)
    }

    // ---------------------------------------------------------------- cena → malha

    /** Contexto de materiais/texturas da importação (ETAPA B.3). */
    private class ContextoMaterial(
        val raiz: JSONObject,
        val buffers: List<ByteArray>,
        val resolutor: ResolvedorExterno?,
        val nomeArquivo: String,
        val manterTangentes: Boolean
    ) {
        val materiaisTabela = ArrayList<KernMaterial>()       // dedup por conteúdo
        val mapaJsonParaIndice = HashMap<Int, Int>()          // índice JSON → índice na tabela
        val texturas = ArrayList<TexturaImportada>()
        val texturasPorHash = HashMap<String, Int>()          // sha1 → índice em texturas
        val avisos = ArrayList<String>(2)
        var algumNormalMap = false
    }

    private fun montarMalha(
        raiz: JSONObject,
        buffers: List<ByteArray>,
        bytesOrigem: Int,
        resolutor: ResolvedorExterno?,
        nomeArquivo: String,
        manterTangentes: Boolean
    ): ImportResult {
        val builder = KernWriter.Builder()
        val ctx = ContextoMaterial(raiz, buffers, resolutor, nomeArquivo, manterTangentes)
        var primitivas = 0
        var nodesComMesh = 0
        var normaisCalculadas = false

        // HONESTIDADE (auditoria V3): o que o glTF DECLARA e esta fase ignora
        // entra no ImportReport como aviso — nada é descartado em silêncio.
        // Extensões OBRIGATÓRIAS (extensionsRequired) rejeitam o arquivo com
        // erro claro (spec glTF: leitor sem suporte NÃO pode fingir sucesso).
        anunciarIgnorados(raiz, ctx)

        val meshes = raiz.optJSONArray("meshes")
            ?: throw GltfImportException("glTF sem array 'meshes' (nada para importar)")
        val nodes = raiz.optJSONArray("nodes")
            ?: throw GltfImportException("glTF sem array 'nodes' — nada para importar")
        val raizesDaCena = escolherCena(raiz)
            ?: throw GltfImportException("glTF sem 'scenes' — nada para importar")

        val identidade = Mat4().setIdentity()
        for (i in 0 until raizesDaCena.length()) {
            val r = percorrerNode(
                raiz, nodes, raizesDaCena.getInt(i), identidade, meshes, buffers, builder, ctx
            )
            primitivas += r.second
            nodesComMesh += r.third
            normaisCalculadas = normaisCalculadas || r.fourth
        }

        if (primitivas == 0 || builder.positions.isEmpty()) {
            throw GltfImportException("cena sem geometria importável (0 primitivas TRIANGLES)")
        }

        // Tabela de materiais dedupada entra no .KERN v2 (B.3)
        for (m in ctx.materiaisTabela) builder.material(m)

        // Avisos repetidos (mesma frase em N materiais) viram UM só.
        val avisosUnicos = ctx.avisos.distinct()
        ctx.avisos.clear()
        ctx.avisos.addAll(avisosUnicos)

        val malha = builder.build()
        val bytesKern = KernWriter.write(malha)
        val report = ImportReport(
            bytesOrigem = bytesOrigem,
            bytesKern = bytesKern.size,
            vertices = malha.vertexCount,
            triangulos = malha.indices.size / 3,
            lods = malha.lods.size,
            primitivas = primitivas,
            nodesComMesh = nodesComMesh,
            normaisCalculadas = normaisCalculadas,
            materialCount = ctx.materiaisTabela.size,
            textureCount = ctx.texturas.size
        )
        return ImportResult(malha, bytesKern, report, ctx.avisos, ctx.texturas)
    }

    /** Percorre o node e seus filhos; devolve (percorridos, primitivas, comMesh, calculouNormais). */
    private fun percorrerNode(
        raiz: JSONObject,
        nodes: org.json.JSONArray,
        indice: Int,
        pai: Mat4,
        meshes: org.json.JSONArray,
        buffers: List<ByteArray>,
        builder: KernWriter.Builder,
        ctx: ContextoMaterial
    ): Quad {
        if (indice < 0 || indice >= nodes.length()) {
            throw GltfImportException("node $indice não existe (${nodes.length()} definidos)")
        }
        val node = nodes.getJSONObject(indice)
        val local = matrizDoNode(node)
        val mundo = Mat4().multiply(pai, local)
        var primitivas = 0
        var comMesh = 0
        var calculouNormais = false

        if (node.has("mesh")) {
            val meshIndex = node.getInt("mesh")
            if (meshIndex < 0 || meshIndex >= meshes.length()) {
                throw GltfImportException("mesh $meshIndex não existe (${meshes.length()} definidas)")
            }
            comMesh = 1
            val mesh = meshes.getJSONObject(meshIndex)
            val prims = mesh.optJSONArray("primitives")
                ?: throw GltfImportException("mesh $meshIndex sem 'primitives'")
            for (p in 0 until prims.length()) {
                val calculou = importarPrimitiva(ctx, builder, prims.getJSONObject(p), meshIndex, p, mundo)
                primitivas++
                calculouNormais = calculouNormais || calculou
            }
        }

        val filhos = node.optJSONArray("children")
        var percorridos = 1
        if (filhos != null) {
            for (i in 0 until filhos.length()) {
                val r = percorrerNode(raiz, nodes, filhos.getInt(i), mundo, meshes, buffers, builder, ctx)
                percorridos += r.first
                primitivas += r.second
                comMesh += r.third
                calculouNormais = calculouNormais || r.fourth
            }
        }
        return Quad(percorridos, primitivas, comMesh, calculouNormais)
    }

    private class Quad(val first: Int, val second: Int, val third: Int, val fourth: Boolean)

    /** Importa UMA primitiva TRIANGLES com transform de mundo aplicado. Retorna true se calculou as normais. */
    private fun importarPrimitiva(
        ctx: ContextoMaterial,
        builder: KernWriter.Builder,
        prim: JSONObject,
        meshIndex: Int,
        primIndex: Int,
        mundo: Mat4
    ): Boolean {
        val mode = prim.optInt("mode", MODE_TRIANGLES)
        if (mode != MODE_TRIANGLES) {
            throw GltfImportException(
                "primitiva $primIndex da mesh $meshIndex usa mode=$mode — só TRIANGLES (4) é suportado nesta fase"
            )
        }
        val attrs = prim.getJSONObject("attributes")
        val posIndex = attrs.optInt("POSITION", -1)
        if (posIndex < 0) {
            throw GltfImportException("primitiva $primIndex da mesh $meshIndex sem POSITION (obrigatório)")
        }
        val pos = lerAcessorFloat(ctx.raiz, ctx.buffers, posIndex)
        if (pos.isEmpty() || pos.size % 3 != 0) {
            throw GltfImportException(
                "POSITION da primitiva $primIndex (mesh $meshIndex) tem ${pos.size} floats — deve ser múltiplo de 3"
            )
        }
        val verts = pos.size / 3

        // MATERIAL (B.3): resolve/cria na tabela; materialIndex = -1 = sem material.
        val materialIdx = if (prim.has("material")) resolverMaterial(ctx, prim.getInt("material")) else -1
        val temNormalMap = materialIdx >= 0 && ctx.materiaisTabela[materialIdx].temTexturaNormal

        val normIndex = attrs.optInt("NORMAL", -1)
        val normais: FloatArray? =
            if (normIndex >= 0) {
                val n = lerAcessorFloat(ctx.raiz, ctx.buffers, normIndex)
                if (n.size != pos.size) {
                    throw GltfImportException("NORMAL (${n.size} floats) diverge de POSITION (${pos.size})")
                }
                n
            } else null

        // TANGENTES (B.6): só têm uso com normal map (ou opção de manter);
        // caso contrário ficam ZERADAS no .KERN (não desperdiça compressão).
        val tangIndex = attrs.optInt("TANGENT", -1)
        val tangentes: FloatArray? =
            if (tangIndex >= 0 && (temNormalMap || ctx.manterTangentes)) {
                val t = lerAcessorFloat(ctx.raiz, ctx.buffers, tangIndex)
                if (t.size != verts * 4 && t.size != verts * 3) {
                    throw GltfImportException(
                        "TANGENT (${t.size} floats) diverge de $verts vértices (esperado 3 ou 4 componentes)"
                    )
                }
                t
            } else null

        val uvIndex = attrs.optInt("TEXCOORD_0", -1)
        val uvs: FloatArray =
            if (uvIndex >= 0) {
                val uv = lerAcessorFloat(ctx.raiz, ctx.buffers, uvIndex)
                if (uv.size != verts * 2) {
                    throw GltfImportException("TEXCOORD_0 (${uv.size} floats) diverge de $verts vértices")
                }
                uv
            } else FloatArray(verts * 2)

        val indicesBrutos: IntArray = if (prim.has("indices")) {
            lerAcessorIndices(ctx.raiz, ctx.buffers, prim.getInt("indices"))
        } else {
            IntArray(verts) { it }
        }
        if (indicesBrutos.size % 3 != 0) {
            throw GltfImportException(
                "primitiva $primIndex (mesh $meshIndex) tem ${indicesBrutos.size} índices — deve ser múltiplo de 3"
            )
        }
        for (idx in indicesBrutos) {
            if (idx < 0 || idx >= verts) {
                throw GltfImportException(
                    "índice $idx fora do range da primitiva $primIndex (mesh $meshIndex, 0..${verts - 1})"
                )
            }
        }

        val base = builder.positions.size / 3
        val grupoOffset = builder.indices.size // início do grupo de material (B.3)
        val matrizNormal = if (normais != null) calcularMatrizNormal(mundo) else null

        // Vértices com o transform de mundo aplicado.
        for (v in 0 until verts) {
            val wx = aplicarLinha(mundo, 0, pos, v)
            val wy = aplicarLinha(mundo, 1, pos, v)
            val wz = aplicarLinha(mundo, 2, pos, v)
            var nx = 0f; var ny = 1f; var nz = 0f
            if (normais != null) {
                val m = matrizNormal!!
                val x = normais[v * 3]
                val y = normais[v * 3 + 1]
                val z = normais[v * 3 + 2]
                nx = m[0] * x + m[1] * y + m[2] * z
                ny = m[3] * x + m[4] * y + m[5] * z
                nz = m[6] * x + m[7] * y + m[8] * z
            }
            var tx = 0f; var ty = 0f; var tz = 0f
            if (tangentes != null) {
                tx = tangentes[v * 3]
                ty = tangentes[v * 3 + 1]
                tz = tangentes[v * 3 + 2]
                // linear do mundo + renormalização (mesma regra da normal)
                val rtx = mundo.m[0] * tx + mundo.m[4] * ty + mundo.m[8] * tz
                val rty = mundo.m[1] * tx + mundo.m[5] * ty + mundo.m[9] * tz
                val rtz = mundo.m[2] * tx + mundo.m[6] * ty + mundo.m[10] * tz
                val lenT = kotlin.math.sqrt(rtx * rtx + rty * rty + rtz * rtz)
                if (lenT > 1e-8f) { tx = rtx / lenT; ty = rty / lenT; tz = rtz / lenT }
            }
            builder.vertex(wx, wy, wz, nx, ny, nz, uvs[v * 2], uvs[v * 2 + 1], tx, ty, tz)
        }

        // Normais de ORIGEM: renormaliza após o transform (mat3 com escala).
        val calculouNormais: Boolean
        if (normais != null) {
            renormalizar(builder, base, verts)
            calculouNormais = false
        } else {
            // Sem NORMAL: acumula normais de face em espaço de mundo e renormaliza.
            val acumuladas = FloatArray(verts * 3)
            for (i in indicesBrutos.indices step 3) {
                acumularFace(acumuladas, pos, mundo, indicesBrutos[i], indicesBrutos[i + 1], indicesBrutos[i + 2])
            }
            for (v in 0 until verts) {
                val i = (base + v) * 3
                builder.normals[i] = acumuladas[v * 3]
                builder.normals[i + 1] = acumuladas[v * 3 + 1]
                builder.normals[i + 2] = acumuladas[v * 3 + 2]
            }
            renormalizar(builder, base, verts)
            calculouNormais = true
        }

        // Índices remapeados pela base de vértices (merge de primitivas).
        for (i in indicesBrutos.indices step 3) {
            builder.triangle(
                indicesBrutos[i] + base,
                indicesBrutos[i + 1] + base,
                indicesBrutos[i + 2] + base
            )
        }
        // GRUPO DE MATERIAL (B.3): intervalo de índices desta primitiva + material.
        if (materialIdx >= 0) {
            builder.materialGroup(grupoOffset, builder.indices.size - grupoOffset, materialIdx)
        }
        return calculouNormais
    }

    // ---------------------------------------------------------------- materiais (B.3)

    /**
     * Resolve o material JSON (índice) para a tabela DEDUPADA do .KERN; cria a
     * entrada na 1ª aparição e coleta os bytes das texturas (baseColor/normal).
     * Textura AUSENTE (arquivo faltando) NÃO derruba a importação: material
     * vira cor sólida + AVISO (nunca silêncio, nunca crash).
     */
    private fun resolverMaterial(ctx: ContextoMaterial, indiceJson: Int): Int {
        ctx.mapaJsonParaIndice[indiceJson]?.let { return it }
        val materiais = ctx.raiz.optJSONArray("materials")
        if (materiais == null || indiceJson < 0 || indiceJson >= materiais.length()) {
            ctx.avisos.add("material $indiceJson não existe (tabela tem ${materiais?.length() ?: 0}) — usando cor sólida")
            return -1
        }
        val m = materiais.getJSONObject(indiceJson)
        val pbr = m.optJSONObject("pbrMetallicRoughness")
        var r = 1f; var g = 1f; var b = 1f; var a = 1f
        pbr?.optJSONArray("baseColorFactor")?.let { c ->
            if (c.length() >= 3) {
                r = c.getDouble(0).toFloat(); g = c.getDouble(1).toFloat(); b = c.getDouble(2).toFloat()
                if (c.length() >= 4) a = c.getDouble(3).toFloat()
            }
        }
        val metallic = pbr?.optDouble("metallicFactor", 0.0)?.toFloat() ?: 0f
        val roughness = pbr?.optDouble("roughnessFactor", 1.0)?.toFloat() ?: 1f
        val nome = m.optString("name", "material$indiceJson")
        val doubleSided = m.optBoolean("doubleSided", false)

        var texBasePath = ""
        var temTexBase = false
        var texNormalPath = ""
        var temTexNormal = false

        pbr?.optJSONObject("baseColorTexture")?.let { t ->
            val caminho = caminhoTextura(ctx, t.optInt("index", -1), "baseColor")
            if (caminho != null) { texBasePath = caminho; temTexBase = true }
        }
        m.optJSONObject("normalTexture")?.let { t ->
            val caminho = caminhoTextura(ctx, t.optInt("index", -1), "normal")
            if (caminho != null) { texNormalPath = caminho; temTexNormal = true; ctx.algumNormalMap = true }
        }

        // HONESTIDADE (V3): texturas de PBR que o .KERN v2 NÃO carrega ficam
        // no ImportReport — o aparelho não pode surpreender o usuário.
        for (ignorado in listOf("emissiveTexture", "occlusionTexture")) {
            if (m.optJSONObject(ignorado) != null) {
                ctx.avisos.add("material '$nome': textura $ignorado IGNORADA nesta fase (o .KERN v2 só leva baseColor + normal)")
            }
        }
        if (pbr?.optJSONObject("metallicRoughnessTexture") != null) {
            ctx.avisos.add("material '$nome': textura metallicRoughness IGNORADA nesta fase (entram os FATORES metallic/roughness)")
        }

        val kern = KernMaterial(
            nome, r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), a.coerceIn(0f, 1f),
            metallic.coerceIn(0f, 1f), roughness.coerceIn(0f, 1f),
            temTexBase, temTexNormal, doubleSided, texBasePath, texNormalPath
        )
        // DEDUP de materiais iguais (B.4): conteúdo idêntico = MESMA entrada.
        val existente = ctx.materiaisTabela.indexOfFirst { it == kern }
        val indiceTabela = if (existente >= 0) existente else {
            ctx.materiaisTabela.add(kern)
            ctx.materiaisTabela.size - 1
        }
        ctx.mapaJsonParaIndice[indiceJson] = indiceTabela
        return indiceTabela
    }

    /**
     * Caminho FINAL do asset de textura ("assets/textures/<sha1>.kimg") a partir
     * do índice na tabela "textures" do glTF; os bytes originais vão para
     * [ctx.texturas] (dedup por hash — C.5).
     */
    private fun caminhoTextura(ctx: ContextoMaterial, indiceTextura: Int, uso: String): String? {
        val textures = ctx.raiz.optJSONArray("textures")
        if (textures == null || indiceTextura < 0 || indiceTextura >= textures.length()) {
            ctx.avisos.add("textura $uso ($indiceTextura) não existe — material vira cor sólida")
            return null
        }
        val t = textures.getJSONObject(indiceTextura)
        val source = t.optInt("source", -1)
        val images = ctx.raiz.optJSONArray("images")
        if (source < 0 || images == null || source >= images.length()) {
            ctx.avisos.add("textura $uso $indiceTextura sem imagem de origem — cor sólida")
            return null
        }
        val img = images.getJSONObject(source)
        val mimeType = img.optString("mimeType", checarMimeTypePorNome(img.optString("uri", "")))
        val bytes: ByteArray = try {
            when {
                img.has("bufferView") -> {
                    // embutida no GLB: recorta do buffer
                    val views = ctx.raiz.getJSONArray("bufferViews")
                    val view = views.getJSONObject(img.getInt("bufferView"))
                    val buf = ctx.buffers[view.optInt("buffer", 0)]
                    val off = view.optInt("byteOffset", 0)
                    val len = view.optInt("byteLength", -1)
                    if (len < 0 || off < 0 || off + len > buf.size) {
                        throw GltfImportException("bufferView da imagem $source estoura o buffer")
                    }
                    buf.copyOfRange(off, off + len)
                }
                img.has("uri") -> {
                    val uri = img.getString("uri")
                    if (uri.startsWith("data:")) {
                        val virgula = uri.indexOf(',')
                        if (virgula < 0) throw GltfImportException("data URI da imagem $source sem ','")
                        java.util.Base64.getMimeDecoder().decode(uri.substring(virgula + 1))
                    } else {
                        val viaResolver = ctx.resolutor?.bytes(uri)
                            ?: throw GltfImportException("imagem '$uri' não achada (importe por Pasta/.zip)")
                        viaResolver
                    }
                }
                else -> throw GltfImportException("imagem $source sem bufferView nem uri")
            }
        } catch (e: Exception) {
            // Textura faltando = degradação visível (aviso), não falha da importação.
            ctx.avisos.add("textura $uso '$source' não pôde ser lida (${e.message ?: e.javaClass.simpleName}) — cor sólida")
            return null
        }
        if (bytes.isEmpty()) {
            ctx.avisos.add("textura $uso '$source' vazia — cor sólida")
            return null
        }
        val sha1 = AssetLayout.sha1Hex(bytes)
        if (sha1 !in ctx.texturasPorHash) {
            ctx.texturasPorHash[sha1] = ctx.texturas.size
            ctx.texturas.add(TexturaImportada(mimeType, bytes, img.optString("name", "$uso-$sha1"), sha1))
        }
        return "${AssetLayout.DIR_TEXTURAS}/$sha1.kimg"
    }

    private fun checarMimeTypePorNome(nome: String): String = when {
        nome.endsWith(".png", true) -> "image/png"
        nome.endsWith(".jpg", true) || nome.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "image/png"
    }

    private fun renormalizar(builder: KernWriter.Builder, base: Int, vertices: Int) {
        for (v in 0 until vertices) {
            val i = (base + v) * 3
            val x = builder.normals[i]; val y = builder.normals[i + 1]; val z = builder.normals[i + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len > 1e-8f) {
                builder.normals[i] = x / len
                builder.normals[i + 1] = y / len
                builder.normals[i + 2] = z / len
            } else {
                builder.normals[i] = 0f
                builder.normals[i + 1] = 1f
                builder.normals[i + 2] = 0f
            }
        }
    }

    /**
     * Retorna uma matriz proporcional à inversa-transposta da parte linear de
     * mundo em ordem por linhas. Como as normais são renormalizadas depois,
     * escala-la por |det| evita divisões instáveis sem alterar a direção.
     */
    private fun calcularMatrizNormal(mundo: Mat4): FloatArray {
        val m = mundo.m
        val escala = maxOf(
            abs(m[0]), abs(m[1]), abs(m[2]), abs(m[4]), abs(m[5]),
            abs(m[6]), abs(m[8]), abs(m[9]), abs(m[10])
        )
        if (!escala.isFinite() || escala == 0f) {
            throw GltfImportException("transform de node singular: não é possível transformar as normais")
        }
        val a00 = m[0] / escala; val a01 = m[4] / escala; val a02 = m[8] / escala
        val a10 = m[1] / escala; val a11 = m[5] / escala; val a12 = m[9] / escala
        val a20 = m[2] / escala; val a21 = m[6] / escala; val a22 = m[10] / escala
        val c00 = a11 * a22 - a12 * a21
        val c01 = a12 * a20 - a10 * a22
        val c02 = a10 * a21 - a11 * a20
        val c10 = a02 * a21 - a01 * a22
        val c11 = a00 * a22 - a02 * a20
        val c12 = a01 * a20 - a00 * a21
        val c20 = a01 * a12 - a02 * a11
        val c21 = a02 * a10 - a00 * a12
        val c22 = a00 * a11 - a01 * a10
        val det = a00 * c00 + a01 * c01 + a02 * c02
        if (!det.isFinite() || abs(det) < 1e-8f) {
            throw GltfImportException("transform de node singular: não é possível transformar as normais")
        }
        val sinal = if (det < 0f) -1f else 1f
        return floatArrayOf(
            c00 * sinal, c01 * sinal, c02 * sinal,
            c10 * sinal, c11 * sinal, c12 * sinal,
            c20 * sinal, c21 * sinal, c22 * sinal
        )
    }

    /** Normal de face em ESPAÇO DE MUNDO (posições transformadas), acumulada nos 3 vértices. */
    private fun acumularFace(
        acumuladas: FloatArray,
        pos: FloatArray,
        mundo: Mat4,
        a: Int, b: Int, c: Int
    ) {
        val ax = aplicarLinha(mundo, 0, pos, a); val ay = aplicarLinha(mundo, 1, pos, a); val az = aplicarLinha(mundo, 2, pos, a)
        val bx = aplicarLinha(mundo, 0, pos, b); val by = aplicarLinha(mundo, 1, pos, b); val bz = aplicarLinha(mundo, 2, pos, b)
        val cx = aplicarLinha(mundo, 0, pos, c); val cy = aplicarLinha(mundo, 1, pos, c); val cz = aplicarLinha(mundo, 2, pos, c)
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

    /**
     * HONESTIDADE (auditoria v0.1.15, V3): lista no ImportReport TUDO que o
     * glTF declara e esta fase ignora (esqueleto, animações, extensões) e
     * REJEITA com erro claro os arquivos que exigem extensão não suportada
     * (Draco/Meshopt = buffers comprimidos — o parser não consegue ler, então
     * "importar por cima" produziria geometria errada em silêncio).
     */
    private fun anunciarIgnorados(raiz: JSONObject, ctx: ContextoMaterial) {
        val exigidas = raiz.optJSONArray("extensionsRequired")
        if (exigidas != null && exigidas.length() > 0) {
            val nomes = (0 until exigidas.length()).joinToString(", ") { exigidas.optString(it) }
            throw GltfImportException(
                "glTF exige extensões que o importador NÃO suporta: [$nomes] — " +
                    "exporte o modelo sem compressão Draco/Meshopt e sem extensões obrigatórias"
            )
        }
        val declaradas = raiz.optJSONArray("extensionsUsed")
        if (declaradas != null && declaradas.length() > 0) {
            val nomes = (0 until declaradas.length()).joinToString(", ") { declaradas.optString(it) }
            ctx.avisos.add("extensões glTF declaradas e IGNORADAS nesta fase: $nomes")
        }
        val skins = raiz.optJSONArray("skins")
        if (skins != null && skins.length() > 0) {
            ctx.avisos.add("contém esqueleto: ignorado nesta fase (Fase 8 OSSATURA)")
        }
        val animacoes = raiz.optJSONArray("animations")
        if (animacoes != null && animacoes.length() > 0) {
            ctx.avisos.add("contém animações: ignoradas nesta fase (Fase 8 OSSATURA)")
        }
    }

    private fun escolherCena(raiz: JSONObject): org.json.JSONArray? {
        val scenes = raiz.optJSONArray("scenes") ?: return null
        val cenaIndex = raiz.optInt("scene", 0)
        if (cenaIndex < 0 || cenaIndex >= scenes.length()) {
            throw GltfImportException("scene $cenaIndex não existe (${scenes.length()} definidas)")
        }
        return scenes.getJSONObject(cenaIndex).optJSONArray("nodes")
    }

    // ---------------------------------------------------------------- helpers de matriz

    /** Aplica a LINHA [linha] de M (column-major: m[col*4+row]) a v3[v]. */
    private fun aplicarLinha(m: Mat4, linha: Int, v3: FloatArray, v: Int): Float =
        m.m[linha] * v3[v * 3] +
            m.m[4 + linha] * v3[v * 3 + 1] +
            m.m[8 + linha] * v3[v * 3 + 2] +
            m.m[12 + linha]

    // ---------------------------------------------------------------- util de GLB (testes)

    /** Monta um GLB válido a partir de JSON + BIN (usado pelos testes JVM e pelo Diagnóstico). */
    fun empacotarGlb(json: String, bin: ByteArray?): ByteArray {
        val jsonPad = pad4(json.toByteArray(Charsets.UTF_8), fill = 0x20)
        val binPad = bin?.let { pad4(it, fill = 0) }
        val total = 12 + 8 + jsonPad.size + (binPad?.let { 8 + it.size } ?: 0)
        val out = ByteArrayOutputStream(total)
        val head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        head.put("glTF".toByteArray(Charsets.US_ASCII))
        head.putInt(2)
        head.putInt(total)
        out.write(head.array())
        val jh = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        jh.putInt(jsonPad.size)
        jh.put("JSON".toByteArray(Charsets.US_ASCII))
        out.write(jh.array())
        out.write(jsonPad)
        if (binPad != null) {
            val bh = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            bh.putInt(binPad.size)
            bh.put("BIN\u0000".toByteArray(Charsets.US_ASCII))
            out.write(bh.array())
            out.write(binPad)
        }
        return out.toByteArray()
    }

    private fun pad4(dados: ByteArray, fill: Byte): ByteArray {
        val resto = dados.size % 4
        if (resto == 0) return dados
        return dados + ByteArray(4 - resto) { fill }
    }
}
