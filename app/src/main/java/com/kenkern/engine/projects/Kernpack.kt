package com.kenkern.engine.projects

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * .KERNPACK (F2.6): um projeto Kenkern COMPLETO empacotado num único arquivo
 * zip (a estrutura de pastas do projeto vai inteira: project.kenkern,
 * scenes/, nicks/, assets/, …). Import/Export pelo Storage Access Framework
 * (o usuário escolhe a pasta/arquivo; zero permissões de armazenamento).
 *
 * DECISÃO: os projetos MORAM no armazenamento do app (I/O rápido — o autosave
 * de 1,5 s não pode esperar ContentResolver por arquivo); o .kernpack é o
 * formato PORTÁTIL para backup e troca de aparelho. Alternativa descartada:
 * projeto aberto direto da árvore SAF (gravação lenta por arquivo, risco de
 * perda em provedores instáveis).
 *
 * SEGURANÇA (zip-slip): nenhuma entrada pode escapar da pasta de destino —
 * caminhos com ".." ou absolutos lançam IllegalArgumentException.
 */
object Kernpack {

    const val EXTENSAO = ".kernpack"
    private const val METADATA = "project.kenkern"

    /** Empacota a pasta do projeto; pula build/ (artefato gerado). Retorna o nº de entradas. */
    fun empacotar(root: File, out: OutputStream): Int = run {
        var entradas = 0
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            root.walkTopDown()
                .filter { it != root }
                .forEach { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (rel == "build" || rel.startsWith("build/")) return@forEach
                    val entry = ZipEntry(if (f.isDirectory) "$rel/" else rel)
                    entry.time = f.lastModified()
                    zip.putNextEntry(entry)
                    if (f.isFile) f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    entradas++
                }
        }
        entradas
    }

    /**
     * Desempacota em [destino] (que deve existir). Retorna o nº de ARQUIVOS
     * extraídos. Lança IllegalArgumentException para entradas hostis (zip-slip)
     * — o chamador apaga a pasta e reporta; nunca grava fora do destino.
     */
    fun desempacotar(input: InputStream, destino: File): Int {
        val limiteDestino = destino.canonicalPath + File.separator
        var arquivos = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                val nome = e.name.replace('\\', '/')
                if (nome.isBlank()) continue
                // ZIP-SLIP: caminho relativo, sem ".." em qualquer posição
                val hostil = nome.startsWith("/") || nome == ".." ||
                    nome.startsWith("../") || nome.contains("/../") || nome.endsWith("/..")
                if (hostil) {
                    throw IllegalArgumentException("entrada hostil no pacote: $nome")
                }
                if (nome.endsWith("/")) continue // entrada de diretório: pastas nascem via parentFile
                val alvo = File(destino, nome)
                if (!alvo.canonicalPath.startsWith(limiteDestino)) {
                    throw IllegalArgumentException("entrada fora do destino: $nome")
                }
                alvo.parentFile?.mkdirs()
                FileOutputStream(alvo).use { zip.copyTo(it) }
                arquivos++
                zip.closeEntry()
            }
        }
        return arquivos
    }

    /** Lê o name= do project.kenkern DENTRO do pacote (sem extrair tudo). */
    fun nomeDoProjeto(input: InputStream): String? {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                if (e.name.replace('\\', '/') == METADATA) {
                    val texto = zip.readBytes().toString(Charsets.UTF_8)
                    for (linha in texto.lineSequence()) {
                        val t = linha.trim()
                        if (t.startsWith("name=")) {
                            var v = t.removePrefix("name=").trim()
                            if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                                v = v.substring(1, v.length - 1)
                            }
                            return v.ifBlank { null }
                        }
                    }
                    return null
                }
            }
        }
        return null
    }
}
