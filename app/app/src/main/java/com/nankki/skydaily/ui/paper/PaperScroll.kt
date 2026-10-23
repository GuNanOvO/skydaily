package com.nankki.skydaily.ui.paper

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlin.math.ceil

/** 纸面与内容是同一个滚动节点；纸张高度由内容决定。 */
@Composable
fun PaperScroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            PaperSheet(Modifier.fillMaxWidth().heightIn(min = viewportHeight)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
            }
        }
    }
}

/** 不透明便签按整行占位，后续纸面文字不会偏离横线节奏。 */
@Composable
fun PaperBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pitch = with(LocalDensity.current) { 32.sp.roundToPx() }
    Box(modifier.fillMaxWidth().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val rows = ceil(placeable.height.toDouble() / pitch).toInt().coerceAtLeast(1)
        val height = constraints.constrainHeight(rows * pitch)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }) { content() }
}

@Composable
fun RuledText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    muted: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    Text(
        text = text,
        color = if (muted) palette.muted else palette.ink,
        fontSize = fontSize,
        lineHeight = 32.sp,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}
