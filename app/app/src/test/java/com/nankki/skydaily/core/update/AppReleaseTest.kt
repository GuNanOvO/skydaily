package com.nankki.skydaily.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    private val packageName = "com.nankki.skydaily"
    private val sha256 = "0123456789abcdef".repeat(4)
    private val commit = "0123456789abcdef0123456789abcdef01234567"

    private fun manifest(
        schemaVersion: Int = 1,
        packageValue: String = packageName,
        versionName: String = "0.2.0",
        versionCode: Int = 1024,
        commitValue: String = commit,
        publishedAt: String = "2026-10-23T10:00:00.000Z",
        minSdk: Int = 28,
        size: Long = 3_137_774L,
        shaValue: String = sha256,
        apkPath: String = "/app/releases/1024/skydaily-0.2.0.apk",
        notes: String = """["feat: 新增应用内更新", "fix: 修正下载进度"]""",
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "packageName": "$packageValue",
          "versionName": "$versionName",
          "versionCode": $versionCode,
          "commit": "$commitValue",
          "publishedAt": "$publishedAt",
          "minSdk": $minSdk,
          "size": $size,
          "sha256": "$shaValue",
          "apkPath": "$apkPath",
          "notes": $notes
        }
    """.trimIndent()

    private fun assertRejected(json: String) {
        assertNull(json, parseAppRelease(json, packageName))
    }

    @Test
    fun `valid manifest is parsed with normalized digests`() {
        val release = parseAppRelease(
            manifest(shaValue = sha256.uppercase(), commitValue = commit.uppercase()),
            packageName,
        )
        assertNotNull(release)
        requireNotNull(release)
        assertEquals("0.2.0", release.versionName)
        assertEquals(1024, release.versionCode)
        assertEquals(28, release.minSdk)
        assertEquals(3_137_774L, release.size)
        assertEquals(sha256, release.sha256)
        assertEquals(commit, release.commit)
        assertEquals("/app/releases/1024/skydaily-0.2.0.apk", release.apkPath)
        assertEquals(2, release.notes.size)
        assertEquals("https://skydaily.nankki.com/app/releases/1024/skydaily-0.2.0.apk", release.apkUrl("https://skydaily.nankki.com"))
    }

    @Test
    fun `unknown fields do not break a valid manifest`() {
        val json = manifest().replaceFirst("{\n", "{\n  \"futureField\": [1, 2],\n")
        assertNotNull(parseAppRelease(json, packageName))
    }

    @Test
    fun `schema version must be one`() {
        assertRejected(manifest(schemaVersion = 2))
        assertRejected(manifest(schemaVersion = 0))
    }

    @Test
    fun `package name must match the installed package`() {
        assertNull(parseAppRelease(manifest(), "com.example.other"))
    }

    @Test
    fun `version code must be positive`() {
        assertRejected(manifest(versionCode = 0))
        assertRejected(manifest(versionCode = -1))
    }

    @Test
    fun `version name must be three numeric parts`() {
        assertRejected(manifest(versionName = "0.2"))
        assertRejected(manifest(versionName = "v0.2.0"))
        assertRejected(manifest(versionName = "0.2.0-beta"))
        assertRejected(manifest(versionName = "0.2.0.1"))
        assertNotNull(parseAppRelease(manifest(versionName = "10.200.3"), packageName))
    }

    @Test
    fun `minimum sdk must be positive and may be higher than the app baseline`() {
        assertRejected(manifest(minSdk = 0))
        assertRejected(manifest(minSdk = -1))
        assertNotNull(parseAppRelease(manifest(minSdk = 28), packageName))
        assertNotNull(parseAppRelease(manifest(minSdk = 29), packageName))
        assertNotNull(parseAppRelease(manifest(minSdk = 34), packageName))
    }

    @Test
    fun `release demanding a higher sdk parses and is reported incompatible`() {
        val release = requireNotNull(parseAppRelease(manifest(minSdk = 34, versionCode = 1025), packageName))
        assertEquals(UpdateCheck.Incompatible(release), compareRelease(release, installedCode = 1024, deviceSdk = 28))
        assertEquals(UpdateCheck.Available(release), compareRelease(release, installedCode = 1024, deviceSdk = 34))
    }

    @Test
    fun `size must be positive and bounded by the shared 128 MiB ceiling`() {
        assertRejected(manifest(size = 0L))
        assertRejected(manifest(size = -1L))
        assertRejected(manifest(size = 134_217_729L))
        assertNotNull(parseAppRelease(manifest(size = 1L), packageName))
        assertNotNull(parseAppRelease(manifest(size = 134_217_728L), packageName))
    }

    @Test
    fun `sha256 must be 64 hex characters`() {
        assertRejected(manifest(shaValue = "a".repeat(63)))
        assertRejected(manifest(shaValue = "a".repeat(65)))
        assertRejected(manifest(shaValue = "g".repeat(64)))
    }

    @Test
    fun `commit must be 40 hex characters`() {
        assertRejected(manifest(commitValue = "a".repeat(39)))
        assertRejected(manifest(commitValue = "a".repeat(41)))
        assertRejected(manifest(commitValue = "z".repeat(40)))
    }

    @Test
    fun `published time must be an ISO instant`() {
        assertRejected(manifest(publishedAt = "2026-10-23"))
        assertRejected(manifest(publishedAt = "23/10/2026"))
        assertRejected(manifest(publishedAt = ""))
        assertNotNull(parseAppRelease(manifest(publishedAt = "2026-10-23T10:00:00+08:00"), packageName))
    }

    @Test
    fun `notes are bounded plain text without links`() {
        val tooMany = (1..13).joinToString(",", "[", "]") { "\"说明 $it\"" }
        assertRejected(manifest(notes = tooMany))
        assertRejected(manifest(notes = """["${"a".repeat(161)}"]"""))
        assertRejected(manifest(notes = """["<b>公告</b>"]"""))
        assertRejected(manifest(notes = """["详情见 https://github.com/GuNanOvO/skydaily"]"""))
        assertRejected(manifest(notes = """["官网 www.example.com"]"""))
        assertRejected(manifest(notes = """[""]"""))
        assertNotNull(parseAppRelease(manifest(notes = """["${"a".repeat(160)}"]"""), packageName))
        assertNotNull(parseAppRelease(manifest(notes = "[]"), packageName))
    }

    @Test
    fun `apk path must be a same-origin releases path`() {
        val rejected = listOf(
            "/app/releases/1024/../../etc/passwd",
            "/app/releases/1024/../1025/skydaily-0.2.0.apk",
            "/app/releases/./1024/skydaily-0.2.0.apk",
            "https://evil.example/app/releases/1024/skydaily-0.2.0.apk",
            "//evil.example/app/releases/1024/skydaily-0.2.0.apk",
            "http://skydaily.nankki.com/app/releases/1024/skydaily-0.2.0.apk",
            "/app/releases/1024/skydaily-0.2.0.apk?download=1",
            "/app/releases/1024/skydaily-0.2.0.apk#top",
            "/app/releases/1024/skydaily-0.2.0.apk%20",
            "/app/releases/1024%2Fskydaily-0.2.0.apk",
            "/app/other/1024/skydaily-0.2.0.apk",
            "app/releases/1024/skydaily-0.2.0.apk",
            "/app/releases/1024/skydaily-0.2.0.txt",
            "/app/releases/1024/",
            "/app/releases/1024/a b.apk",
            "",
        )
        for (path in rejected) {
            assertNull(path, parseAppRelease(manifest(apkPath = path), packageName))
        }
    }

    @Test
    fun `malformed or incomplete json is rejected`() {
        assertRejected("{")
        assertRejected("[]")
        assertRejected("""{"schemaVersion": 1}""")
        assertRejected(
            manifest().replace("\"versionCode\": 1024,", ""),
        )
        assertRejected(
            manifest().replace("\"notes\": [\"feat: 新增应用内更新\", \"fix: 修正下载进度\"]", "\"notes\": \"feat\""),
        )
    }

    @Test
    fun `parsed release survives a trailing whitespace manifest`() {
        val parsed = parseAppRelease(manifest() + "\n\n", packageName)
        assertTrue(parsed != null)
    }
}
