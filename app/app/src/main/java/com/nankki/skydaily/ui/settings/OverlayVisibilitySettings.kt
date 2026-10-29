package com.nankki.skydaily.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.overlay.BallPreferences
import com.nankki.skydaily.overlay.GameForegroundMonitor
import com.nankki.skydaily.overlay.OverlayService
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.PaperToggleRow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
internal fun OverlayVisibilitySettings() {
    val context = LocalContext.current
    val palette = LocalPaperPalette.current
    val preferences = remember(context) { BallPreferences(AndroidKeyValueStore(context)) }
    var onlyInGame by remember(preferences) { mutableStateOf(preferences.onlyInGame) }
    var usageAccess by remember(context) { mutableStateOf(GameForegroundMonitor.hasUsageAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onlyInGame = preferences.onlyInGame
        usageAccess = GameForegroundMonitor.hasUsageAccess(context)
    }
    Column {
        Spacer(Modifier.height(8.dp))
        PaperDivider()
        Spacer(Modifier.height(8.dp))
        PaperToggleRow(
            title = "仅在游戏内显示",
            checked = onlyInGame,
            tone = palette.tabColors[2],
            accent = palette.noteAccents[2],
            onCheckedChange = { enabled ->
                preferences.onlyInGame = enabled
                onlyInGame = enabled
                usageAccess = GameForegroundMonitor.hasUsageAccess(context)
                OverlayService.settingsChanged(context)
            },
        )
        Text(
            text = "光遇国服在前台时显示，切到其他应用时隐藏；返回游戏后自动恢复。",
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 18.sp,
        )
        if (onlyInGame && !usageAccess) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "需要允许「使用情况访问权限」识别前台应用；授权前悬浮球保持隐藏。",
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
            PaperButton(
                text = "允许使用情况访问",
                onClick = { openSystemSettings(context, GameForegroundMonitor.permissionIntent(context)) },
            )
        }
    }
}
