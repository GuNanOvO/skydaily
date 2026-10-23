package com.nankki.skydaily.core

import kotlin.math.max
import kotlin.math.min

/**
 * 一个可用于摆放悬浮球的矩形区域，右/下边界为开区间。
 * 通常来自安全显示区（扣除系统栏与刘海），不包含球自身尺寸。
 */
data class BallArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = max(0, right - left)
    val height: Int get() = max(0, bottom - top)
}

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

    /**
     * 把球吸附到 [area] 的最近左右边缘并夹在上下边界内。
     * 区域可能带偏移（状态栏、刘海、折叠屏分屏），因此不能直接复用从 0 开始的 [snap]。
     */
    fun snapInArea(
        x: Int,
        y: Int,
        area: BallArea,
        viewWidth: Int,
        viewHeight: Int,
        margin: Int = 0,
    ): Pair<Int, Int> {
        val minX = area.left + margin
        val maxX = max(minX, area.right - margin - viewWidth)
        val minY = area.top + margin
        val maxY = max(minY, area.bottom - margin - viewHeight)
        val centerX = area.left + area.width / 2
        val snappedX = if (x + viewWidth / 2 < centerX) minX else maxX
        val snappedY = min(max(y, minY), maxY)
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
