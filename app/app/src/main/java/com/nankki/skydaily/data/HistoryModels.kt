package com.nankki.skydaily.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 归档索引中的一条：日期与 `daily/<date>.json` 路径已一一校验。 */
data class HistoryEntry(val date: String, val file: String)

/** 已排序（新 → 旧）的归档索引。 */
data class HistoryIndex(val entries: List<HistoryEntry>)

@Serializable
internal data class HistoryIndexPayload(
    @SerialName("dates") val dates: List<HistoryEntryPayload>,
)

@Serializable
internal data class HistoryEntryPayload(
    val date: String,
    val file: String,
)

/**
 * 索引解析与边界校验：任何一条无效日期、重复日期或非 `daily/<date>.json`
 * 路径都会让整份索引失效，避免坏数据覆盖已经可用的缓存。
 */
object HistoryJson {
    private val parser = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }
    private val datePattern = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun parseIndex(raw: String): HistoryIndex? = runCatching {
        fromPayload(parser.decodeFromString(HistoryIndexPayload.serializer(), raw))
    }.getOrNull()

    fun isValidDate(date: String): Boolean {
        if (!datePattern.matches(date)) return false
        return try {
            LocalDate.parse(date)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun fromPayload(payload: HistoryIndexPayload): HistoryIndex? {
        val seen = HashSet<String>(payload.dates.size)
        val entries = ArrayList<HistoryEntry>(payload.dates.size)
        for (item in payload.dates) {
            if (!isValidDate(item.date)) return null
            if (item.file != "daily/${item.date}.json") return null
            if (!seen.add(item.date)) return null
            entries.add(HistoryEntry(item.date, item.file))
        }
        entries.sortByDescending { it.date }
        return HistoryIndex(entries)
    }
}
