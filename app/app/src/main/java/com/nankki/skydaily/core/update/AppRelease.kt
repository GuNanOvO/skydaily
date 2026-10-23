package com.nankki.skydaily.core.update

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 站点 `/app/latest.json` 的发布对象；字段与网站共用同一份序列化结果。 */
@Serializable
data class AppRelease(
    val schemaVersion: Int,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val commit: String,
    val publishedAt: String,
    val minSdk: Int,
    val size: Long,
    val sha256: String,
    val apkPath: String,
    val notes: List<String> = emptyList(),
) {
    /** 安装包地址始终拼在配置站点上，发布信息只提供站内路径。 */
    fun apkUrl(siteUrl: String): String = siteUrl.trimEnd('/') + apkPath
}

internal const val SUPPORTED_SCHEMA_VERSION = 1

/** 与 Node 发布/暂存契约一致的上限：128 MiB。 */
internal const val MAX_APK_BYTES = 128L * 1024L * 1024L
internal const val MAX_MANIFEST_BYTES = 64L * 1024L
private const val MAX_NOTES = 12
private const val MAX_NOTE_LENGTH = 160
private const val RELEASE_PATH_PREFIX = "/app/releases/"

private val manifestJson = Json { ignoreUnknownKeys = true }
private val VERSION_NAME = Regex("^\\d+\\.\\d+\\.\\d+$")
private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
private val HEX_40 = Regex("^[0-9a-fA-F]{40}$")
private val PATH_SEGMENT = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")
private val URL_LIKE = Regex("(?i)([a-z][a-z0-9+.-]*://|www\\.)")

/**
 * 解析并校验发布信息。任何一项不合规都返回 null，调用方必须把 null 当作
 * 检查失败而不是「已是最新版本」。
 */
fun parseAppRelease(json: String, installedPackage: String): AppRelease? {
    val release = runCatching { manifestJson.decodeFromString<AppRelease>(json) }.getOrNull() ?: return null
    return release.validated(installedPackage)
}

private fun AppRelease.validated(installedPackage: String): AppRelease? {
    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) return null
    if (packageName != installedPackage) return null
    if (versionCode < 1) return null
    if (!VERSION_NAME.matches(versionName)) return null
    // 只要求是正数；更高的 minSdk 是合法的发布信息，由设备侧报告不兼容。
    if (minSdk < 1) return null
    if (size !in 1..MAX_APK_BYTES) return null
    if (!HEX_64.matches(sha256)) return null
    if (!HEX_40.matches(commit)) return null
    if (!isIsoInstant(publishedAt)) return null
    if (notes.size > MAX_NOTES) return null
    if (notes.any { !it.isAllowedNote() }) return null
    if (!isAllowedApkPath(apkPath)) return null
    return copy(sha256 = sha256.lowercase(), commit = commit.lowercase())
}

/** 说明文字只允许简短纯文本：无标签、无链接、无控制字符。 */
private fun String.isAllowedNote(): Boolean =
    isNotBlank() &&
        length <= MAX_NOTE_LENGTH &&
        none { it == '<' || it == '>' || it.isISOControl() } &&
        !URL_LIKE.containsMatchIn(this)

/**
 * 安装包路径必须是 `/app/releases/` 下的站内绝对路径：
 * 禁止协议、其它主机、查询串、片段、反斜杠与 `..` 之类的上级目录。
 */
private fun isAllowedApkPath(path: String): Boolean {
    if (!path.startsWith(RELEASE_PATH_PREFIX)) return false
    if (path.contains("://")) return false
    if (path.any { it == '\\' || it == '?' || it == '#' || it == '%' }) return false
    if (path.any { it.isWhitespace() || it.isISOControl() }) return false
    val segments = path.split('/')
    if (segments.first() != "") return false
    val tail = segments.drop(1)
    if (tail.size != 4) return false
    if (tail[0] != "app" || tail[1] != "releases") return false
    if (tail.any { !PATH_SEGMENT.matches(it) }) return false
    return tail.last().endsWith(".apk")
}

private fun isIsoInstant(value: String): Boolean =
    runCatching { OffsetDateTime.parse(value) }.isSuccess ||
        runCatching { Instant.parse(value) }.isSuccess
