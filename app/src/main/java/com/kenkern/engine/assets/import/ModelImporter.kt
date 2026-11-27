package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.image.PerfilTexturaImportacao
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernMesh
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.diag.KenkernLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Erro de IMPORTAÇÃO DE MODELO com motivo CLARO (regra #17: o chamador registra
 * no KenkernLog/Console e mostra na tela — nunca silêncio, nunca crash).
 */
class ImportException(motivo: String) : Exception(motivo)

/**
 * Formato detectado pelo magic/assinatura (nunca só pela extensão).
 */
enum class TipoFormato(val rotulo: String) {
    GLB("GLB"), GLTF_JSON("glTF (JSON)"), FBX_BIN("FBX binário"),
    FBX_ASCII("FBX ASCII"), ZIP("glTF/FBX dentro de .zip"), DESCONHECIDO("desconhecido")
}

/**
 * RESOLVEDOR DE ARQUIVOS EXTERNOS do glTF (.bin, texturas) — B.1/C.4.
 * Implementações: pasta SAF (ViewModel, ContentResolver) e .zip (aqui, com
 * bloqueio de zip-slip). Devolve os bytes do caminho RELATIVO pedido pelo
 * glTF ou null (o importador converte null em erro CLARO com o nome).
 */
interface ResolvedorExterno {
    fun bytes(caminhoRelativo: String): ByteArray?
}

/** Resolver sobre as entradas de um .zip já em memória. Zip-slip bloqueado em AssetLayout. */
class ResolvedorZip(val entradas: Map<String, ByteArray>, private val pastaBase: String = "") : ResolvedorExterno {
    override fun bytes(caminhoRelativo: String): ByteArray? {
        val seguro = AssetLayout.caminhoRelativoSeguro(caminhoRelativo) ?: return null
        // O glTF resolve URIs RELATIVAS AO MODELO: tenta 1) raiz do zip, 2) pasta
        // do modelo, 3) só o nome do arquivo (exports que acham tudo na mesma pasta).
        if (pastaBase.isNotEmpty()) entradas["$pastaBase/$seguro"]?.let { return it }
        return entradas[seguro] ?: entradas[seguro.substringAfterLast('/')]
    }

    companion object {
        /** Lê TODAS as entradas do zip (com limites; zip-slip bloqueado por entrada). */
        fun ler(bytes: ByteArray, maxEntradas: Int = 4096, maxTotalBytes: Long = AssetLayout.LIMITE_DURO_BYTES): ResolvedorZip {
            val mapa = HashMap<String, ByteArray>()
            var total = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                var e: ZipEntry? = zin.nextEntry
                var contagem = 0
                while (e != null) {
                    if (contagem++ > maxEntradas) throw ImportException("zip com mais de $maxEntradas entradas — recusado")
                    val caminho = AssetLayout.caminhoRelativoSeguro(e.name)
                        ?: throw ImportException(
                            "entrada do zip com caminho inseguro ('${e.name.take(64)}') — zip-slip bloqueado"
                        )
                    if (!e.isDirectory) {
                        val buf = ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        var n = zin.read(chunk)
                        while (n >= 0) {
                            total += n
                            if (total > maxTotalBytes) throw ImportException("zip descomprime além do limite de ${maxTotalBytes / (1024 * 1024)} MB — recusado")
                            buf.write(chunk, 0, n)
                            n = zin.read(chunk)
                        }
                        mapa[caminho] = buf.toByteArray()
                    }
                    e = zin.nextEntry
                }
            }
            if (mapa.isEmpty()) throw ImportException("zip vazio (nenhuma entrada utilizável)")
            return ResolvedorZip(mapa)
        }
    }
}

/**
 * RELATÓRIO COMPLETO da importação (ETAPA A.4): tudo que o diálogo mostra e o
 * "Copiar relatório" leva. Números REAIS medidos (nunca estimativa sem marca).
 */
