package com.nankki.skydaily.core

import com.nankki.skydaily.data.Envelope
import com.nankki.skydaily.data.isUsableFor

/** 今日数据的自动获取策略，日期按北京时间计算。 */
class DailyRefreshPolicy(private val store: KeyValueStore) {
    private var lastAttemptDate: String? = null
    private var lastAttemptAt: Long? = null

    var enabled: Boolean
        get() = store.getString(ENABLED) != "false"
        set(value) {
            if (value == enabled) return
            store.putString(ENABLED, value.toString())
            if (value) {
                lastAttemptDate = null
                lastAttemptAt = null
            }
        }

    var allowMetered: Boolean
        get() = store.getString(ALLOW_METERED) == "true"
        set(value) { store.putString(ALLOW_METERED, value.toString()) }

    val completedDate: String?
        get() = store.getString(COMPLETED_DATE)

    fun shouldAttempt(nowMillis: Long): Boolean {
        if (!enabled) return false
        val today = BeijingClock.today(nowMillis)
        if (completedDate == today) return false
        val previous = lastAttemptAt ?: return true
        return lastAttemptDate != today || nowMillis < previous || nowMillis - previous >= RETRY_INTERVAL_MS
    }

    fun recordAttempt(nowMillis: Long) {
        lastAttemptDate = BeijingClock.today(nowMillis)
        lastAttemptAt = nowMillis
    }

    fun recordSuccess(envelope: Envelope, nowMillis: Long): Boolean {
        if (!envelope.isUsableFor(BeijingClock.today(nowMillis))) return false
        store.putString(COMPLETED_DATE, envelope.date)
        return true
    }

    fun clearCompletion() {
        store.remove(COMPLETED_DATE)
        lastAttemptDate = null
        lastAttemptAt = null
    }

    private companion object {
        const val ENABLED = "refresh:enabled"
        const val ALLOW_METERED = "refresh:allow-metered"
        const val COMPLETED_DATE = "refresh:completed-date"
        const val RETRY_INTERVAL_MS = 10 * 60_000L
    }
}
