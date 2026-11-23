package com.kenkern.engine.assets.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class ProjectAssetBrowserTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `files are categorized with relative paths and actual byte sizes`() {
        val root = temp.newFolder()
        fun add(path: String, bytes: Int) = File(root, path).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(bytes))
        }
        add("assets/models/Car.KERN", 317)
        add("assets/textures/car.PNG", 24)
        add("scenes/cena.ck", 11)
        add("scripts/spin.lua", 21)
        add("audio/Bell.wav", 99)
        add("project.kenkern", 12)
        add("assets/.lixeira-import/Car.KERN", 317)
        val index = ProjectAssetBrowser.scan(root, emptyList())
        assertFalse(index.incomplete)
        assertEquals(6, index.files.size)
        assertEquals(AssetCategory.entries.toSet(), index.files.map { it.category }.toSet())
        assertEquals(listOf(ProjectAsset("assets/models/Car.KERN", 317)), index.filter(AssetCategory.MESH, " car ", false))
        assertEquals(listOf("assets/models/Car.KERN", "assets/textures/car.PNG"), index.filter(null, "CAR", false).map { it.path })
        assertTrue(index.filter(null, "", true).isEmpty())
    }

    @Test fun `explicit missing references are grouped and present files are not marked broken`() {
        val root = temp.newFolder()
        File(root, "live.KERN").writeText("mesh")
        File(root, "directory.KERN").mkdir()
        val index = ProjectAssetBrowser.scan(root, listOf(
            SceneMeshReference("live.KERN", "Present"), SceneMeshReference("./live.KERN", "Present too"),
            SceneMeshReference("gone.KERN", "First"), SceneMeshReference("gone.KERN", "Second"),
            SceneMeshReference("directory.KERN", "Not a file"), SceneMeshReference("live.kern", "Case matters")
        ))
        val broken = index.filter(null, "", true)
        assertEquals(listOf("directory.KERN", "gone.KERN", "live.kern"), broken.map { it.path })
        val shared = index.filter(AssetCategory.MESH, "Second", true).single()
        assertEquals(listOf("First", "Second"), shared.brokenOwners)
        assertNull(shared.sizeBytes)
    }

    @Test fun `never lists or resolves links or unsafe paths`() {
        val root = temp.newFolder()
        val outside = temp.newFolder()
        val secret = File(outside, "target.KERN").apply { writeText("outside") }
        Files.createSymbolicLink(File(root, "link.KERN").toPath(), secret.toPath())
        Files.createSymbolicLink(File(root, "link-folder").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "loop").toPath(), root.toPath())
        val invalid = listOf("../${outside.name}/target.KERN", secret.absolutePath, "link.KERN", "link-folder/target.KERN", "C:\\models\\target.KERN")
        assertTrue(ProjectAssetBrowser.scan(root, emptyList()).files.isEmpty())
        invalid.forEach { assertNull(ProjectAssetBrowser.resolve(root, it)) }
        val entries = ProjectAssetBrowser.scan(root, invalid.map { SceneMeshReference(it, "Bad Nick") }).files
        assertEquals(invalid.toSet(), entries.filter { it.broken }.map { it.path }.toSet())
        assertEquals("outside", secret.readText())
    }

    @Test fun `walk does not swallow cancellation`() {
        assertThrows(CancellationException::class.java) {
            ProjectAssetBrowser.scan(temp.newFolder(), emptyList()) { throw CancellationException() }
        }
    }
}
