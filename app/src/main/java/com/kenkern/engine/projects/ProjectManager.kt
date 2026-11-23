package com.kenkern.engine.projects

import android.content.Context
import android.util.Log
import com.kenkern.engine.BuildConfig
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Handle de um projeto Kenkern aberto/registrado. */
class ProjectHandle(
    val root: File,
    val name: String,
    val createdAt: String
) {
    val scenesDir: File get() = File(root, "scenes")
    val mainSceneFile: File get() = File(scenesDir, "cena.ck")
    val autosaveFile: File get() = File(scenesDir, "cena.autosave.ck")
}

/**
 * Gerenciador de projetos da KENKERN.
 *
 * ANTI-CRASH: toda operação de sistema de arquivos está protegida por runCatching.
 * Nenhuma exceção de I/O escapa deste objeto — falhas retornam null/false/empty e
 * são reportadas via [lastError] para a camada de UI exibir (nunca crashar).
 */
object ProjectManager {

    private const val TAG = "Kenkern/ProjectManager"

    const val PROJECT_FILE = "project.kenkern"
    /** FONTE ÚNICA: a versão da engine é a versão do app (BuildConfig). */
    const val ENGINE_VERSION: String = BuildConfig.VERSION_NAME
    const val FORMAT_VERSION = 1

    val FOLDERS = listOf(
        "assets", "scenes", "nicks", "scripts", "audio", "animations", "build", "shaders",
        "ui", "worlds"
    )

    /** Último erro ocorrido (para a UI mostrar ao usuário; thread-safe via volatile). */
    @Volatile
    var lastError: String? = null
        private set

    private fun reportError(where: String, e: Throwable) {
        lastError = "$where: ${e.message ?: e.javaClass.simpleName}"
        Log.e(TAG, lastError, e)
        // Console da engine (regra: runCatching não engole em silêncio —
        // lastError sozinho nem sempre chega à UI).
        KenkernLog.e(TAG, where, e)
    }

    private fun clearError() {
        lastError = null
    }

    /** Raiz de projetos dentro do armazenamento interno do app (sempre gravável). */
    fun projectsRoot(context: Context): File {
        val base = context.filesDir ?: File("/data/local/tmp/kenkern")
        val root = File(base, "projects")
        ensureDir(root)
        return root
    }

    /**
     * Cria um projeto novo com a estrutura padrão de pastas e o project.kenkern.
     * Retorna null em caso de falha (mensagem em [lastError]) — NUNCA lança exceção.
     */
    fun createProject(context: Context, projectName: String, template: String? = null): ProjectHandle? = runCatching {
        clearError()
        val clean = sanitize(projectName)
        if (clean.isBlank()) {
            lastError = "Nome de projeto inválido"
            return null
        }
        val root = uniqueDirectory(projectsRoot(context), clean)
        if (!ensureDir(root)) {
            lastError = "Não foi possível criar a pasta do projeto"
            return null
        }
        for (folder in FOLDERS) {
            if (!ensureDir(File(root, folder))) {
                lastError = "Falha ao criar pasta '$folder'"
                return null
            }
        }
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val meta = buildString {
            appendLine("KENKERN_PROJECT")
            appendLine("format=$FORMAT_VERSION")
            appendLine("engine=$ENGINE_VERSION")
            appendLine("name=${CkFormat.quote(projectName.trim())}")
            appendLine("folder=${CkFormat.quote(root.name)}")
            appendLine("created=${CkFormat.quote(created)}")
            appendLine("scene=scenes/cena.ck")
        }
        if (!writeFileAtomic(File(root, PROJECT_FILE), meta)) {
            lastError = "Falha ao gravar $PROJECT_FILE"
            return null
        }

        // Cena padrão (T0.2, adendo v4): TIPOS com name NULO — a Hierarquia
        // exibe o RÓTULO do tipo no idioma do app ("Group Nick"/"Nick de Grupo"
        // + "Mesh Nick"/"Nick de Malha"); o usuário renomeia se quiser. O cubo
        // mantém a classe Spin (mostra o loop de funções em Play).
        val scene = Scene(projectName.trim())
        val world = scene.createRootNick(
            null, kind = NickKindRegistry.NICK_GROUP
        )
        val cube = scene.createNick(
            null, world, kind = NickKindRegistry.NICK_MESH
        )
        cube.transform.setLocalPosition(0f, 0.5f, 0f)
        NickClassRegistry.createInstance("Spin")?.let { cube.addClassInstance(it) }

        // M1.7 (v0.1.23): TEMPLATE "COLETAR MOEDAS" — chão, jogador, ponto de
        // surgimento, câmera seguidora, 3 moedas (áreas-gatilho com script que
        // soma no contador e se destrói) e o contador no Lua (ui.contagem).
        val templateEfetivo = template ?: "vazio"
        if (templateEfetivo == "coletar-moedas") {
            montarTemplateColetarMoedas(scene)
        }
        writeFileAtomic(
            File(root, "scenes/cena.ck"),
            "# Cena principal criada pelo projeto $projectName\n" + CkFormat.writeToString(scene)
        )
        writeFileAtomic(File(root, "nicks/README.txt"), "Nicks salvos individualmente (.ck) ficam nesta pasta.\n")
        writeFileAtomic(File(root, "assets/README.txt"), "Assets do projeto (malhas .kern, texturas, fontes).\n")
        ProjectHandle(root, projectName.trim(), created)
    }.onFailure { reportError("createProject", it) }.getOrNull()

