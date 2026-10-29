package com.nankki.skydaily.ui.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.ui.theme.LocalPaperPalette

/**
 * 纸面开关行：整行可点，右侧一枚纸片状态标签。
 * 开关状态与副作用由调用方持有；这里只负责既有纸面样式与可访问语义，
 * 避免「仅在游戏内显示」与「自动刷新今日数据」各自复制同一份控件。
 *
 * @param tone 开启时的纸片底色（按设置分组取色）
 * @param accent 开启时的纸片描边色
 */
@Composable
fun PaperToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tone: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val controlShape = remember { RoundedCornerShape(2.dp) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = palette.ink,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .widthIn(min = 64.dp)
                .heightIn(min = 32.dp)
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(if (checked) tone else palette.paper, controlShape)
                .border(1.dp, if (checked) accent else palette.outline, controlShape)
                .clearAndSetSemantics { }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (checked) "已开启" else "已关闭",
                color = if (checked) palette.tabInk else palette.muted,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
