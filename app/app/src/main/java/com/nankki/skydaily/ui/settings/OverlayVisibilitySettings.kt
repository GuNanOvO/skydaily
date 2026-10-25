package com.nankki.skydaily.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
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
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
internal fun OverlayVisibilitySettings() {
    val context = LocalContext.current
    val palette = LocalPaperPalette.current
    val preferences = remember(context) { BallPreferences(AndroidKeyValueStore(context)) }
    val controlShape = remember { RoundedCornerShape(2.dp) }
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
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = onlyInGame, role = Role.Switch, onValueChange = { enabled ->
                    preferences.onlyInGame = enabled
                    onlyInGame = enabled
                    usageAccess = GameForegroundMonitor.hasUsageAccess(context)
                    OverlayService.settingsChanged(context)
                }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "仅在游戏内显示",
                color = palette.ink,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.widthIn(min = 64.dp).heightIn(min = 32.dp)
                    .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                    .background(if (onlyInGame) palette.tabColors[2] else palette.paper, controlShape)
                    .border(1.dp, if (onlyInGame) palette.noteAccents[2] else palette.outline, controlShape)
                    .clearAndSetSemantics { }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (onlyInGame) "已开启" else "已关闭",
                    color = if (onlyInGame) palette.tabInk else palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
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
