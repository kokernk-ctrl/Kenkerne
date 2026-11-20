package com.kenkern.engine.assets.import

import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * LAYOUT DE ASSETS do projeto (ETAPA A do fechamento da Fase 2):
 *
 *     assets/
 *       models/
 *         <Nome>.KERN            ← malha convertida (o que o Nick usa)
 *         origem/<Nome>.glb|...  ← arquivo original copiado (prova e re-import)
 *       textures/
 *         <sha1>.KIMG            ← texturas convertidas (F2.9, chave = hash)
 *
 * Regras:
 *  - Nome ÚNICO garantido por [nomeUnico] (colisão nunca sobrescreve: vira Nome-2, Nome-3…);
 *  - Cópia do arquivo original É EM STREAMING ([copiarStreaming]): o arquivo inteiro
 *    NUNCA é carregado duas vezes na memória (regra de memória da ETAPA A.7) e o
 *    limite [LIMITE_DURO_BYTES] rejeita arquivos absurdos com erro CLARO;
 *  - zip-slip e caminhos com ".." são bloqueados em [nomeSeguro] (ETAPA B.1).
 */
object AssetLayout {

    const val DIR_MODELOS = "assets/models"
    const val DIR_ORIGEM = "assets/models/origem"
    const val DIR_TEXTURAS = "assets/textures"

    /** Aviso acima deste tamanho (200 MB) — importação prossegue SÓ com confirmação. */
    const val AVISO_GRANDE_BYTES: Long = 200L * 1024L * 1024L

    /** Limite DURO (500 MB) — acima disso o importador recusa com erro claro. */
    const val LIMITE_DURO_BYTES: Long = 500L * 1024L * 1024L

    /** Extensões aceitas no seletor SAF (duplicadas aqui para validação e teste). */
    val EXTENSOES_MODELO = listOf("glb", "gltf", "fbx", "zip")

    /**
     * Nome de arquivo único na pasta: `base.ext` se não existe, senão
     * `base-2.ext`, `base-3.ext`… [base] é limpo por [sanearNome] (sem barra,
     * sem "..", sem caracteres de caminho — nunca sobrescreve nada fora da pasta).
     */
    fun nomeUnico(dir: File, base: String, ext: String): String {
        val b = sanearNome(base).ifBlank { "Modelo" }
        val e = ext.trimStart('.').lowercase()
        if (!dir.exists()) dir.mkdirs()
        var candidato = "$b.$e"
        var n = 2
        while (File(dir, candidato).exists()) {
            candidato = "$b-$n.$e"
            n++
            if (n > 999) throw ImportException("não foi possível achar nome único em ${dir.name} (999 tentativas)")
        }
        return candidato
    }

    /**
     * Limpa um nome vindo do SAF/zip: só letras/números/traço/sublinhado/espaço;
     * barras, "..", e caracteres de controle viram "_". Regra zip-slip (B.1):
     * o resultado é SEMPRE um nome de arquivo simples, nunca caminho.
     */
    fun sanearNome(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when {
                c.isLetterOrDigit() || c in "-_ " -> sb.append(c)
                else -> sb.append('_')
            }
        }
        return sb.toString().trim().take(80)
    }

    /**
     * Caminho RELATIVO seguro dentro do pacote/pasta (B.1): recusa absoluto,
     * "..", backslash e vazio. Devolve o caminho normalizado com '/' ou null.
     */
    fun caminhoRelativoSeguro(raw: String): String? {
        if (raw.isBlank()) return null
        val norm = raw.replace('\\', '/')
        if (norm.startsWith("/")) return null
        val partes = norm.split('/')
        for (p in partes) {
            if (p.isEmpty() || p == "." || p == "..") return null
        }
        return norm
    }

    /**
     * Cópia EM STREAMING (A.7): lê [entrada] em blocos de 256 KB e grava em
     * [destino]; retorna o total de bytes. Acima de [limiteBytes] → erro CLARO
     * (o parcial é apagado). NUNCA carrega o arquivo inteiro na memória.
     */
    fun copiarStreaming(
        entrada: InputStream,
        destino: File,
        limiteBytes: Long = LIMITE_DURO_BYTES,
        cancelado: () -> Boolean = { false }
    ): Long {
        if (!destino.parentFile!!.exists()) destino.parentFile!!.mkdirs()
        var total = 0L
        try {
            val buffer = ByteArray(256 * 1024)
            destino.outputStream().use { saida: OutputStream ->
                while (true) {
                    if (cancelado()) throw ImportException("importação cancelada pelo usuário")
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > limiteBytes) {
                        throw ImportException(
                            "arquivo maior que o limite de ${limiteBytes / (1024 * 1024)} MB — " +
                                "importação cancelada (nada foi gravado além do parcial)"
                        )
                    }
                    saida.write(buffer, 0, n)
                }
            }
        } catch (e: Exception) {
            destino.delete() // não deixa parcial de arquivo recusado
            throw e
        }
        return total
    }

    /** SHA-1 hex de bytes (chave do cache de texturas da F2.9/C.5). */
    fun sha1Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hex = md.digest(bytes)
        val sb = StringBuilder(hex.size * 2)
        for (b in hex) {
            sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0xF])
            sb.append("0123456789abcdef"[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    /** Garante que as pastas de assets do projeto existem (criação idempotente). */
    fun criarPastas(raizProjeto: File) {
        for (p in listOf(DIR_MODELOS, DIR_ORIGEM, DIR_TEXTURAS)) {
            val d = File(raizProjeto, p)
            if (!d.exists() && !d.mkdirs()) {
                KenkernLog.w("AssetLayout", "não criou a pasta $p (já existe?)")
            }
        }
    }
}