    /** Lista os projetos existentes; nunca lança, retorna lista (possivelmente vazia). */
    fun listProjects(context: Context): List<ProjectHandle> = runCatching {
        val root = projectsRoot(context)
        val dirs = root.listFiles { f -> f.isDirectory && File(f, PROJECT_FILE).isFile }
            ?: return@runCatching emptyList<ProjectHandle>()
        val handles = ArrayList<ProjectHandle>(dirs.size)
        for (dir in dirs) {
            val meta = readMetadata(File(dir, PROJECT_FILE))
            handles.add(ProjectHandle(dir, meta["name"] ?: dir.name, meta["created"] ?: "-"))
        }
        handles.sortByDescending { it.root.lastModified() }
        handles
    }.onFailure { reportError("listProjects", it) }.getOrDefault(emptyList())

    fun deleteProject(handle: ProjectHandle): Boolean = runCatching {
        handle.root.deleteRecursively()
    }.onFailure { reportError("deleteProject", it) }.getOrDefault(false)

    /**
     * DUPLICAR projeto ("Salvar como", F2.5): copia a pasta inteira para
     * "Nome (cópia)" (única) e regrava o project.kenkern com o novo nome.
     * Nunca lança: null em falha (motivo em [lastError]). Não mexe no original.
     */
    fun duplicateProject(handle: ProjectHandle): ProjectHandle? = runCatching {
        clearError()
        if (!handle.root.isDirectory || !File(handle.root, PROJECT_FILE).isFile) {
            lastError = "Projeto original inválido"
            return null
        }
        val base = sanitize(handle.name).ifBlank { "Projeto" }
        var candidate = File(handle.root.parentFile, "$base (cópia)")
        var i = 2
        while (candidate.exists()) {
            candidate = File(handle.root.parentFile, "$base (cópia $i)")
            i++
            if (i > 999) {
                lastError = "Já existem muitas cópias de \"$base\""
                return null
            }
        }
        if (!handle.root.copyRecursively(candidate, overwrite = false)) {
            lastError = "Falha ao copiar os arquivos do projeto"
            runCatching { candidate.deleteRecursively() } // não deixa cópia pela metade
                .onFailure { KenkernLog.w(TAG, "limpeza da cópia parcial falhou", it) }
            return null
        }
        // Novo nome no project.kenkern da CÓPIA (o original fica intocado).
        val metaFile = File(candidate, PROJECT_FILE)
        val novoNome = candidate.name
        val atualizado = metaFile.readLines().map { line ->
            if (line.trim().startsWith("name=")) "name=${CkFormat.quote(novoNome)}" else line
        }
        if (!writeFileAtomic(metaFile, atualizado.joinToString("\n") + "\n")) {
            lastError = "Falha ao gravar $PROJECT_FILE da cópia"
            runCatching { candidate.deleteRecursively() }
                .onFailure { KenkernLog.w(TAG, "limpeza da cópia sem meta falhou", it) }
            return null
        }
        ProjectHandle(candidate, novoNome, handle.createdAt)
    }.onFailure { reportError("duplicateProject", it) }.getOrNull()

