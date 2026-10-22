package com.nankki.skydaily.data

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 站点 `src/link-policy.json` 中 `seasonGuide` 段的 App 侧只读视图。
 *
 * 校验规则与 `src/link-policy.mjs` 的 `normalizeSeasonGuideLink` 保持一致：
 * 协议必须是 https，主机必须等于策略 origin，禁止用户凭据，端口只能是默认 443，
 * 路径必须匹配 pathPattern；规范化结果丢弃查询串与片段，并统一为单个结尾斜杠。
 *
 * 官方指南链接只取 taskDetails 中 keyword 命中的条目（与站点 detailByKeyword 相同，
 * 重复 keyword 时以最后一条为准），candles 不参与解析。
 */
class SeasonGuidePolicy private constructor(
    val keyword: String,
    val origin: String,
    private val pathPattern: Regex,
    private val fallbackCandidate: String,
) {
    private val host: String = origin.substringAfter("://")

    /** 策略自带的兜底地址；规范化后仍不合法时为 null。 */
    val fallback: String? = normalize(fallbackCandidate)

    /** 合法则返回规范化地址，否则 null。 */
    fun normalize(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.isOpaque) return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.rawUserInfo != null) return null
        val port = uri.port
        if (port != -1 && port != 443) return null
        if (!uri.host.equals(host, ignoreCase = true)) return null
        val path = uri.rawPath
        if (path.isNullOrEmpty() || !pathPattern.matches(path)) return null
        return if (path.endsWith("/")) origin + path else origin + path + "/"
    }

    /** WebView 顶级导航白名单：只有通过策略校验的官方地址才允许留在容器内。 */
    fun isAllowedTopLevel(value: String?): Boolean = normalize(value) != null

    fun resolve(data: DailyData): List<String> = resolve(data.taskDetails)

    /**
     * 优先返回 taskDetails 中通过校验的链接（去重、保留出现顺序），
     * 没有可用链接时退回策略兜底地址；两者都不可用时返回空列表。
     */
    fun resolve(details: List<TaskDetail>): List<String> {
        val guide = details.lastOrNull { it.keyword == keyword }
        val links = guide?.links.orEmpty().mapNotNull(::normalize).distinct()
        if (links.isNotEmpty()) return links
        return listOfNotNull(fallback)
    }

    companion object {
        /** APK 资产路径；构建时由站点 `src/link-policy.json` 复制生成，App 内不重复维护。 */
        const val ASSET_PATH: String = "policy/link-policy.json"

        private val parser = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = false
        }

        /** 解析失败（缺段、origin 不规范、正则非法）时返回 null，调用方显示错误态。 */
        fun parse(policyJson: String): SeasonGuidePolicy? {
            val file = runCatching {
                parser.decodeFromString(LinkPolicyFile.serializer(), policyJson)
            }.getOrNull() ?: return null
            val guide = file.seasonGuide ?: return null
            if (guide.keyword.isBlank() || guide.pathPattern.isBlank()) return null

            val rawOrigin = guide.origin.trim()
            val originUri = runCatching { URI(rawOrigin) }.getOrNull() ?: return null
            if (!originUri.isAbsolute || originUri.isOpaque) return null
            if (!originUri.scheme.equals("https", ignoreCase = true)) return null
            if (originUri.rawUserInfo != null || originUri.rawQuery != null || originUri.rawFragment != null) return null
            val originPort = originUri.port
            if (originPort != -1 && originPort != 443) return null
            val originPath = originUri.rawPath
            if (!originPath.isNullOrEmpty() && originPath != "/") return null
            val originHost = originUri.host?.lowercase() ?: return null
            val normalizedOrigin = "https://$originHost"
            if (rawOrigin != normalizedOrigin) return null

            val pattern = runCatching { Regex(guide.pathPattern) }.getOrNull() ?: return null
            return SeasonGuidePolicy(
                keyword = guide.keyword,
                origin = normalizedOrigin,
                pathPattern = pattern,
                fallbackCandidate = guide.fallback.trim(),
            )
        }
    }
}

@Serializable
private class LinkPolicyFile(val seasonGuide: SeasonGuideSection? = null)

@Serializable
private class SeasonGuideSection(
    val keyword: String = "",
    val origin: String = "",
    val pathPattern: String = "",
    val fallback: String = "",
)
