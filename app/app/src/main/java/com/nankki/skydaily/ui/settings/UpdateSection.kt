package com.nankki.skydaily.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.update.AppRelease
import com.nankki.skydaily.core.update.InstallOutcome
import com.nankki.skydaily.core.update.UpdateController
import com.nankki.skydaily.core.update.UpdateInstaller
import com.nankki.skydaily.core.update.UpdateState
import com.nankki.skydaily.core.update.UpdateStore
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import java.util.Locale

/**
 * 关于页的更新区块：检查、下载进度、失败重试与安装。
 * 当前版本由关于页本身展示，这里只呈现更新流程的状态。
 */
@Composable
fun UpdateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalPaperPalette.current
    val state by UpdateStore.state.collectAsState()
    val release by UpdateStore.release.collectAsState()
    var needsUnknownSources by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (needsUnknownSources && context.packageManager.canRequestPackageInstalls()) {
            needsUnknownSources = false
        }
    }

    val busy = state is UpdateState.Checking ||
        state is UpdateState.Downloading ||
        state is UpdateState.Verifying ||
        state is UpdateState.Ready ||
        state is UpdateState.Installing
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "应用更新",
                color = palette.ink,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                modifier = Modifier.weight(1f),
            )
            PaperButton(
                text = "检查更新",
                onClick = { UpdateController.check(context, manual = true) },
                enabled = !busy,
            )
        }
        when (val current = state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Hint("正在获取版本信息…")
            is UpdateState.UpToDate -> {
                Hint("已是最新版本 · ${BeijingClock.updatedAt(current.checkedAt)} 检查")
            }

            is UpdateState.Available -> {
                ReleaseNote(current.release) {
                    PaperButton(
                        text = "下载",
                        onClick = { UpdateController.download(context) },
                        tone = palette.tabColors[2],
                    )
                }
            }

            is UpdateState.Incompatible -> {
                Hint("${current.release.versionName} 需要更高的 Android 版本，当前系统无法安装。")
            }

            is UpdateState.Downloading -> DownloadProgress(current) {
                UpdateController.cancel(context)
            }

            is UpdateState.Verifying -> Hint("正在校验安装包…")
            is UpdateState.Ready -> {
                ReadyNote(current.release) {
                    val outcome = UpdateController.install(context)
                    needsUnknownSources = outcome == InstallOutcome.NeedsUnknownSources
                }
            }

            is UpdateState.Installing -> Hint("已交给系统安装，返回后会重新核对版本。")
            is UpdateState.Failed -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = current.message,
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
                if (current.retryable) {
                    Spacer(Modifier.height(6.dp))
                    PaperButton(
                        text = "重试",
                        onClick = {
                            if (release != null) UpdateController.download(context)
                            else UpdateController.check(context, manual = true)
                        },
                    )
                }
            }
        }
        if (needsUnknownSources && state is UpdateState.Ready) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "需要允许安装未知应用",
                color = palette.ink,
                fontSize = 12.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(6.dp))
            PaperButton(
                text = "去允许",
                onClick = {
                    openSystemSettings(context, UpdateInstaller.unknownSourcesIntent(context))
                },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    val palette = LocalPaperPalette.current
    Spacer(Modifier.height(4.dp))
    Text(text, color = palette.muted, fontSize = 11.sp, lineHeight = 18.sp)
}

@Composable
private fun ReleaseNote(release: AppRelease, action: @Composable () -> Unit) {
    val palette = LocalPaperPalette.current
    Spacer(Modifier.height(4.dp))
    NoteCard {
        Text(
            text = "新版本 ${release.versionName}",
            color = palette.ink,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "安装包 ${formatSize(release.size)} · ${BeijingClock.noticeDate(release.publishedAt)}",
            color = palette.muted,
            fontSize = 10.sp,
            lineHeight = 16.sp,
        )
        if (release.notes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            for (note in release.notes) {
                Text(
                    text = "· $note",
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PaperDivider()
        Spacer(Modifier.height(8.dp))
        action()
    }
}

@Composable
private fun DownloadProgress(state: UpdateState.Downloading, onCancel: () -> Unit) {
    val palette = LocalPaperPalette.current
    val fraction = if (state.total > 0) {
        (state.received.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "正在下载 ${formatSize(state.received)} / ${formatSize(state.total)}",
        color = palette.ink,
        fontSize = 12.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(palette.paper, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(palette.noteAccents[2], RoundedCornerShape(1.dp)),
        )
    }
    Spacer(Modifier.height(8.dp))
    PaperButton(text = "取消", onClick = onCancel)
}

@Composable
private fun ReadyNote(release: AppRelease, onInstall: () -> Unit) {
    val palette = LocalPaperPalette.current
    Spacer(Modifier.height(4.dp))
    NoteCard {
        Text(
            text = "${release.versionName} 已下载并通过校验",
            color = palette.ink,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton(
                text = "安装",
                onClick = onInstall,
                tone = palette.tabColors[2],
            )
            PaperButton(
                text = "稍后",
                onClick = { UpdateController.dismiss() },
            )
        }
    }
}

@Composable
private fun NoteCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, end = 2.dp)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.noteColors[2], RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
