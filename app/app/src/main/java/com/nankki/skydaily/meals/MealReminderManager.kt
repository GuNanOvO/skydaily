package com.nankki.skydaily.meals

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.nankki.skydaily.MainActivity
import com.nankki.skydaily.R
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.KeyValueStore

/** 通知权限与精确闹钟权限的当前读取结果。 */
data class MealAccess(val notificationsAllowed: Boolean, val exactAlarmAllowed: Boolean)

/**
 * 饭点提醒的本地持久化与系统适配：保存设置、创建专属通知渠道、
 * 维护最多七个精确一次性闹钟，并在触发前后按当前设置与权限核对。
 */
class MealReminderManager(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val store: KeyValueStore = AndroidKeyValueStore(appContext)
    private val notificationManager: NotificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val alarmManager: AlarmManager =
        appContext.getSystemService(AlarmManager::class.java)

    fun loadPreferences(): MealPreferences {
        val hours = store.getString(KEY_HOURS)
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MealSchedule.hours }
            .toSet()
        val lead = store.getString(KEY_LEAD)
            ?.toIntOrNull()
            ?.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
            ?: DEFAULT_LEAD_MINUTES
        return MealPreferences(
            enabled = store.getString(KEY_ENABLED) == "true",
            hours = hours,
            leadMinutes = lead,
        )
    }

    /**
     * 用户显式保存设置时调用。只有开启且选中饭点时才创建提醒通知渠道；
     * 随后取消旧闹钟，并按当前权限只登记仍然可用且未来的提醒。
     */
    fun savePreferences(preferences: MealPreferences) {
        val sanitized = preferences.copy(
            hours = preferences.hours.filter { it in MealSchedule.hours }.toSet(),
            leadMinutes = preferences.leadMinutes.coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES),
        )
        store.putString(KEY_ENABLED, sanitized.enabled.toString())
        val orderedHours = MealSchedule.hours.filter { it in sanitized.hours }
        if (orderedHours.isEmpty()) {
            store.remove(KEY_HOURS)
        } else {
            store.putString(KEY_HOURS, orderedHours.joinToString(","))
        }
        store.putString(KEY_LEAD, sanitized.leadMinutes.toString())
        if (sanitized.enabled && orderedHours.isNotEmpty()) ensureChannel()
        applyPlan(sanitized)
        cancelUnselectedNotifications(sanitized)
    }

    fun accessState(): MealAccess = MealAccess(
        notificationsAllowed = notificationsAllowed(),
        exactAlarmAllowed = exactAlarmAllowed(),
    )

    /**
     * 前台返回、开机、应用更新、系统时间或时区变化、精确闹钟授权后重新核对。
     * 设置关闭时清理残留闹钟与提醒，开启时只登记当前仍可送达的未来提醒。
     */
    fun reconcile() {
        applyPlan(loadPreferences())
    }

    fun notificationSettingsIntent(): Intent {
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        val channelBlocked = channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE
        return if (channelBlocked) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        }
    }

    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${appContext.packageName}"),
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}"),
            )
        }

    /** 闹钟触发入口：重新核对后发送提醒，并登记该饭点的下一次提醒。 */
    internal fun handleAlarm(hour: Int, startsAtMillis: Long, triggerAtMillis: Long) {
        val preferences = loadPreferences()
        val access = accessState()
        val nowMillis = System.currentTimeMillis()
        val reminder = MealReminder(MealSchedule.mealWindow(hour, startsAtMillis), triggerAtMillis)
        if (MealReminderPolicy.shouldNotify(
                reminder,
                preferences,
                nowMillis,
                lastNotifiedStart(hour),
                access.notificationsAllowed,
                access.exactAlarmAllowed,
            )
        ) {
            postNotification(reminder, nowMillis)
        }
        applyPlan(preferences)
    }

    private fun lastNotifiedStart(hour: Int): Long? = store.getString(KEY_LAST_NOTIFIED + hour)?.toLongOrNull()

    private fun applyPlan(preferences: MealPreferences) {
        if (!isConfigured(preferences)) {
            setRestoreReceiverEnabled(false)
            cancelAlarms()
            cancelAllNotifications()
            return
        }
        setRestoreReceiverEnabled(true)
        cancelAlarms()
        val access = accessState()
        val readiness = MealReminderPolicy.readiness(
            preferences,
            access.notificationsAllowed,
            access.exactAlarmAllowed,
        )
        if (readiness != MealReadiness.Ready) return
        scheduleAlarms(MealSchedule.reminders(preferences, System.currentTimeMillis()))
    }

    private fun isConfigured(preferences: MealPreferences): Boolean =
        preferences.enabled && preferences.hours.any { it in MealSchedule.hours }

    private fun scheduleAlarms(reminders: List<MealReminder>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return
        for (reminder in reminders) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    alarmPendingIntent(reminder),
                )
            } catch (exception: SecurityException) {
                // 授权在检查与设置之间被撤销：本轮停止登记，不降级成不精确提醒。
                return
            }
        }
    }

    private fun cancelAlarms() {
        for (hour in MealSchedule.hours) {
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                ALARM_REQUEST_BASE + hour,
                alarmIntent(hour),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: continue
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun alarmIntent(hour: Int): Intent =
        Intent(appContext, MealAlarmReceiver::class.java).setAction(ACTION_MEAL_ALARM)

    private fun alarmPendingIntent(reminder: MealReminder): PendingIntent {
        val intent = alarmIntent(reminder.meal.hour).apply {
            putExtra(EXTRA_MEAL_HOUR, reminder.meal.hour)
            putExtra(EXTRA_MEAL_START, reminder.meal.startsAtMillis)
            putExtra(EXTRA_MEAL_TRIGGER, reminder.triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_BASE + reminder.meal.hour,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun postNotification(reminder: MealReminder, nowMillis: Long) {
        ensureChannel()
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID) ?: return
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return
        val hour = reminder.meal.hour
        val open = nowMillis >= reminder.meal.startsAtMillis
        val range = rangeLabel(hour)
        val title = if (open) "开饭中" else "即将开饭"
        val text = if (open) "老奶奶食堂 $range 正在开饭" else "老奶奶食堂 $range 即将开饭"
        val contentIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_BASE + hour,
            Intent(appContext, MainActivity::class.java).apply {
                action = OPEN_MEALS_ACTION
                putExtra(EXTRA_MEAL_START, reminder.meal.startsAtMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setTimeoutAfter((reminder.meal.endsAtMillis - nowMillis).coerceAtLeast(1L))
            .build()
        notificationManager.notify(NOTIFICATION_ID_BASE + hour, notification)
        store.putString(KEY_LAST_NOTIFIED + hour, reminder.meal.startsAtMillis.toString())
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
            },
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!notificationManager.areNotificationsEnabled()) return false
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun exactAlarmAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private fun setRestoreReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(appContext, MealRestoreReceiver::class.java)
        val desired = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (appContext.packageManager.getComponentEnabledSetting(component) == desired) return
        appContext.packageManager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
    }

    private fun cancelUnselectedNotifications(preferences: MealPreferences) {
        for (hour in MealSchedule.hours) {
            if (!preferences.enabled || hour !in preferences.hours) cancelNotification(hour)
        }
    }

    private fun cancelAllNotifications() {
        for (hour in MealSchedule.hours) cancelNotification(hour)
    }

    private fun cancelNotification(hour: Int) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + hour)
    }

    private fun rangeLabel(hour: Int): String {
        val start = hour.toString().padStart(2, '0') + ":00"
        val end = hour.toString().padStart(2, '0') + ":30"
        return "$start–$end"
    }

    companion object {
        /** 点按提醒后交给 MainActivity 的导航动作。 */
        const val OPEN_MEALS_ACTION = "com.nankki.skydaily.OPEN_MEALS"

        /** 提醒对应的饭点开始时刻；随通知意图交给 MainActivity 用于单次导航去重。 */
        const val EXTRA_MEAL_START = "meal_start"

        internal const val ACTION_MEAL_ALARM = "com.nankki.skydaily.action.MEAL_ALARM"
        internal const val EXTRA_MEAL_HOUR = "com.nankki.skydaily.extra.MEAL_HOUR"
        internal const val EXTRA_MEAL_TRIGGER = "com.nankki.skydaily.extra.MEAL_TRIGGER"

        private const val CHANNEL_ID = "skydaily-meals"
        private const val CHANNEL_NAME = "饭点提醒"
        private const val CHANNEL_DESCRIPTION = "在选定的饭点提示开饭"
        private const val NOTIFICATION_ID_BASE = 2000
        private const val ALARM_REQUEST_BASE = 5000
        private const val CONTENT_REQUEST_BASE = 7000
        private const val KEY_ENABLED = "meals:enabled"
        private const val KEY_HOURS = "meals:hours"
        private const val KEY_LEAD = "meals:lead_minutes"
        private const val KEY_LAST_NOTIFIED = "meals:last_notified_start:"
        private const val DEFAULT_LEAD_MINUTES = 5
        private const val MIN_LEAD_MINUTES = 0
        private const val MAX_LEAD_MINUTES = 60
    }
}
