package com.nankki.skydaily.core

/** 主题偏好：自动按北京时间 19:00–07:00 切到暗色，手动选择优先并持久化。 */
enum class ThemeMode { Auto, Light, Dark }

object ThemeResolver {
    fun isDark(mode: ThemeMode, nowMillis: Long = System.currentTimeMillis()): Boolean = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> {
            val hour = BeijingClock.hour(nowMillis)
            hour >= 19 || hour < 7
        }
    }

    fun toggle(mode: ThemeMode, nowMillis: Long = System.currentTimeMillis()): ThemeMode =
        if (isDark(mode, nowMillis)) ThemeMode.Light else ThemeMode.Dark
}

class ThemePreference(private val store: KeyValueStore) {
    var mode: ThemeMode
        get() = when (store.getString(KEY)) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.Auto
        }
        set(value) {
            if (value == ThemeMode.Auto) store.remove(KEY) else store.putString(KEY, value.name.lowercase())
        }

    private companion object {
        const val KEY = "theme:mode"
    }
}
