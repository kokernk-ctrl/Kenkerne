package com.kenkern.engine.projects

import java.io.File

/**
 * F3 (v0.1.31) — OPERAÇÕES DE ARQUIVO DO EXPLORADOR (PURO, sem Android;
 * JVM-testado em OperacoesArquivosTest). TODAS passam pelas guardas de
 * caminho do F1 (dentro da raiz, sem "..", nome válido) e a exclusão vai
 * para a LIXEIRA `.kenkern/lixeira` (com desfazer e "esvaziar lixeira").
 *
 * Nomes duplicados ganham sufixo " 2", " 3", … (mesma regra do Hub — R7).
 * Erros devolvem Resultado com mensagem clara — nada silencioso.
 */
object OperacoesArquivos {

    const val LIXEIRA = ".kenkern/lixeira"

    /** Resultado de uma operação: sucesso traz o caminho resultante (relativo). */
    data class Resultado(val ok: Boolean, val caminho: String? = null, val erro: String? = null) {
        companion object {
            fun ok(caminho: String) = Resultado(true, caminho, null)
            fun erro(msg: String) = Resultado(false, null, msg)
        }
    }

    /** "rel/Path/nome.ext" como caminho RELATIVO à raiz (canônico, com '/'). */
    fun relativo(raiz: File, f: File): String {
        val c = f.canonicalPath
        val r = raiz.canonicalPath.trimEnd('/')
        val rel = if (c == r) "" else c.removePrefix(r + "/")
        return rel.replace('\\', '/')
    }

    /**
     * Nome único na pasta: "nome.ext" → "nome 2.ext", "nome 3.ext"…
     * (pastas sem extensão: "pasta 2"). O MESMO formato do sufixo do Hub.
     */
    fun nomeUnico(dir: File, nomeDesejado: String): String {
        if (!File(dir, nomeDesejado).exists()) return nomeDesejado
        val ext = nomeDesejado.substringAfterLast('.', "")
        val base = if (ext.isEmpty()) nomeDesejado
        else nomeDesejado.removeSuffix(".$ext")
        for (n in 2..999) {
            val candidato = if (ext.isEmpty()) "$base $n" else "$base $n.$ext"
            if (!File(dir, candidato).exists()) return candidato
        }
        return "$base ${System.currentTimeMillis()}"
    }

    /** Guardas comuns: nome válido (arquivo OU pasta) E destino dentro da raiz. */
    private fun guardar(raiz: File, destino: File, nome: String): String? {
        if (!GuardasCaminho.nomeArquivoValido(nome)) return "nome inválido: '$nome'"
        if (!GuardasCaminho.caminhoDentro(raiz, relativo(raiz, destino))) {
            return "caminho fora da raiz do projeto: ${relativo(raiz, destino)}"
        }
        return null
    }

    /** Cria a pasta [nome] dentro de [dirPai]. */
    fun criarPasta(raiz: File, dirPai: File, nome: String): Resultado {
        val nomeFinal = nomeUnico(dirPai, nome)
        val destino = File(dirPai, nomeFinal)
        guardar(raiz, destino, nomeFinal)?.let { return Resultado.erro(it) }
        return if (destino.mkdirs() || destino.isDirectory) Resultado.ok(relativo(raiz, destino))
        else Resultado.erro("mkdirs falhou: ${relativo(raiz, destino)}")
    }

    /** Cria o arquivo [nome] com [conteudo] dentro de [dirPai] (gravação atômica). */
    fun criarArquivo(raiz: File, dirPai: File, nome: String, conteudo: String): Resultado {
        val nomeFinal = nomeUnico(dirPai, nome)
        val destino = File(dirPai, nomeFinal)
        guardar(raiz, destino, nomeFinal)?.let { return Resultado.erro(it) }
        return if (ProjectManager.writeFileAtomic(destino, conteudo)) {
            Resultado.ok(relativo(raiz, destino))
        } else {
            Resultado.erro("gravar falhou: ${relativo(raiz, destino)}")
        }
    }

    /** Renomeia [alvo] para [novoNome] (na MESMA pasta). */
    fun renomear(raiz: File, alvo: File, novoNome: String): Resultado {
        if (!alvo.exists()) return Resultado.erro("não existe: ${relativo(raiz, alvo)}")
        val nomeFinal = nomeUnico(alvo.parentFile ?: raiz, novoNome)
        val destino = File(alvo.parentFile, nomeFinal)
        guardar(raiz, destino, nomeFinal)?.let { return Resultado.erro(it) }
        return if (alvo.renameTo(destino)) Resultado.ok(relativo(raiz, destino))
        else Resultado.erro("rename falhou: ${relativo(raiz, alvo)} → $nomeFinal")
    }

    /** Duplica [alvo] na MESMA pasta (arquivo OU pasta, recursivo). */
    fun duplicar(raiz: File, alvo: File): Resultado {
        if (!alvo.exists()) return Resultado.erro("não existe: ${relativo(raiz, alvo)}")
        val nomeNovo = nomeUnico(alvo.parentFile ?: raiz, alvo.name)
        val destino = File(alvo.parentFile, nomeNovo)
        guardar(raiz, destino, nomeNovo)?.let { return Resultado.erro(it) }
        val ok = if (alvo.isDirectory) {
            destino.mkdirs() && copiarArvores(alvo, destino)
        } else {
            alvo.copyTo(destino, overwrite = false)
            destino.isFile && destino.length() == alvo.length()
        }
        return if (ok) Resultado.ok(relativo(raiz, destino))
        else Resultado.erro("duplicar falhou: ${relativo(raiz, alvo)} → $nomeNovo")
    }

