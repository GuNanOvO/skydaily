package com.nankki.skydaily.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 站点与勾选都以北京时间为准：日期在 00:00 换天。 */
object BeijingClock {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val isoDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val isoDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
    private val dotted = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val weekdayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    fun today(nowMillis: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().format(isoDate)

    fun date(nowMillis: Long = System.currentTimeMillis()): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    fun hour(nowMillis: Long = System.currentTimeMillis()): Int =
        Instant.ofEpochMilli(nowMillis).atZone(zone).hour

    fun weekday(date: String): String {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
        return weekdayNames[parsed.dayOfWeek.value - 1]
    }

    fun monthLabel(date: String): String {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
        return "%04d / %02d".format(parsed.year, parsed.monthValue)
    }

    fun dayLabel(date: String): String =
        runCatching { LocalDate.parse(date).dayOfMonth.toString().padStart(2, '0') }.getOrDefault("--")

    fun updatedAt(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(hourMinute)

    fun updatedDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(isoDate)

    /** 公告时间形如 `2026-10-09T00:27:39.000Z`，展示为北京时间日期。 */
    fun noticeDate(iso: String): String {
        val instant = parseInstant(iso) ?: return ""
        return instant.atZone(zone).toLocalDate().format(dotted)
    }

    fun sameDay(a: String, b: String): Boolean = a == b

    private fun parseInstant(raw: String): Instant? =
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(raw, isoDateTime).toInstant() }.getOrNull()
}
