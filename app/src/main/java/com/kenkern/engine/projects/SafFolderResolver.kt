package com.kenkern.engine.projects

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import com.kenkern.engine.assets.import.AssetLayout
import com.kenkern.engine.assets.import.ResolvedorExterno

/**
 * RESOLVEDOR DE PASTA SAF (ETAPA A.1 — opção "Pasta" do botão Importar):
 * resolve URIs relativas de um glTF (modelo.bin, texturas) contra a pasta
 * escolhida com ACTION_OPEN_DOCUMENT_TREE.
 *
 * - índice construído por nome SIMPLES e por caminho RELATIVO (2 níveis de
 *   subpasta — o padrão dos exports de Blender/gltf-pipeline);
 * - leitura por openInputStream (sem cópia para o armazenamento interno);
 * - caminho com ".." / absoluto é RECUSADO (mesma regra do zip — AssetLayout).
 * NUNCA lança: bytes() devolve null (o importador transforma em erro CLARO
 * com o nome do arquivo que faltou).
 */
class SafFolderResolver(context: Context, treeUri: Uri) : ResolvedorExterno {

    private val resolver = context.contentResolver
    private val porNome = HashMap<String, Uri>()
    private val porCaminho = HashMap<String, Uri>()

    init {
        listarRecursivo(treeUri, "", 0)
    }

    private fun listarRecursivo(pasta: Uri, prefixo: String, profundidade: Int) {
        if (profundidade > 2) return // perf: o padrão dos exports é 1–2 níveis
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                pasta, DocumentsContract.getDocumentId(pasta)
            )
        } catch (e: Exception) {
            return // tree Uri inválido: index fica vazio e os pedidos viram erro claro depois
        }
        try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val colId = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val colName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val colMime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val nome = cursor.getString(colName) ?: continue
                    val docId = cursor.getString(colId)
                    val mime = cursor.getString(colMime) ?: ""
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(pasta, docId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        listarRecursivo(docUri, if (prefixo.isEmpty()) "$nome/" else "$prefixo$nome/", profundidade + 1)
                    } else {
                        porNome[nome] = docUri
                        porCaminho[prefixo + nome] = docUri
                    }
                }
            }
        } catch (e: Exception) {
            // Listagem parcialmente falhada: registra o índice que deu (o import
            // avisa o nome que não achou — erro claro, não silêncio).
            android.util.Log.w("SafFolderResolver", "listagem parcial de $prefixo falhou", e)
        }
    }

    override fun bytes(caminhoRelativo: String): ByteArray? {
        val seguro = AssetLayout.caminhoRelativoSeguro(caminhoRelativo) ?: return null
        val uri = porCaminho[seguro] ?: porNome[seguro.substringAfterLast('/')] ?: return null
        return try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            android.util.Log.w("SafFolderResolver", "leitura de '$seguro' falhou", e)
            null
        }
    }

    /** Índice imutável caminho relativo → Uri (para copiar a pasta inteira para origem/). */
    val arquivos: Map<String, Uri> get() = porCaminho

    companion object {
        /** Nome de exibição + tamanho de um Uri SAF (arquivo). Nulls se indisponível. */
        fun nomeETamanho(context: Context, uri: Uri): Pair<String, Long>? {
            var nome: String? = null
            var tamanho = -1L
            try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val iN = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val iS = c.getColumnIndex(OpenableColumns.SIZE)
                        if (iN >= 0) nome = c.getString(iN)
                        if (iS >= 0 && !c.isNull(iS)) tamanho = c.getLong(iS)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SafFolderResolver", "nomeETamanho falhou para $uri", e)
            }
            if (nome == null) {
                // Fallback: última parte do path do Uri (SAF document sempre tem).
                nome = uri.lastPathSegment?.substringAfterLast('/')
            }
            return nome?.let { it to tamanho }
        }

        /** Lê os bytes de um Uri SAF com LIMITE DURO (500 MB) — erro claro acima. */
        fun lerComLimite(context: Context, uri: Uri, limiteBytes: Long = AssetLayout.LIMITE_DURO_BYTES): ByteArray =
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                val buf = java.io.ByteArrayOutputStream(minOf(limiteBytes, 8L * 1024 * 1024).toInt())
                val chunk = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    val n = entrada.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > limiteBytes) {
                        throw com.kenkern.engine.assets.import.ImportException(
                            "arquivo maior que o limite de ${limiteBytes / (1024 * 1024)} MB — recusado"
                        )
                    }
                    buf.write(chunk, 0, n)
                }
                buf.toByteArray()
            } ?: throw com.kenkern.engine.assets.import.ImportException("não foi possível abrir o arquivo escolhido")
    }
}
