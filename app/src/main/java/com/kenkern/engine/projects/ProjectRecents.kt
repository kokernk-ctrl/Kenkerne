package com.kenkern.engine.projects

import android.content.Context
import com.kenkern.engine.diag.KenkernLog

/** A project is recent when it was successfully opened, or newly added to this library. */
data class RecentProject(val project: ProjectHandle, val lastOpenedAt: Long, val addedAt: Long) {
    val activityAt: Long get() = maxOf(lastOpenedAt, addedAt)
}

internal fun orderRecentProjects(projects: List<RecentProject>): List<RecentProject> =
    projects.sortedWith(compareByDescending<RecentProject> { it.activityAt }.thenBy { it.project.root.name })

/** Local history is keyed by folder identity, never embedded in a shared .kernpack. */
class ProjectRecents(context: Context) {
    private val prefs = context.getSharedPreferences("kenkern_project_recents", Context.MODE_PRIVATE)

    @Synchronized
    fun list(projects: List<ProjectHandle>): List<RecentProject> {
        val added = prefs.edit()
        var changed = false
        val entries = projects.map { project ->
            val key = project.root.name
            val addedAt = read("added:$key").takeIf { it > 0 } ?: project.root.lastModified().also {
                if (it > 0) {
                    added.putLong("added:$key", it)
                    changed = true
                }
            }
            RecentProject(project, read("opened:$key"), addedAt)
        }
        if (changed) added.apply()
        return orderRecentProjects(entries)
    }

    @Synchronized
    fun recordOpen(project: ProjectHandle) {
        val key = project.root.name
        prefs.edit().putLong("opened:$key", System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun forget(project: ProjectHandle) {
        val key = project.root.name
        prefs.edit().remove("opened:$key").remove("added:$key").apply()
    }

    private fun read(key: String): Long = runCatching { prefs.getLong(key, 0L) }
        .onFailure { KenkernLog.w("Kenkern/ProjectRecents", "Histórico local ilegível", it) }
        .getOrDefault(0L)
}
