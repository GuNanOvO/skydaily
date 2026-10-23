package com.nankki.skydaily.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import java.io.File
import java.security.MessageDigest

/** 安装包校验结果。系统安装器仍是签名与升级的最终裁决者。 */
sealed interface VerifyResult {
    data object Ok : VerifyResult
    data object WrongPackage : VerifyResult
    data object WrongVersion : VerifyResult
    data object WrongCertificate : VerifyResult
    data object WrongSize : VerifyResult
    data object WrongHash : VerifyResult
}

/** 从 APK 归档读出的身份信息；归档无法读取时字段为 null。 */
data class ApkFacts(
    val packageName: String?,
    val versionCode: Long,
    val signerSha256: String?,
)

fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val builder = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xff
        builder.append(digits[value ushr 4]).append(digits[value and 0x0f])
    }
    return builder.toString()
}

fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    file.inputStream().use { stream ->
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

/**
 * 安装包大小、摘要、包名、版本与签名证书的前置校验。
 * 判定函数完全接收已读出的值，Android 读取保持薄层。
 */
object ApkVerifier {
    fun decide(
        release: AppRelease,
        facts: ApkFacts,
        installedCertSha256: String?,
        installedCode: Int,
        actualSize: Long,
        actualSha256: String,
    ): VerifyResult {
        if (actualSize != release.size) return VerifyResult.WrongSize
        if (!actualSha256.equals(release.sha256, ignoreCase = true)) return VerifyResult.WrongHash
        if (facts.packageName != release.packageName) return VerifyResult.WrongPackage
        if (facts.versionCode != release.versionCode.toLong()) return VerifyResult.WrongVersion
        if (release.versionCode <= installedCode) return VerifyResult.WrongVersion
        val signer = facts.signerSha256 ?: return VerifyResult.WrongCertificate
        val installed = installedCertSha256 ?: return VerifyResult.WrongCertificate
        if (!signer.equals(installed, ignoreCase = true)) return VerifyResult.WrongCertificate
        return VerifyResult.Ok
    }

    /**
     * 校验下载完成的安装包。调用方已增量算过摘要时可传入 [knownSha256]，
     * 避免重复读取整个文件。
     */
    fun verifyArchive(
        context: Context,
        file: File,
        release: AppRelease,
        installedCertSha256: String?,
        installedCode: Int,
        knownSha256: String? = null,
    ): VerifyResult {
        if (!file.isFile) return VerifyResult.WrongSize
        val size = file.length()
        if (size != release.size) return VerifyResult.WrongSize
        val actualSha256 = knownSha256 ?: sha256Hex(file)
        val facts = readFacts(context.packageManager, file)
        return decide(release, facts, installedCertSha256, installedCode, size, actualSha256)
    }

    /** 当前安装包的签名证书 SHA-256，用于保证升级沿用同一签名身份。 */
    @Suppress("DEPRECATION")
    fun installedCertSha256(context: Context): String? = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        info.signingInfo?.let { signerSha256(it) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun installedVersionCode(context: Context): Int? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun readFacts(manager: PackageManager, file: File): ApkFacts {
        val info = runCatching {
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return ApkFacts(null, 0L, null)
        return ApkFacts(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signerSha256 = info.signingInfo?.let { signerSha256(it) },
        )
    }

    private fun signerSha256(info: SigningInfo): String? {
        // apkContentsSigners 是当前签名身份；旋转密钥后历史证书不再代表当前签名。
        val current = info.apkContentsSigners
        val signatures = if (current.isNotEmpty()) current else info.signingCertificateHistory
        val certificate = signatures.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray()).toHex()
    }
}
