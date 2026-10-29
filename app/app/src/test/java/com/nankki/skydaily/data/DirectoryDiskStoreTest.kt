package com.nankki.skydaily.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectoryDiskStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `writes reads and removes entries under the root`() {
        val store = DirectoryDiskStore(folder.newFolder("journal"))

        store.write("today.json", "body")
        store.write("notices.json", "notice")
        assertEquals("body", store.read("today.json"))
        store.remove("notices.json")
        assertNull(store.read("notices.json"))
        store.removeAll("today")
        assertNull(store.read("today.json"))
    }

    @Test
    fun `an unusable root never throws out of read or write`() {
        val unusable = folder.newFile("journal")
        val store = DirectoryDiskStore(unusable)

        store.write("today.json", "body")

        assertNull(store.read("today.json"))
        assertEquals(false, File(unusable, "today.json").exists())
    }
}
