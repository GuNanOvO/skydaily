package com.nankki.skydaily.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateStoreTest {
    @Test
    fun `only the latest download session stays active`() {
        val first = UpdateStore.beginDownload()
        assertTrue(UpdateStore.isDownloadActive(first))

        val second = UpdateStore.beginDownload()
        assertFalse(UpdateStore.isDownloadActive(first))
        assertTrue(UpdateStore.isDownloadActive(second))

        UpdateStore.cancelDownload()
        assertFalse(UpdateStore.isDownloadActive(second))
        assertFalse(UpdateStore.isDownloadActive(-1L))
        assertFalse(UpdateStore.isDownloadActive(0L))
    }
}
