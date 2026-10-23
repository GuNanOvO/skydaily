package com.nankki.skydaily.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.nankki.skydaily.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.NoticeVersion
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun NoticeCard(notice: NoticeVersion, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPaperPalette.current
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(top = 8.dp).fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.noticePaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = notice.title,
                    color = palette.ink,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = BeijingClock.noticeDate(notice.publishedAt),
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = JournalFormat.noticePreview(notice.message),
                color = palette.ink.copy(alpha = 0.86f),
                fontSize = 12.sp,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            PaperDivider(Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("查看全文", color = palette.muted, fontSize = 11.sp, lineHeight = 22.sp)
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up_right),
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Tape(Modifier.align(Alignment.TopCenter), width = 58.dp, height = 16.dp, rotation = 3f)
    }
}
