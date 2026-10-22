package com.nankki.skydaily.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.NoticeVersion
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.SectionHeader
import com.nankki.skydaily.ui.paper.Sticker
import com.nankki.skydaily.ui.paper.Tape
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun CandleCard(
    detail: TaskDetail,
    baseUrl: String,
    onOpen: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalPaperPalette.current
    val firstImage = detail.images.firstOrNull()
    val imageHeight = if (compact) 120.dp else 148.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            .clickable(onClick = onOpen)
            .padding(if (compact) 10.dp else 12.dp),
    ) {
        Text(
            text = detail.title.ifBlank { "位置参考" },
            color = palette.ink,
            fontSize = if (compact) 13.sp else 14.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium,
        )
        detail.sections.firstOrNull { it.text.isNotBlank() }?.let { section ->
            Text(
                text = section.text.replace('\n', ' '),
                color = palette.muted,
                fontSize = 12.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!firstImage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .clickable { onOpenImage(detail.images, 0) },
                contentAlignment = Alignment.Center,
            ) {
                RemoteImage(
                    url = com.nankki.skydaily.data.Media.absolute(
                        baseUrl,
                        com.nankki.skydaily.data.Media.thumb(firstImage),
                    ),
                    modifier = Modifier.fillMaxWidth().height(imageHeight),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = if (detail.images.size > 1) "查看 ${detail.images.size} 张位置图 ↗" else "查看位置参考 ↗",
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
fun NoticeCard(
    notice: NoticeVersion?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    if (notice == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "暂未收到新公告",
                color = palette.muted,
                fontSize = 12.sp,
                lineHeight = 24.sp,
            )
        }
        return
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
                .background(palette.noticePaper, RoundedCornerShape(1.dp))
                .border(1.dp, palette.outline.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Text("查看全文 ↗", color = palette.muted, fontSize = 11.sp, lineHeight = 22.sp)
        }
        Tape(
            modifier = Modifier.align(Alignment.TopCenter),
            width = 58.dp,
            height = 16.dp,
            rotation = 3f,
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    sticker: Int? = null,
    stickerSize: androidx.compose.ui.unit.Dp = 44.dp,
    stickerRotation: Float = 0f,
) {
    SectionHeader(
        title = title,
        modifier = modifier.padding(top = 8.dp),
        trailing = trailing,
        sticker = sticker,
        stickerSize = stickerSize,
        stickerRotation = stickerRotation,
    )
}

@Composable
fun FloatingHintCard(
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(1.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(1.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sticker(
            res = com.nankki.skydaily.R.drawable.sky_jelly_handdrawn,
            size = 34.dp,
            rotation = -6f,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "在游戏内点按悬浮球，今日任务会从右侧滑出。",
            color = palette.muted,
            fontSize = 12.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun FooterCard(
    fetchedAt: Long,
    versionName: String,
    onOpenSite: () -> Unit,
    onOpenFeedback: () -> Unit,
    onClearCache: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.outline.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "图文来源于游戏内「小精灵」，版权归原权利人所有。",
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 20.sp,
        )
        Text(
            text = if (fetchedAt > 0) {
                "本地缓存于 ${BeijingClock.updatedDate(fetchedAt)} ${BeijingClock.updatedAt(fetchedAt)}"
            } else {
                "尚未写入本地缓存"
            },
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaperButton("网站", onOpenSite)
            Spacer(Modifier.size(6.dp))
            PaperButton("反馈", onOpenFeedback)
            if (onClearCache != null) {
                Spacer(Modifier.size(6.dp))
                PaperButton("清除并刷新", onClearCache)
            }
            if (onOpenApp != null) {
                Spacer(Modifier.size(6.dp))
                PaperButton("打开应用", onOpenApp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("光遇每日任务", color = palette.muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("v$versionName", color = palette.muted, fontSize = 10.sp)
        }
    }
}
