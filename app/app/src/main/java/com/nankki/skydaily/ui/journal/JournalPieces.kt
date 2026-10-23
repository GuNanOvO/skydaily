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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.nankki.skydaily.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.TextStyle
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
import com.nankki.skydaily.ui.theme.LightPalette

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
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up_right),
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(12.dp),
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

internal val TaskNoteTextStyle = TextStyle(
    fontSize = 18.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.Medium,
)

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
    textLines: Int = 2,
) {
    CompositionLocalProvider(LocalPaperPalette provides LightPalette) {
        val palette = LocalPaperPalette.current
        val noteColor = palette.noteColors[index % palette.noteColors.size]
        Box(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .hardShadow(palette.shadowStrong, dx = 1.dp, dy = 2.dp)
                    .background(noteColor, RoundedCornerShape(1.dp))
                    .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                    .clickable(
                        enabled = detail != null,
                        onClickLabel = "查看任务 ${task.number} 指南",
                        onClick = onOpenGuide,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "任务 ${task.number.toString().padStart(2, '0')}",
                        color = palette.muted,
                        fontSize = 11.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = task.text,
                        color = if (done) palette.muted else palette.ink,
                        style = TaskNoteTextStyle,
                        minLines = textLines,
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (editable) {
                    PaperCheckbox(checked = done, onCheckedChange = onToggle)
                } else {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Text("只读", color = palette.muted, fontSize = 11.sp)
                    }
                }
            }
            Tape(
                modifier = Modifier.align(Alignment.TopCenter).offset(x = tapeOffset(index)),
                width = 46.dp,
                height = 12.dp,
                rotation = if (index % 2 == 0) -4f else 4f,
                color = palette.noteTapes[index % palette.noteTapes.size],
                edge = palette.noteAccents[index % palette.noteAccents.size].copy(alpha = 0.45f),
            )
        }
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
