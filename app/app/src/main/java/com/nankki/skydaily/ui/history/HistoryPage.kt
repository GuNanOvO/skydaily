package com.nankki.skydaily.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.DirectoryDiskStore
import com.nankki.skydaily.data.HistoryEntry
import com.nankki.skydaily.data.HistoryIndexLoad
import com.nankki.skydaily.data.HistoryRepository
import com.nankki.skydaily.data.OkHttpTransport
import com.nankki.skydaily.ui.journal.EmptyTasksCard
import com.nankki.skydaily.ui.paper.NotebookHeader
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText
import com.nankki.skydaily.ui.paper.StarMark
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private sealed interface HistoryListState {
    data object Loading : HistoryListState

    data class Ready(
        val entries: List<HistoryEntry>,
        val refreshing: Boolean = false,
        val reason: String? = null,
    ) : HistoryListState

    data class Failure(val reason: String) : HistoryListState
}

/**
 * 记录页与归档详情共用的仓库实例：同一份磁盘缓存与 ETag。
 * 单日归档由调用方通过 [HistoryRepository.loadDate] 在 IO 调度器读取。
 */
@Composable
fun rememberHistoryRepository(): HistoryRepository {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        HistoryRepository(
            transport = OkHttpTransport(AppConfig.SITE_URL),
            store = DirectoryDiskStore(appContext),
        )
    }
}

/**
 * 记录页：顶部笔记本标题与快捷纸卡，下面是从 Pages 归档索引读到的倒序日期列表。
 * 自身只负责索引；单日归档由调用方通过 [HistoryRepository.loadDate] 打开。
 */
