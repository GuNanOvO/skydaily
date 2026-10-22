package com.nankki.skydaily.ui.paper

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.R
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import java.time.LocalDate

@Composable
fun NotebookHeader(
    title: String,
    date: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    darkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    @DrawableRes sticker: Int = R.drawable.sky_cape_handdrawn,
    subtitle: String? = null,
    updatedAt: String? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuledText(
                            title,
                            fontSize = if (compact) 20.sp else 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Box(Modifier.size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            Sticker(
                                sticker,
                                size = 48.dp,
                                rotation = -8f,
                                modifier = Modifier.requiredSize(48.dp),
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        subtitle?.let { RuledText(it, fontSize = 12.sp, muted = true) }
                        updatedAt?.let { RuledText(it, fontSize = 11.sp, muted = true) }
                    }
                }
                date?.let { DateSticker(it) }
            }
            if (onRefresh != null || onToggleTheme != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    onRefresh?.let {
                        PaperButton(
                            text = if (refreshing) "刷新中" else "刷新",
                            onClick = it,
                            enabled = !refreshing,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    onToggleTheme?.let { toggle ->
                        Box(
                            Modifier.size(44.dp)
                                .semantics { contentDescription = if (darkTheme) "切换浅色" else "切换深色" }
                                .clickable(role = Role.Button, onClick = toggle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Sticker(
                                if (darkTheme) R.drawable.theme_sun else R.drawable.theme_moon,
                                size = 48.dp,
                                modifier = Modifier.requiredSize(48.dp),
                                rotation = if (darkTheme) 8f else -8f,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSticker(date: String) {
    val palette = LocalPaperPalette.current
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
    val scale = LocalDensity.current.fontScale
    Column(
        Modifier.width(62.dp * scale).rotate(2f)
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${parsed.year} / ${parsed.monthValue.toString().padStart(2, '0')}", color = palette.muted, fontSize = 9.sp, lineHeight = 14.sp)
        Text(parsed.dayOfMonth.toString().padStart(2, '0'), color = palette.ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
        Text(BeijingClock.weekday(date), color = palette.muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
fun NotebookSection(title: String, @DrawableRes sticker: Int) {
    PaperBlock {
        Row(
            Modifier.fillMaxWidth().heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuledText(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 48.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Sticker(
                    sticker,
                    size = 48.dp,
                    rotation = -6f,
                    modifier = Modifier.requiredSize(48.dp),
                )
            }
        }
    }
}
