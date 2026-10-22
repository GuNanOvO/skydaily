package com.nankki.skydaily.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BallGeometryTest {
    @Test
    fun `snaps to the nearest horizontal edge`() {
        assertEquals(0 to 100, BallGeometry.snap(40, 100, 1080, 56))
        assertEquals(1024 to 100, BallGeometry.snap(900, 100, 1080, 56))
    }

    @Test
    fun `keeps the ball inside the screen vertically`() {
        assertEquals(0 to 0, BallGeometry.snap(-20, -20, 1080, 56, screenHeight = 1920, viewHeight = 56))
        assertEquals(1024 to 1864, BallGeometry.snap(2000, 2000, 1080, 56, screenHeight = 1920, viewHeight = 56))
    }

    @Test
    fun `fades after idle`() {
        assertEquals(1f, BallGeometry.idleAlpha(1_000, 0, idleAfterMs = 4_000))
        assertEquals(0.55f, BallGeometry.idleAlpha(5_000, 0, idleAfterMs = 4_000))
    }
}
