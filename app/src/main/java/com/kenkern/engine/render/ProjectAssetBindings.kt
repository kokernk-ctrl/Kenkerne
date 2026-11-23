package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.core.Scene
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.WeakHashMap

/** Publish the root with the source scene, never with the renderer or with a Play copy. */
internal object ProjectAssetBindings {
    private val sessions = WeakHashMap<Scene, ProjectAssetScope>()

    @Synchronized
    fun attach(scene: Scene, root: File) {
        sessions[scene] = ProjectAssetScope(root)
    }

    @Synchronized
    fun detach(scene: Scene) {
        sessions.remove(scene)
    }

    @Synchronized
    fun of(scene: Scene?): ProjectAssetScope? = sessions[scene]

    @Synchronized
    fun invalidate(scene: Scene, relativePaths: Collection<String>) {
        sessions[scene]?.invalidate(relativePaths)
    }
}

internal class ProjectAssetScope(val root: File) {
    // Mutated only by the binding; renderer reads one immutable snapshot per frame.
    @Volatile
    var revisions: Map<String, Long> = emptyMap()
        private set
    private var version = 0L
    private val materials = HashMap<String, List<KernMaterial>>()
    private val metadataRevision = MutableStateFlow(0L)

    fun materialsFor(path: String) = metadataRevision.map {
        synchronized(this) { materials[path].orEmpty() }
    }.distinctUntilChanged()

    @Synchronized
    fun publishMaterials(path: String, revision: Long, value: List<KernMaterial>) {
        if ((revisions[path] ?: 0L) != revision || materials[path] == value) return
        materials[path] = value
        metadataRevision.value++
    }

    @Synchronized
    fun invalidate(relativePaths: Collection<String>) {
        if (relativePaths.isEmpty()) return
        val revision = ++version
        revisions = HashMap(revisions).apply { for (path in relativePaths) put(path, revision) }
        for (path in relativePaths) materials.remove(path)
        metadataRevision.value++
    }
}
