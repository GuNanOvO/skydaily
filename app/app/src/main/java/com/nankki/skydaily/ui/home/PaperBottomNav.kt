package com.nankki.skydaily.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.ui.paper.StarMark
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

private data class NavItem(val label: String, val hint: String)

private val items = listOf(
    NavItem("今日", "任务进度"),
    NavItem("指南", "任务与蜡烛"),
    NavItem("设置", "主题与悬浮球"),
)

/** 纸面风格的移动端底部分页：今日 / 指南 / 设置。 */
@Composable
fun PaperBottomNav(
    selected: Int,
    done: Int,
    total: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    Column(modifier.fillMaxWidth().background(palette.page)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.outline.copy(alpha = 0.55f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .selectableGroup()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val background by animateColorAsState(
                    targetValue = if (active) palette.tabColors[index] else Color.Transparent,
                    animationSpec = tween(180),
                    label = "nav-bg-$index",
                )
                val scale by animateFloatAsState(if (active) 1f else 0.97f, tween(180), label = "nav-scale-$index")
                val hint = when (index) {
                    0 -> if (total > 0) "$done / $total" else "等待更新"
                    else -> item.hint
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .hardShadow(if (active) palette.shadow else Color.Transparent, dx = 1.dp, dy = 2.dp)
                        .background(background)
                        .clip(RoundedCornerShape(2.dp))
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    NavLabel(item.label, hint, active)
                }
            }
        }
    }
}

@Composable
private fun NavLabel(label: String, hint: String, active: Boolean) {
    val palette = LocalPaperPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active && label == "今日") {
                StarMark(Modifier.size(12.dp), tint = palette.tabInk)
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = label,
                color = if (active) palette.tabInk else palette.ink,
                fontSize = if (active) 13.sp else 12.sp,
                lineHeight = 18.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        Text(
            text = hint,
            color = if (active) palette.tabMuted else palette.muted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
