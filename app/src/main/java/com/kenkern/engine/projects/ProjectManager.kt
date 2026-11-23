package com.kenkern.engine.projects

import android.content.Context
import android.util.Log
import com.kenkern.engine.BuildConfig
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    // Not a possible path for a user-created, duplicated, or imported project.
    private const val SANDBOX_FOLDER = ".workspace-sandbox"
    private const val SANDBOX_NAME = "Sandbox"

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
    fun createProject(context: Context, projectName: String): ProjectHandle? =
        createProject(projectName) { novaPastaUnica(projectsRoot(context), sanitize(projectName)) }

    /** The three shortcuts share the project even after its display name is changed. */
    @Synchronized
    fun openOrCreateSandbox(context: Context): ProjectHandle? = runCatching {
        clearError()
        val root = File(projectsRoot(context), SANDBOX_FOLDER)
        if (root.exists()) {
            // Never recover by replacing a possibly valuable scene with a default.
            return@runCatching openProject(context, root)
        }
        createProject(SANDBOX_NAME) {
            if (!root.mkdir()) throw IOException("Não foi possível reservar o Sandbox")
            root
        }
    }.onFailure { reportError("openOrCreateSandbox", it) }.getOrNull()

    private fun createProject(projectName: String, reserveRoot: () -> File): ProjectHandle? = runCatching {
        clearError()
        val clean = sanitize(projectName)
        if (clean.isBlank()) {
            lastError = "Nome de projeto inválido"
            return null
        }
        val root = reserveRoot()
        var published = false
        try {
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
        // Publica o marcador que torna o projeto visível ao Hub somente após a cena.
        if (!writeFileAtomic(File(root, "scenes/cena.ck"), CkFormat.writeToString(scene)) ||
            !writeFileAtomic(File(root, PROJECT_FILE), meta)) {
            lastError = "Falha ao inicializar a cena ou os metadados do projeto"
            return null
        }
        published = true
        writeFileAtomic(File(root, "nicks/README.txt"), "Nicks salvos individualmente (.ck) ficam nesta pasta.\n")
        writeFileAtomic(File(root, "assets/README.txt"), "Assets do projeto (malhas .kern, texturas, fontes).\n")
        ProjectHandle(root, projectName.trim(), created)
        } finally {
            // Undo only a directory reserved by this creation; never an existing project.
            if (!published && !root.deleteRecursively()) {
                KenkernLog.w(TAG, "Não foi possível limpar a criação incompleta: ${root.name}")
            }
        }
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

    data class CenaCarregada(val scene: Scene, val fromAutosave: Boolean)

    /** Nunca ativa uma cena substituta vazia: a saída/pausa a gravaria sobre os originais. */
    fun loadSceneForEditing(handle: ProjectHandle): CenaCarregada? {
        val main = handle.mainSceneFile
        val auto = handle.autosaveFile
        val order = if (hasAutosave(handle)) listOf(auto, main) else listOf(main, auto)
        for (file in order) {
            if (!file.isFile) continue
            try {
                val text = file.readText()
                val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }
                val first = lines.firstOrNull()
                if ((first == CkFormat.MAGIC || first == CkFormat.MAGIC_LEGADO) &&
                    lines.any { it == "SCENE" || it.startsWith("SCENE ") } && lines.lastOrNull() == "ENDCK") {
                    CkFormat.parseToNewScene(text, handle.name)?.let { return CenaCarregada(it, file == auto) }
                }
                KenkernLog.w(TAG, "Cena incompleta ou inválida (${file.name}); verificando outra cópia")
            } catch (e: IOException) {
                KenkernLog.w(TAG, "Cena ilegível (${file.name}); verificando outra cópia", e)
            }
        }
        lastError = "Nenhuma cópia completa de cena.ck ou cena.autosave.ck; originais preservados"
        return null
    }

    /** Compatibilidade para consumidores que apenas consultam a cena; o editor usa o resultado anulável. */
    fun loadScene(handle: ProjectHandle): Scene = loadSceneForEditing(handle)?.scene ?: Scene(handle.name)

    fun hasAutosave(handle: ProjectHandle): Boolean = handle.autosaveFile.isFile &&
        (!handle.mainSceneFile.isFile || runCatching {
            Files.getLastModifiedTime(handle.autosaveFile.toPath()) > Files.getLastModifiedTime(handle.mainSceneFile.toPath())
        }.getOrElse { handle.autosaveFile.lastModified() > handle.mainSceneFile.lastModified() })

    /** O destino nunca é apagado/truncado antes da publicação do temporário no mesmo volume. */
    fun writeFileAtomic(target: File, content: String): Boolean {
        var tmp: File? = null
        return try {
            val parent = target.absoluteFile.parentFile ?: return false
            if (!ensureDir(parent)) return false
            val staged = File.createTempFile(".ken-", ".tmp", parent)
            tmp = staged
            FileOutputStream(staged).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            reportError("writeFileAtomic(${target.name})", e)
            false
        } finally {
            tmp?.let { if (it.exists() && !it.delete()) KenkernLog.w(TAG, "temporário não removido: ${it.name}") }
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
    fun novaPastaUnica(parent: File, base: String): File {
        val clean = sanitize(base).ifBlank { "Projeto" }
        for (i in 1..999) {
            val candidate = File(parent, if (i == 1) clean else "$clean ($i)")
            if (candidate.mkdir()) return candidate
            if (!candidate.exists()) throw IOException("Falha ao reservar diretório do projeto")
        }
        throw IOException("Não há nome livre para o projeto")
    }
}
