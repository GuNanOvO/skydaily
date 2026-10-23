package com.nankki.skydaily.meals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机、应用更新、系统时间/时区变化和精确闹钟授权后的恢复入口。
 * 仅在用户配置并开启提醒后由 [MealReminderManager] 动态启用。
 */
class MealRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MealReminderManager(context).reconcile()
    }
}
