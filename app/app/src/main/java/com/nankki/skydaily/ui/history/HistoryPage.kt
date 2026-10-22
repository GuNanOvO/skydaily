package com.nankki.skydaily.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.DirectoryDiskStore
import com.nankki.skydaily.data.HistoryEntry
import com.nankki.skydaily.data.HistoryIndexLoad
import com.nankki.skydaily.data.HistoryRepository
import com.nankki.skydaily.data.OkHttpTransport
import com.nankki.skydaily.ui.journal.EmptyTasksCard
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
 * 记录页：顶部快捷操作 + 从 Pages 归档索引读到的倒序日期列表。
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

    PaperScroll(modifier.fillMaxSize()) {
        PaperBlock {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaperButton(
                    text = "刷新",
                    onClick = {
                        reloadKey++
                        onRefresh()
                    },
                    height = 40.dp,
                )
                PaperButton(
                    text = "悬浮球",
                    onClick = onOverlayAction,
                    tone = if (overlayRunning) palette.tabColors[2] else null,
                    height = 40.dp,
                )
                PaperButton(text = "设置", onClick = onSettings, height = 40.dp)
            }
        }

        when (val current = state) {
            is HistoryListState.Loading -> RuledText(
                text = "正在读取归档…",
                fontSize = 12.sp,
                muted = true,
            )

            is HistoryListState.Failure -> PaperBlock {
                HistoryErrorCard(
                    message = humanizeReason(current.reason),
                    onRetry = { reloadKey++ },
                )
            }

            is HistoryListState.Ready -> {
                val countText = when {
                    current.refreshing && current.entries.isEmpty() -> "正在读取归档…"
                    current.refreshing -> "刷新中 · 共 ${current.entries.size} 天"
                    current.entries.isEmpty() -> "还没有可查看的归档记录"
                    else -> "共 ${current.entries.size} 天 · 最近 ${displayDate(current.entries.first().date)}"
                }
                RuledText(text = countText, fontSize = 12.sp, muted = true)
                if (current.reason != null) {
                    RuledText(
                        text = "离线缓存 · ${humanizeReason(current.reason)}",
                        fontSize = 11.sp,
                        muted = true,
                    )
                }

                if (current.entries.isEmpty() && !current.refreshing) {
                    PaperBlock {
                        EmptyTasksCard(text = "归档索引为空，稍后再刷新")
                    }
                } else {
                    val today = BeijingClock.today()
                    current.entries.forEach { entry ->
                        HistoryRow(
                            entry = entry,
                            isToday = entry.date == today,
                            onClick = { onOpenDate(entry.date) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuledText(
            text = buildString {
                append(displayDate(entry.date))
                val weekday = BeijingClock.weekday(entry.date)
                if (weekday.isNotEmpty()) {
                    append("  ")
                    append(weekday)
                }
                if (isToday) append(" · 今天")
            },
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.SemiBold else null,
        )
        Text(text = "›", color = palette.muted, fontSize = 18.sp)
    }
}

@Composable
private fun HistoryErrorCard(message: String, onRetry: () -> Unit) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "归档暂时不可用", color = palette.ink, fontSize = 14.sp, lineHeight = 24.sp)
        Text(text = message, color = palette.muted, fontSize = 12.sp, lineHeight = 20.sp)
        PaperButton(text = "重试", onClick = onRetry, height = 40.dp)
    }
}

private fun displayDate(date: String): String =
    runCatching { LocalDate.parse(date) }
        .map { "%04d.%02d.%02d".format(it.year, it.monthValue, it.dayOfMonth) }
        .getOrDefault(date)

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
