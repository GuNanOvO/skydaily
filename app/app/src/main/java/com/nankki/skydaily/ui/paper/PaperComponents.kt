package com.nankki.skydaily.ui.paper

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlin.math.cos
import kotlin.math.sin

/** 纸面投影：小偏移、近乎实心的接触阴影。 */
fun Modifier.hardShadow(
    color: Color,
    corner: Dp = 2.dp,
    dx: Dp = 1.dp,
    dy: Dp = 2.dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(dx.toPx(), dy.toPx()),
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(corner.toPx()),
    )
}

@Composable
fun rememberTextureBrush(): Brush {
    val context = LocalContext.current
    val bitmap = remember(context) {
        checkNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.notebook_paper_texture))
    }
    val tile = with(LocalDensity.current) { 256.dp.toPx() }
    return remember(bitmap, tile) {
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val scaleX = tile / bitmap.width
        val scaleY = tile / bitmap.height
        shader.setLocalMatrix(Matrix().apply { setScale(scaleX, scaleY) })
        ShaderBrush(shader)
    }
}

/** 站点同款纸面：纹理 + 32dp 横线 + 左侧红线 + 装订孔。 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    texture: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPaperPalette.current
    val brush = if (texture) rememberTextureBrush() else null
    val density = LocalDensity.current
    val corner = with(density) { 2.dp.toPx() }
    Box(
        modifier = modifier.drawBehind {
            drawRoundRect(
                color = palette.shadow,
                topLeft = Offset(0f, 3.dp.toPx()),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(corner),
            )
            drawRoundRect(color = palette.paper, cornerRadius = CornerRadius(corner))
            if (brush != null) {
                if (palette.dark) drawRect(brush, alpha = 0.10f) else drawRect(brush)
            }
            drawRoundRect(
                color = palette.outline,
                cornerRadius = CornerRadius(corner),
                style = Stroke(width = 1.dp.toPx()),
            )

            val top = 16.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            var y = top + 30.dp.toPx()
            while (y < bottom) {
                drawRect(
                    color = palette.rule,
                    topLeft = Offset(16.dp.toPx(), y),
                    size = Size(size.width - 32.dp.toPx(), 2.dp.toPx()),
                )
                y += 32.dp.toPx()
            }

            val marginX = 30.dp.toPx()
            drawLine(
                color = palette.margin,
                start = Offset(marginX, top),
                end = Offset(marginX, bottom),
                strokeWidth = 1.dp.toPx(),
            )

            var holeY = top + 40.dp.toPx()
            while (holeY < bottom) {
                drawCircle(palette.bindingOuter, radius = 4.dp.toPx(), center = Offset(10.dp.toPx(), holeY))
                drawCircle(palette.bindingHole, radius = 2.2.dp.toPx(), center = Offset(10.dp.toPx(), holeY))
                holeY += 80.dp.toPx()
            }
        },
        content = content,
    )
}

/** 手绘贴纸：保留原色，带一层沿轮廓的轻微投影。 */
@Composable
fun Sticker(
    @DrawableRes res: Int,
    size: Dp,
    rotation: Float = 0f,
    modifier: Modifier = Modifier,
    shadow: Boolean = true,
) {
    val palette = LocalPaperPalette.current
    val painter = painterResource(res)
    Box(modifier = modifier.size(size).graphicsLayer { rotationZ = rotation }) {
        if (shadow) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(palette.shadowStrong, BlendMode.SrcIn),
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 0.5.dp, y = 1.dp),
            )
        }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
