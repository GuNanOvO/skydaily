package com.nankki.skydaily.overlay

import com.nankki.skydaily.core.KeyValueStore

/** 悬浮球位置与显示范围记忆。 */
class BallPreferences(private val store: KeyValueStore) {
    var onlyInGame: Boolean
        get() = store.getString("ball:only-in-game") == "true"
        set(value) { store.putString("ball:only-in-game", value.toString()) }

    var x: Int?
        get() = store.getString("ball:x")?.toIntOrNull()
        set(value) {
            if (value == null) store.remove("ball:x") else store.putString("ball:x", value.toString())
        }

    var y: Int?
        get() = store.getString("ball:y")?.toIntOrNull()
        set(value) {
            if (value == null) store.remove("ball:y") else store.putString("ball:y", value.toString())
        }
}