    /**
     * Renomeia um projeto (grava o novo nome no project.kenkern e retorna o
     * handle atualizado). NUNCA lança: null em falha (motivo em [lastError]).
     */
    fun renameProject(handle: ProjectHandle, newName: String): ProjectHandle? = runCatching {
        val clean = newName.trim()
        if (clean.isBlank()) {
            lastError = "Nome de projeto inválido"
            return null
        }
        val metaFile = File(handle.root, PROJECT_FILE)
        if (!metaFile.isFile) {
            lastError = "project.kenkern não encontrado"
            return null
        }
        val updated = metaFile.readLines().map { line ->
            if (line.trim().startsWith("name=")) "name=${CkFormat.quote(clean)}" else line
        }
        if (!writeFileAtomic(metaFile, updated.joinToString("\n") + "\n")) {
            lastError = "Falha ao gravar $PROJECT_FILE"
            return null
        }
        ProjectHandle(handle.root, clean, handle.createdAt)
    }.onFailure { reportError("renameProject", it) }.getOrNull()

    fun openProject(context: Context, root: File): ProjectHandle? = runCatching {
        if (!root.isDirectory) return null
        if (!File(root, PROJECT_FILE).isFile) return null
        val meta = readMetadata(File(root, PROJECT_FILE))
        val handle = ProjectHandle(root, meta["name"] ?: root.name, meta["created"] ?: "-")
        // MIGRAÇÃO de estrutura (F2.4): projeto antigo ganha as pastas novas
        // (ui/, worlds/) na abertura — idempotente e nunca impede o projeto de abrir.
        ensureStructure(handle)
        handle
    }.onFailure { reportError("openProject", it) }.getOrNull()

    /** Cria as pastas padrão que faltarem (projetos antigos; tolera repetição). */
    fun ensureStructure(handle: ProjectHandle) {
        for (folder in FOLDERS) {
            val dir = File(handle.root, folder)
            if (!dir.isDirectory) {
                runCatching { dir.mkdirs() }
                    .onFailure { KenkernLog.w(TAG, "pasta '$folder' não criada — projeto segue sem ela", it) }
            }
        }
    }

    /** Carrega a cena principal; se existir auto-save mais recente, usa-o (recuperação pós-crash). */
    fun loadScene(handle: ProjectHandle): Scene = runCatching {
        val autosave = handle.autosaveFile
        val main = handle.mainSceneFile
        val useAutosave = autosave.isFile && (!main.isFile || autosave.lastModified() > main.lastModified())
        val source = if (useAutosave) autosave else main
        if (source.isFile) {
            val text = runCatching { source.readText() }
                .onFailure {
                    // Cena ILEGÍVEL não pode virar cena vazia em silêncio (regra nova).
                    KenkernLog.e(TAG, "arquivo de cena ilegível (${source.name}) — abrindo cena vazia", it)
                }
                .getOrNull()
            if (!text.isNullOrBlank()) {
                val scene = CkFormat.parseToNewScene(text, handle.name)
                if (scene != null) return scene
                KenkernLog.w(TAG, "cena .ck não parseou (${source.name}) — abrindo cena vazia")
            } else if (text != null) {
                KenkernLog.w(TAG, "arquivo de cena em branco (${source.name}) — abrindo cena vazia")
            }
        } else {
            KenkernLog.w(TAG, "arquivo de cena ausente (${source.name}) — cena nova")
        }
        Scene(handle.name)
    }.onFailure { reportError("loadScene", it) }.getOrDefault(Scene(handle.name))

    fun hasAutosave(handle: ProjectHandle): Boolean = runCatching {
        handle.autosaveFile.isFile &&
            (!handle.mainSceneFile.isFile || handle.autosaveFile.lastModified() > handle.mainSceneFile.lastModified())
    }.onFailure { KenkernLog.w(TAG, "hasAutosave falhou — recuperação pode não ocorrer", it) }
        .getOrDefault(false)

    /** Gravação atômica: escreve em .tmp e renomeia (nunca corrompe o arquivo final). */
    fun writeFileAtomic(target: File, content: String): Boolean {
        return try {
            val parent = target.parentFile
            if (parent != null && !ensureDir(parent)) return false
            val tmp = File(parent, target.name + ".tmp")
            tmp.writeText(content)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                // Fallback: gravação direta (melhor falhar suave do que perder o dado).
                target.writeText(content)
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            // Perda de gravação NUNCA é silenciosa (regra nova: runCatching/catch
            // em caminho de carregamento registra Log + Console + Diagnóstico).
            KenkernLog.e(TAG, "writeFileAtomic(${target.name}) falhou — dado não gravado", e)
            false
        }
    }

