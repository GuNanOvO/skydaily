package com.nankki.skydaily.meals

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 北京时间的饭点窗口：开始时刻包含，结束时刻不包含。 */
data class MealWindow(val hour: Int, val startsAtMillis: Long, val endsAtMillis: Long)

/** 当前时刻的饭点状态；没有进行中的饭点时 current 为空。 */
data class MealStatus(val current: MealWindow?, val next: MealWindow, val finishedToday: Boolean)

/** 用户保存的饭点提醒设置；默认关闭且不选饭点。 */
data class MealPreferences(
    val enabled: Boolean = false,
    val hours: Set<Int> = emptySet(),
    val leadMinutes: Int = 5,
)

/** 某个饭点下一次提醒的计划。 */
data class MealReminder(val meal: MealWindow, val triggerAtMillis: Long)

/** 每天固定的老奶奶食堂时间，全部按北京时间计算。 */
object MealSchedule {
    val hours: List<Int> = listOf(8, 10, 12, 16, 18, 20, 22)

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private const val SESSION_MINUTES = 30L
    private const val MINUTE_MILLIS = 60_000L

    /** 当前饭点、下一饭点以及今日是否已结束，均为显式北京时间。 */
    fun status(nowMillis: Long): MealStatus {
        val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val date = local.toLocalDate()
        val hour = local.hour
        val current = if (local.minute < SESSION_MINUTES && hour in hours) windowOn(date, hour) else null
        val nextHour = hours.firstOrNull { it > hour }
        val next = if (nextHour != null) windowOn(date, nextHour) else windowOn(date.plusDays(1), hours.first())
        val finished = hour > hours.last() || hour == hours.last() && local.minute >= SESSION_MINUTES
        return MealStatus(current, next, finished)
    }

    /**
     * 每个选中的饭点只给出下一次提醒，触发时刻严格晚于当前时刻。
     * 结果按实际触发时刻升序排列，最多七个。
     */
    fun reminders(preferences: MealPreferences, nowMillis: Long): List<MealReminder> {
        if (!preferences.enabled) return emptyList()
        val selected = hours.filter { it in preferences.hours }
        if (selected.isEmpty()) return emptyList()
        val leadMillis = leadMillis(preferences.leadMinutes)
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val planned = ArrayList<MealReminder>(selected.size)
        for (hour in selected) {
            var found: MealWindow? = null
            for (dayOffset in 0..1) {
                val candidate = windowOn(date.plusDays(dayOffset.toLong()), hour)
                if (candidate.startsAtMillis - leadMillis > nowMillis) {
                    found = candidate
                    break
                }
            }
            val meal = found ?: continue
            planned += MealReminder(meal, meal.startsAtMillis - leadMillis)
        }
        planned.sortBy { it.triggerAtMillis }
        return planned
    }

    internal fun mealWindow(hour: Int, startsAtMillis: Long): MealWindow =
        MealWindow(hour, startsAtMillis, startsAtMillis + SESSION_MINUTES * MINUTE_MILLIS)

    internal fun leadMillis(leadMinutes: Int): Long = leadMinutes.toLong() * MINUTE_MILLIS


    private fun windowOn(date: LocalDate, hour: Int): MealWindow {
        val startsAtMillis = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return mealWindow(hour, startsAtMillis)
    }
}
