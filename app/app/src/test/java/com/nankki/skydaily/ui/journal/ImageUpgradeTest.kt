package com.nankki.skydaily.ui.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUpgradeTest {
    private val thumb = "https://example.test/history/thumbs/abc.webp"
    private val original = "https://example.test/history/media/abc.webp"

    @Test
    fun `loads original when network is unrestricted and not cached`() {
        assertTrue(shouldRequestOriginal(thumb, original, originalCached = false, dataSaver = false))
    }

    @Test
    fun `uses cached original even when data saver is on`() {
        assertTrue(shouldRequestOriginal(thumb, original, originalCached = true, dataSaver = true))
    }

    @Test
    fun `keeps thumbnail when data saver is on and original is not cached`() {
        assertFalse(shouldRequestOriginal(thumb, original, originalCached = false, dataSaver = true))
    }

    @Test
    fun `skips upgrade when original is missing or equal to thumbnail`() {
        assertFalse(shouldRequestOriginal(thumb, "", originalCached = false, dataSaver = false))
        assertFalse(shouldRequestOriginal(thumb, thumb, originalCached = true, dataSaver = false))
    }
}
