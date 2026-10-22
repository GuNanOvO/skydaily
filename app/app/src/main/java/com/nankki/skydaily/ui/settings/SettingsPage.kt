package com.nankki.skydaily.ui.settings

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.ui.home.OverlayCard
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperSheet
import com.nankki.skydaily.ui.paper.Sticker
import com.nankki.skydaily.ui.theme.LocalPaperPalette

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
    onRefresh: () -> Unit,
    onOpenToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 360.dp || maxHeight < 560.dp
        PaperSheet(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = if (compact) 38.dp else 46.dp,
                    end = if (compact) 12.dp else 18.dp,
                    top = if (compact) 12.dp else 16.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "设置",
                            color = palette.ink,
                            fontSize = if (compact) 18.sp else 20.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                        Sticker(
                            res = R.drawable.sky_cape_handdrawn,
                            size = if (compact) 32.dp else 38.dp,
                            rotation = -8f,
                        )
                    }
                    Text(
                        text = "主题、悬浮球与本地缓存",
                        color = palette.muted,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                    )
                }

                item(key = "theme") {
                    SettingsGroup(title = "主题") {
                        Text(
                            text = "自动：北京时间 19:00–07:00 使用深色；手动选择优先并持久化。",
                            color = palette.muted,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                PaperButton(
                                    text = when (mode) {
                                        ThemeMode.Auto -> "自动"
                                        ThemeMode.Light -> "浅色"
                                        ThemeMode.Dark -> "深色"
                                    },
                                    onClick = { onThemeModeChange(mode) },
                                    tone = if (mode == themeMode) palette.tabColors[2] else null,
                                )
                            }
                        }
                    }
                }

                item(key = "overlay") {
                    Column {
                        SettingsTitle("悬浮球")
                        Spacer(Modifier.height(4.dp))
                        OverlayCard(
                            granted = overlayGranted,
                            running = overlayRunning,
                            onAction = onOverlayAction,
                        )
                        Text(
                            text = "小窗、分屏或全屏时都会自动使用紧凑排版；悬浮面板固定在右侧。",
                            color = palette.muted,
                            fontSize = 11.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                item(key = "cache") {
                    SettingsGroup(title = "本地缓存") {
                        Text(
                            text = if (state.fetchedAt > 0) {
                                "上次写入：${BeijingClock.updatedDate(state.fetchedAt)} " +
                                    BeijingClock.updatedAt(state.fetchedAt)
                            } else {
                                "尚未写入缓存。"
                            },
                            color = palette.muted,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PaperButton("刷新今日数据", onRefresh)
                            PaperButton("清除并重新获取", onClearCache)
                            PaperButton("返回今日", onOpenToday)
                        }
                    }
                }

                item(key = "source") {
                    SettingsGroup(title = "数据来源") {
                        Text(
                            text = "今日任务、指南、天气、日历与公告来自 skydaily 的公开页面。",
                            color = palette.muted,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaperButton("网站", onOpenSite)
                            PaperButton("反馈", onOpenFeedback)
                        }
                        Text(
                            text = "图文来源于游戏内「小精灵」，版权归原权利人所有。",
                            color = palette.muted,
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                item(key = "about") {
                    Column {
                        PaperDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("光遇每日任务", color = palette.muted, fontSize = 10.sp)
                            Spacer(Modifier.weight(1f))
                            Text("v0.1.0", color = palette.muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTitle(title)
        content()
    }
}

@Composable
private fun SettingsTitle(text: String) {
    val palette = LocalPaperPalette.current
    Text(
        text = text,
        color = palette.ink,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
}
