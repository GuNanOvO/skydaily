package com.nankki.skydaily.overlay

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

/** 在悬浮服务的 IO 协程中读取使用情况事件，保留最后一次前台切换。 */
internal class GameForegroundMonitor(private val context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val state = GameForegroundState()
    private val event = UsageEvents.Event()
    private var queryFrom = bootTime(System.currentTimeMillis())

    fun isGameInForeground(): Boolean {
        val now = System.currentTimeMillis()
        if (!hasUsageAccess(context)) {
            reset(now)
            return state.shouldShow(usageAccessGranted = false, screenAvailable = true)
        }
        val screenAvailable = power.isInteractive && !keyguard.isKeyguardLocked
        if (!screenAvailable) return state.shouldShow(usageAccessGranted = true, screenAvailable = false)
        if (now < queryFrom) reset(now)
        val events = try {
            usageStats.queryEvents(queryFrom, now)
        } catch (_: SecurityException) {
            null
        }
        if (events == null) {
            reset(now)
            return false
        }
        while (events.getNextEvent(event)) {
            // 保留边界事件，下一次重复处理也会得到同一前台状态。
            queryFrom = maxOf(queryFrom, event.timeStamp)
            val packageName = event.packageName
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (packageName != null) state.onResumed(packageName, event.className)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (packageName != null) state.onPaused(packageName, event.className)
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> state.clear()
            }
        }
        return state.shouldShow(usageAccessGranted = true, screenAvailable = true)
    }

    private fun reset(now: Long) {
        state.clear()
        queryFrom = bootTime(now)
    }

    private fun bootTime(now: Long): Long = maxOf(0L, now - SystemClock.elapsedRealtime())

    companion object {
        fun hasUsageAccess(context: Context): Boolean =
            context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED

        fun permissionIntent(context: Context): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
}
