package com.nankki.skydaily.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nankki.skydaily.ui.paper.StarMark
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlin.math.min

/** 悬浮球：纸片圆盘 + 手绘星 + 今日完成进度环。 */
@Composable
fun FloatingBall(
    progress: Float,
    alpha: Float,
    pressed: Boolean,
) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    val scale = if (pressed) 0.94f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .hardShadow(palette.shadowStrong, corner = 28.dp, dx = 1.dp, dy = 2.dp)
                .background(palette.paper, CircleShape)
                .border(1.dp, palette.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = palette.rule,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke),
                )
                val sweep = 360f * min(1f, progress.coerceAtLeast(0f))
                if (sweep > 0f) {
                    drawArc(
                        color = palette.accent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            StarMark(Modifier.size(26.dp))
        }
    }
}
