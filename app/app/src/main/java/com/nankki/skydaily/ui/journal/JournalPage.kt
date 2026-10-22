package com.nankki.skydaily.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText

@Composable
fun JournalPage(
    state: JournalUiState,
    onRefresh: () -> Unit,
    onToggleTask: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    archived: Boolean = false,
) {
    val envelope = state.envelope
    val data = envelope?.data
    val tasks = data?.tasks.orEmpty()
    var guide by remember(envelope?.date) { mutableStateOf<TaskDetail?>(null) }
    var lightbox by remember(envelope?.date) { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var fullGuideUrl by remember(envelope?.date) { mutableStateOf<String?>(null) }
    val editable = envelope != null && JournalFormat.canEdit(envelope.date, state.today)
    if (onClose == null) {
        BackHandler(enabled = guide != null || lightbox != null || fullGuideUrl != null) {
            when {
                lightbox != null -> lightbox = null
                guide != null -> guide = null
                else -> fullGuideUrl = null
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val compact = maxWidth < 420.dp || maxHeight < 600.dp
        val columns = when {
            maxWidth >= (700 * fontScale).dp && maxHeight < 450.dp -> 4
            maxWidth >= (300 * fontScale).dp -> 2
            else -> 1
        }
        PaperScroll {
            if (onClose != null || onOpenApp != null) {
                PaperBlock {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onClose?.let { PaperButton("关闭", it, height = 40.dp) }
                        onOpenApp?.let { PaperButton("打开应用", it, height = 40.dp) }
                    }
                }
            }
            DataStatus(state, archived)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tasks.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { task ->
                                    val detail = JournalFormat.taskDetailFor(task.keyword, data?.taskDetails.orEmpty())
                                    TaskNote(
                                        task = task,
                                        detail = detail,
                                        done = task.number in state.done,
                                        editable = editable,
                                        index = tasks.indexOf(task),
                                        onToggle = { onToggleTask(task.number, it) },
                                        onOpenGuide = { guide = detail },
                                        modifier = Modifier.weight(1f),
                                        compact = compact,
                                    )
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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
                    RuledText("季节蜡烛", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    PaperBlock {
                        SeasonGuide(
                            data = data,
                            onOpenGuideFullscreen = if (onClose == null) ({ url -> fullGuideUrl = url }) else null,
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
        fullGuideUrl?.let { url ->
            SeasonGuideFullscreen(url = url, onClose = { fullGuideUrl = null })
        }
    }
}
