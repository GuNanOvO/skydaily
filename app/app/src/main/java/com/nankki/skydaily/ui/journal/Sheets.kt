package com.nankki.skydaily.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.data.Media
import com.nankki.skydaily.data.NoticeVersion
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun GuideSheet(
    detail: TaskDetail,
    baseUrl: String,
    onClose: () -> Unit,
    onOpenImage: (List<String>, Int) -> Unit,
) {
    val palette = LocalPaperPalette.current
    val uriHandler = LocalUriHandler.current
    var variantIndex by remember(detail) { mutableIntStateOf(-1) }
    val variant = detail.variants.getOrNull(variantIndex)
    val sections = variant?.sections ?: detail.sections
    val images = variant?.images ?: detail.images
    val links = (variant?.links ?: detail.links).filter { it.startsWith("http") }

    ModalScrim(onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .hardShadow(palette.shadowStrong, dx = 1.dp, dy = 3.dp)
                .background(palette.paper, RoundedCornerShape(2.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
                .consumeClicks(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.photoPaper)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("任务指南", color = palette.muted, fontSize = 11.sp, lineHeight = 24.sp)
                Spacer(Modifier.weight(1f))
                PaperButton("关闭", onClose)
            }
            PaperDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = variant?.title?.ifBlank { detail.title } ?: detail.title,
                    color = palette.ink,
                    fontSize = 20.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                if (detail.variants.isNotEmpty()) {
                    Text(
                        text = "选择对应情况",
                        color = palette.muted,
                        fontSize = 11.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        detail.variants.forEachIndexed { index, item ->
                            PaperButton(
                                text = item.label.ifBlank { "情况 ${index + 1}" },
                                onClick = { variantIndex = if (variantIndex == index) -1 else index },
                                tone = if (variantIndex == index) palette.tabColors[2] else null,
                                fontSize = 11,
                            )
                        }
                    }
                }
                sections.forEach { section ->
                    if (section.label.isNotBlank()) {
                        Text(
                            text = section.label,
                            color = palette.muted,
                            fontSize = 12.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    Text(
                        text = section.text,
                        color = palette.ink,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                images.forEachIndexed { index, image ->
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(1.dp))
                            .clickable { onOpenImage(images, index) },
                    ) {
                        RemoteImage(
                            url = Media.absolute(baseUrl, image),
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
                if (!variant?.videos.isNullOrEmpty() || variant == null && detail.videos.isNotEmpty()) {
                    Text(
                        text = "视频指引请在网页端查看。",
                        color = palette.muted,
                        fontSize = 11.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (links.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        links.forEachIndexed { index, link ->
                            PaperButton(
                                text = if (links.size == 1) "打开相关链接" else "链接 ${index + 1}",
                                onClick = { uriHandler.openUri(link) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun NoticeSheet(
    notice: NoticeVersion,
    onClose: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    val lines = remember(notice.message) { noticeLines(notice.message) }
    ModalScrim(onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .hardShadow(palette.shadowStrong, dx = 1.dp, dy = 3.dp)
                .background(palette.noticePaper, RoundedCornerShape(2.dp))
                .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
                .consumeClicks(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.photoPaper)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = BeijingClock.noticeDate(notice.publishedAt),
                    color = palette.muted,
                    fontSize = 11.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.weight(1f))
                PaperButton("关闭", onClose)
            }
            PaperDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = notice.title,
                    color = palette.ink,
                    fontSize = 20.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                lines.forEach { line ->
                    when (line.kind) {
                        NoticeLineKind.Heading -> Text(
                            text = line.text,
                            color = palette.ink,
                            fontSize = 16.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp),
                        )

                        NoticeLineKind.Item -> Text(
                            text = line.text,
                            color = palette.ink,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                        )

                        NoticeLineKind.Bullet -> Text(
                            text = line.text,
                            color = palette.ink,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        )

                        NoticeLineKind.Paragraph -> Text(
                            text = line.text,
                            color = palette.ink,
                            fontSize = 14.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ImageLightbox(
    baseUrl: String,
    images: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    if (images.isEmpty()) return
    val palette = LocalPaperPalette.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = Media.absolute(baseUrl, images[page]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${images.size}",
                color = palette.paper,
                fontSize = 12.sp,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.weight(1f))
            PaperButton("关闭", onClose)
        }
    }
}

@Composable
private fun ModalScrim(
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalPaperPalette.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private fun Modifier.consumeClicks(): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {},
)
