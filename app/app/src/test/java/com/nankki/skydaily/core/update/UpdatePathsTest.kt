package com.nankki.skydaily.core.update

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdatePathsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun updatesDirectory(): File {
        val filesDir = folder.newFolder("files")
        val updates = File(filesDir, UpdatePaths.DIRECTORY)
        assertTrue(updates.mkdirs() || updates.isDirectory)
        return updates
    }

    @Test
    fun `plain apk names resolve inside the updates directory`() {
        val root = updatesDirectory()
        val file = UpdatePaths.resolve(root, "1024.apk")
        assertNotNull(file)
        assertEquals(root.canonicalFile, file?.parentFile)
        assertEquals("1024.apk", file?.name)
    }

    @Test
    fun `directory traversal is refused`() {
        val root = updatesDirectory()
        assertNull(UpdatePaths.resolve(root, "../1024.apk"))
        assertNull(UpdatePaths.resolve(root, "../../1024.apk"))
        assertNull(UpdatePaths.resolve(root, "nested/1024.apk"))
        assertNull(UpdatePaths.resolve(root, ".."))
        assertNull(UpdatePaths.resolve(root, "."))
        assertNull(UpdatePaths.resolve(root, "/data/local/tmp/1024.apk"))
        assertNull(UpdatePaths.resolve(root, "..\\1024.apk"))
    }

    @Test
    fun `only the apk extension is served`() {
        val root = updatesDirectory()
        assertNull(UpdatePaths.resolve(root, "1024.txt"))
        assertNull(UpdatePaths.resolve(root, "1024.apk.part"))
        assertNull(UpdatePaths.resolve(root, "1024.part"))
        assertNull(UpdatePaths.resolve(root, ".apk"))
        assertNull(UpdatePaths.resolve(root, "1024.APK"))
        assertNull(UpdatePaths.resolve(root, ""))
    }

    @Test
    fun `files outside the updates directory are refused`() {
        val root = updatesDirectory()
        val outside = folder.newFile("1024.apk")
        assertFalse(UpdatePaths.contains(root, outside))
        assertFalse(UpdatePaths.contains(root, File(root.parentFile, "1024.apk")))
        assertTrue(UpdatePaths.contains(root, File(root, "1024.apk")))
        assertFalse(UpdatePaths.contains(root, File(root, "1024.txt")))
    }

    @Test
    fun `symlink escaping the updates directory is refused`() {
        val root = updatesDirectory()
        val outside = folder.newFile("outside.apk")
        val link = File(root, "linked.apk")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue(created)
        assertNull(UpdatePaths.resolve(root, "linked.apk"))
    }

    @Test
    fun `part names include the version and download session`() {
        val filesDir = folder.newFolder("files")
        assertEquals("1025-7.part", UpdatePaths.part(filesDir, 1025, 7).name)
        assertEquals("1025-8.part", UpdatePaths.part(filesDir, 1025, 8).name)
        assertEquals("1025.apk", UpdatePaths.apk(filesDir, 1025).name)
        assertEquals(UpdatePaths.directory(filesDir), UpdatePaths.part(filesDir, 1025, 7).parentFile)
        assertEquals(UpdatePaths.directory(filesDir), UpdatePaths.apk(filesDir, 1025).parentFile)
    }

    @Test
    fun `obsolete partial and apk files are removed`() {
        val filesDir = folder.newFolder("files")
        val root = UpdatePaths.directory(filesDir)
        assertTrue(root.mkdirs())
        val keepPart = UpdatePaths.part(filesDir, 1025, 7).apply { writeText("part") }
        val oldPart = UpdatePaths.part(filesDir, 1025, 6).apply { writeText("part") }
        val keepApk = UpdatePaths.apk(filesDir, 1025).apply { writeText("apk") }
        val oldApk = UpdatePaths.apk(filesDir, 1024).apply { writeText("apk") }
        val unrelated = File(root, "notes.txt").apply { writeText("keep") }

        UpdatePaths.clearOtherParts(filesDir, keepPart)
        UpdatePaths.clearOtherApks(filesDir, 1025)

        assertTrue(keepPart.isFile)
        assertTrue(keepApk.isFile)
        assertTrue(unrelated.isFile)
        assertFalse(oldPart.exists())
        assertFalse(oldApk.exists())
        assertEquals(listOf("1025-7.part", "1025.apk", "notes.txt"), root.list()?.sorted())
    }

    @Test
    fun `restart cleanup drops every part and downloaded apk`() {
        val filesDir = folder.newFolder("files")
        val root = UpdatePaths.directory(filesDir)
        assertTrue(root.mkdirs())
        val part = UpdatePaths.part(filesDir, 1025, 7).apply { writeText("part") }
        val apk = UpdatePaths.apk(filesDir, 1025).apply { writeText("apk") }
        val unrelated = File(root, "notes.txt").apply { writeText("keep") }

        UpdatePaths.clearStale(filesDir)

        assertFalse(part.exists())
        assertFalse(apk.exists())
        assertTrue(unrelated.isFile)
        assertEquals(listOf("notes.txt"), root.list()?.sorted())
    }

    @Test
    fun `restart cleanup tolerates a missing updates directory`() {
        val filesDir = folder.newFolder("missing")
        UpdatePaths.clearStale(filesDir)
        assertFalse(UpdatePaths.directory(filesDir).exists())
    }
}
