package com.kenkern.engine.projects

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipException
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
    // Extra ZIP do KERNPACK: ID "KK", tamanho 8, mtime em ms desde o epoch (little endian).
    // O mtime padrão do ZIP tem resolução de segundos e pode empatar cena e autosave.
    private const val EXTRA_MTIME = 0x4b4b

    /** Empacota a pasta do projeto; pula build/ (artefato gerado). Retorna o nº de entradas. */
    fun empacotar(root: File, out: OutputStream): Int = run {
        var entradas = 0
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            require(root.isDirectory) { "pasta do projeto inválida: $root" }
            val build = File(root, "build")
            root.walkTopDown()
                .onEnter { it != build }
                .onFail { _, erro -> throw erro }
                .filter { it != root && it != build }
                .forEach { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    // A leitura aceita '\' como separador de ZIPs legados, não como parte de um nome.
                    if ('\\' in rel) throw IOException("nome incompatível com .kernpack: $rel")
                    val attrs = Files.readAttributes(f.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                    if (!attrs.isDirectory && !attrs.isRegularFile) {
                        throw IOException("entrada não é arquivo regular nem pasta: $rel")
                    }
                    val mtime = attrs.lastModifiedTime().toMillis()
                    val entry = ZipEntry(if (attrs.isDirectory) "$rel/" else rel)
                    entry.time = mtime
                    entry.extra = ByteBuffer.allocate(4 + Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(EXTRA_MTIME.toShort()).putShort(Long.SIZE_BYTES.toShort()).putLong(mtime).array()
                    zip.putNextEntry(entry)
                    if (attrs.isRegularFile) f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    entradas++
                }
        }
        entradas
    }

    /**
     * Desempacota em [destino] (uma pasta nova, já criada). Retorna o nº de ARQUIVOS
     * extraídos. Lança IllegalArgumentException para entradas hostis (zip-slip)
     * ou IOException para falhas de I/O ou conflitos, sem sobrescrever arquivos.
     * Em caso de falha o chamador apaga a pasta parcial e reporta.
     */
    fun desempacotar(input: InputStream, destino: File): Int {
        var arquivos = 0
        val mtimesPastas = mutableMapOf<File, FileTime>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            require(destino.isDirectory) { "pasta de destino inválida: $destino" }
            val limiteDestino = destino.canonicalFile.toPath()
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                val nome = e.name.replace('\\', '/')
                // ZIP-SLIP: caminho relativo, sem ".." em qualquer posição
                val hostil = nome.isEmpty() || '\u0000' in nome || nome.startsWith("/") || nome == ".." ||
                    nome.startsWith("../") || nome.contains("/../") || nome.endsWith("/..")
                if (hostil) {
                    throw IllegalArgumentException("entrada hostil no pacote: $nome")
                }
                val alvo = File(destino, nome)
                val canonico = alvo.canonicalFile.toPath()
                if (canonico == limiteDestino || !canonico.startsWith(limiteDestino)) {
                    throw IllegalArgumentException("entrada fora do destino: $nome")
                }
                val mtime = mtimeDaEntrada(e)
                val caminho = alvo.toPath()
                if (nome.endsWith("/")) {
                    if (zip.read() != -1) throw ZipException("entrada de diretório contém dados: $nome")
                    Files.createDirectories(caminho)
                    if (mtime != null) mtimesPastas[alvo] = mtime
                } else {
                    Files.createDirectories(caminho.parent)
                    Files.newOutputStream(caminho, CREATE_NEW, WRITE).use { zip.copyTo(it) }
                    if (mtime != null) Files.setLastModifiedTime(caminho, mtime)
                    arquivos++
                }
                zip.closeEntry()
            }
        }
        // Criar arquivos e subpastas muda o mtime das pastas; restaure-o só no fim.
        for ((pasta, mtime) in mtimesPastas) Files.setLastModifiedTime(pasta.toPath(), mtime)
        return arquivos
    }

    private fun mtimeDaEntrada(entrada: ZipEntry): FileTime? {
        val extra = entrada.extra?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
        if (extra != null) {
            var offset = 0
            while (offset + 4 <= extra.limit()) {
                val id = extra.getShort(offset).toInt() and 0xffff
                val tamanho = extra.getShort(offset + 2).toInt() and 0xffff
                offset += 4
                if (offset + tamanho > extra.limit()) break
                if (id == EXTRA_MTIME && tamanho == Long.SIZE_BYTES) {
                    return FileTime.fromMillis(extra.getLong(offset))
                }
                offset += tamanho
            }
        }
        // Pacotes legados ou ZIPs de terceiros: use a resolução disponível, sem inventar uma ordem.
        return entrada.lastModifiedTime
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
