package com.nankki.skydaily.ui.journal

import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.TaskDetail

/** 面板里用到的纯格式化逻辑，全部可单测。 */
object JournalFormat {
    fun progress(done: Int, total: Int): String = "$done / $total 已完成"

    fun canEdit(envelopeDate: String, today: String): Boolean =
        envelopeDate.isNotEmpty() && envelopeDate == today

    fun staleLabel(envelopeDate: String, today: String): String? =
        if (envelopeDate.isNotEmpty() && envelopeDate != today) "数据日期 ${envelopeDate}" else null

    fun updatedLabel(epochMillis: Long, offline: Boolean, stale: Boolean): String {
        if (epochMillis <= 0L) return if (offline) "离线缓存" else "等待更新"
        val time = BeijingClock.updatedAt(epochMillis)
        val date = BeijingClock.updatedDate(epochMillis)
        val day = if (date == BeijingClock.today()) "" else "$date "
        return buildString {
            append("更新于 ")
            append(day)
            append(time)
            append("（北京时间）")
            if (offline) append(" · 离线缓存")
            if (stale) append(" · 缓存")
        }
    }

    fun humanizeReason(reason: String): String {
        val lower = reason.lowercase()
        return when {
            "unable to resolve host" in lower ||
                "failed to connect" in lower ||
                "timeout" in lower ||
                "network" in lower ||
                "connect" in lower -> "网络不可用，显示本地缓存"
            "http 5" in lower -> "服务暂时不可用，显示本地缓存"
            "http 4" in lower -> "请求失败（${reason}）"
            else -> reason
        }
    }

    fun briefSections(detail: TaskDetail?, limit: Int = 2): List<Pair<String, String>> =
        detail?.sections.orEmpty()
            .filter { it.text.isNotBlank() }
            .take(limit)
            .map { it.label to it.text.replace('\n', ' ').trim() }

    fun taskDetailFor(keyword: String?, details: List<TaskDetail>): TaskDetail? {
        if (keyword.isNullOrBlank()) return null
        return details.firstOrNull { it.keyword == keyword }
    }

    fun noticePreview(message: String, maxChars: Int = 96): String {
        val flat = message.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (flat.length <= maxChars) flat else flat.take(maxChars).trimEnd() + "…"
    }

    fun imageCountLabel(count: Int): String = when {
        count <= 1 -> "查看大图 ↗"
        else -> "查看 $count 张图 ↗"
    }

    fun countLabel(count: Int): String = if (count > 0) " $count" else ""
}

/** 公告正文按站点排版做最小结构化：栏目标题、编号项、项目符号、普通段落。 */
data class NoticeLine(val text: String, val kind: NoticeLineKind)

enum class NoticeLineKind { Heading, Item, Bullet, Paragraph }

fun noticeLines(message: String): List<NoticeLine> =
    message.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val kind = when {
                line.startsWith("【") && line.endsWith("】") -> NoticeLineKind.Heading
                line.matches(Regex("^\\d+[、.．].*")) -> NoticeLineKind.Item
                line.startsWith("·") || line.startsWith("- ") -> NoticeLineKind.Bullet
                else -> NoticeLineKind.Paragraph
            }
            NoticeLine(line, kind)
        }
        .toList()

