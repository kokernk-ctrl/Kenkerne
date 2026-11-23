package com.kenkern.engine.render

import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.KimgFormato
import com.kenkern.engine.assets.image.KimgImagem
import com.kenkern.engine.assets.image.KimgMip
import com.kenkern.engine.assets.image.KimgReader
import com.kenkern.engine.assets.mesh.KernGrupoMaterial
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.diag.KenkernLog
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One per renderer/context; owns only its own GPU handles. No I/O on GL. */
internal class ProjectAssetStream(
    submit: (Runnable) -> Boolean = ::submitToWorker,
    remove: (Runnable) -> Unit = { worker.remove(it) }
) {
    private enum class Format { MESH, TEXTURE }
    private data class Key(val scope: ProjectAssetScope, val path: String, val format: Format, val revision: Long)
    internal class Mesh(val handle: Int, val groups: List<KernGrupoMaterial>, val materials: List<KernMaterial>)
    private sealed interface Decoded {
        class Model(val buffers: PreparedMesh, val groups: List<KernGrupoMaterial>, val materials: List<KernMaterial>) : Decoded
        class Texture(val image: KimgImagem) : Decoded
    }
    private sealed interface Resident {
        class Model(val mesh: Mesh) : Resident
        class Texture(val handle: Int) : Resident
    }

    private var scope: ProjectAssetScope? = null
    private var revisions = emptyMap<String, Long>()
    private val retired = ArrayDeque<Resident>()
    private val queue = AssetLoadQueue<Key, Decoded, Resident>(submit, remove, ::decode)

    fun beginFrame(project: ProjectAssetScope?, backend: RenderBackend) {
        val current = project?.revisions.orEmpty()
        if (scope !== project || revisions !== current) {
            queue.invalidate({ it.scope !== project || (current[it.path] ?: 0L) != it.revision }) { _, asset ->
                retired.add(asset)
            }
            scope = project
            revisions = current
        }
        repeat(16) {
            when (val old = retired.pollFirst() ?: return@repeat) {
                is Resident.Model -> backend.destroyMesh(old.mesh.handle)
                is Resident.Texture -> backend.destroyTexture(old.handle)
            }
        }
        // One GPU upload attempt per frame, shared by KERN and KIMG. A single
        // glBufferData/glTexImage can still overrun the frame on slow drivers.
        queue.tick({ key, data -> upload(key, data, backend) }) { key, error ->
            KenkernLog.e(TAG, "${key.scope.root.name}/${key.path}: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    /** After the GL thread has stopped, or before allocating a new GL context. */
    fun abandonContext() {
        queue.invalidate({ true }) { _, _ -> }
        retired.clear()
        scope = null
        revisions = emptyMap()
    }

    fun mesh(path: String): Mesh? = (request(path, Format.MESH) as? Resident.Model)?.mesh
    fun texture(path: String): Int = (request(path, Format.TEXTURE) as? Resident.Texture)?.handle ?: 0

    private fun request(path: String, format: Format): Resident? {
        val project = scope ?: return null
        return queue.request(Key(project, path, format, revisions[path] ?: 0L))
    }

    private fun upload(key: Key, data: Decoded, backend: RenderBackend): AssetUpload<Decoded, Resident> = when (data) {
        is Decoded.Model -> {
            val handle = backend.createPreparedMesh(data.buffers)
            check(handle > 0) { "GPU recusou a malha .KERN" }
            key.scope.publishMaterials(key.path, key.revision, data.materials)
            AssetUpload.Ready(Resident.Model(Mesh(handle, data.groups, data.materials)))
        }
        is Decoded.Texture -> {
            val max = backend.maxTextureSize
            check(max <= 0 || maxOf(data.image.largura, data.image.altura) <= max) {
                "dimensões .KIMG excedem o limite da GPU ($max px)"
            }
            when (val handle = backend.uploadPreparedTexture(data.image)) {
                TEXTURE_RETRY_AS_RGBA -> {
                    KenkernLog.w(TAG, "driver recusou ETC2 para ${key.path}; preparando fallback RGBA8")
                    AssetUpload.DecodeFallback { Decoded.Texture(textureFallback(data.image, MAX_TEXTURE_BYTES)) }
                }
                0 -> error("GPU recusou a textura .KIMG")
                else -> AssetUpload.Ready(Resident.Texture(handle))
            }
        }
    }

    companion object {
        private const val TAG = "ProjectAssetStream"
        // One shared worker with bounded admission, and at most one outstanding
        // decoded payload per renderer. File and inflated texture sizes are bounded.
        private const val MAX_MESH_BYTES = 32 * 1024 * 1024
        private const val MAX_TEXTURE_BYTES = 64 * 1024 * 1024
        private val worker = ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, ArrayBlockingQueue(4)) { task ->
            Thread(task, "Kenkern-asset-decode").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }.apply { allowCoreThreadTimeOut(true) }

        private fun submitToWorker(task: Runnable): Boolean = try {
            worker.execute(task)
            true
        } catch (_: RejectedExecutionException) {
            false // Still needed? Try admission again next frame, without blocking GL.
        }

        private fun decode(key: Key): Decoded = when (key.format) {
            Format.MESH -> {
                val mesh = KernReader.read(readProjectFile(key, MAX_MESH_BYTES))
                val meshData = MeshData(mesh.positions, mesh.normals, mesh.uvs, mesh.indices, mesh.boundsMin, mesh.boundsMax)
                Decoded.Model(PreparedMesh.pack(meshData), mesh.gruposMaterial, mesh.materiais)
            }
            Format.TEXTURE -> Decoded.Texture(KimgReader.read(readProjectFile(key, MAX_TEXTURE_BYTES), MAX_TEXTURE_BYTES))
        }

        private fun readProjectFile(key: Key, limit: Int): ByteArray {
            val root = key.scope.root.canonicalFile
            require(!File(key.path).isAbsolute) { "caminho de asset não é relativo" }
            val file = File(root, key.path).canonicalFile
            require(file.toPath().startsWith(root.toPath()) && file != root) { "asset fora da raiz do projeto" }
            check(file.isFile) { "arquivo de asset ausente ou inválido" }
            return FileInputStream(file).use { input ->
                val size = input.channel.size()
                require(size in 1..limit.toLong()) { "tamanho do asset fora do limite de carga ($limit bytes)" }
                val bytes = ByteArray(size.toInt())
                val stream = DataInputStream(input)
                var offset = 0
                while (offset < bytes.size) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val step = minOf(64 * 1024, bytes.size - offset)
                    stream.readFully(bytes, offset, step)
                    offset += step
                }
                check(stream.read() == -1) { "asset mudou durante a leitura" }
                bytes
            }
        }
    }
}

internal const val TEXTURE_RETRY_AS_RGBA = -1

internal fun textureFallback(image: KimgImagem, maxBytes: Int): KimgImagem {
    require(image.largura.toLong() * image.altura * 4 <= maxBytes) { "textura RGBA8 excede o limite de fallback" }
    val first = image.mips.first()
    val rgba = when (image.formato) {
        KimgFormato.ETC2_RGBA -> Etc2.decodificarRgba(first.dados, image.largura, image.altura)
        KimgFormato.ETC2_RGB -> Etc2.decodificarRgb(first.dados, image.largura, image.altura)
        else -> error("formato ${image.formato} sem fallback ETC2")
    }
    return KimgImagem(KimgFormato.RGBA8_RAW, image.largura, image.altura, image.srgb, image.temAlfa,
        image.normalMap, listOf(KimgMip(image.largura, image.altura, 0, rgba)))
}