class ImportSummary(
    val nomeArquivo: String,
    val formato: String,
    val bytesOrigem: Long,
    val bytesKern: Int,
    val bytesKernData: ByteArray,
    val vertices: Int,
    val triangulos: Int,
    val lods: Int,
    val materiais: Int,
    val texturas: Int,
    val avisos: List<String>,
    val tempoMs: Long,
    val picoMemoriaMb: Float,
    val integridadeOk: Boolean,
    val problemasIntegridade: List<String>,
    val erroMaxQuantizacao: Float,
    /** Preenchido pelo chamador AO GRAVAR os arquivos (var: o caminho só existe depois). */
    var caminhoKernRelativo: String = "",
    var caminhoOrigemRelativo: String = "",
    val aabbMin: FloatArray,
    val aabbMax: FloatArray,
    val contemEsqueleto: Boolean,
    /** T0.4 (v0.1.20): vértices com JOINTS/WEIGHTS (skin não aplicado — Fase 8). */
    val verticesComSkin: Int = 0,
    /** Escala do Nick sugerida pela opção "Ajustar ao tamanho" (1f = sem ajuste). */
    val escalaSugerida: Float = 1f,
    /** Texturas de origem coletadas (ETAPA C converte para .KIMG com esses bytes). */
    val texturasExtraidas: List<TexturaImportada> = emptyList(),
    /** Relatórios de conversão de texturas preenchidos pelo chamador (ETAPA C). */
    val texturasRelatorio: MutableList<String> = mutableListOf(),
    /**
     * B1 (v0.1.17): tabela de tempo por etapa (ms e % do total) medida pelo
     * [CronometroImport] durante TODA a importação — preenchida pelo chamador
     * ao concluir; entra no textoRelatorio() e no Console.
     */
    var tabelaTempo: String = "",
    /** B2 (v0.1.17): opções da importação (limite de textura, qualidade ETC2). */
    var opcoesImport: String = ""
) {
    /** Razão origem/.KERN (ex.: 5.0 = 100 MB viraram 20 MB — meta do ROADMAP). */
    val razao: Float get() = if (bytesKern > 0) bytesOrigem / bytesKern.toFloat() else 0f

    /** Texto completo para o diálogo e para a área de transferência. */
    fun textoRelatorio(): String = buildString {
        appendLine("ImportReport — $nomeArquivo")
        appendLine("Formato: $formato")
        appendLine("Tamanho: %.1f MB origem → %.2f MB .KERN (razão %.2f×)".format(
            java.util.Locale.US, bytesOrigem / 1048576f, bytesKern / 1048576f, razao))
        appendLine("Geometria: %d vértices · %d triângulos · %d LOD".format(java.util.Locale.US, vertices, triangulos, lods))
        appendLine("Materiais: $materiais · Texturas: $texturas")
        appendLine("AABB: min (%.2f, %.2f, %.2f) max (%.2f, %.2f, %.2f)".format(
            java.util.Locale.US, aabbMin[0], aabbMin[1], aabbMin[2], aabbMax[0], aabbMax[1], aabbMax[2]))
        appendLine("KernIntegrity: ${if (integridadeOk) "OK" else "FALHOU"}" +
            (if (problemasIntegridade.isNotEmpty()) " — " + problemasIntegridade.joinToString("; ") else "") +
            " · erro máx de quantização %.2f passo(s)".format(java.util.Locale.US, erroMaxQuantizacao))
        appendLine("Tempo: %d ms · pico de memória ≈ %.0f MB".format(java.util.Locale.US, tempoMs, picoMemoriaMb))
        if (opcoesImport.isNotEmpty()) appendLine("Opções: $opcoesImport")
        if (tabelaTempo.isNotEmpty()) {
            appendLine()
            append(tabelaTempo.trimEnd())
            appendLine()
        }
        if (contemEsqueleto) appendLine("AVISO: esqueleto/skinning ainda não suportado — Onda 3 (ossos ignorados nesta versão)")
        if (verticesComSkin > 0) {
            appendLine(
                "AVISO: esqueleto/skinning ainda não suportado — Onda 3: $verticesComSkin vértices com JOINTS/WEIGHTS " +
                    "NÃO APLICADOS (os ossos não movem vértices nesta versão) — partes presas ao esqueleto ficam na " +
                    "pose de bind e podem aparecer colapsadas/ausentes (ex.: cabeça); a malha entra ESTÁTICA."
            )
        }
        for (a in avisos) appendLine("AVISO: $a")
        for (r in texturasRelatorio) appendLine("TEXTURA: $r")
        appendLine("Gravado: $caminhoKernRelativo · origem copiada: $caminhoOrigemRelativo")
    }
}

/**
 * ORQUESTRADOR DE IMPORTAÇÃO (ETAPA A): detecta o formato, despacha para o
 * importador certo (glTF/GLB do F2.7, FBX do F2.8), mede tempo/memória, roda o
 * KernIntegrity e devolve o [ImportSummary]. SEM efeito colateral de arquivo —
 * quem grava origem/.KERN é o chamador (ViewModel), que também cria o Nick via
 * UM Command (undo remove Nick e assets).
 *
 * Progresso por etapas ("lendo" é do chamador): "triangulando", "gravando
 * .KERN", "validando" — via [onEtapa]. CANCELAMENTO checado entre etapas
 * (bom o suficiente para arquivos de segundos; cancelar DENTRO de um parser
 * único é escopo do streaming F2.11).
 */
