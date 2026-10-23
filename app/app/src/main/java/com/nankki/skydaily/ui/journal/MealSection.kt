package com.nankki.skydaily.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.meals.MealSchedule
import com.nankki.skydaily.ui.paper.NotebookSection
import com.nankki.skydaily.ui.paper.PaperBlock
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.paper.rememberMinuteTime
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import com.nankki.skydaily.ui.theme.PaperPalette

private val mealHourLabels = MealSchedule.hours.associateWith { it.toString().padStart(2, '0') + ":00" }
internal fun mealHourLabel(hour: Int): String = mealHourLabels.getValue(hour)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.MealSection(onSettings: () -> Unit) {
    val palette = LocalPaperPalette.current
    val now = rememberMinuteTime()
    val status = remember(now) { MealSchedule.status(now) }
    val current = status.current
    val minutes = ((if (current != null) current.endsAtMillis else status.next.startsAtMillis) - now + 59_999L) / 60_000L
    val title = when {
        current != null -> "开饭中"
        status.finishedToday -> "今日饭点已结束"
        else -> "下一顿 ${mealHourLabel(status.next.hour)}"
    }
    val detail = if (current != null) {
        "${mealHourLabel(current.hour)}–${BeijingClock.updatedAt(current.endsAtMillis)} · 距结束 $minutes 分钟"
    } else {
        val until = if (minutes < 60) "$minutes 分钟" else {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
        }
        val next = if (status.finishedToday) "明日 ${mealHourLabel(status.next.hour)} · " else ""
        "${next}还有 $until · 北京时间"
    }
    NotebookSection(title = "雨林老奶奶食堂", sticker = R.drawable.season_candle)
    PaperBlock {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                    .background(palette.photoPaper, RoundedCornerShape(1.dp))
                    .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
                    .padding(12.dp),
            ) {
                Text(title, color = palette.ink, fontSize = 20.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = palette.muted, fontSize = 12.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(8.dp))
                MealHourRow(
                    hours = MealSchedule.hours.filter { it <= 12 },
                    slots = 4,
                    currentHour = current?.hour,
                    finishedToday = status.finishedToday,
                    nextHour = status.next.hour,
                    palette = palette,
                )
                Spacer(Modifier.height(8.dp))
                MealHourRow(
                    hours = MealSchedule.hours.filter { it > 12 },
                    slots = 4,
                    currentHour = current?.hour,
                    finishedToday = status.finishedToday,
                    nextHour = status.next.hour,
                    palette = palette,
                )
                Spacer(Modifier.height(8.dp))
                PaperButton("提醒设置", onSettings, modifier = Modifier.align(Alignment.End))
            }
            Tape(Modifier.align(Alignment.TopCenter), width = 58.dp, height = 12.dp, rotation = -3f)
        }
    }
}

/**
 * 一行饭点：按固定列数均分宽度，上午只有三个时右侧留空。
 */
@Composable
private fun MealHourRow(
    hours: List<Int>,
    slots: Int,
    currentHour: Int?,
    finishedToday: Boolean,
    nextHour: Int,
    palette: PaperPalette,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (hour in hours) {
            val active = currentHour == hour
            val past = finishedToday || hour < (currentHour ?: nextHour)
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp)
                    .background(if (active) palette.tabColors[0] else palette.photoPaper, RoundedCornerShape(2.dp))
                    .border(1.dp, if (active) palette.noteAccents[0] else palette.outline, RoundedCornerShape(2.dp))
                    .semantics { stateDescription = if (active) "正在开饭" else if (past) "已结束" else "尚未开始" }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mealHourLabel(hour),
                    color = if (active) palette.tabInk else if (past) palette.muted else palette.ink,
                    fontSize = 13.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        repeat((slots - hours.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}
