package com.nankki.skydaily.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomGeometryTest {

    @Test
    fun scalesStayInsideTheAllowedRange() {
        assertEquals(ZoomGeometry.MinScale, ZoomGeometry.clampScale(0.2f), 0.0001f)
        assertEquals(ZoomGeometry.MaxScale, ZoomGeometry.clampScale(12f), 0.0001f)
        assertEquals(2f, ZoomGeometry.clampScale(2f), 0.0001f)
    }

    @Test
    fun doubleTapTogglesBetweenFitAndEnlarged() {
        assertEquals(ZoomGeometry.DoubleTapScale, ZoomGeometry.nextScale(ZoomGeometry.MinScale), 0.0001f)
        assertEquals(ZoomGeometry.MinScale, ZoomGeometry.nextScale(ZoomGeometry.DoubleTapScale), 0.0001f)
        assertEquals(ZoomGeometry.MinScale, ZoomGeometry.nextScale(1.4f), 0.0001f)
    }

    @Test
    fun fittedImageCannotBePanned() {
        val (maxX, maxY) = ZoomGeometry.maxOffset(ZoomGeometry.MinScale, 400f, 800f)
        assertEquals(0f, maxX, 0.0001f)
        assertEquals(0f, maxY, 0.0001f)
    }

    @Test
    fun panLimitGrowsWithScale() {
        val (maxX, maxY) = ZoomGeometry.maxOffset(3f, 400f, 800f)
        assertEquals(400f, maxX, 0.0001f)
        assertEquals(800f, maxY, 0.0001f)
    }

    @Test
    fun offsetIsClampedToThePanLimit() {
        val (x, y) = ZoomGeometry.clampOffset(900f, -900f, 3f, 400f, 800f)
        assertEquals(400f, x, 0.0001f)
        assertEquals(-800f, y, 0.0001f)
    }

    @Test
    fun offsetInsideTheLimitIsKept() {
        val (x, y) = ZoomGeometry.clampOffset(120f, -80f, 3f, 400f, 800f)
        assertEquals(120f, x, 0.0001f)
        assertEquals(-80f, y, 0.0001f)
    }
}
