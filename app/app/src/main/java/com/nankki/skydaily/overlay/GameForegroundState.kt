package com.nankki.skydaily.overlay

/** 前台 Activity 的状态；旧 Activity 延迟送达的暂停事件不覆盖新的前台。 */
internal class GameForegroundState {
    private var foregroundPackage: String? = null
    private var foregroundActivity: String? = null

    fun onResumed(packageName: String, activityName: String?) {
        foregroundPackage = packageName
        foregroundActivity = activityName
    }

    fun onPaused(packageName: String, activityName: String?) {
        if (foregroundPackage == packageName &&
            (foregroundActivity == activityName || foregroundActivity == null || activityName == null)
        ) {
            clear()
        }
    }

    fun clear() {
        foregroundPackage = null
        foregroundActivity = null
    }

    fun shouldShow(usageAccessGranted: Boolean, screenAvailable: Boolean): Boolean =
        usageAccessGranted && screenAvailable && when (foregroundPackage) {
            "com.netease.sky",
            "com.netease.sky.huawei",
            "com.netease.sky.vivo",
            "com.netease.sky.bilibili",
            "com.netease.sky.aligames",
            "com.tencent.tmgp.eyou.eygy" -> true
            else -> false
        }
}
