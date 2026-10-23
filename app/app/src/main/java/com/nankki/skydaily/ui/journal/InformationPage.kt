package com.nankki.skydaily.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.R
import com.nankki.skydaily.ui.paper.NotebookHeader
import com.nankki.skydaily.ui.paper.NotebookSection
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText

@Composable
fun InformationPage(state: JournalUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    var images by remember { mutableStateOf<List<String>?>(null) }
    var noticeOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = images != null || noticeOpen) {
        if (images != null) images = null else noticeOpen = false
    }
    Box(modifier.fillMaxSize()) {
        PaperScroll {
            PaperBlock {
                NotebookHeader(
                    title = "资讯",
                    subtitle = "公告 · 天气 · 活动日历",
                    date = state.envelope?.date ?: state.today,
                    onRefresh = onRefresh,
                    refreshing = state.loading || state.refreshing,
                    sticker = R.drawable.sky_butterfly_handdrawn,
                )
            }
            DataStatus(state)
            if (state.envelope == null) {
                PaperBlock {
                    if (state.loading) EmptyTasksCard(text = "正在读取资讯…")
                    else ErrorCard(state.message ?: "请检查网络后重试", onRefresh)
                }
            } else {
                InformationContent(state, onOpenImages = { images = it }, onOpenNotice = { noticeOpen = true })
            }
        }
        images?.let { ImageLightbox(AppConfig.SITE_URL, it, 0, onClose = { images = null }) }
        if (noticeOpen) state.notice?.let { NoticeSheet(it, onClose = { noticeOpen = false }) }
    }
}

@Composable
internal fun ColumnScope.InformationContent(
    state: JournalUiState,
    onOpenImages: (List<String>) -> Unit,
    onOpenNotice: () -> Unit,
) {
    val data = state.envelope?.data
    NotebookSection(title = "天气预报", sticker = R.drawable.sky_manta_flat)
    val weather = data?.weather
    if (weather == null) {
        RuledText("暂无天气记录", muted = true)
    } else {
        if (weather.text.isNotBlank()) RuledText(weather.text)
        if (weather.images.isNotEmpty()) PaperBlock {
            PhotoNote(
                baseUrl = AppConfig.SITE_URL,
                images = weather.images,
                caption = JournalFormat.imageCountLabel(weather.images.size),
                onClick = { onOpenImages(weather.images) },
            )
        }
    }
    NotebookSection(title = "本月日历", sticker = R.drawable.sky_butterfly_handdrawn)
    val calendar = data?.calendar
    if (calendar?.images.isNullOrEmpty()) {
        RuledText("暂无日历记录", muted = true)
    } else PaperBlock {
        PhotoNote(
            baseUrl = AppConfig.SITE_URL,
            images = calendar.images,
            caption = JournalFormat.imageCountLabel(calendar.images.size),
            onClick = { onOpenImages(calendar.images) },
        )
    }
    if (state.notice != null) {
        NotebookSection(title = "游戏公告", sticker = R.drawable.sky_candles_handdrawn)
        PaperBlock { NoticeCard(state.notice, onOpenNotice) }
    }
}
