package com.nankki.skydaily.ui.journal

/** 灯箱当前的缩放与平移；[scale] 为 1 时图片铺满可视区且不可平移。 */
data class ZoomState(
    val scale: Float = ZoomGeometry.MinScale,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    val zoomed: Boolean get() = scale > ZoomGeometry.MinScale + ZoomGeometry.ZoomEpsilon
}

/** 图片灯箱的纯计算部分：缩放范围、双击切换与平移边界。 */
object ZoomGeometry {
    const val MinScale = 1f
    const val MaxScale = 5f
    const val DoubleTapScale = 2.5f

    /** 判定“仍按原尺寸显示”的浮点容差。 */
    const val ZoomEpsilon = 0.01f

    fun clampScale(scale: Float): Float = scale.coerceIn(MinScale, MaxScale)

    /** 双击在铺满与放大之间切换；部分放大的状态直接回到铺满。 */
    fun nextScale(scale: Float): Float = if (scale > MinScale + ZoomEpsilon) MinScale else DoubleTapScale

    /**
     * 平移上限：放大 [scale] 倍后内容从视口两侧各溢出多少。
     * 按原尺寸显示时为 0，因此不会拖动一张铺满的图。
     */
    fun maxOffset(scale: Float, viewportWidth: Float, viewportHeight: Float): Pair<Float, Float> {
        val overflow = ((clampScale(scale) - MinScale) / 2f).coerceAtLeast(0f)
        return overflow * viewportWidth.coerceAtLeast(0f) to overflow * viewportHeight.coerceAtLeast(0f)
    }

    fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Pair<Float, Float> {
        val (maxX, maxY) = maxOffset(scale, viewportWidth, viewportHeight)
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }
}