fun Tape(
    modifier: Modifier = Modifier,
    width: Dp = 58.dp,
    height: Dp = 18.dp,
    rotation: Float = -4f,
    color: Color? = null,
    edge: Color? = null,
) {
    val palette = LocalPaperPalette.current
    val fill = color ?: palette.tape
    val border = edge ?: palette.tapeEdge
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer { rotationZ = rotation }
            .drawBehind {
                drawRect(fill)
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                drawLine(
                    color = border,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
                drawLine(
                    color = border,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
            },
    )
}

@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    height: Dp = 28.dp,
    fontSize: Int = 12,
    enabled: Boolean = true,
) {
    val palette = LocalPaperPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "paper-button")
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(tone ?: palette.paper, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) palette.ink else palette.muted,
            fontSize = fontSize.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun PaperCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val scale by animateFloatAsState(if (checked) 1.04f else 1f, label = "paper-check")
    Box(
        modifier = modifier
            .size(40.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .size(24.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            val stroke = 1.5.dp.toPx()
            if (checked) {
                drawRoundRect(
                    color = palette.accent,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                val path = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.70f)
                    lineTo(size.width * 0.78f, size.height * 0.32f)
                }
                drawPath(
                    path = path,
                    color = palette.paper,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            } else {
                drawRoundRect(
                    color = palette.checkInk.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

@Composable
fun PaperDivider(modifier: Modifier = Modifier) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.outline.copy(alpha = 0.5f)),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    @DrawableRes sticker: Int? = null,
    stickerSize: Dp = 44.dp,
    stickerRotation: Float = 0f,
    compact: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    Box(modifier = modifier.fillMaxWidth().height(if (compact) 28.dp else 32.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (sticker != null) stickerSize + 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = palette.ink,
                fontSize = if (compact) 17.sp else 20.sp,
                lineHeight = if (compact) 28.sp else 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        if (sticker != null) {
            Sticker(
                res = sticker,
                size = stickerSize,
                rotation = stickerRotation,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 2.dp, y = (-8).dp),
            )
        }
    }
}

@Composable
fun DateStamp(
    date: String,
    weekday: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    val month = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
        ?.let { "%04d / %02d".format(it.year, it.monthValue) }
        ?: ""
    val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
        ?.dayOfMonth?.toString()?.padStart(2, '0') ?: "--"
    Column(
        modifier = modifier
            .width(if (compact) 58.dp else 66.dp)
            .rotate(2f)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.paper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(horizontal = 4.dp, vertical = if (compact) 3.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(month, color = palette.muted, fontSize = if (compact) 9.sp else 10.sp, lineHeight = 13.sp, letterSpacing = 0.3.sp, maxLines = 1)
        Text(day, color = palette.ink, fontSize = if (compact) 18.sp else 20.sp, lineHeight = if (compact) 24.sp else 26.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(weekday, color = palette.muted, fontSize = if (compact) 9.sp else 10.sp, lineHeight = 13.sp, maxLines = 1)
    }
}

data class JumpTab(val key: String, val label: String, val count: Int = 0)

@Composable
fun JumpTabs(
    tabs: List<JumpTab>,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val color = palette.tabColors[index % palette.tabColors.size]
            Box(
                modifier = Modifier
                    .height(if (compact) 30.dp else 32.dp)
                    .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                    .background(color, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .clickable { onJump(tab.key) }
                    .padding(horizontal = if (compact) 6.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (tab.count > 0) "${tab.label} ${tab.count}" else tab.label,
                    color = palette.tabInk,
                    fontSize = if (compact) 10.sp else 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 悬浮球上的手绘小星星：外圈墨线 + 内层金色。 */
@Composable
fun StarMark(modifier: Modifier = Modifier, tint: Color? = null) {
    val palette = LocalPaperPalette.current
    val stroke = tint ?: palette.ink
    androidx.compose.foundation.Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension * 0.32f
        val inner = outer * 0.46f
        val path = Path()
        repeat(10) { index ->
            val radius = if (index % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + index * 36).toDouble())
            val point = Offset(
                center.x + (cos(angle) * radius).toFloat(),
                center.y + (sin(angle) * radius).toFloat(),
            )
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color = Color(0xFFECD69B))
        drawPath(path, color = stroke.copy(alpha = 0.7f), style = Stroke(width = 1.2.dp.toPx()))
    }
}
