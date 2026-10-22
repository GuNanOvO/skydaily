package com.nankki.skydaily.ui.guides

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.Task
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.journal.CandleCard
import com.nankki.skydaily.ui.journal.EmptyTasksCard
import com.nankki.skydaily.ui.journal.ErrorCard
import com.nankki.skydaily.ui.journal.GuideSheet
import com.nankki.skydaily.ui.journal.ImageLightbox
import com.nankki.skydaily.ui.journal.JournalFormat
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.paper.DateStamp
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperSheet
import com.nankki.skydaily.ui.paper.SectionHeader
import com.nankki.skydaily.ui.paper.Sticker
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

/** 指南分页：按今日任务列出可读指南，并保留蜡烛位置。 */
@Composable
fun GuidesPage(
    state: JournalUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val envelope = state.envelope
    val data = envelope?.data
    val tasks = data?.tasks.orEmpty()
    val details = data?.taskDetails.orEmpty()
    val candles = data?.candles.orEmpty()
    var guide by remember { mutableStateOf<TaskDetail?>(null) }
    var lightbox by remember { mutableStateOf<Triple<List<String>, Int, Int>?>(null) }
    val date = envelope?.date ?: state.today

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 360.dp || maxHeight < 560.dp
        PaperSheet(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (compact) 38.dp else 46.dp,
                    end = if (compact) 12.dp else 18.dp,
                    top = if (compact) 10.dp else 14.dp,
                    bottom = 22.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "今日指南",
                                    color = palette.ink,
                                    fontSize = if (compact) 18.sp else 20.sp,
                                    lineHeight = 30.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                )
                                Sticker(
                                    res = R.drawable.sky_cape_handdrawn,
                                    size = if (compact) 32.dp else 38.dp,
                                    rotation = -8f,
                                    modifier = Modifier.offset(x = 2.dp, y = (-2).dp),
                                )
                            }
                            Text(
                                text = if (tasks.isNotEmpty()) "任务攻略与蜡烛位置" else "等待今日数据",
                                color = palette.muted,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        DateStamp(date = date, weekday = BeijingClock.weekday(date), compact = compact)
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PaperButton(
                            text = if (state.refreshing) "刷新中" else "刷新",
                            onClick = onRefresh,
                            enabled = !state.refreshing,
                            height = if (compact) 30.dp else 32.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "共 ${details.size} 条任务指南 · ${candles.size} 条蜡烛位置",
                            color = palette.muted,
                            fontSize = 10.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (envelope == null) {
                    item(key = "loading") {
                        if (state.loading) {
                            EmptyTasksCard(text = "正在读取今日指南…")
                        } else {
                            ErrorCard(state.message ?: "请检查网络后重试", onRetry = onRefresh)
                        }
                    }
                } else {
                    if (tasks.isNotEmpty()) {
                        item(key = "tasks-header") {
                            SectionHeader(
                                title = "任务指南",
                                sticker = R.drawable.sky_jelly_handdrawn,
                                stickerSize = if (compact) 34.dp else 40.dp,
                                stickerRotation = -6f,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        tasks.forEachIndexed { index, task ->
                            item(key = "guide-${task.number}") {
                                val detail = JournalFormat.taskDetailFor(task.keyword, details)
                                GuideEntryCard(
                                    task = task,
                                    detail = detail,
                                    index = index,
                                    compact = compact,
                                    onOpen = { detail?.let { guide = it } },
                                )
                            }
                        }
                    }
                    if (candles.isNotEmpty()) {
                        item(key = "candles-header") {
                            SectionHeader(
                                title = "蜡烛位置",
                                sticker = R.drawable.sky_candles_handdrawn,
                                stickerSize = if (compact) 34.dp else 40.dp,
                                stickerRotation = 5f,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        candles.forEach { candle ->
                            item(key = "candle-${candle.title}") {
                                CandleCard(
                                    detail = candle,
                                    baseUrl = AppConfig.SITE_URL,
                                    onOpen = { guide = candle },
                                    onOpenImage = { images, index ->
                                        lightbox = Triple(images, index, images.size)
                                    },
                                    compact = compact,
                                )
                            }
                        }
                    }
                    if (tasks.isEmpty() && candles.isEmpty()) {
                        item(key = "empty") { EmptyTasksCard(text = "今日还没有可读指南") }
                    }
                }

                item(key = "footer") {
                    Column(Modifier.padding(top = 8.dp)) {
                        PaperDivider()
                        Text(
                            text = "图文来源于游戏内「小精灵」，版权归原权利人所有。",
                            color = palette.muted,
                            fontSize = 11.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            guide?.let { detail ->
                GuideSheet(
                    detail = detail,
                    baseUrl = AppConfig.SITE_URL,
                    onClose = { guide = null },
                    onOpenImage = { images, index -> lightbox = Triple(images, index, images.size) },
                )
            }
            lightbox?.let { (images, index) ->
                ImageLightbox(
                    baseUrl = AppConfig.SITE_URL,
                    images = images,
                    initialIndex = index,
                    onClose = { lightbox = null },
                )
            }
        }
    }
}

@Composable
private fun GuideEntryCard(
    task: Task,
    detail: TaskDetail?,
    index: Int,
    compact: Boolean,
    onOpen: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    val noteColor = palette.noteColors[index % palette.noteColors.size]
    val brief = JournalFormat.briefSections(detail)
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(noteColor, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                .drawBehind {
                    if (palette.dark) {
                        drawRect(
                            color = palette.noteAccents[index % palette.noteAccents.size],
                            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                        )
                    }
                }
                .clickable(enabled = detail != null, onClick = onOpen)
                .padding(
                    start = if (compact) 12.dp else 16.dp,
                    end = if (compact) 12.dp else 16.dp,
                    top = 14.dp,
                    bottom = 12.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "任务 ${task.number.toString().padStart(2, '0')}",
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (detail == null) "暂无指南" else "查看指南 ↗",
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 20.sp,
                )
            }
            Text(
                text = detail?.title?.takeIf { it.isNotBlank() } ?: task.text,
                color = palette.ink,
                fontSize = if (compact) 15.sp else 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (brief.isNotEmpty()) {
                Text(
                    text = brief.first().second,
                    color = palette.ink.copy(alpha = 0.86f),
                    fontSize = 12.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = buildString {
                    append("${detail?.sections?.count { it.text.isNotBlank() } ?: 0} 段指引")
                    val images = detail?.images?.size ?: 0
                    if (images > 0) append(" · $images 张图")
                    if (!detail?.variants.isNullOrEmpty()) append(" · ${detail.variants.size} 种情况")
                },
                color = palette.muted,
                fontSize = 10.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = if (compact) 46.dp else 52.dp,
            height = 14.dp,
            rotation = if (index % 2 == 0) -4f else 4f,
            color = palette.noteTapes[index % palette.noteTapes.size],
            edge = palette.noteAccents[index % palette.noteAccents.size].copy(alpha = 0.45f),
        )
    }
}