object ModelImporter {

    const val CLASSE_MALHA = "Malha"

    private const val TAG = "ModelImporter"

    /** Opções da importação (B.6: manter tangentes mesmo sem normal map). */
    class Opcoes(
        val ajustarAoTamanho: Boolean = false,
        val alturaMeta: Float = 2f,
        val manterTangentes: Boolean = false,
        val perfilTextura: PerfilTexturaImportacao? = null
    )

    /** Detecta o formato pela ASSINATURA (magic), nunca pela extensão sozinha. */
    fun detectarFormato(bytes: ByteArray): TipoFormato {
        if (bytes.size < 8) return TipoFormato.DESCONHECIDO
        // GLB: "glTF"
        if (bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) return TipoFormato.GLB
        // ZIP: "PK\x03\x04"
        if (bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()
        ) return TipoFormato.ZIP
        // FBX binário: "Kaydara FBX Binary  "
        if (bytes.size >= 23) {
            var ok = true
            for (i in 0 until 18) {
                val esperado = "Kaydara FBX Binary  "[i]
                if (bytes[i] != esperado.code.toByte()) { ok = false; break }
            }
            if (ok) return TipoFormato.FBX_BIN
        }
        // FBX ASCII ou glTF JSON: texto no início (tolera BOM/espaços)
        var start = 0
        while (start < bytes.size && bytes[start].toInt().toChar().isWhitespace()) start++
        if (start + 2 < bytes.size && bytes[start] == 0xEF.toByte() &&
            bytes[start + 1] == 0xBB.toByte() && bytes[start + 2] == 0xBF.toByte()
        ) start += 3
        while (start < bytes.size && bytes[start].toInt().toChar().isWhitespace()) start++
        val head = String(bytes, start, minOf(bytes.size - start, 200), Charsets.US_ASCII)
        if (head.startsWith("FBXHeaderExtension")) return TipoFormato.FBX_ASCII
        if (head.contains("FBXHeaderExtension")) return TipoFormato.FBX_ASCII
        if (head.startsWith("{")) return TipoFormato.GLTF_JSON
        return TipoFormato.DESCONHECIDO
    }