@Composable
fun HistoryPage(
    onOpenDate: (String) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    overlayRunning: Boolean,
    onOverlayAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val repository = rememberHistoryRepository()
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<HistoryListState>(HistoryListState.Loading) }

    val requestRefresh: () -> Unit = {
        reloadKey++
        onRefresh()
    }

    LaunchedEffect(reloadKey) {
        val cached = withContext(Dispatchers.IO) { repository.cachedIndex() }
        if (cached != null) {
            state = HistoryListState.Ready(cached.index.entries, refreshing = true)
        } else {
            state = HistoryListState.Loading
        }
        val result = withContext(Dispatchers.IO) { repository.refreshIndex() }
        ensureActive()
        state = when (result) {
            is HistoryIndexLoad.Success -> HistoryListState.Ready(result.index.entries)
            is HistoryIndexLoad.Offline -> HistoryListState.Ready(
                entries = result.index.entries,
                reason = result.reason,
            )
            is HistoryIndexLoad.Failure -> {
                val retained = (state as? HistoryListState.Ready)?.entries
                if (retained.isNullOrEmpty()) {
                    HistoryListState.Failure(result.reason)
                } else {
                    HistoryListState.Ready(retained, reason = result.reason)
                }
            }
        }
    }

    val ready = state as? HistoryListState.Ready
    val refreshing = when (val current = state) {
        is HistoryListState.Loading -> true
        is HistoryListState.Ready -> current.refreshing
        is HistoryListState.Failure -> false
    }
    val latestDate = ready?.entries?.firstOrNull()?.date

    PaperScroll(modifier.fillMaxSize()) {
        PaperBlock {
            NotebookHeader(
                title = "历史记录",
                subtitle = "按日期查看任务与资讯",
                date = latestDate,
                sticker = R.drawable.sky_cape_handdrawn,
            )
        }

        HistoryQuickActions(
            refreshing = refreshing,
            overlayRunning = overlayRunning,
            onRefresh = requestRefresh,
            onOverlayAction = onOverlayAction,
            onSettings = onSettings,
        )

        when (val current = state) {
            is HistoryListState.Loading -> HistoryLoadingCard()

            is HistoryListState.Failure -> PaperBlock {
                HistoryErrorCard(
                    message = humanizeReason(current.reason),
                    onRetry = { reloadKey++ },
                )
            }

            is HistoryListState.Ready -> {
                if (current.entries.isNotEmpty()) {
                    HistorySummary(entries = current.entries, refreshing = current.refreshing)
                } else if (current.refreshing) {
                    RuledText(text = "正在读取归档…", fontSize = 12.sp, muted = true)
                }
                if (current.reason != null) {
                    OfflineNotice(reason = current.reason)
                }

                if (current.entries.isEmpty() && !current.refreshing) {
                    PaperBlock {
                        EmptyTasksCard(text = "归档索引为空，稍后再刷新")
                    }
                } else {
                    val today = BeijingClock.today()
                    val groups = current.entries.groupBy { it.date.take(7) }
                    var rowIndex = 0
                    groups.entries.forEachIndexed { groupIndex, (month, entries) ->
                        MonthTab(
                            label = monthLabel(month),
                            tone = palette.tabColors[groupIndex % palette.tabColors.size],
                        )
                        entries.forEach { entry ->
                            HistoryRow(
                                entry = entry,
                                isToday = entry.date == today,
                                accentIndex = rowIndex,
                                onClick = { onOpenDate(entry.date) },
                            )
                            rowIndex++
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 快捷纸卡：刷新与悬浮球并排，设置单独一条，始终可见。 */
@Composable
private fun HistoryQuickActions(
    refreshing: Boolean,
    overlayRunning: Boolean,
    onRefresh: () -> Unit,
    onOverlayAction: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuickTab(
                label = "刷新",
                hint = if (refreshing) "刷新中…" else "重读归档",
                tone = palette.tabColors[0],
                dot = palette.noteAccents[0],
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.weight(1f),
            )
            QuickTab(
                label = "悬浮球",
                hint = if (overlayRunning) "运行中" else "未显示",
                tone = palette.tabColors[2],
                dot = palette.noteAccents[2],
                onClick = onOverlayAction,
                modifier = Modifier.weight(1f),
            )
        }
        SettingsQuickTab(onClick = onSettings)
    }
}

/** 设置入口保持整行宽度，标题与用途都写清楚，不是一枚裸露的工具栏按钮。 */
@Composable
private fun SettingsQuickTab(onClick: () -> Unit) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.tabColors[3], RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(palette.noteAccents[3].copy(alpha = 0.9f), RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "设置",
                color = palette.tabInk,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "主题 · 悬浮球 · 缓存 · 来源",
                color = palette.tabMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            color = palette.tabMuted,
            fontSize = 18.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun QuickTab(
    label: String,
    hint: String,
    tone: Color,
    dot: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(tone, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(dot.copy(alpha = if (enabled) 0.9f else 0.4f), RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (enabled) palette.tabInk else palette.tabMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = hint,
            color = palette.tabMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistorySummary(entries: List<HistoryEntry>, refreshing: Boolean) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArchiveTag(
            text = "共 ${entries.size} 天",
            tone = palette.tabColors[0],
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = archiveRange(entries).orEmpty(),
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
        if (refreshing) {
            Text(
                text = "刷新中…",
                color = palette.muted,
                fontSize = 10.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OfflineNotice(reason: String) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArchiveTag(text = "离线", tone = palette.tabColors[1])
        Spacer(Modifier.width(8.dp))
        Text(
            text = "显示本地缓存 · ${humanizeReason(reason)}",
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ArchiveTag(text: String, tone: Color) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier
            .hardShadow(palette.shadow, dx = 1.dp, dy = 1.dp)
            .background(tone, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = palette.tabInk,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MonthTab(label: String, tone: Color) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(tone, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                color = palette.tabInk,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(8.dp))
        PaperDivider(Modifier.weight(1f))
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    isToday: Boolean,
    accentIndex: Int,
    onClick: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(22.dp)
                .background(
                    if (isToday) palette.tabColors[0]
                    else palette.noteAccents[accentIndex % palette.noteAccents.size].copy(alpha = 0.55f),
                    RoundedCornerShape(1.dp),
                ),
        )
        Spacer(Modifier.width(10.dp))
        RuledText(
            text = displayDate(entry.date),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.SemiBold else null,
        )
        val weekday = BeijingClock.weekday(entry.date)
        if (weekday.isNotEmpty()) {
            Text(
                text = weekday,
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (isToday) {
            Box(
                modifier = Modifier
                    .background(palette.tabColors[0], RoundedCornerShape(1.dp))
                    .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "今天",
                    color = palette.tabInk,
                    fontSize = 9.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(text = "›", color = palette.muted, fontSize = 18.sp)
    }
}

@Composable
private fun HistoryLoadingCard() {
    val palette = LocalPaperPalette.current
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.photoPaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StarMark(Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "正在读取归档…",
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "稍候",
                color = palette.muted,
                fontSize = 10.sp,
                lineHeight = 18.sp,
            )
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 48.dp,
            height = 12.dp,
            rotation = -4f,
        )
    }
}

@Composable
private fun HistoryErrorCard(message: String, onRetry: () -> Unit) {
    val palette = LocalPaperPalette.current
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.photoPaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "归档暂时不可用",
                    color = palette.ink,
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f),
                )
                ArchiveTag(text = "读取失败", tone = palette.tabColors[1])
            }
            Text(text = message, color = palette.muted, fontSize = 12.sp, lineHeight = 20.sp)
            PaperButton(
                text = "重新读取",
                onClick = onRetry,
                tone = palette.tabColors[0],
            )
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 54.dp,
            height = 14.dp,
            rotation = 3f,
        )
    }
}

private fun archiveRange(entries: List<HistoryEntry>): String? {
    if (entries.isEmpty()) return null
    val newest = entries.first().date
    val oldest = entries.last().date
    return if (newest == oldest) monthDay(oldest) else "${monthDay(oldest)} – ${monthDay(newest)}"
}

private fun monthDay(date: String): String =
    runCatching { LocalDate.parse(date) }
        .map { "%02d.%02d".format(it.monthValue, it.dayOfMonth) }
        .getOrDefault(date)

private fun displayDate(date: String): String =
    runCatching { LocalDate.parse(date) }
        .map { "%04d.%02d.%02d".format(it.year, it.monthValue, it.dayOfMonth) }
        .getOrDefault(date)

private fun monthLabel(month: String): String =
    runCatching { YearMonth.parse(month) }
        .map { "${it.year} 年 ${it.monthValue} 月" }
        .getOrDefault(month)

private fun humanizeReason(reason: String): String {
    val lower = reason.lowercase()
    return when {
        "unable to resolve host" in lower ||
            "failed to connect" in lower ||
            "timeout" in lower ||
            "network" in lower ||
            "connect" in lower -> "网络不可用，连不上归档服务"
        "http 5" in lower -> "归档服务暂时不可用"
        "http 4" in lower -> "归档请求失败（$reason）"
        else -> reason
    }
}
