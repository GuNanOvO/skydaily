package com.nankki.skydaily.core.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(versionCode: Int, minSdk: Int = 28) = AppRelease(
        schemaVersion = 1,
        packageName = "com.nankki.skydaily",
        versionName = "0.2.0",
        versionCode = versionCode,
        commit = "0".repeat(40),
        publishedAt = "2026-10-23T10:00:00.000Z",
        minSdk = minSdk,
        size = 1L,
        sha256 = "a".repeat(64),
        apkPath = "/app/releases/$versionCode/skydaily-0.2.0.apk",
        notes = emptyList(),
    )

    @Test
    fun `newer version code offers the update`() {
        val result = compareRelease(release(1025), installedCode = 1024, deviceSdk = 34)
        assertEquals(UpdateCheck.Available(release(1025)), result)
    }

    @Test
    fun `equal version code is up to date`() {
        assertEquals(UpdateCheck.UpToDate, compareRelease(release(1024), installedCode = 1024, deviceSdk = 34))
    }

    @Test
    fun `older version code is up to date and never a downgrade`() {
        assertEquals(UpdateCheck.UpToDate, compareRelease(release(1023), installedCode = 1024, deviceSdk = 34))
        assertEquals(UpdateCheck.UpToDate, compareRelease(release(1), installedCode = 4096, deviceSdk = 34))
    }

    @Test
    fun `newer release requiring a higher sdk is incompatible`() {
        assertEquals(
            UpdateCheck.Incompatible(release(1025, minSdk = 34)),
            compareRelease(release(1025, minSdk = 34), installedCode = 1024, deviceSdk = 28),
        )
        assertEquals(UpdateCheck.UpToDate, compareRelease(release(1024, minSdk = 34), installedCode = 1024, deviceSdk = 28))
    }

    @Test
    fun `release requiring exactly the device sdk is installable`() {
        assertEquals(
            UpdateCheck.Available(release(1025, minSdk = 28)),
            compareRelease(release(1025, minSdk = 28), installedCode = 1024, deviceSdk = 28),
        )
    }

    @Test
    fun `same origin policy accepts only the configured https host and port`() {
        val origin = "https://skydaily.nankki.com".toHttpUrl()
        assertTrue(isSameOriginHttps("https://skydaily.nankki.com/app/latest.json".toHttpUrl(), origin))
        assertTrue(isSameOriginHttps("https://skydaily.nankki.com:443/app/latest.json".toHttpUrl(), origin))
        assertFalse(isSameOriginHttps("http://skydaily.nankki.com/app/latest.json".toHttpUrl(), origin))
        assertFalse(isSameOriginHttps("https://evil.example/app/latest.json".toHttpUrl(), origin))
        assertFalse(isSameOriginHttps("https://skydaily.nankki.com:8443/app/latest.json".toHttpUrl(), origin))
    }

    @Test
    fun `redirect resolution follows same-origin paths and rejects escapes`() {
        val origin = "https://skydaily.nankki.com".toHttpUrl()
        val manifest = resolveSameOriginHttps(origin, origin, "/app/latest.json")
        assertEquals("https://skydaily.nankki.com/app/latest.json", manifest.toString())

        val apk = resolveSameOriginHttps(origin, manifest, "/app/releases/1024/skydaily-0.2.0.apk")
        assertEquals(
            "https://skydaily.nankki.com/app/releases/1024/skydaily-0.2.0.apk",
            apk.toString(),
        )

        for (link in listOf(
            "https://evil.example/app/latest.json",
            "//evil.example/app/latest.json",
            "http://skydaily.nankki.com/app/latest.json",
            "https://skydaily.nankki.com:8443/app/latest.json",
            "ftp://skydaily.nankki.com/app/latest.json",
        )) {
            assertTrue(link, runCatching { resolveSameOriginHttps(origin, manifest, link) }.isFailure)
        }
    }
}
