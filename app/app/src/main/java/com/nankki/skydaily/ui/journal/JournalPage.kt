package com.nankki.skydaily.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText
import com.nankki.skydaily.ui.paper.NotebookHeader
import com.nankki.skydaily.ui.paper.NotebookSection

@Composable
fun JournalPage(
    state: JournalUiState,
    onRefresh: () -> Unit,
    onToggleTask: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    archived: Boolean = false,
    darkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    headerDate: String? = null,
    guideSession: SeasonGuideSession? = null,
) {
    val envelope = state.envelope
    val data = envelope?.data
    val tasks = data?.tasks.orEmpty()
    var guide by remember(envelope?.date) { mutableStateOf<TaskDetail?>(null) }
    var lightbox by remember(envelope?.date) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val updatedAt = remember(state.fetchedAt, state.today, archived) {
        state.fetchedAt.takeIf { it > 0 && !archived }?.let { timestamp ->
            val date = BeijingClock.updatedDate(timestamp)
            val day = if (date == state.today) "" else "${date.takeLast(5)} "
            "更新于 $day${BeijingClock.updatedAt(timestamp)}"
        }
    }
    val editable = envelope != null && JournalFormat.canEdit(envelope.date, state.today)
    if (onClose == null) {
        BackHandler(enabled = guide != null || lightbox != null) {
            if (lightbox != null) lightbox = null else guide = null
        }
    }
    Box(modifier.fillMaxSize()) {
        PaperScroll {
            PaperBlock {
                NotebookHeader(
                    title = if (archived) "历史记录" else "光遇每日任务",
                    subtitle = if (archived) "当日任务与资讯" else "每日任务 · 季节蜡烛",
                    updatedAt = updatedAt,
                    date = when {
                        onClose != null -> null
                        archived -> headerDate ?: envelope?.date
                        else -> envelope?.date ?: state.today
                    },
                    onRefresh = if (onClose == null) onRefresh else null,
                    refreshing = state.loading || state.refreshing,
                    darkTheme = darkTheme,
                    onToggleTheme = onToggleTheme,
                )
            }
            if (onClose != null || onOpenApp != null) {
                PaperBlock {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onClose?.let { PaperButton("关闭", it) }
                        onOpenApp?.let { PaperButton("打开应用", it) }
                    }
                }
            }
            DataStatus(state, archived)
            if (onClose == null && tasks.isNotEmpty()) {
                NotebookSection("每日任务", R.drawable.sky_jelly_handdrawn, stickerInset = 64.dp)
            }
            if (tasks.isEmpty()) {
                PaperBlock {
                    when {
                        state.loading -> EmptyTasksCard(text = "正在读取任务…")
                        envelope == null -> ErrorCard(state.message ?: "请检查网络后重试", onRefresh)
                        else -> EmptyTasksCard()
                    }
                }
            } else {
                PaperBlock {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val textMeasurer = rememberTextMeasurer()
                        val textWidth = with(LocalDensity.current) {
                            (maxWidth - 80.dp).roundToPx().coerceAtLeast(1)
                        }
                        val textLines = remember(tasks, textWidth, textMeasurer) {
                            tasks.maxOf { task ->
                                textMeasurer.measure(
                                    text = task.text,
                                    style = TaskNoteTextStyle,
                                    constraints = Constraints(maxWidth = textWidth),
                                ).lineCount
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tasks.forEachIndexed { index, task ->
                                val detail = JournalFormat.taskDetailFor(task.keyword, data?.taskDetails.orEmpty())
                                TaskNote(
                                    task = task,
                                    detail = detail,
                                    done = task.number in state.done,
                                    editable = editable,
                                    index = index,
                                    onToggle = { onToggleTask(task.number, it) },
                                    onOpenGuide = { guide = detail },
                                    textLines = textLines,
                                )
                            }
                        }
                    }
                }
            }
            if (data != null) {
                if (archived) {
                    data.seasonCandleMap?.let { RuledText("季节蜡烛所在地图：$it") }
                    InformationContent(
                        state = state,
                        onOpenImages = { images -> lightbox = images to 0 },
                        onOpenNotice = {},
                    )
                } else {
                    NotebookSection(
                        data.seasonCandleMap?.trim()?.takeIf { it.isNotEmpty() }
                            ?.let { "季节蜡烛：今日$it" } ?: "季节蜡烛",
                        R.drawable.sky_candles_handdrawn,
                    )
                    PaperBlock {
                        SeasonGuide(
                            data = data,
                            date = envelope.date,
                            session = guideSession,
                        )
                    }
                }
            }
        }
        guide?.let { detail ->
            GuideSheet(
                detail = detail,
                baseUrl = AppConfig.SITE_URL,
                onClose = { guide = null },
                onOpenImage = { images, index -> lightbox = images to index },
            )
        }
        lightbox?.let { (images, index) ->
            ImageLightbox(AppConfig.SITE_URL, images, index, onClose = { lightbox = null })
        }
    }
}
