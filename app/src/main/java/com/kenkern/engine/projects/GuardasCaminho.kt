package com.kenkern.engine.projects

import java.io.File

/**
 * F1/F2 (v0.1.30) — GUARDAS DE CAMINHO E ESTRUTURA DE PROJETO (PURO, sem
 * Android; JVM-testado em GuardasCaminhoTest). Regras da tarefa:
 *  - caminho SEMPRE dentro da raiz do projeto (bloqueio de "..");
 *  - nomes de projeto/pasta válidos (letra/dígito/espaço/-/_ · ≤ 60);
 *  - espaço livre checado ANTES de gravar;
 *  - gravação atômica MANTIDA (ProjectManager.writeFileAtomic);
 *  - auto-save IGUAL nos dois locais (o handle aponta a raiz REAL).
 *
 * F2 — árvore visível ao criar (projetos antigos recebem o que falta na
 * abertura — ensureStructure — sem APAGAR nada):
 *   assets/{models,textures,materials,audio,fonts,video}, scenes, nicks,
 *   scripts, animations, shaders, ui, worlds, dialogues, build e .kenkern
 *   (cache e lixeira).
 */
object GuardasCaminho {

    /** Árvore completa F2 (relativa à raiz do projeto). */
    val ARVORE_PROJETO: List<String> = listOf(
        "assets", "assets/models", "assets/textures", "assets/materials",
        "assets/audio", "assets/fonts", "assets/video",
        "scenes", "nicks", "scripts", "animations", "shaders",
        "ui", "worlds", "dialogues", "build", ".kenkern"
    )

    /** Destino de importação por extensão (F4: modelos → assets/models…). */
    fun pastaDe(extensao: String): String = when (extensao.lowercase()) {
        "glb", "gltf", "fbx", "kern" -> "assets/models"
        "png", "jpg", "jpeg", "webp", "kimg" -> "assets/textures"
        "ttf", "otf" -> "assets/fonts"
        "ogg", "mp3", "wav" -> "assets/audio"
        "mp4" -> "assets/video"
        "lua" -> "scripts"
        "kui" -> "ui"
        "ck" -> "scenes"
        else -> "assets"
    }

    /**
     * O caminho relativo é VÁLIDO (dentro da raiz, sem escapar)? Resolve
     * CANONICAMENTE e confere o prefixo — "assets/models/../audio" fica
     * dentro (válido); "assets/../../outro" ESCAPA da raiz (inválido);
     * absoluto também é inválido. [raiz] e [relativo] como o chamador usa
     * (File(raiz, relativo)).
     */
    fun caminhoDentro(raiz: File, relativo: String): Boolean {
        val limpo = relativo.trim().replace('\\', '/')
        if (limpo.isEmpty()) return false
        if (limpo.startsWith("/")) return false
        val canonRaiz = raiz.canonicalPath.trimEnd('/')
        val canonDestino = File(raiz, limpo).canonicalPath
        return canonDestino == canonRaiz || canonDestino.startsWith(canonRaiz + "/")
    }

    /** Nome de projeto/pasta válido: letra/dígito/espaço/-/_ (sanitize já aplica). */
    fun nomeValido(nome: String): Boolean {
        val t = nome.trim()
        if (t.isEmpty() || t.length > 60) return false
        if (t == "." || t == "..") return false
        return t.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
    }

    /** Espaço livre suficiente no [volume] para [bytesNecessarios]? */
    fun espacoLivre(volume: File, bytesNecessarios: Long): Boolean =
        volume.usableSpace >= bytesNecessarios

    /**
     * Cria a ÁRVORE F2 completa sob [raiz] (idempotente; devolve as pastas
     * NOVAS criadas). NUNCA apaga nada — a migração só acrescenta.
     */
    fun garantirArvore(raiz: File, arvore: List<String> = ARVORE_PROJETO): List<String> {
        val criadas = ArrayList<String>()
        for (pasta in arvore) {
            val dir = File(raiz, pasta)
            if (!dir.isDirectory) {
                if (dir.mkdirs() || dir.isDirectory) criadas.add(pasta)
            }
        }
        return criadas
    }

    /** A árvore está completa (tudo presente)? */
    fun arvoreCompleta(raiz: File, arvore: List<String> = ARVORE_PROJETO): Boolean =
        arvore.all { File(raiz, it).isDirectory }

    /**
     * F1 — CÓPIA VERIFICADA: [origem] e [destino] têm exatamente os MESMOS
     * caminhos relativos com os MESMOS tamanhos (byte a byte não — tamanho
     * por arquivo; a tarefa exige "cópia verificada", não hash completo).
     */
    fun copiaVerificada(origem: File, destino: File): Boolean {
        if (!origem.isDirectory || !destino.isDirectory) return false
        val arquivosA = origem.walkTopDown().toList().filter { it.isFile }.map { it.relativeTo(origem).path to it.length() }
        val arquivosB = destino.walkTopDown().toList().filter { it.isFile }.map { it.relativeTo(destino).path to it.length() }
        if (arquivosA.size != arquivosB.size) return false
        return arquivosA.toSet() == arquivosB.toSet()
    }
}
