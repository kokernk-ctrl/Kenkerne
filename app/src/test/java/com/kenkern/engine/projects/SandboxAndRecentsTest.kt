package com.kenkern.engine.projects

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SandboxAndRecentsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `shortcuts reuse one Sandbox without commandeering a user project or overwriting its scene`() {
        val context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tmp.root)
        val userProject = checkNotNull(ProjectManager.createProject(context, "Sandbox"))
        val sandbox = checkNotNull(ProjectManager.openOrCreateSandbox(context))
        assertNotEquals(userProject.root, sandbox.root)
        assertNotNull(ProjectManager.loadSceneForEditing(sandbox))
        sandbox.mainSceneFile.writeText("invalid scene, keep me for recovery")
        assertNotNull(ProjectManager.renameProject(sandbox, "My workspace"))
        val reopened = checkNotNull(ProjectManager.openOrCreateSandbox(context))
        assertEquals(sandbox.root, reopened.root)
        assertEquals("My workspace", reopened.name)
        assertNull(ProjectManager.loadSceneForEditing(reopened))
        assertEquals("invalid scene, keep me for recovery", reopened.mainSceneFile.readText())
        assertEquals(setOf(userProject.root, sandbox.root), ProjectManager.listProjects(context).map { it.root }.toSet())
    }

    @Test fun `recents use actual open and add time with stable folder ties`() {
        fun row(folder: String, name: String, opened: Long, added: Long) =
            RecentProject(ProjectHandle(File(tmp.root, folder), name, "old date"), opened, added)
        val items = listOf(
            row("b", "A display name", 0, 100),
            row("a", "Renamed", 70, 100),
            row("c", "Old project", 200, 1)
        )
        assertEquals(listOf("c", "a", "b"), orderRecentProjects(items).map { it.project.root.name })
    }
}
