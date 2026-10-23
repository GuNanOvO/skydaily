package com.nankki.skydaily.meals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 精确闹钟到点后的入口：只做本地核对与提醒，随后登记下一次提醒。 */
class MealAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MealReminderManager.ACTION_MEAL_ALARM) return
        val hour = intent.getIntExtra(MealReminderManager.EXTRA_MEAL_HOUR, -1)
        val startsAtMillis = intent.getLongExtra(MealReminderManager.EXTRA_MEAL_START, -1L)
        val triggerAtMillis = intent.getLongExtra(MealReminderManager.EXTRA_MEAL_TRIGGER, -1L)
        if (hour !in MealSchedule.hours || startsAtMillis <= 0L || triggerAtMillis <= 0L) return
        MealReminderManager(context).handleAlarm(hour, startsAtMillis, triggerAtMillis)
    }
}
