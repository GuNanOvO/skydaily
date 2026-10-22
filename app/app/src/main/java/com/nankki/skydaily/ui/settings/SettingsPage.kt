package com.nankki.skydaily.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.ui.home.OverlayCard
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.RuledText

@Composable
fun SettingsPage(
    state: JournalUiState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onOverlayAction: () -> Unit,
    onClearCache: () -> Unit,
    onOpenSite: () -> Unit,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaperScroll(modifier) {
        RuledText("主题", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        RuledText("自动：北京时间 19:00–07:00 使用深色。手动选择优先。", fontSize = 12.sp, muted = true)
        PaperBlock {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.Auto -> "自动"
                        ThemeMode.Light -> "浅色"
                        ThemeMode.Dark -> "深色"
                    }
                    PaperButton(
                        text = if (mode == themeMode) "✓ $label" else label,
                        onClick = { onThemeModeChange(mode) },
                        height = 40.dp,
                    )
                }
            }
        }
        RuledText("悬浮球", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        PaperBlock {
            OverlayCard(granted = overlayGranted, running = overlayRunning, onAction = onOverlayAction)
        }
        RuledText("点按打开任务，拖动后吸附屏幕边缘。", fontSize = 12.sp, muted = true)
        RuledText("本地缓存", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        RuledText(
            if (state.fetchedAt > 0) "上次更新：${BeijingClock.updatedDate(state.fetchedAt)} ${BeijingClock.updatedAt(state.fetchedAt)}"
            else "尚无缓存",
            fontSize = 12.sp,
            muted = true,
        )
        PaperBlock { PaperButton("清除并重新获取", onClearCache, height = 40.dp) }
        RuledText("数据来源", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        RuledText("光遇国服 · skydaily 公开归档", fontSize = 12.sp, muted = true)
        PaperBlock {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaperButton("网站", onOpenSite, height = 40.dp)
                PaperButton("反馈", onOpenFeedback, height = 40.dp)
            }
        }
        RuledText("图文来源于游戏内「小精灵」，版权归原权利人所有。", fontSize = 11.sp, muted = true)
    }
}
