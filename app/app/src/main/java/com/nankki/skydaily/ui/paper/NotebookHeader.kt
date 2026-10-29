package com.nankki.skydaily.ui.paper

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun NotebookHeader(
    title: String,
    date: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    darkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    @DrawableRes sticker: Int = R.drawable.sky_cape_handdrawn,
    subtitle: String? = null,
    updatedAt: String? = null,
    overlayRunning: Boolean = false,
    onOverlayAction: (() -> Unit)? = null,
) {
    val palette = LocalPaperPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuledText(
                            title,
                            fontSize = if (compact) 20.sp else 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false).alignToPaperRule(),
                        )
                        Box(Modifier.size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            Sticker(
                                sticker,
                                size = 48.dp,
                                rotation = -8f,
                                modifier = Modifier.requiredSize(48.dp),
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        subtitle?.let {
                            Text(
                                it,
                                modifier = Modifier.alignToPaperRule(),
                                color = palette.muted,
                                fontSize = 12.sp,
                                lineHeight = 32.sp,
                            )
                        }
                        updatedAt?.let {
                            Text(
                                it,
                                modifier = Modifier.alignToPaperRule(),
                                color = palette.muted,
                                fontSize = 11.sp,
                                lineHeight = 32.sp,
                            )
                        }
                    }
                }
                if (date != null || onToggleTheme != null) {
                    // 主题开关与日期卡并排：日期卡下方留空会把页头撑高整整一行。
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        onToggleTheme?.let { toggle ->
                            Box(
                                Modifier.size(44.dp)
                                    .semantics { contentDescription = if (darkTheme) "切换浅色" else "切换深色" }
                                    .clickable(role = Role.Button, onClick = toggle),
                                contentAlignment = Alignment.Center,
                            ) {
                                Sticker(
                                    if (darkTheme) R.drawable.theme_sun else R.drawable.theme_moon,
                                    size = 48.dp,
                                    modifier = Modifier.requiredSize(48.dp),
                                    rotation = if (darkTheme) 8f else -8f,
                                )
                            }
                        }
                        date?.let { DateSticker(it) }
                    }
                }
            }
            if (onRefresh != null || onOverlayAction != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    onRefresh?.let { refresh ->
                        PaperButton(
                            text = if (refreshing) "刷新中" else "刷新",
                            onClick = refresh,
                            enabled = !refreshing,
                        )
                    }
                    onOverlayAction?.let { toggle ->
                        PaperButton(
                            text = if (overlayRunning) "关闭悬浮窗" else "开启悬浮窗",
                            onClick = toggle,
                            tone = if (overlayRunning) palette.tabColors[2] else null,
                        )
                    }
                }
            }
        }
    }
}

/** 首行基线落在纸面的 28sp 横线上，换行仍按整行占位。 */
private fun Modifier.alignToPaperRule(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val firstBaseline = placeable[FirstBaseline]
    val pitch = 32.sp.roundToPx()
    val rows = ((placeable[LastBaseline] - firstBaseline).toFloat() / pitch).roundToInt() + 1
    val height = constraints.constrainHeight(rows * pitch)
    val offset = 28.sp.roundToPx() - firstBaseline
    layout(placeable.width, height) { placeable.placeRelative(0, offset) }
}

@Composable
private fun DateSticker(date: String) {
    val palette = LocalPaperPalette.current
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
    val scale = LocalDensity.current.fontScale
    Column(
        Modifier.width(62.dp * scale).rotate(2f)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${parsed.year} / ${parsed.monthValue.toString().padStart(2, '0')}", color = palette.muted, fontSize = 9.sp, lineHeight = 14.sp)
        Text(parsed.dayOfMonth.toString().padStart(2, '0'), color = palette.ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
        Text(BeijingClock.weekday(date), color = palette.muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
fun NotebookSection(title: String, @DrawableRes sticker: Int, stickerInset: Dp = 0.dp) {
    PaperBlock {
        Row(
            Modifier.fillMaxWidth().heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuledText(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.padding(end = stickerInset).size(width = 48.dp, height = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Sticker(
                    sticker,
                    size = 48.dp,
                    rotation = -6f,
                    modifier = Modifier.requiredSize(48.dp),
                )
            }
        }
    }
}
