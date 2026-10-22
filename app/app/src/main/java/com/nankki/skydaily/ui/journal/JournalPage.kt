package com.nankki.skydaily.ui.journal

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.Task
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.DateStamp
import com.nankki.skydaily.ui.paper.JumpTab
import com.nankki.skydaily.ui.paper.JumpTabs
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperSheet
import com.nankki.skydaily.ui.paper.SectionHeader
import com.nankki.skydaily.ui.paper.Sticker
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlinx.coroutines.launch

private sealed interface JournalBlock {
    data object Header : JournalBlock
    data object Tabs : JournalBlock
    data object Overlay : JournalBlock
    data object TasksHeader : JournalBlock
    data class TaskRow(val task: Task) : JournalBlock
    data class TaskGrid(val tasks: List<Task>) : JournalBlock
    data object Weather : JournalBlock
    data object Calendar : JournalBlock
    data object Candles : JournalBlock
    data object Notice : JournalBlock
    data object Empty : JournalBlock
    data object Footer : JournalBlock
}

private data class ImageRequestState(val images: List<String>, val index: Int)

@Composable
fun JournalPage(
    state: JournalUiState,
    onRefresh: () -> Unit,
    onToggleTask: (Int, Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    overlayCard: (@Composable () -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    onClearCache: (() -> Unit)? = null,
    versionName: String = "0.1.0",
) {
    val palette = LocalPaperPalette.current
    val envelope = state.envelope
    val data = envelope?.data
    val tasks = data?.tasks.orEmpty()
    val details = data?.taskDetails.orEmpty()
    val weather = data?.weather
    val calendar = data?.calendar
    val candles = data?.candles.orEmpty()
    val notice = state.notice
    val editable = envelope != null && JournalFormat.canEdit(envelope.date, state.today)
    val stale = envelope != null && JournalFormat.staleLabel(envelope.date, state.today) != null
    val updated = JournalFormat.updatedLabel(state.fetchedAt, state.offline, stale)

    var guide by remember { mutableStateOf<TaskDetail?>(null) }
    var noticeOpen by remember { mutableStateOf(false) }
    var lightbox by remember { mutableStateOf<ImageRequestState?>(null) }

    val hasTasks = tasks.isNotEmpty()
    val hasWeather = weather != null && (weather.text.isNotBlank() || weather.images.isNotEmpty())
    val hasCalendar = calendar != null && calendar.images.isNotEmpty()
    val hasCandles = candles.isNotEmpty()

    val tabs = buildList {
        if (hasTasks) add(JumpTab("tasks", "每日任务", tasks.size))
        if (hasWeather) add(JumpTab("weather", "天气预报"))
        if (hasCalendar) add(JumpTab("calendar", "本月日历"))
        if (hasCandles) add(JumpTab("candles", "季节蜡烛"))
        if (notice != null) add(JumpTab("notice", "游戏公告"))
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxWidth < 360.dp || maxHeight < 560.dp
        val wide = maxWidth >= 600.dp
        val blocks = buildList {
            add(JournalBlock.Header)
            if (tabs.isNotEmpty()) add(JournalBlock.Tabs)
            if (overlayCard != null) add(JournalBlock.Overlay)
            if (envelope == null) {
                add(JournalBlock.Empty)
            } else {
                if (hasTasks) {
                    add(JournalBlock.TasksHeader)
                    if (wide) add(JournalBlock.TaskGrid(tasks)) else tasks.forEach { add(JournalBlock.TaskRow(it)) }
                } else {
                    add(JournalBlock.Empty)
                }
                if (hasWeather) add(JournalBlock.Weather)
                if (hasCalendar) add(JournalBlock.Calendar)
                if (hasCandles) add(JournalBlock.Candles)
                if (notice != null) add(JournalBlock.Notice)
            }
            add(JournalBlock.Footer)
        }
        PaperSheet(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (compact) 38.dp else 46.dp,
                    end = if (compact) 12.dp else 18.dp,
                    top = if (compact) 10.dp else 14.dp,
                    bottom = 22.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                blocks.forEach { block ->
                    item(key = block.key()) {
                        when (block) {
                            JournalBlock.Header -> HeaderBlock(
                                state = state,
                                updated = updated,
                                darkTheme = darkTheme,
                                compact = compact,
                                onRefresh = onRefresh,
                                onToggleTheme = onToggleTheme,
                                onClose = onClose,
                            )

                            JournalBlock.Tabs -> JumpTabs(
                                tabs = tabs,
                                compact = compact,
                                onJump = { key ->
                                    val target = blocks.indexOfFirst { it.jumpKey() == key }
                                    if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                },
                            )

                            JournalBlock.Overlay -> overlayCard?.invoke()

                            JournalBlock.TasksHeader -> SectionHeader(
                                title = "每日任务",
                                trailing = JournalFormat.progress(state.done.size, tasks.size),
                                sticker = R.drawable.sky_jelly_handdrawn,
                                stickerSize = if (compact) 34.dp else 40.dp,
                                stickerRotation = -6f,
                                compact = compact,
                                modifier = Modifier.padding(top = 6.dp),
                            )

                            is JournalBlock.TaskRow -> {
                                val task = block.task
                                val detail = JournalFormat.taskDetailFor(task.keyword, details)
                                TaskNote(
                                    task = task,
                                    detail = detail,
                                    done = task.number in state.done,
                                    editable = editable,
                                    index = tasks.indexOf(task).coerceAtLeast(0),
                                    onToggle = { onToggleTask(task.number, it) },
                                    onOpenGuide = { detail?.let { guide = it } },
                                    compact = compact,
                                )
                            }

                            is JournalBlock.TaskGrid -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    block.tasks.chunked(2).forEach { rowTasks ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            rowTasks.forEach { task ->
                                                val detail = JournalFormat.taskDetailFor(task.keyword, details)
                                                Box(Modifier.weight(1f)) {
                                                    TaskNote(
                                                        task = task,
                                                        detail = detail,
                                                        done = task.number in state.done,
                                                        editable = editable,
                                                        index = tasks.indexOf(task).coerceAtLeast(0),
                                                        onToggle = { onToggleTask(task.number, it) },
                                                        onOpenGuide = { detail?.let { guide = it } },
                                                    )
                                                }
                                            }
                                            if (rowTasks.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            JournalBlock.Weather -> Column {
                                SectionHeader(
                                    title = "天气预报",
                                    sticker = R.drawable.sky_manta_flat,
                                    stickerSize = if (compact) 34.dp else 40.dp,
                                    stickerRotation = 7f,
                                    compact = compact,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                PhotoNote(
                                    baseUrl = AppConfig.SITE_URL,
                                    images = weather?.images.orEmpty(),
                                    caption = JournalFormat.imageCountLabel(weather?.images?.size ?: 0),
                                    previewText = weather?.text,
                                    onClick = {
                                        weather?.images?.takeIf { it.isNotEmpty() }?.let {
                                            lightbox = ImageRequestState(it, 0)
                                        }
                                    },
                                    imageHeight = if (compact) 140.dp else 176.dp,
                                )
                            }

                            JournalBlock.Calendar -> Column {
                                SectionHeader(
                                    title = "本月日历",
                                    sticker = R.drawable.sky_butterfly_handdrawn,
                                    stickerSize = if (compact) 30.dp else 36.dp,
                                    stickerRotation = -12f,
                                    compact = compact,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                PhotoNote(
                                    baseUrl = AppConfig.SITE_URL,
                                    images = calendar?.images.orEmpty(),
                                    caption = JournalFormat.imageCountLabel(calendar?.images?.size ?: 0),
                                    onClick = {
                                        calendar?.images?.takeIf { it.isNotEmpty() }?.let {
                                            lightbox = ImageRequestState(it, 0)
                                        }
                                    },
                                    imageHeight = if (compact) 150.dp else 192.dp,
                                )
                            }

                            JournalBlock.Candles -> Column {
                                SectionHeader(
                                    title = "季节蜡烛",
                                    sticker = R.drawable.sky_candles_handdrawn,
                                    stickerSize = if (compact) 34.dp else 40.dp,
                                    stickerRotation = 5f,
                                    compact = compact,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    candles.forEach { candle ->
                                        CandleCard(
                                            detail = candle,
                                            baseUrl = AppConfig.SITE_URL,
                                            onOpen = { guide = candle },
                                            onOpenImage = { images, index -> lightbox = ImageRequestState(images, index) },
                                            compact = compact,
                                        )
                                    }
                                }
                            }

                            JournalBlock.Notice -> Column {
                                SectionHeader(
                                    title = "游戏公告",
                                    compact = compact,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                NoticeCard(notice = notice, onOpen = { noticeOpen = true })
                            }

                            JournalBlock.Empty -> {
                                if (envelope == null) {
                                    if (state.loading) {
                                        EmptyTasksCard(text = "正在读取今日任务…")
                                    } else {
                                        ErrorCard(
                                            message = state.message ?: "请检查网络后重试",
                                            onRetry = onRefresh,
                                        )
                                    }
                                } else {
                                    EmptyTasksCard()
                                }
                            }

                            JournalBlock.Footer -> FooterCard(
                                fetchedAt = state.fetchedAt,
                                versionName = versionName,
                                onOpenSite = { uriHandler.openUri(AppConfig.SITE_URL) },
                                onOpenFeedback = { uriHandler.openUri(AppConfig.FEEDBACK_URL) },
                                onClearCache = onClearCache,
                                onOpenApp = onOpenApp,
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
                    onOpenImage = { images, index -> lightbox = ImageRequestState(images, index) },
                )
            }
            if (noticeOpen && notice != null) {
                NoticeSheet(
                    notice = notice,
                    onClose = { noticeOpen = false },
                )
            }
            lightbox?.let { request ->
                ImageLightbox(
                    baseUrl = AppConfig.SITE_URL,
                    images = request.images,
                    initialIndex = request.index,
                    onClose = { lightbox = null },
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(
    state: JournalUiState,
    updated: String,
    darkTheme: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    onToggleTheme: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val palette = LocalPaperPalette.current
    val date = state.envelope?.date ?: state.today
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "光遇每日任务",
                        color = palette.ink,
                        fontSize = if (compact) 18.sp else 20.sp,
                        lineHeight = if (compact) 28.sp else 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                    if (!compact) {
                        Sticker(
                            res = R.drawable.sky_cape_handdrawn,
                            size = 40.dp,
                            rotation = -8f,
                            modifier = Modifier.offset(x = 2.dp, y = (-2).dp),
                        )
                    }
                }
                Text(
                    text = if (state.refreshing) "刷新中 · $updated" else updated,
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            DateStamp(date = date, weekday = BeijingClock.weekday(date), compact = compact)
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaperButton(
                text = if (state.refreshing) "刷新中" else "刷新",
                onClick = onRefresh,
                enabled = !state.refreshing,
                height = if (compact) 30.dp else 32.dp,
            )
            if (onClose != null) {
                Spacer(Modifier.width(6.dp))
                PaperButton("关闭", onClick = onClose, height = if (compact) 30.dp else 32.dp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(if (compact) 34.dp else 38.dp)
                    .clickable(onClick = onToggleTheme)
                    .semantics { contentDescription = if (darkTheme) "切换到浅色主题" else "切换到深色主题" },
                contentAlignment = Alignment.Center,
            ) {
                Sticker(
                    res = if (darkTheme) R.drawable.theme_sun else R.drawable.theme_moon,
                    size = if (compact) 30.dp else 34.dp,
                    rotation = -8f,
                )
            }
        }
        state.message?.let { message ->
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(palette.noteColors[1].copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                    .hardShadow(palette.shadow, dx = 1.dp, dy = 1.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .drawLeftRule(palette.accent),
            ) {
                Text(message, color = palette.ink, fontSize = 11.sp, lineHeight = 18.sp)
            }
        }
    }
}

private fun Modifier.drawLeftRule(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(2.dp.toPx(), size.height))
}

private fun JournalBlock.key(): String = when (this) {
    JournalBlock.Header -> "header"
    JournalBlock.Tabs -> "tabs"
    JournalBlock.Overlay -> "overlay"
    JournalBlock.TasksHeader -> "tasks-header"
    is JournalBlock.TaskRow -> "task-${task.number}"
    is JournalBlock.TaskGrid -> "task-grid"
    JournalBlock.Weather -> "weather"
    JournalBlock.Calendar -> "calendar"
    JournalBlock.Candles -> "candles"
    JournalBlock.Notice -> "notice"
    JournalBlock.Empty -> "empty"
    JournalBlock.Footer -> "footer"
}

private fun JournalBlock.jumpKey(): String? = when (this) {
    JournalBlock.TasksHeader -> "tasks"
    JournalBlock.Weather -> "weather"
    JournalBlock.Calendar -> "calendar"
    JournalBlock.Candles -> "candles"
    JournalBlock.Notice -> "notice"
    else -> null
}
