package com.nankki.skydaily.meals

/** 饭点提醒能否实际送达的当前状态。 */
enum class MealReadiness { Off, NoMeals, NotificationBlocked, ExactAlarmBlocked, Ready }

/** 提醒送达前的纯策略判断：设置、权限、时间窗口和去重。 */
object MealReminderPolicy {
    fun readiness(
        preferences: MealPreferences,
        notificationsAllowed: Boolean,
        exactAlarmAllowed: Boolean,
    ): MealReadiness = when {
        !preferences.enabled -> MealReadiness.Off
        !hasSelection(preferences) -> MealReadiness.NoMeals
        !notificationsAllowed -> MealReadiness.NotificationBlocked
        !exactAlarmAllowed -> MealReadiness.ExactAlarmBlocked
        else -> MealReadiness.Ready
    }

    /**
     * 闹钟触发后再次核对：设置未变、时间窗口仍然有用、没有重复且送达条件允许。
     * 提前触发、饭点结束、旧的提前量或已取消的饭点都不会发送提醒。
     */
    fun shouldNotify(
        reminder: MealReminder,
        preferences: MealPreferences,
        nowMillis: Long,
        lastNotifiedStart: Long?,
        notificationsAllowed: Boolean,
        exactAlarmAllowed: Boolean,
    ): Boolean {
        if (!preferences.enabled) return false
        if (!hasSelection(preferences)) return false
        if (reminder.meal.hour !in MealSchedule.hours) return false
        if (reminder.meal.hour !in preferences.hours) return false
        if (!notificationsAllowed || !exactAlarmAllowed) return false
        val expectedTrigger = reminder.meal.startsAtMillis - MealSchedule.leadMillis(preferences.leadMinutes)
        if (reminder.triggerAtMillis != expectedTrigger) return false
        if (nowMillis < reminder.triggerAtMillis) return false
        if (nowMillis >= reminder.meal.endsAtMillis) return false
        if (lastNotifiedStart == reminder.meal.startsAtMillis) return false
        return true
    }

    private fun hasSelection(preferences: MealPreferences): Boolean =
        preferences.hours.any { it in MealSchedule.hours }
}
