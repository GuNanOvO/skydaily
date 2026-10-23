package com.nankki.skydaily.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.paper.NotebookHeader
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.paper.StarMark
import com.nankki.skydaily.ui.paper.Sticker
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val context = LocalContext.current
    val versionName = remember(context) { installedVersionName(context) }
    PaperScroll(modifier) {
        PaperBlock {
            Column(Modifier.fillMaxWidth()) {
                PaperButton(text = "返回记录", onClick = onBack)
                Spacer(Modifier.height(4.dp))
                NotebookHeader(
                    title = "设置",
                    subtitle = "外观 · 悬浮球 · 本地数据",
                    sticker = R.drawable.sky_candles_handdrawn,
                )
            }
        }

        SettingsSection(
            title = "外观",
            note = if (themeMode == ThemeMode.Auto) "自动" else "手动",
            tone = palette.tabColors[0],
            tape = palette.noteTapes[0],
        ) {
            ThemePicker(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "自动模式在北京时间 19:00–07:00 使用深色；手动选择优先。",
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
        }

        SettingsSection(
            title = "悬浮窗",
            note = null,
            tone = palette.tabColors[2],
            tape = palette.noteTapes[2],
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                        .background(palette.paper, CircleShape)
                        .border(1.dp, palette.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StarMark(Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "悬浮球",
                    color = palette.ink,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PaperButton(
                    text = when {
                        !overlayGranted -> "去授权"
                        overlayRunning -> "关闭"
                        else -> "显示"
                    },
                    onClick = onOverlayAction,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !overlayGranted -> "需要「显示在其他应用上层」权限"
                    overlayRunning -> "已开启 · 拖动换位置，点按看任务"
                    else -> "已授权，尚未显示"
                },
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "点按打开任务，拖动后吸附屏幕边缘。",
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
        }

        SettingsSection(
            title = "缓存",
            note = if (state.fetchedAt > 0) "已缓存" else "尚无缓存",
            tone = palette.tabColors[3],
            tape = palette.noteTapes[3],
        ) {
            if (state.fetchedAt > 0) {
                Text(
                    text = "上次更新",
                    color = palette.muted,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = "${BeijingClock.updatedDate(state.fetchedAt)} ${BeijingClock.updatedAt(state.fetchedAt)}",
                    color = palette.ink,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = "首次打开需要联网获取今日数据。",
                    color = palette.ink,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            PaperDivider()
            Spacer(Modifier.height(8.dp))
            PaperButton(
                text = "清除并重新获取",
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
                tone = palette.tabColors[0],
            )
        }

        SettingsSection(
            title = "关于",
            note = "光遇国服",
            tone = palette.tabColors[4],
            tape = palette.noteTapes[1],
        ) {
            Text(
                text = "光遇每日任务",
                color = palette.ink,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (versionName.isNullOrBlank()) {
                    "com.nankki.skydaily"
                } else {
                    "com.nankki.skydaily · 版本 $versionName"
                },
                color = palette.muted,
                fontSize = 10.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            PaperDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelTag(text = "skydaily 公开归档", tone = palette.tabColors[3])
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "最近 30 天归档",
                    color = palette.muted,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PaperButton("打开网站", onOpenSite, tone = palette.tabColors[3])
                PaperButton("反馈", onOpenFeedback)
            }
            Spacer(Modifier.height(8.dp))
            PaperDivider()
            Spacer(Modifier.height(6.dp))
            Text(
                text = "图文来源于游戏内「小精灵」，版权归原权利人所有。",
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 从已安装包读取 Gradle versionName，避免在界面里硬编码版本号。
 */
@Suppress("DEPRECATION")
private fun installedVersionName(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()

/**
 * 纸面上的分组：左上角一枚色纸标签，右侧一条状态注记。
 */
@Composable
private fun SettingsSection(
    title: String,
    note: String?,
    tone: Color,
    tape: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPaperPalette.current
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.photoPaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTag(text = title, tone = tone)
                Spacer(Modifier.weight(1f))
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        color = palette.muted,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 48.dp,
            height = 12.dp,
            rotation = 3f,
            color = tape ?: palette.noteTapes[0],
        )
    }
}

@Composable
private fun SectionTag(text: String, tone: Color) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier
            .hardShadow(palette.shadow, dx = 1.dp, dy = 1.dp)
            .background(tone, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = palette.tabInk,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PastelTag(text: String, tone: Color) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier
            .background(tone, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = palette.tabInk,
            fontSize = 10.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ThemePicker(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = ((maxWidth - 12.dp) / 3f).coerceAtLeast((104f * fontScale).dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                ThemeChoice(
                    mode = mode,
                    selected = mode == themeMode,
                    toneIndex = index,
                    onClick = { onThemeModeChange(mode) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    mode: ThemeMode,
    selected: Boolean,
    toneIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val tone = palette.tabColors[toneIndex % palette.tabColors.size]
    val label = when (mode) {
        ThemeMode.Auto -> "自动"
        ThemeMode.Light -> "浅色"
        ThemeMode.Dark -> "深色"
    }
    val hint = when (mode) {
        ThemeMode.Auto -> "19–07 自动"
        ThemeMode.Light -> "始终浅色"
        ThemeMode.Dark -> "始终深色"
    }
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(if (selected) tone else palette.photoPaper, RoundedCornerShape(2.dp))
            .border(
                width = 1.dp,
                color = if (selected) palette.tabInk.copy(alpha = 0.55f) else palette.outline,
                shape = RoundedCornerShape(2.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (mode) {
                ThemeMode.Auto -> StarMark(
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) palette.tabInk else palette.ink,
                )
                ThemeMode.Light -> Sticker(
                    res = R.drawable.theme_sun,
                    size = 20.dp,
                    rotation = -4f,
                    shadow = false,
                )
                ThemeMode.Dark -> Sticker(
                    res = R.drawable.theme_moon,
                    size = 20.dp,
                    rotation = 4f,
                    shadow = false,
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = if (selected) palette.tabInk else palette.ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (selected) "当前" else hint,
            color = if (selected) palette.tabMuted else palette.muted,
            fontSize = 9.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
