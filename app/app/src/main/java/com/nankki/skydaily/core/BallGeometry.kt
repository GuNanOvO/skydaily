package com.nankki.skydaily.core

import kotlin.math.max
import kotlin.math.min

/** 悬浮球的纯计算部分：吸附、垂直夹紧、闲置透明度。 */
object BallGeometry {
    fun snap(
        x: Int,
        y: Int,
        screenWidth: Int,
        viewWidth: Int,
        margin: Int = 0,
        screenHeight: Int = Int.MAX_VALUE,
        viewHeight: Int = 0,
    ): Pair<Int, Int> {
        val rightEdge = max(margin, screenWidth - viewWidth - margin)
        val snappedX = if (x + viewWidth / 2 < screenWidth / 2) margin else rightEdge
        val maxY = if (screenHeight == Int.MAX_VALUE) Int.MAX_VALUE else max(margin, screenHeight - viewHeight - margin)
        val snappedY = min(max(y, margin), maxY)
        return snappedX to snappedY
    }

    fun idleAlpha(
        nowMillis: Long,
        lastTouchMillis: Long,
        idleAfterMs: Long = 4_000L,
        idleAlpha: Float = 0.55f,
    ): Float = if (nowMillis - lastTouchMillis >= idleAfterMs) idleAlpha else 1f

    fun clamp01(value: Float): Float = min(1f, max(0f, value))
}