    /** Move [alvo] para [pastaDestino] (nome único mantido se colidir). */
    fun mover(raiz: File, alvo: File, pastaDestino: File): Resultado {
        if (!alvo.exists()) return Resultado.erro("não existe: ${relativo(raiz, alvo)}")
        if (!pastaDestino.isDirectory) return Resultado.erro("pasta destino não existe: ${relativo(raiz, pastaDestino)}")
        if (alvo.parentFile?.canonicalPath == pastaDestino.canonicalPath) {
            return Resultado.erro("origem e destino são a mesma pasta")
        }
        // mover uma pasta para DENTRO de si mesma é impossível
        if (alvo.isDirectory && GuardasCaminho.caminhoDentro(alvo, relativo(alvo, pastaDestino))) {
            return Resultado.erro("não é possível mover uma pasta para dentro dela mesma")
        }
        val nomeFinal = nomeUnico(pastaDestino, alvo.name)
        val destino = File(pastaDestino, nomeFinal)
        guardar(raiz, destino, nomeFinal)?.let { return Resultado.erro(it) }
        return if (alvo.renameTo(destino)) Resultado.ok(relativo(raiz, destino))
        else Resultado.erro("move falhou: ${relativo(raiz, alvo)} → ${relativo(raiz, destino)}")
    }

    /**
     * APAGAR vai para a LIXEIRA (nunca apaga na hora): move [alvo] para
     * `.kenkern/lixeira/<id>/<nome>` e devolve o caminho NA LIXEIRA (o undo
     * restaura de lá). [id] evita colisões (timestamp + contador).
     */
    fun apagarParaLixeira(raiz: File, alvo: File, id: String): Resultado {
        if (!alvo.exists()) return Resultado.erro("não existe: ${relativo(raiz, alvo)}")
        val lixeira = File(raiz, LIXEIRA)
        if (!lixeira.isDirectory && !lixeira.mkdirs()) {
            return Resultado.erro("criar lixeira falhou: $LIXEIRA")
        }
        val destino = File(lixeira, "$id/" + alvo.name)
        destino.parentFile?.mkdirs()
        return if (alvo.renameTo(destino)) Resultado.ok(relativo(raiz, destino))
        else Resultado.erro("mover para a lixeira falhou: ${relativo(raiz, alvo)}")
    }

    /** Restaura [caminhoNaLixeira] para [destinoOriginal] (undo do apagar). */
    fun restaurarDaLixeira(raiz: File, caminhoNaLixeira: String, destinoOriginal: File): Resultado {
        val origem = File(raiz, caminhoNaLixeira)
        if (!origem.exists()) return Resultado.erro("não existe na lixeira: $caminhoNaLixeira")
        if (!GuardasCaminho.caminhoDentro(raiz, relativo(raiz, destinoOriginal))) {
            return Resultado.erro("destino fora da raiz: ${relativo(raiz, destinoOriginal)}")
        }
        destinoOriginal.parentFile?.mkdirs()
        return if (origem.renameTo(destinoOriginal)) Resultado.ok(relativo(raiz, destinoOriginal))
        else Resultado.erro("restaurar falhou: $caminhoNaLixeira")
    }

    /** "Esvaziar lixeira" — apaga DEFINITIVAMENTE o conteúdo de .kenkern/lixeira. */
    fun esvaziarLixeira(raiz: File): Resultado {
        val lixeira = File(raiz, LIXEIRA)
        if (!lixeira.isDirectory) return Resultado.ok(LIXEIRA)
        return try {
            lixeira.listFiles()?.forEach { it.deleteRecursively() }
            Resultado.ok(LIXEIRA)
        } catch (e: Exception) {
            Resultado.erro("esvaziar lixeira falhou: ${e.message}")
        }
    }

    /** Conteúdo da lixeira: id → lista de nomes (para a UI listar). */
    fun lixeira(raiz: File): Map<String, List<String>> {
        val lixeira = File(raiz, LIXEIRA)
        val saida = LinkedHashMap<String, List<String>>()
        val pastas = lixeira.listFiles()?.sortedBy { it.name } ?: return saida
        for (p in pastas) {
            saida[p.name] = p.listFiles()?.map { it.name } ?: emptyList()
        }
        return saida
    }

    /** Copia árvore recursivamente (para duplicar pasta). */
    private fun copiarArvores(origem: File, destino: File): Boolean {
        for (f in origem.listFiles() ?: return true) {
            val alvo = File(destino, f.name)
            if (f.isDirectory) {
                if (!alvo.mkdirs() || !copiarArvores(f, alvo)) return false
            } else {
                f.copyTo(alvo, overwrite = false)
            }
        }
        return destino.walkTopDown().count { it.isFile } == origem.walkTopDown().count { it.isFile }
    }

    /** Ícone/tipo por extensão (F3.1: ícone por tipo; imagens viram miniatura). */
    fun tipoDe(nome: String): String {
        val ext = nome.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "lua" -> "script"
            "ck" -> "cena"
            "kui" -> "ui"
            "kern" -> "malha"
            "kimg" -> "imagem-projeto"
            "png", "jpg", "jpeg", "webp" -> "imagem"
            "glb", "gltf", "fbx" -> "modelo"
            "ogg", "mp3", "wav" -> "audio"
            "ttf", "otf" -> "fonte"
            "mp4" -> "video"
            "kmat" -> "material"
            "kdlg" -> "dialogo"
            "kernpack" -> "pacote"
            else -> "arquivo"
        }
    }
}
