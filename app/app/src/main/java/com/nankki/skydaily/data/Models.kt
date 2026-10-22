package com.nankki.skydaily.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
}
