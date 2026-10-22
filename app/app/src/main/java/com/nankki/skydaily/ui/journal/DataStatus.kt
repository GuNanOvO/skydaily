package com.nankki.skydaily.ui.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.ui.paper.RuledText

@Composable
internal fun DataStatus(state: JournalUiState, archived: Boolean = false) {
    val envelope = state.envelope ?: return
    val stale = envelope.date != state.today
    if (archived || stale || state.offline) {
        val context = when {
            archived -> "历史记录"
            stale -> "今日尚未更新"
            else -> "缓存记录"
        }
        RuledText(
            "${envelope.date} · $context${if (state.offline) " · 离线缓存" else ""}",
            fontSize = 12.sp,
            muted = true,
        )
    }
    if (!state.message.isNullOrBlank()) RuledText(state.message, fontSize = 12.sp, muted = true)
}
