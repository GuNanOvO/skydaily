package com.nankki.skydaily.ui.journal

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nankki.skydaily.data.DailyData
import com.nankki.skydaily.data.SeasonGuidePolicy
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.RuledText
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlin.math.roundToInt

/**
 * 季节蜡烛区块：今日地图 + 官方 H5 指南。
 *
 * 指南地址来自 [DailyData.taskDetails] 中 link-policy 命中的条目（不使用 candles），
 * 校验失败时退回策略兜底地址；两者都不可用时只显示原生错误说明，不伪造内容。
 * 官方 H5 是全 App 唯一允许的 WebView 例外，导航被限制在策略范围内。
 *
 * 宿主应在页面切换之外 hoist 一个 [rememberSeasonGuideSession] 并传入 [session]，
 * 这样底部 Tab 往返时官方 H5 不会重新初始化；不传时组件使用自身组合生命周期的默认会话。
 *
 * @param date 当日日期键，用于“同一天+同一地图只自动选图一次”；无日期时仍按地图去重。
 */
@Composable
fun SeasonGuide(
    data: DailyData,
    modifier: Modifier = Modifier,
    date: String? = null,
    session: SeasonGuideSession? = null,
) {
    val context = LocalContext.current
    val policy = remember(context) { loadSeasonGuidePolicy(context) }
    // resolve() 已规范化；这里再收敛一次，确保会话缓存键始终是规范 URL。
    val urls = remember(data, policy) {
        val current = policy
        if (current == null) {
            emptyList()
        } else {
            current.resolve(data).mapNotNull(current::normalize).distinct()
        }
    }
    val guideSession = session ?: rememberSeasonGuideSession()

    LaunchedEffect(guideSession, urls) { guideSession.retain(urls) }

    Column(modifier = modifier.fillMaxWidth()) {
        data.seasonCandleMap?.takeIf { it.isNotBlank() }?.let { map ->
            RuledText("今日季节蜡烛所在地图：$map")
        }
        when {
            policy == null -> GuideUnavailable("缺少官方指南策略，暂时无法加载季节指南。")
            urls.isEmpty() -> GuideUnavailable("官方指南链接未通过校验，暂时无法加载。")
            else -> urls.forEach { url ->
                key(url) {
                    GuideFrame(
                        url = url,
                        policy = policy,
                        session = guideSession,
                        date = date,
                        map = data.seasonCandleMap,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideFrame(
    url: String,
    policy: SeasonGuidePolicy,
    session: SeasonGuideSession,
    date: String?,
    map: String?,
) {
    val palette = LocalPaperPalette.current
    val entry = session.entry(url, policy)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp)),
    ) {
        GuideWebView(session = session, entry = entry, date = date, map = map)
    }
}

@Composable
private fun GuideWebView(
    session: SeasonGuideSession,
    entry: GuideEntry,
    date: String?,
    map: String?,
) {
    val status = entry.status
    // 缓存命中的视图不重复等待首帧；首次创建仍让任务列表先落一帧。
    var started by remember(entry) { mutableStateOf(status is GuideStatus.Ready) }

    LaunchedEffect(entry) {
        if (!started) {
            withFrameNanos { }
            withFrameNanos { }
            started = true
        }
    }
    LaunchedEffect(session, entry, date, map) {
        session.setAutoMapTarget(entry, date, map)
    }

    Column(Modifier.fillMaxWidth()) {
        // 状态 UI 不放进 667x375 画布：小屏大字号时重试按钮不会被裁切。
        when (status) {
            GuideStatus.Loading -> GuideLoading()
            is GuideStatus.Failed -> GuideFailure(status.message) { session.retry(entry) }
            GuideStatus.Ready -> Unit
        }
        key(entry) {
            if (started && status !is GuideStatus.Failed) {
                GuideCanvasHost(
                    expanded = status is GuideStatus.Ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AndroidView(
                        factory = { context -> session.acquire(entry, context) },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        onRelease = { view -> session.release(entry, view) },
                    )
                }
            }
        }
    }
}

private const val GUIDE_DESIGN_WIDTH = 667f
private const val GUIDE_DESIGN_HEIGHT = 375f

/**
 * 始终以官方 667x375 比例测量 WebView（保证 H5 视口正确），但在加载/失败状态
 * 只向上报告 0 高度，把可读的状态 UI 留给兄弟节点决定；成功后才展示整块画布。
 */
@Composable
private fun GuideCanvasHost(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val canvasHeight = (width * (GUIDE_DESIGN_HEIGHT / GUIDE_DESIGN_WIDTH)).roundToInt()
        val placeable = measurables.firstOrNull()?.measure(Constraints.fixed(width, canvasHeight))
        val reportHeight = if (expanded) canvasHeight else 0
        layout(width, reportHeight) {
            placeable?.place(0, 0)
        }
    }
}

@Composable
private fun GuideLoading() {
    val palette = LocalPaperPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.photoPaper)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = palette.accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在加载官方指南…",
            color = palette.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun GuideFailure(message: String, onRetry: () -> Unit) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.paper)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            color = palette.ink,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(10.dp))
        PaperButton(text = "重试", onClick = onRetry)
    }
}

@Composable
private fun GuideUnavailable(message: String) {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.noticePaper, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .padding(12.dp),
    ) {
        Text(
            text = message,
            color = palette.muted,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        )
    }
}

private fun loadSeasonGuidePolicy(context: Context): SeasonGuidePolicy? = runCatching {
    context.applicationContext.assets.open(SeasonGuidePolicy.ASSET_PATH)
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}.getOrNull()?.let { SeasonGuidePolicy.parse(it) }
