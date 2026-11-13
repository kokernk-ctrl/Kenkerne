package com.kenkern.engine.projects

import android.content.Context
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
 * Criação gera a estrutura padrão de pastas e o arquivo project.kenkern com metadados.
 */
object ProjectManager {

    const val PROJECT_FILE = "project.kenkern"
    const val ENGINE_VERSION = "0.1.0-fase1"
    const val FORMAT_VERSION = 1

    val FOLDERS = listOf(
        "assets", "scenes", "nicks", "scripts", "audio", "animations", "build", "shaders"
    )

    fun projectsRoot(context: Context): File = File(context.filesDir, "projects")

    fun createProject(context: Context, projectName: String): ProjectHandle? {
        val clean = sanitize(projectName)
        if (clean.isBlank()) return null
        val root = uniqueDirectory(projectsRoot(context), clean)
        if (!root.mkdirs()) return null
        for (folder in FOLDERS) {
            if (!File(root, folder).mkdirs()) return null
        }
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val meta = buildString {
            appendLine("KENKERN_PROJECT")
            appendLine("format=$FORMAT_VERSION")
            appendLine("engine=$ENGINE_VERSION")
            appendLine("name=${CkFormat.quote(projectName)}")
            appendLine("folder=${CkFormat.quote(root.name)}")
            appendLine("created=${CkFormat.quote(created)}")
            appendLine("scene=scenes/cena.ck")
        }
        if (!writeFileAtomic(File(root, PROJECT_FILE), meta)) return null

        // Cena padrão: raiz + cubo com a classe Spin (mostra o loop de funções em Play).
        val scene = Scene(projectName)
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
        return ProjectHandle(root, projectName, created)
    }

    fun listProjects(context: Context): List<ProjectHandle> {
        val root = projectsRoot(context)
        val dirs = root.listFiles { f -> f.isDirectory && File(f, PROJECT_FILE).exists() } ?: return emptyList()
        val handles = ArrayList<ProjectHandle>(dirs.size)
        for (dir in dirs) {
            val meta = readMetadata(File(dir, PROJECT_FILE))
            handles.add(
                ProjectHandle(
                    dir,
                    meta["name"] ?: dir.name,
                    meta["created"] ?: "-"
                )
            )
        }
        handles.sortByDescending { it.root.lastModified() }
        return handles
    }

    fun deleteProject(handle: ProjectHandle): Boolean = handle.root.deleteRecursively()

    fun openProject(context: Context, root: File): ProjectHandle? {
        if (!root.isDirectory) return null
        if (!File(root, PROJECT_FILE).exists()) return null
        val meta = readMetadata(File(root, PROJECT_FILE))
        return ProjectHandle(root, meta["name"] ?: root.name, meta["created"] ?: "-")
    }

    /** Carrega a cena principal; se existir auto-save mais recente, usa-o (recuperação pós-crash). */
    fun loadScene(handle: ProjectHandle): Scene {
        val autosave = handle.autosaveFile
        val main = handle.mainSceneFile
        val useAutosave = autosave.exists() && (!main.exists() || autosave.lastModified() > main.lastModified())
        val source = if (useAutosave) autosave else main
        if (source.exists()) {
            val text = runCatching { source.readText() }.getOrNull()
            if (text != null) {
                val scene = CkFormat.parseToNewScene(text, handle.name)
                if (scene != null) return scene
            }
        }
        return Scene(handle.name)
    }

    fun hasAutosave(handle: ProjectHandle): Boolean =
        handle.autosaveFile.exists() && (!handle.mainSceneFile.exists() || handle.autosaveFile.lastModified() > handle.mainSceneFile.lastModified())

    /** Gravação atômica: escreve em .tmp e renomeia (nunca corrompe o arquivo final). */
    fun writeFileAtomic(target: File, content: String): Boolean {
        return try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(content)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
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
        if (!file.exists()) return emptyMap()
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

    private fun uniqueDirectory(parent: File, base: String): File {
        var candidate = File(parent, base)
        var i = 2
        while (candidate.exists()) {
            candidate = File(parent, "$base ($i)")
            i++
        }
        return candidate
    }
}
