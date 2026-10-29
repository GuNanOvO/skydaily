package com.nankki.skydaily.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.Json as JsonParser

@Serializable
data class Envelope(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val date: String,
    val timezone: String = "Asia/Shanghai",
    val data: DailyData,
    val meta: Meta = Meta(),
    val errors: List<SectionError> = emptyList(),
)

/** 任务与攻略错误阻止当日完成；已发布的天气、日历、媒体缺失不阻止查看任务。 */
internal fun Envelope.isUsableFor(date: String): Boolean =
    schemaVersion == 1 && this.date == date && !meta.stale && data.tasks.isNotEmpty() &&
        errors.none { it.section == "tasks" || it.section.startsWith("detail:") }

@Serializable
data class Meta(
    @SerialName("fetched_at") val fetchedAt: String = "",
    val cached: Boolean = false,
    val stale: Boolean = false,
)

@Serializable
data class SectionError(val section: String = "", val code: String = "")

@Serializable
data class DailyData(
    val tasks: List<Task> = emptyList(),
    val taskDetails: List<TaskDetail> = emptyList(),
    val weather: Weather? = null,
    val calendar: Calendar? = null,
    val candles: List<TaskDetail> = emptyList(),
    val seasonCandleMap: String? = null,
)

@Serializable
data class Task(
    val number: Int,
    val text: String,
    val keyword: String? = null,
)

@Serializable
data class DetailSection(
    val label: String = "",
    val text: String = "",
)

@Serializable
data class TaskVariant(
    val label: String = "",
    val title: String = "",
    val sections: List<DetailSection> = emptyList(),
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val text: String = "",
)

@Serializable
data class TaskDetail(
    val keyword: String = "",
    val title: String = "",
    val sections: List<DetailSection> = emptyList(),
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val text: String = "",
    val variants: List<TaskVariant> = emptyList(),
)

@Serializable
data class Weather(
    val text: String = "",
    val images: List<String> = emptyList(),
)

@Serializable
data class Calendar(
    val images: List<String> = emptyList(),
)

@Serializable
data class NoticeArchive(
    val schema: Int = 1,
    @SerialName("generated_at") val generatedAt: String = "",
    val current: NoticeCurrent? = null,
    val versions: List<NoticeVersion> = emptyList(),
) {
    fun currentVersion(): NoticeVersion? {
        val hash = current?.hash ?: return versions.lastOrNull()
        return versions.lastOrNull { it.hash == hash } ?: versions.lastOrNull()
    }
}

@Serializable
data class NoticeCurrent(
    val hash: String = "",
    val title: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("first_seen_at") val firstSeenAt: String = "",
)

@Serializable
data class NoticeVersion(
    val hash: String = "",
    val title: String = "",
    val message: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("first_seen_at") val firstSeenAt: String = "",
)

/** 站点产物里的媒体路径是站内绝对路径；App 统一补上站点前缀。 */
object Media {
    private const val MEDIA_MARKER = "/history/media/"

    fun absolute(baseUrl: String, path: String): String = when {
        path.isBlank() -> ""
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    fun thumb(path: String): String {
        val index = path.indexOf(MEDIA_MARKER)
        if (index < 0 || !path.endsWith(".webp")) return path
        return path.substring(0, index) + "/history/thumbs/" + path.substring(index + MEDIA_MARKER.length)
    }
}

object JournalJson {
    private val parser = JsonParser {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }

    fun parseEnvelope(raw: String): Envelope = parser.decodeFromString(Envelope.serializer(), raw)

    fun parseNotices(raw: String): NoticeArchive = parser.decodeFromString(NoticeArchive.serializer(), raw)

    /**
     * `/v1/notice`：Envelope 外壳，`data` 即当前公告。
     * `meta.stale` 或非空 `errors` 表示上游不可信；缺字段的默认模型同样拒绝。
     */
    fun parseNoticeApi(raw: String): NoticeVersion? {
        val root = objectOf(raw) ?: return null
        val meta = root["meta"] as? JsonObject
        if ((meta?.get("stale") as? JsonPrimitive)?.booleanOrNull == true) return null
        if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) return null
        val data = root["data"] as? JsonObject ?: return null
        val notice = runCatching { parser.decodeFromJsonElement(NoticeVersion.serializer(), data) }.getOrNull() ?: return null
        return notice.takeIf { it.hash.isNotBlank() && it.title.isNotBlank() }
    }

    /**
     * `history/notices.json`：必须是显式归档——`versions` 数组存在，空数组表示确认当前无公告。
     * 缺少 `versions` 的默认对象和空白公告不参与择路；版本选择沿用归档的当前版本规则。
     */
    fun parseNoticeArchive(raw: String): NoticeArchive? {
        val root = objectOf(raw) ?: return null
        val versions = root["versions"] as? JsonArray ?: return null
        val current = root["current"]
        if (current != null && current !is JsonObject && current !is JsonNull) return null
        val archive = runCatching { parser.decodeFromJsonElement(NoticeArchive.serializer(), root) }.getOrNull() ?: return null
        if (versions.isEmpty()) return archive.takeUnless { current is JsonObject }
        val selected = archive.currentVersion() ?: return null
        return archive.takeIf { selected.hash.isNotBlank() && selected.title.isNotBlank() }
    }

    private fun objectOf(raw: String): JsonObject? =
        runCatching { parser.parseToJsonElement(raw) as? JsonObject }.getOrNull()
}
