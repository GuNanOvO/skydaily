package com.nankki.skydaily.core

/** 连击只使用单调时间；解锁状态与普通设置一样保存在本机。 */
internal class DebugAccess(private val store: KeyValueStore) {
    private var lastTap: Long? = null
    private var taps = 0

    val isUnlocked: Boolean
        get() = store.getString(KEY) == "true"

    /** 仅在这次点击刚好完成解锁时返回 true。 */
    fun tap(elapsedMillis: Long): Boolean {
        if (isUnlocked) return false
        val previous = lastTap
        taps = if (previous != null && elapsedMillis >= previous && elapsedMillis - previous <= 800L) taps + 1 else 1
        lastTap = elapsedMillis
        if (taps < 7) return false
        store.putString(KEY, "true")
        reset()
        return true
    }

    fun hide() {
        store.remove(KEY)
        reset()
    }

    private fun reset() {
        lastTap = null
        taps = 0
    }

    private companion object {
        const val KEY = "debug:unlocked"
    }
}
