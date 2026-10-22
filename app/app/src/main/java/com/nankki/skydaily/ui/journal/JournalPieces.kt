package com.nankki.skydaily.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.nankki.skydaily.data.Media
import com.nankki.skydaily.data.Task
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.PaperCheckbox
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val palette = LocalPaperPalette.current
    if (url.isBlank()) {
        ImageFallback(text = "暂无图片", modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("载入中…", color = palette.muted, fontSize = 11.sp)
            }
        },
        error = {
            ImageFallback(text = "图片暂不可用", modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
private fun ImageFallback(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = modifier.height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = palette.muted, fontSize = 11.sp)
    }
}

/** 照片便签：纸框 + 胶带 + 图注，点开大图。 */
@Composable
fun PhotoNote(
    baseUrl: String,
    images: List<String>,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 176.dp,
    previewText: String? = null,
) {
    val palette = LocalPaperPalette.current
    val first = images.firstOrNull().orEmpty()
    val url = if (first.isBlank()) "" else Media.absolute(baseUrl, Media.thumb(first))
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.photoPaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            if (!previewText.isNullOrBlank()) {
                Text(
                    text = previewText,
                    color = palette.ink,
                    fontSize = 13.sp,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(1.dp)),
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = caption,
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 62.dp,
            height = 16.dp,
            rotation = -5f,
        )
    }
}

@Composable
fun TaskNote(
    task: Task,
    detail: TaskDetail?,
    done: Boolean,
    editable: Boolean,
    index: Int,
    onToggle: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    val noteColor = palette.noteColors[index % palette.noteColors.size]
    val brief = JournalFormat.briefSections(detail)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.992f else 1f,
        label = "task-press",
    )
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(noteColor, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                .drawBehind {
                    if (palette.dark) {
                        drawRect(
                            color = palette.noteAccents[index % palette.noteAccents.size],
                            size = Size(3.dp.toPx(), size.height),
                        )
                    }
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = detail != null,
                    onClick = onOpenGuide,
                )
                .padding(
                    start = if (compact) 12.dp else 16.dp,
                    end = if (compact) 12.dp else 16.dp,
                    top = if (compact) 10.dp else 14.dp,
                    bottom = if (compact) 10.dp else 12.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "任务 ${task.number.toString().padStart(2, '0')}",
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.weight(1f))
                if (editable) {
                    PaperCheckbox(checked = done, onCheckedChange = onToggle)
                } else {
                    Text(if (done) "已完成" else "只读", color = palette.muted, fontSize = 11.sp)
                }
            }
            Text(
                text = task.text,
                color = if (done) palette.muted else palette.ink,
                fontSize = if (compact) 15.sp else 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            if (brief.isEmpty()) {
                Text(
                    text = "暂无更多指引",
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            } else {
                brief.forEach { (label, text) ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = label,
                            color = palette.muted,
                            fontSize = 11.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.width(if (compact) 30.dp else 34.dp),
                        )
                        Text(
                            text = text,
                            color = palette.ink.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            PaperDivider(Modifier.padding(top = 6.dp, bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (detail != null) "查看指南 ↗" else "暂无指南",
                    color = if (detail != null) palette.muted else palette.muted.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = done,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 1.3f, animationSpec = tween(180)),
                    exit = fadeOut(tween(120)),
                ) {
                    Text(
                        text = "完成",
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .rotate(-10f)
                            .border(1.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Tape(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = tapeOffset(index)),
            width = if (compact) 46.dp else 52.dp,
            height = 14.dp,
            rotation = if (index % 2 == 0) -4f else 4f,
            color = palette.noteTapes[index % palette.noteTapes.size],
            edge = palette.noteAccents[index % palette.noteAccents.size].copy(alpha = 0.45f),
        )
    }
}

private fun tapeOffset(index: Int): Dp = when (index % 4) {
    0 -> (-10).dp
    1 -> 8.dp
    2 -> (-4).dp
    else -> 14.dp
}

@Composable
fun EmptyTasksCard(modifier: Modifier = Modifier, text: String = "今日任务尚未更新") {
    val palette = LocalPaperPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(16.dp),
    ) {
        Text(text, color = palette.muted, fontSize = 12.sp, lineHeight = 24.sp)
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(16.dp),
    ) {
        Text("还没有读到今日数据", color = palette.ink, fontSize = 14.sp, lineHeight = 24.sp)
        Text(message, color = palette.muted, fontSize = 12.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(8.dp))
        PaperButton("重试", onClick = onRetry)
    }
}
