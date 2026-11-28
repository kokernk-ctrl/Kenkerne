package com.kenkern.engine.projects

import com.kenkern.engine.assets.import.ResolvedorExterno
import java.io.File

/**
 * F4 (v0.1.31) — IMPORTAR DE QUALQUER PASTA (núcleo PURO, JVM-testado).
 *
 * Roteamento por EXTENSÃO (GuardasCaminho.pastaDe): modelos .glb/.gltf/.fbx
 * → assets/models, .png/.jpg → assets/textures, .ogg/.mp3/.wav →
 * assets/audio, .ttf/.otf → assets/fonts, .mp4 → assets/video, .lua →
 * scripts, .ck → scenes, .kui → ui, .kimg → assets/textures, .kern →
 * assets/models. O ORIGINAL é copiado PRESERVADO (DECISIONS v0.1.31 #4) — a
 * conversão para .KERN/.KIMG acontece nas pipelines próprias (importador de
 * modelo / material), chamadas depois pelo explorador.
 *
 * "Importar pasta inteira" (recursivo, com CONTAGEM e cancelamento).
 */
object ImportadorArquivos {

    /** Resultado da importação de UMA árvore. */
    data class ResumoArvore(
        val copiados: Int,
        val pulados: Int,
        val cancelado: Boolean,
        val destinos: List<String>,
        val motivos: List<String>,
        val tempoMs: Long
    ) {
        fun texto(): String =
            "$copiados copiado(s) · $pulados pulado(s)${if (cancelado) " · CANCELADO" else ""} em $tempoMs ms" +
                (if (motivos.isEmpty()) "" else " · ${motivos.take(3).joinToString("; ")}")
    }

    /** ext em minúsculas (sem ponto; vazio se não tem). */
    fun extDe(nome: String): String = nome.substringAfterLast('.', "").lowercase()

    /** destino relativo (pasta/nome) de UM arquivo do projeto. */
    fun destinoDe(raizProjeto: File, nomeOrigem: String): Pair<String, File> {
        val pasta = GuardasCaminho.pastaDe(extDe(nomeOrigem))
        val dir = File(raizProjeto, pasta)
        if (!dir.isDirectory) dir.mkdirs()
        val nomeFinal = OperacoesArquivos.nomeUnico(dir, nomeOrigem)
        return "$pasta/$nomeFinal" to File(dir, nomeFinal)
    }

    /** Resultado da cópia de UM arquivo (erro com motivo — nada silencioso). */
    sealed class CopiaResult {
        data class Ok(val relativo: String, val bytes: Long) : CopiaResult()
        data class Falhou(val motivo: String) : CopiaResult()
    }

    /** copia UM arquivo PRESERVADO para o destino por extensão (nome único). */
    fun copiarPreservado(raizProjeto: File, origem: File): CopiaResult {
        if (!origem.isFile) return CopiaResult.Falhou("não é arquivo: ${origem.name}")
        val (relativo, destino) = destinoDe(raizProjeto, origem.name)
        return try {
            origem.copyTo(destino, overwrite = false)
            if (destino.isFile && destino.length() == origem.length()) {
                CopiaResult.Ok(relativo, origem.length())
            } else {
                CopiaResult.Falhou("cópia incompleta: ${origem.name} → $relativo")
            }
        } catch (e: Exception) {
            CopiaResult.Falhou("copiar '${origem.name}' falhou: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * IMPORTA UMA ÁRVORE inteira (recursivo): percorre [origemDir], rotea
     * cada arquivo por extensão e copia com nome único. Reporta progresso
     * (n processados / total) e respeita cancelamento entre arquivos.
     */
    fun importarArvore(
        raizProjeto: File,
        origemDir: File,
        onProgresso: (processados: Int, total: Int) -> Unit = { _, _ -> },
        cancelado: () -> Boolean = { false }
    ): ResumoArvore {
        val t0 = System.currentTimeMillis()
        val arquivos = origemDir.walkTopDown().filter { it.isFile }.toList()
        val total = arquivos.size
        var copiados = 0
        var pulados = 0
        val destinos = ArrayList<String>(total)
        val motivos = ArrayList<String>(2)
        for ((i, f) in arquivos.withIndex()) {
            if (cancelado()) {
                return ResumoArvore(copiados, pulados, true, destinos, motivos, System.currentTimeMillis() - t0)
            }
            when (val resultado = copiarPreservado(raizProjeto, f)) {
                is CopiaResult.Ok -> {
                    copiados++
                    destinos.add(resultado.relativo)
                }
                is CopiaResult.Falhou -> {
                    pulados++
                    motivos.add(resultado.motivo)
                }
            }
            onProgresso(i + 1, total)
        }
        return ResumoArvore(copiados, pulados, false, destinos, motivos, System.currentTimeMillis() - t0)
    }

    /**
     * F4.3: resolvedor de ARQUIVOS LOCAIS para glTF com .bin/texturas externas
     * (o importador pede caminhos RELATIVOS ao .gltf; o zip já tinha o
     * ResolvedorZip — este resolve no DIRETÓRIO da origem).
     */
    class ResolvedorArquivos(private val dirOrigem: File) : ResolvedorExterno {

        override fun bytes(caminhoRelativo: String): ByteArray? {
            val alvo = File(dirOrigem, caminhoRelativo)
            if (!GuardasCaminho.caminhoDentro(dirOrigem, OperacoesArquivos.relativo(dirOrigem, alvo))) return null
            if (!alvo.isFile) return null
            return try {
                alvo.readBytes()
            } catch (e: Exception) {
                null
            }
        }
    }
}
