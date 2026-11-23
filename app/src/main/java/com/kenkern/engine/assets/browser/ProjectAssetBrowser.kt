package com.kenkern.engine.assets.browser

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

internal enum class AssetCategory {
    MESH, TEXTURE, SCENE, SCRIPT, AUDIO, OTHER;

    companion object {
        fun of(path: String): AssetCategory = when (path.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "kern", "glb", "gltf", "fbx", "obj" -> MESH
            "kimg", "png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif", "avif" -> TEXTURE
            "ck" -> SCENE
            "lua", "glsl", "frag", "vert" -> SCRIPT
            "wav", "ogg", "mp3", "flac", "m4a", "aac", "opus" -> AUDIO
            else -> OTHER
        }
    }
}

internal data class SceneMeshReference(val path: String, val owner: String)

internal data class ProjectAsset(
    val path: String,
    val sizeBytes: Long?,
    val brokenOwners: List<String> = emptyList()
) {
    val name = path.substringAfterLast('/')
    val category = if (brokenOwners.isEmpty()) AssetCategory.of(path) else AssetCategory.MESH
    val broken get() = brokenOwners.isNotEmpty()
    val listKey get() = "$broken:$path"
}

internal data class ProjectAssetIndex(val files: List<ProjectAsset>, val incomplete: Boolean) {
    fun filter(category: AssetCategory?, search: String, brokenOnly: Boolean): List<ProjectAsset> {
        val query = search.trim()
        return files.filter { entry ->
            (category == null || entry.category == category) && (!brokenOnly || entry.broken) &&
                (entry.path.contains(query, ignoreCase = true) ||
                    entry.brokenOwners.any { it.contains(query, ignoreCase = true) })
        }
    }
}

/** Read-only view of visible project files. No linking, importing or renderer interaction. */
internal object ProjectAssetBrowser {
    fun scan(root: File, references: List<SceneMeshReference>, checkCancelled: () -> Unit = {}): ProjectAssetIndex {
        val base = projectRoot(root)
        val files = ArrayList<ProjectAsset>()
        var incomplete = false
        Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                return if (dir != base && dir.fileName.toString().startsWith('.')) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkCancelled()
                if (attrs.isRegularFile && !file.fileName.toString().startsWith('.')) {
                    val relative = base.relativize(file).joinToString("/")
                    files.add(ProjectAsset(relative, attrs.size()))
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                checkCancelled()
                if (file == base) throw exc
                incomplete = true
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                checkCancelled()
                if (dir == base && exc != null) throw exc
                if (exc != null) incomplete = true
                return FileVisitResult.CONTINUE
            }
        })
        for ((path, origins) in references.groupBy { it.path }) {
            checkCancelled()
            try {
                if (resolveRegularFile(base, path) == null) {
                    files.add(ProjectAsset(path, null, origins.map { it.owner }.distinct()))
                }
            } catch (_: IOException) {
                // Unreadable is not the same as missing. Warn that the scan is incomplete.
                incomplete = true
            }
        }
        files.sortWith(compareByDescending<ProjectAsset> { it.broken }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
            .thenBy { it.path })
        return ProjectAssetIndex(files, incomplete)
    }

    fun resolve(root: File, relative: String): Path? = resolveRegularFile(projectRoot(root), relative)

    private fun projectRoot(root: File): Path {
        val base = root.toPath().toRealPath(NOFOLLOW_LINKS)
        if (!Files.isDirectory(base, NOFOLLOW_LINKS)) throw IOException("Invalid project directory")
        // Normalize aliases outside the project (e.g. Android's app-data path), not links inside it.
        return base.toRealPath()
    }

    private fun resolveRegularFile(base: Path, relative: String): Path? {
        if (relative.startsWith('/') || '\\' in relative || '\u0000' in relative ||
            (relative.length >= 2 && relative[0].isLetter() && relative[1] == ':')
        ) return null
        val parts = relative.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it.startsWith('.') }) return null // includes traversal and internal files
        var path = base
        for ((index, part) in parts.withIndex()) {
            path = path.resolve(part)
            val attrs = try {
                Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: NoSuchFileException) {
                return null
            }
            if (index == parts.lastIndex) return path.takeIf { attrs.isRegularFile }
            if (!attrs.isDirectory) return null
        }
        return null
    }
}
