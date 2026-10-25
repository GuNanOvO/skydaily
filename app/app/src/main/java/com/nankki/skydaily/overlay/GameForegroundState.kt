package com.nankki.skydaily.overlay

/**
 * 已恢复的 Activity 栈。多窗口下可能同时存在多个前台 Activity，
 * 记录最近恢复的一个；上层窗口暂停后回到仍在运行的下一层，
 * 旧 Activity 延迟送达的暂停事件不会覆盖新的前台。
 */
internal class GameForegroundState {
    private val resumed = ArrayList<ResumedActivity>(4)

    fun onResumed(packageName: String, activityName: String?) {
        var index = 0
        while (index < resumed.size) {
            val entry = resumed[index]
            if (entry.packageName == packageName && entry.activityName == activityName) {
                resumed.removeAt(index)
            } else {
                index++
            }
        }
        resumed.add(ResumedActivity(packageName, activityName))
    }

    fun onPaused(packageName: String, activityName: String?) {
        var index = 0
        while (index < resumed.size) {
            val entry = resumed[index]
            if (entry.packageName == packageName &&
                (entry.activityName == activityName || entry.activityName == null || activityName == null)
            ) {
                resumed.removeAt(index)
            } else {
                index++
            }
        }
    }

    fun clear() {
        resumed.clear()
    }

    fun shouldShow(usageAccessGranted: Boolean, screenAvailable: Boolean): Boolean {
        if (!usageAccessGranted || !screenAvailable) return false
        val foreground = resumed.lastOrNull() ?: return false
        return when (foreground.packageName) {
            "com.netease.sky",
            "com.netease.sky.huawei",
            "com.netease.sky.vivo",
            "com.netease.sky.bilibili",
            "com.netease.sky.aligames",
            "com.tencent.tmgp.eyou.eygy" -> true
            else -> false
        }
    }

    private class ResumedActivity(val packageName: String, val activityName: String?)
}
