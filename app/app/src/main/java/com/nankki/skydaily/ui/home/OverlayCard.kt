package com.nankki.skydaily.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.StarMark
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

/** 悬浮球开关卡：未授权、已授权未显示、运行中三种状态。 */
@Composable
fun OverlayCard(
    granted: Boolean,
    running: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.photoPaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = "悬浮球",
                    color = palette.ink,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = when {
                        !granted -> "需要「显示在其他应用上层」权限"
                        running -> "已开启 · 拖动换位置，点按看任务"
                        else -> "已授权，尚未显示"
                    },
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            PaperButton(
                text = when {
                    !granted -> "去授权"
                    running -> "关闭"
                    else -> "显示"
                },
                onClick = onAction,
            )
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 54.dp,
            height = 16.dp,
            rotation = -3f,
        )
    }
}