    /**
     * Importa o arquivo completo e devolve o resumo SEM gravar nada.
     * [bytesOrigem] é o conteúdo do arquivo de origem JÁ lido (uma única vez).
     * [resolutorExterno] resolve os arquivos externos de um .gltf vindo de PASTA
     * (SAF) — para .zip o resolver é construído aqui, das entradas do pacote.
     * [cronometro] (B1): mede parse, dedup, LODs, serialização .KERN e validação
     * — a tabela final vai para o ImportReport e para o Console.
     */
    fun importarModelo(
        nomeArquivo: String,
        bytesOrigem: ByteArray,
        opcoes: Opcoes = Opcoes(),
        onEtapa: (String) -> Unit = {},
        cancelado: () -> Boolean = { false },
        resolutorExterno: ResolvedorExterno? = null,
        cronometro: CronometroImport? = null
    ): ImportSummary {
        val t0 = System.nanoTime()
        val rt = Runtime.getRuntime()
        var pico = (rt.totalMemory() - rt.freeMemory()) / 1048576f
        fun medirPico() {
            val atual = (rt.totalMemory() - rt.freeMemory()) / 1048576f
            if (atual > pico) pico = atual
        }

        if (bytesOrigem.isEmpty()) throw ImportException("arquivo vazio ('$nomeArquivo' tem 0 bytes)")
        val avisos = ArrayList<String>(2)
        var resultado: ImportResult
        var formato: TipoFormato

        try {
            onEtapa("triangulando")
            formato = detectarFormato(bytesOrigem)
            resultado = cronometro?.medir(
                "parse ${formato.rotulo}",
                "${bytesOrigem.size} bytes"
            ) {
                importarFormato(formato, bytesOrigem, resolutorExterno, nomeArquivo, opcoes)
            } ?: importarFormato(formato, bytesOrigem, resolutorExterno, nomeArquivo, opcoes)
        } catch (e: GltfImportException) {
            throw ImportException("glTF inválido: ${e.message}")
        } catch (e: FbxImportException) {
            throw ImportException("FBX inválido: ${e.message}")
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            // Falha inesperada vira erro CLARO com a causa (nunca crash nem silêncio).
            throw ImportException(
                "falha ao ler '$nomeArquivo': ${e.message ?: e.javaClass.simpleName} — " +
                    "confira se o arquivo está completo e sem corrupção"
            )
        }
        medirPico()

        // Avisos dos importadores (B.3/B.6: texturas faltando, camadas extras…)
        avisos.addAll(resultado.avisos)
        if (formato == TipoFormato.ZIP) {
            avisos.add("modelo importado de dentro do zip")
        }

        if (cancelado()) throw ImportException("importação cancelada pelo usuário")
        onEtapa("triangulando: dedup + LODs")
        // PÓS-PROCESSAMENTO (B.4/B.5): dedup de vértices + LODs (3 níveis)
        // B1: PosProcessamento mede dedup e LODs SEPARADAMENTE no cronômetro.
        // B2d: cancelamento checado DENTRO dos laços pesados (dedup/decimação).
        val malha = opcoes.perfilTextura?.let { aplicarPerfilTextura(resultado, it) } ?: resultado.mesh
        val malhaFinal = PosProcessamento.aplicar(malha, cronometro, cancelado)
        val texturasFinais = resultado.texturas
        medirPico()

        if (cancelado()) throw ImportException("importação cancelada pelo usuário")
        onEtapa("gravando .KERN")
        val bytesKern = cronometro?.medir(
            "serialização .KERN",
            "${malhaFinal.vertexCount} vértices"
        ) {
            com.kenkern.engine.assets.mesh.KernWriter.write(malhaFinal)
        } ?: com.kenkern.engine.assets.mesh.KernWriter.write(malhaFinal)
        medirPico()

        if (cancelado()) throw ImportException("importação cancelada pelo usuário")
        onEtapa("validando")
        val problemas = cronometro?.medir("validação de integridade", "KernIntegrity") {
            KernIntegrity.verificar(malhaFinal, bytesKern)
        } ?: KernIntegrity.verificar(malhaFinal, bytesKern)
        val erroMax = try {
            KernIntegrity.medirErroMaximo(malhaFinal, bytesKern)
        } catch (e: Exception) {
            KenkernLog.w(TAG, "não mediu erro de quantização de '$nomeArquivo'", e)
            -1f
        }
        medirPico()

        // Aviso de skinning (regra: só avisar — skinning é da Fase 8).
        val esqueleto = malhaFinal.bones.isNotEmpty()
        if (esqueleto) avisos.add("contém esqueleto: ignorado nesta fase")

        // Opção "Ajustar ao tamanho": a escala vai no NICK (editável/desfazível),
        // o .KERN permanece fiel à origem (nunca assa escala nos vértices).
        var escalaSugerida = 1f
        if (opcoes.ajustarAoTamanho) {
            val dx = resultado.mesh.boundsMax.x - resultado.mesh.boundsMin.x
            val dy = resultado.mesh.boundsMax.y - resultado.mesh.boundsMin.y
            val dz = resultado.mesh.boundsMax.z - resultado.mesh.boundsMin.z
            val maior = maxOf(dx, dy, dz)
            if (maior > 1e-6f) {
                val escala = opcoes.alturaMeta / maior
                if (escala > 0.001f && escala < 1000f) {
                    escalaSugerida = escala
                    avisos.add(
                        "escala do Nick ajustada em %.3f× (AABB maior vira %.1f m)".format(
                            java.util.Locale.US, escala, opcoes.alturaMeta
                        )
                    )
                } else {
                    avisos.add("AABB degenerada — \"Ajustar ao tamanho\" ignorado")
                }
            }
        }

        val min = malhaFinal.boundsMin
        val max = malhaFinal.boundsMax
        val tempoMs = (System.nanoTime() - t0) / 1_000_000L
        return ImportSummary(
            nomeArquivo = nomeArquivo,
            formato = formato.rotulo,
            bytesOrigem = bytesOrigem.size.toLong(),
            bytesKern = bytesKern.size,
            bytesKernData = bytesKern,
            vertices = malhaFinal.vertexCount,
            triangulos = malhaFinal.indices.size / 3,
            lods = malhaFinal.lods.size,
            materiais = malhaFinal.materiais.size,
            texturas = texturasFinais.size,
            avisos = avisos,
            tempoMs = tempoMs,
            picoMemoriaMb = pico,
            integridadeOk = problemas.isEmpty(),
            problemasIntegridade = problemas,
            erroMaxQuantizacao = erroMax,
            aabbMin = floatArrayOf(min.x, min.y, min.z),
            aabbMax = floatArrayOf(max.x, max.y, max.z),
            contemEsqueleto = esqueleto,
            verticesComSkin = resultado.report.verticesComSkin,
            escalaSugerida = escalaSugerida,
            texturasExtraidas = texturasFinais
        )
    }

