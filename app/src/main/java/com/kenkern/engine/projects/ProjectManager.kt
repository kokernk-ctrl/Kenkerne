package com.kenkern.engine.projects

import android.content.Context
import android.util.Log
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.serialization.CkFormat
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
    const val ENGINE_VERSION = "0.1.1-fase1"
    const val FORMAT_VERSION = 1

    val FOLDERS = listOf(
        "assets", "scenes", "nicks", "scripts", "audio", "animations", "build", "shaders"
    )

    /** Último erro ocorrido (para a UI mostrar ao usuário; thread-safe via volatile). */
    @Volatile
    var lastError: String? = null
        private set

    private fun reportError(where: String, e: Throwable) {
        lastError = "$where: ${e.message ?: e.javaClass.simpleName}"
        Log.e(TAG, lastError, e)
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
    fun createProject(context: Context, projectName: String): ProjectHandle? = runCatching {
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

        // Cena padrão: raiz + cubo com a classe Spin (mostra o loop de funções em Play).
        val scene = Scene(projectName.trim())
        val world = scene.createRootNick("Mundo")
        val cube = scene.createNick("Cubo", world)
        cube.transform.setLocalPosition(0f, 0.5f, 0f)
        NickClassRegistry.createInstance("Spin")?.let { cube.addClassInstance(it) }
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

    fun openProject(context: Context, root: File): ProjectHandle? = runCatching {
        if (!root.isDirectory) return null
        if (!File(root, PROJECT_FILE).isFile) return null
        val meta = readMetadata(File(root, PROJECT_FILE))
        ProjectHandle(root, meta["name"] ?: root.name, meta["created"] ?: "-")
    }.onFailure { reportError("openProject", it) }.getOrNull()

    /** Carrega a cena principal; se existir auto-save mais recente, usa-o (recuperação pós-crash). */
    fun loadScene(handle: ProjectHandle): Scene = runCatching {
        val autosave = handle.autosaveFile
        val main = handle.mainSceneFile
        val useAutosave = autosave.isFile && (!main.isFile || autosave.lastModified() > main.lastModified())
        val source = if (useAutosave) autosave else main
        if (source.isFile) {
            val text = runCatching { source.readText() }.getOrNull()
            if (!text.isNullOrBlank()) {
                val scene = CkFormat.parseToNewScene(text, handle.name)
                if (scene != null) return scene
            }
        }
        Scene(handle.name)
    }.onFailure { reportError("loadScene", it) }.getOrDefault(Scene(handle.name))

    fun hasAutosave(handle: ProjectHandle): Boolean = runCatching {
        handle.autosaveFile.isFile &&
            (!handle.mainSceneFile.isFile || handle.autosaveFile.lastModified() > handle.mainSceneFile.lastModified())
    }.getOrDefault(false)

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
        } catch (_: Exception) {
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
        }
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
