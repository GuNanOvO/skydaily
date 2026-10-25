package com.nankki.skydaily.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameForegroundStateTest {
    @Test
    fun `leaving for the launcher hides and returning to the game restores visibility`() {
        val state = GameForegroundState()
        state.onResumed("com.netease.sky", "GameActivity")
        assertTrue(state.shouldShow(usageAccessGranted = true, screenAvailable = true))

        state.onResumed("com.miui.home", "Launcher")
        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = true))

        state.onResumed("com.netease.sky", "GameActivity")
        assertTrue(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }

    @Test
    fun `pausing the game hides even before another activity resumes`() {
        val state = GameForegroundState()
        state.onResumed("com.netease.sky", "GameActivity")
        state.onPaused("com.netease.sky", "GameActivity")

        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }

    @Test
    fun `a delayed pause from an old activity does not hide the resumed game activity`() {
        val state = GameForegroundState()
        state.onResumed("com.netease.sky", "LaunchActivity")
        state.onResumed("com.netease.sky", "GameActivity")
        state.onPaused("com.netease.sky", "LaunchActivity")
        state.onPaused("com.android.settings", "Settings")

        assertTrue(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }

    @Test
    fun `verified channel clients obey foreground permission and screen requirements`() {
        for (packageName in listOf(
            "com.netease.sky.huawei",
            "com.netease.sky.vivo",
            "com.netease.sky.bilibili",
            "com.netease.sky.aligames",
            "com.tencent.tmgp.eyou.eygy",
        )) {
            val state = GameForegroundState()
            state.onResumed(packageName, "GameActivity")
            assertTrue(packageName, state.shouldShow(usageAccessGranted = true, screenAvailable = true))
            assertFalse(packageName, state.shouldShow(usageAccessGranted = false, screenAvailable = true))
            assertFalse(packageName, state.shouldShow(usageAccessGranted = true, screenAvailable = false))

            state.onPaused(packageName, "GameActivity")
            assertFalse(packageName, state.shouldShow(usageAccessGranted = true, screenAvailable = true))
        }
    }

    @Test
    fun `lookalike packages do not inherit a previously visible game`() {
        for (packageName in listOf(
            "com.netease.sky.huawei.clone",
            "com.tencent.tmgp.eyou.eygy.clone",
        )) {
            val state = GameForegroundState()
            state.onResumed("com.netease.sky", "GameActivity")
            state.onResumed(packageName, "GameActivity")

            assertFalse(packageName, state.shouldShow(usageAccessGranted = true, screenAvailable = true))
        }
    }

    @Test
    fun `international sky and other apps do not enable the overlay`() {
        val state = GameForegroundState()
        state.onResumed("com.netease.sky", "GameActivity")
        state.onResumed("com.tgc.sky.android", "GameActivity")
        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = true))

        state.onResumed("com.nankki.skydaily", "MainActivity")
        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }

    @Test
    fun `missing access or an unavailable display hides a remembered foreground game`() {
        val state = GameForegroundState()
        state.onResumed("com.netease.sky", "GameActivity")

        assertFalse(state.shouldShow(usageAccessGranted = false, screenAvailable = true))
        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = false))
        assertTrue(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }

    @Test
    fun `unknown foreground state stays hidden until a game resume is observed`() {
        val state = GameForegroundState()
        state.onPaused("com.netease.sky", "GameActivity")

        assertFalse(state.shouldShow(usageAccessGranted = true, screenAvailable = true))
    }
}
