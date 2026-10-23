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

    @Test
    fun `snaps inside an offset safe area`() {
        val area = BallArea(left = 100, top = 200, right = 1100, bottom = 2200)
        assertEquals(102 to 202, BallGeometry.snapInArea(400, 100, area, 56, 56, margin = 2))
        assertEquals(1042 to 2142, BallGeometry.snapInArea(900, 5000, area, 56, 56, margin = 2))
    }

    @Test
    fun `keeps the ball visible when the area is smaller than it`() {
        val area = BallArea(left = 0, top = 0, right = 50, bottom = 50)
        assertEquals(2 to 2, BallGeometry.snapInArea(400, 400, area, 60, 60, margin = 2))
    }
}