    // Os importadores ainda fornecem caminhos por hash simples. Novas importações
    // com perfil apontam para uma variante; .KERN antigos continuam a usar o caminho antigo.
    private fun aplicarPerfilTextura(resultado: ImportResult, perfil: PerfilTexturaImportacao): KernMesh {
        val mesh = resultado.mesh
        if (mesh.materiais.isEmpty() || resultado.texturas.isEmpty()) return mesh
        val caminhos = resultado.texturas.associate { textura ->
            val hash = textura.sha1
            "${AssetLayout.DIR_TEXTURAS}/$hash.kimg" to perfil.caminho(hash)
        }
        val materiais = mesh.materiais.map { m ->
            KernMaterial(
                m.nome, m.baseColorR, m.baseColorG, m.baseColorB, m.baseColorA, m.metallic, m.roughness,
                m.temTexturaBase, m.temTexturaNormal, m.doubleSided,
                caminhos[m.texturaBasePath] ?: m.texturaBasePath,
                caminhos[m.texturaNormalPath] ?: m.texturaNormalPath
            )
        }
        return KernMesh(
            mesh.positions, mesh.normals, mesh.uvs, mesh.tangents, mesh.indices, mesh.boundsMin, mesh.boundsMax,
            mesh.lods, mesh.boneIndices, mesh.boneWeights, mesh.bones, mesh.clusters, mesh.gruposMaterial, materiais
        )
    }

    /**
     * Despacho por formato detectado (B1: extraído para o cronômetro medir o
     * parse INTEIRO — zip, glTF e FBX — como UMA etapa).
     */
    private fun importarFormato(
        formato: TipoFormato,
        bytesOrigem: ByteArray,
        resolutorExterno: ResolvedorExterno?,
        nomeArquivo: String,
        opcoes: Opcoes
    ): ImportResult {
        return when (formato) {
            TipoFormato.GLB, TipoFormato.GLTF_JSON ->
                GltfImporter.importar(
                    bytesOrigem, resolutorExterno, nomeArquivo, opcoes.manterTangentes
                )
            TipoFormato.ZIP -> {
                val zip = ResolvedorZip.ler(bytesOrigem)
                val nomeModelo = zip.entradas.keys.firstOrNull {
                    it.endsWith(".glb", ignoreCase = true) || it.endsWith(".gltf", ignoreCase = true)
                } ?: throw ImportException(
                    "o .zip não contém nenhum .glb/.gltf — para FBX use o arquivo .fbx direto (não zipe)"
                )
                val bytesModelo = zip.entradas[nomeModelo]
                    ?: throw ImportException("entrada '$nomeModelo' não encontrada no zip")
                val r = GltfImporter.importar(
                    bytesModelo,
                    ResolvedorZip(zip.entradas, nomeModelo.substringBeforeLast('/')),
                    nomeModelo,
                    opcoes.manterTangentes
                )
                r
            }
            TipoFormato.FBX_BIN, TipoFormato.FBX_ASCII -> FbxImporter.importar(bytesOrigem)
            TipoFormato.DESCONHECIDO -> throw ImportException(
                "formato não reconhecido pela assinatura de '$nomeArquivo' — use .glb, .gltf, .fbx ou um .zip com glTF dentro"
            )
        }
    }

    /**
     * Instância da classe "Malha" anexada ao Nick criado pela importação:
     * caminho RELATIVO do .KERN + AABB (para picking/foco sem carregar a malha).
     */
    fun criarClasseMalha(
        caminhoKern: String,
        aabbMin: FloatArray,
        aabbMax: FloatArray,
        escalaFit: Float = 1f
    ): NickClassInstance {
        val inst = NickClassInstance(CLASSE_MALHA)
        inst.fields["path"] = NickValue.StringValue(caminhoKern)
        inst.fields["aabbMinX"] = NickValue.FloatValue(aabbMin[0])
        inst.fields["aabbMinY"] = NickValue.FloatValue(aabbMin[1])
        inst.fields["aabbMinZ"] = NickValue.FloatValue(aabbMin[2])
        inst.fields["aabbMaxX"] = NickValue.FloatValue(aabbMax[0])
        inst.fields["aabbMaxY"] = NickValue.FloatValue(aabbMax[1])
        inst.fields["aabbMaxZ"] = NickValue.FloatValue(aabbMax[2])
        inst.fields["escalaFit"] = NickValue.FloatValue(escalaFit)
        return inst
    }
}