    fun saveSceneAtomic(handle: ProjectHandle, scene: Scene): Boolean =
        writeFileAtomic(handle.mainSceneFile, CkFormat.writeToString(scene))

    fun saveAutosaveAtomic(handle: ProjectHandle, scene: Scene): Boolean =
        writeFileAtomic(handle.autosaveFile, CkFormat.writeToString(scene))

    private fun readMetadata(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val map = HashMap<String, String>()
        runCatching {
            for (line in file.readLines()) {
                val trimmed = line.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val key = trimmed.substring(0, eq).trim()
                var value = trimmed.substring(eq + 1).trim()
                if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                    value = CkFormat.unquote(value)
                }
                map[key] = value
            }
        }.onFailure { KenkernLog.w(TAG, "project.kenkern ilegível — nome vira o da pasta", it) }
        return map
    }

    fun sanitize(name: String): String =
        name.trim().map { c -> if (c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_') c else '_' }
            .joinToString("").take(60)

    /** Garante que o diretório existe (tolera chamadas repetidas). */
    private fun ensureDir(dir: File): Boolean =
        if (dir.isDirectory) true else runCatching { dir.mkdirs() && dir.isDirectory }
            .onFailure { reportError("ensureDir(${dir.path})", it) }
            .getOrDefault(false)

    /** Pasta única para IMPORTS (.kernpack) e futuras cópias: "Base", "Base 2"… */
    fun novaPastaUnica(parent: File, base: String): File = uniqueDirectory(parent, base)

    /**
     * M1.7 (v0.1.23): TEMPLATE "COLETAR MOEDAS" — o projeto já nasce JOGÁVEL:
     * chão estático, Corpo de Jogador, Ponto de Surgimento, Câmera Seguidora,
     * 3 moedas (area.volume com Script Lua que soma no contador e se destrói
     * ao ser tocada) e a classe Script no jogador. Contagem via ui.contagem
     * (acumulativa por sessão de Play) — aparece no overlay "moedas: N".
     */
    fun montarTemplateColetarMoedas(scene: com.kenkern.engine.core.Scene) {
        val mundo = scene.createRootNick(null, kind = NickKindRegistry.NICK_GROUP)
        // chão
        val chao = scene.createNick("Chão", mundo, kind = "body.static")
        chao.transform.setLocalPosition(0f, -0.5f, 0f)
        chao.transform.scale.set(14f, 1f, 14f)
        // jogador
        val player = scene.createNick("Herói", mundo, kind = "body.player")
        player.transform.setLocalPosition(-3f, 0.5f, 0f)
        // surgimento
        val spawn = scene.createNick(null, mundo, kind = "area.spawn")
        spawn.transform.setLocalPosition(0f, 1.5f, 0f)
        // câmera seguidora
        val follow = scene.createNick(null, mundo, kind = "camera.follow")
        follow.transform.setLocalPosition(0f, 5f, 6f)
        // 3 moedas: área-gatilho com script de coleta
        val posicoes = listOf(
            Triple(0f, 0.5f, 0f), Triple(3f, 0.5f, 2f), Triple(-3f, 0.5f, 3f)
        )
        for ((i, p) in posicoes.withIndex()) {
            val moeda = scene.createNick("Moeda ${i + 1}", mundo, kind = "area.volume")
            moeda.transform.setLocalPosition(p.first, p.second, p.third)
            moeda.transform.scale.set(0.6f, 0.6f, 0.6f)
            val inst = NickClassRegistry.createInstance("Script")
            if (inst != null) {
                inst.fields["fonte"] = NickValue.StringValue(
                    "// coleta: soma no contador e desaparece\n" +
                        "on_trigger = function(outro)\n" +
                        "  ui.contagem(\"moedas\", 1)\n" +
                        "  nick.destroy()\n" +
                        "end"
                )
                moeda.addClassInstance(inst)
            }
        }
    }

    private fun uniqueDirectory(parent: File, base: String): File {
        var candidate = File(parent, base)
        var i = 2
        while (candidate.exists()) {
            candidate = File(parent, "$base ($i)")
            i++
            if (i > 999) break // proteção contra loop infinito em FS degenerado
        }
        return candidate
    }
}
