package com.nankki.skydaily.core.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkVerifierTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sha256 = "0123456789abcdef".repeat(4)
    private val signer = "f".repeat(64)

    private fun release(
        versionCode: Int = 1024,
        size: Long = 4L,
        sha: String = sha256,
        packageName: String = "com.nankki.skydaily",
    ) = AppRelease(
        schemaVersion = 1,
        packageName = packageName,
        versionName = "0.2.0",
        versionCode = versionCode,
        commit = "0".repeat(40),
        publishedAt = "2026-10-23T10:00:00.000Z",
        minSdk = 28,
        size = size,
        sha256 = sha,
        apkPath = "/app/releases/$versionCode/skydaily-0.2.0.apk",
        notes = emptyList(),
    )

    private fun facts(
        packageName: String? = "com.nankki.skydaily",
        versionCode: Long = 1024L,
        signerSha256: String? = signer,
    ) = ApkFacts(packageName, versionCode, signerSha256)

    @Test
    fun `sha256 helper matches a known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".toByteArray().sha256Hex(),
        )
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ByteArray(0).sha256Hex())
    }

    @Test
    fun `sha256 file helper reads the whole stream`() {
        val file = folder.newFile("sample.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(file))
    }

    @Test
    fun `hex formatting is lowercase and zero padded`() {
        assertEquals("000fff", byteArrayOf(0x00, 0x0f, 0xff.toByte()).toHex())
        assertEquals("", ByteArray(0).toHex())
    }

    @Test
    fun `matching archive passes every check`() {
        val result = ApkVerifier.decide(
            release = release(),
            facts = facts(),
            installedCertSha256 = signer,
            installedCode = 1,
            actualSize = 4L,
            actualSha256 = sha256,
        )
        assertEquals(VerifyResult.Ok, result)
    }

    @Test
    fun `size mismatch is reported before reading identity`() {
        assertEquals(
            VerifyResult.WrongSize,
            ApkVerifier.decide(release(), facts(packageName = null), signer, 1, 5L, "bad"),
        )
    }

    @Test
    fun `hash mismatch is rejected`() {
        assertEquals(
            VerifyResult.WrongHash,
            ApkVerifier.decide(release(), facts(), signer, 1, 4L, "0".repeat(64)),
        )
    }

    @Test
    fun `package mismatch is rejected`() {
        assertEquals(
            VerifyResult.WrongPackage,
            ApkVerifier.decide(release(), facts(packageName = "com.example.other"), signer, 1, 4L, sha256),
        )
        assertEquals(
            VerifyResult.WrongPackage,
            ApkVerifier.decide(release(), facts(packageName = null), signer, 1, 4L, sha256),
        )
    }

    @Test
    fun `archive version must equal the published code`() {
        assertEquals(
            VerifyResult.WrongVersion,
            ApkVerifier.decide(release(), facts(versionCode = 1025L), signer, 1, 4L, sha256),
        )
        assertEquals(
            VerifyResult.WrongVersion,
            ApkVerifier.decide(
                release(versionCode = 1025),
                facts(versionCode = 1025L),
                signer,
                installedCode = 1025,
                4L,
                sha256,
            ),
        )
    }

    @Test
    fun `signing certificate must match the installed app`() {
        assertEquals(
            VerifyResult.WrongCertificate,
            ApkVerifier.decide(release(), facts(signerSha256 = "1".repeat(64)), signer, 1, 4L, sha256),
        )
        assertEquals(
            VerifyResult.WrongCertificate,
            ApkVerifier.decide(release(), facts(signerSha256 = null), signer, 1, 4L, sha256),
        )
        assertEquals(
            VerifyResult.WrongCertificate,
            ApkVerifier.decide(release(), facts(), installedCertSha256 = null, installedCode = 1, actualSize = 4L, actualSha256 = sha256),
        )
    }

    @Test
    fun `certificate comparison ignores hex case`() {
        assertEquals(
            VerifyResult.Ok,
            ApkVerifier.decide(release(), facts(), signer.uppercase(), 1, 4L, sha256.uppercase()),
        )
    }
}
