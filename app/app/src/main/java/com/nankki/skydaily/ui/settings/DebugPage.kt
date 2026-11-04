package com.nankki.skydaily.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nankki.skydaily.R
import com.nankki.skydaily.meals.MealChannelStatus
import com.nankki.skydaily.meals.MealReminderManager
import com.nankki.skydaily.meals.MealTestResult
import com.nankki.skydaily.ui.paper.NotebookHeader
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperScroll
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
internal fun DebugPage(
    manager: MealReminderManager,
    onBack: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = LocalPaperPalette.current
    var access by remember(manager) { mutableStateOf(manager.accessState()) }
    var channel by remember(manager) { mutableStateOf(manager.channelStatus()) }
    var result by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = manager.accessState()
        channel = manager.channelStatus()
    }
    BackHandler(onBack = onBack)
    PaperScroll(modifier) {
        PaperBlock {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { (32.sp.roundToPx() * 2).toDp() }),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    PaperButton("返回设置", onBack)
                }
                NotebookHeader(
                    title = "调试",
                    subtitle = "饭点通知 · 系统权限",
                    sticker = R.drawable.sky_candles_handdrawn,
                )
            }
        }
        SettingsSection(
            title = "饭点通知",
            note = null,
            tone = palette.tabColors[1],
            tape = palette.noteTapes[1],
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaperButton(
                    "发送饭点测试通知",
                    onClick = {
                        result = when (manager.sendTestNotification()) {
                            MealTestResult.Sent -> "测试通知已提交，可在通知栏查看并点击打开光遇。"
                            MealTestResult.NotificationBlocked -> "通知未允许，请打开通知设置。"
                        }
                        access = manager.accessState()
                        channel = manager.channelStatus()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tone = palette.tabColors[1],
                )
                PaperButton(
                    "清除测试通知",
                    onClick = {
                        manager.cancelTestNotification()
                        result = "测试通知已清除。"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    result ?: "测试通知会在 5 分钟后自动消失。",
                    color = palette.muted, fontSize = 12.sp, lineHeight = 20.sp,
                )
            }
        }
        SettingsSection(
            title = "系统状态",
            note = null,
            tone = palette.tabColors[3],
            tape = palette.noteTapes[0],
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "通知可用 · ${if (access.notificationsAllowed) "是" else "否"}\n" +
                        "饭点渠道 · ${when (channel) {
                            MealChannelStatus.NotCreated -> "尚未创建"
                            MealChannelStatus.Blocked -> "已关闭"
                            MealChannelStatus.Enabled -> "已开启"
                        }}\n" +
                        "精确提醒 · ${if (access.exactAlarmAllowed) "已允许" else "未允许"}",
                    color = palette.ink, fontSize = 12.sp, lineHeight = 24.sp,
                )
                PaperButton("通知设置", { openSystemSettings(context, manager.notificationSettingsIntent()) })
                PaperButton("精确提醒设置", { openSystemSettings(context, manager.exactAlarmSettingsIntent()) })
            }
        }
        SettingsSection(
            title = "入口管理",
            note = null,
            tone = palette.tabColors[2],
            tape = palette.noteTapes[1],
        ) {
            PaperButton("隐藏调试入口", onHide, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}
