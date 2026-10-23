package com.nankki.skydaily.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.meals.MealPreferences
import com.nankki.skydaily.meals.MealReadiness
import com.nankki.skydaily.meals.MealReminderManager
import com.nankki.skydaily.meals.MealReminderPolicy
import com.nankki.skydaily.meals.MealSchedule
import com.nankki.skydaily.ui.journal.mealHourLabel
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.rememberMinuteTime
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealReminderSettings(manager: MealReminderManager) {
    val context = LocalContext.current
    val palette = LocalPaperPalette.current
    var preferences by remember(manager) { mutableStateOf(manager.loadPreferences()) }
    var access by remember(manager) { mutableStateOf(manager.accessState()) }
    var leadInput by remember(manager) { mutableStateOf(preferences.leadMinutes.toString()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val now = rememberMinuteTime()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        preferences = manager.loadPreferences()
        access = manager.accessState()
        leadInput = preferences.leadMinutes.toString()
    }
    val readiness = MealReminderPolicy.readiness(preferences, access.notificationsAllowed, access.exactAlarmAllowed)
    val next = remember(preferences, now, readiness) {
        if (readiness == MealReadiness.Ready) MealSchedule.reminders(preferences, now).firstOrNull() else null
    }
    val lead = leadInput.toIntOrNull()
    val validLead = lead != null && lead in 0..60
    val persist: (MealPreferences) -> Unit = { value ->
        manager.savePreferences(value)
        preferences = manager.loadPreferences()
        access = manager.accessState()
        problem = null
    }
    val saveLead: () -> Unit = {
        val value = leadInput.toIntOrNull()
        if (value != null && value in 0..60) persist(preferences.copy(leadMinutes = value))
        else problem = "请输入 0–60 的整数"
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("每天提醒", color = palette.ink, fontSize = 14.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
            PaperButton(if (preferences.enabled) "关闭" else "开启", onClick = {
                if (!preferences.enabled && preferences.hours.isEmpty()) problem = "请先选择要提醒的饭点"
                else persist(preferences.copy(enabled = !preferences.enabled))
            })
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (hour in MealSchedule.hours) {
                val selected = hour in preferences.hours
                Box(
                    Modifier.widthIn(min = 76.dp).heightIn(min = 44.dp)
                        .background(if (selected) palette.tabColors[0] else palette.paper, RoundedCornerShape(2.dp))
                        .border(1.dp, if (selected) palette.noteAccents[0] else palette.outline, RoundedCornerShape(2.dp))
                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = {
                            val hours = if (selected) preferences.hours - hour else preferences.hours + hour
                            persist(preferences.copy(hours = hours, enabled = preferences.enabled && hours.isNotEmpty()))
                        })
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (selected) "✓ ${mealHourLabel(hour)}" else mealHourLabel(hour),
                        color = if (selected) palette.tabInk else palette.ink,
                        fontSize = 13.sp,
                        lineHeight = 24.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("提前提醒（分钟）", color = palette.muted, fontSize = 11.sp, lineHeight = 20.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = leadInput,
                onValueChange = { value ->
                    if (value.length <= 2 && value.all(Char::isDigit)) {
                        leadInput = value
                        problem = null
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(palette.paper, RoundedCornerShape(2.dp))
                    .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(color = palette.ink, fontSize = 14.sp, lineHeight = 24.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveLead() }),
            )
            PaperButton("保存", onClick = saveLead, enabled = validLead && lead != preferences.leadMinutes)
        }
        Spacer(Modifier.height(8.dp))
        val status = problem ?: if (!validLead) "请输入 0–60 的整数" else when (readiness) {
            MealReadiness.Off -> "提醒已关闭"
            MealReadiness.NoMeals -> "请选择要提醒的饭点"
            MealReadiness.NotificationBlocked -> "通知尚未允许，提醒未生效"
            MealReadiness.ExactAlarmBlocked -> "精确提醒尚未允许，提醒未生效"
            MealReadiness.Ready -> next?.let {
                val day = if (BeijingClock.today(it.triggerAtMillis) == BeijingClock.today(now)) "今天" else "明天"
                "下次 $day ${BeijingClock.updatedAt(it.triggerAtMillis)} 提醒 · ${mealHourLabel(it.meal.hour)} 饭点"
            } ?: "暂无待提醒饭点"
        }
        Text(status, color = palette.muted, fontSize = 11.sp, lineHeight = 20.sp)
        if (preferences.enabled) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PaperButton(if (access.notificationsAllowed) "通知设置" else "允许通知", onClick = {
                    openSystemSettings(context, manager.notificationSettingsIntent())
                })
                if (Build.VERSION.SDK_INT >= 31) PaperButton(if (access.exactAlarmAllowed) "提醒设置" else "允许精确提醒", onClick = {
                    openSystemSettings(context, manager.exactAlarmSettingsIntent())
                })
            }
        }
    }
}
