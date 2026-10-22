package com.nankki.skydaily.ui.journal

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nankki.skydaily.data.DailyData
import com.nankki.skydaily.data.SeasonGuidePolicy
import com.nankki.skydaily.ui.paper.LocalPaperScrollState
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.paper.PaperDivider
import com.nankki.skydaily.ui.paper.RuledText
import com.nankki.skydaily.ui.paper.hardShadow
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import kotlin.math.abs

/**
 * 季节蜡烛区块：今日地图 + 官方 H5 指南。
 *
 * 指南地址来自 [DailyData.taskDetails] 中 link-policy 命中的条目（不使用 candles），
 * 校验失败时退回策略兜底地址；两者都不可用时只显示原生错误说明，不伪造内容。
 * 官方 H5 是全 App 唯一允许的 WebView 例外，导航被限制在策略范围内。
 *
 * @param onOpenGuideFullscreen 宿主提供整页查看入口时传入；主界面与悬浮窗的窗口归属不同，
 * 全屏浮层由宿主决定（见 [SeasonGuideFullscreen]），本组件自身不创建 Dialog。
 */
@Composable
fun SeasonGuide(
    data: DailyData,
    modifier: Modifier = Modifier,
    onOpenGuideFullscreen: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val policy = remember(context) { loadSeasonGuidePolicy(context) }
    val urls = remember(data, policy) { policy?.resolve(data).orEmpty() }

    Column(modifier = modifier.fillMaxWidth()) {
        data.seasonCandleMap?.takeIf { it.isNotBlank() }?.let { map ->
            RuledText("今日季节蜡烛所在地图：$map")
        }
        when {
            policy == null -> GuideUnavailable("缺少官方指南策略，暂时无法加载季节指南。")
            urls.isEmpty() -> GuideUnavailable("官方指南链接未通过校验，暂时无法加载。")
            else -> urls.forEach { url ->
                GuideFrame(
                    url = url,
                    policy = policy,
                    onOpenFullscreen = onOpenGuideFullscreen,
                )
            }
        }
    }
}

/**
 * 宿主整页浮层里的同一份官方指南：纯 Compose 视图，不使用 Dialog，
 * 因此 Activity 与悬浮窗服务上下文都能安全承载。返回键由宿主负责。
 */
@Composable
fun SeasonGuideFullscreen(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val context = LocalContext.current
    val policy = remember(context) { loadSeasonGuidePolicy(context) }
    val allowed = policy != null && policy.normalize(url) != null

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "季节蜡烛探索指南",
                color = palette.ink,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            PaperButton(text = "关闭", onClick = onClose, height = 40.dp)
        }
        PaperDivider()
        if (policy != null && allowed) {
            val controller = remember(url, policy) { GuideController(url) }
            GuideWebView(
                controller = controller,
                policy = policy,
                handoffToParent = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "官方指南链接未通过校验，无法打开。",
                    color = palette.muted,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun GuideFrame(
    url: String,
    policy: SeasonGuidePolicy,
    onOpenFullscreen: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPaperPalette.current
    val controller = remember(url, policy) { GuideController(url) }
    val openFullscreen = onOpenFullscreen
    val frameHeight = rememberFrameHeight()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(palette.shadow, dx = 1.dp, dy = 2.dp)
            .background(palette.photoPaper, RoundedCornerShape(2.dp))
            .border(1.dp, palette.outline, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "季节蜡烛探索指南",
                color = palette.ink,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            if (openFullscreen != null) {
                PaperButton(
                    text = "整页",
                    onClick = { openFullscreen(url) },
                    height = 40.dp,
                )
            }
        }
        PaperDivider()
        GuideWebView(
            controller = controller,
            policy = policy,
            handoffToParent = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(frameHeight),
        )
    }
}

@Composable
private fun GuideWebView(
    controller: GuideController,
    policy: SeasonGuidePolicy,
    handoffToParent: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val parentScroll = LocalPaperScrollState.current
    var started by remember(controller) { mutableStateOf(false) }

    // 任务区先落一帧再启动 H5，避免 WebView 初始化与今日任务首帧争用主线程。
    LaunchedEffect(controller) {
        withFrameNanos { }
        withFrameNanos { }
        started = true
    }

    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.webView?.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.background(Color.White)) {
        if (started) {
            key(controller.generation) {
                AndroidView(
                    factory = { context ->
                        createGuideWebView(
                            context = context,
                            policy = policy,
                            controller = controller,
                            onScrollParent = if (handoffToParent && parentScroll != null) {
                                { delta -> parentScroll.dispatchRawDelta(delta) }
                            } else null,
                        ).also { view ->
                            controller.attach(view)
                            view.loadUrl(controller.url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view -> controller.detach(view) },
                )
            }
        }
        when (val status = controller.status) {
            GuideStatus.Loading -> GuideLoading()
            is GuideStatus.Failed -> GuideFailure(status.message, controller::retry)
            GuideStatus.Ready -> Unit
        }
    }
}

@Composable
private fun GuideLoading() {
    val palette = LocalPaperPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.photoPaper),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun GuideFailure(message: String, onRetry: () -> Unit) {
    val palette = LocalPaperPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = palette.ink,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
        PaperButton(text = "重试", onClick = onRetry, height = 40.dp)
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

@Composable
private fun rememberFrameHeight(): Dp {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) {
        (containerHeight.toDp() * 0.6f).coerceIn(320.dp, 600.dp)
    }
}

private fun loadSeasonGuidePolicy(context: Context): SeasonGuidePolicy? = runCatching {
    context.applicationContext.assets.open(SeasonGuidePolicy.ASSET_PATH)
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}.getOrNull()?.let { SeasonGuidePolicy.parse(it) }

private sealed interface GuideStatus {
    data object Loading : GuideStatus
    data object Ready : GuideStatus
    data class Failed(val message: String) : GuideStatus
}

/** WebView 生命周期与加载状态的唯一持有者，供 Compose 状态与重试按钮共用。 */
private class GuideController(val url: String) {
    var status by mutableStateOf<GuideStatus>(GuideStatus.Loading)
        private set
    var generation by mutableIntStateOf(0)
        private set
    var webView: WebView? = null
        private set
    private var disposed = false

    fun attach(view: WebView) {
        if (disposed) return
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) webView = null
    }

    fun markLoading(view: WebView) {
        if (!disposed && webView === view) status = GuideStatus.Loading
    }

    fun markReady(view: WebView) {
        if (!disposed && webView === view && status is GuideStatus.Loading) {
            status = GuideStatus.Ready
        }
    }

    fun markFailed(view: WebView, message: String) {
        if (!disposed && webView === view) status = GuideStatus.Failed(message)
    }

    fun retry() {
        if (disposed) return
        val view = webView
        status = GuideStatus.Loading
        if (view == null) {
            // 渲染进程退出或尚未创建：重建 WebView 后由工厂重新加载。
            generation += 1
        } else {
            runCatching { view.stopLoading() }
            view.loadUrl(url)
        }
    }

    fun onRendererGone(view: WebView) {
        if (disposed) return
        detach(view)
        (view.parent as? ViewGroup)?.removeView(view)
        view.post { runCatching { view.destroy() } }
        status = GuideStatus.Failed("官方指南渲染进程已退出，请重试。")
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        val view = webView ?: return
        webView = null
        runCatching { view.stopLoading() }
        runCatching { view.loadUrl("about:blank") }
        runCatching { view.clearHistory() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }
}

@Suppress("DEPRECATION")
private fun createGuideWebView(
    context: Context,
    policy: SeasonGuidePolicy,
    controller: GuideController,
    onScrollParent: ((Float) -> Unit)?,
): WebView {
    val view = PolicyGuideWebView(context, onScrollParent)
    // WebView 默认 wrap_content 会让滚动纸面内的 H5 视口高度为 0。
    view.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    view.setBackgroundColor(android.graphics.Color.WHITE)
    view.isVerticalScrollBarEnabled = true
    view.settings.apply {
        javaScriptEnabled = true
        // 官方 H5 依赖 localStorage；不启用数据库、文件或内容访问。
        domStorageEnabled = true
        databaseEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        setGeolocationEnabled(false)
        setSaveFormData(false)
        builtInZoomControls = false
        displayZoomControls = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 保留官方原始配色，不跟随 App 深色主题。
            setAlgorithmicDarkeningAllowed(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDark(WebSettings.FORCE_DARK_OFF)
        }
    }
    view.webChromeClient = GuideChromeClient()
    view.webViewClient = GuideWebClient(controller, policy)
    return view
}

/** WebView 到达纵向边界后直接驱动纸面的滚动状态，横向交互留给官方 H5。 */
private class PolicyGuideWebView(
    context: Context,
    private val onScrollParent: ((Float) -> Unit)?,
) : WebView(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var scrollingPaper = false
    // 判定方向后再交给 H5，避免纵向拖动被图片的按下事件当成点击。
    private var pendingDown: MotionEvent? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scrollParent = onScrollParent ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollingPaper = false
                downX = event.rawX
                downY = event.rawY
                lastY = downY
                pendingDown?.recycle()
                pendingDown = MotionEvent.obtain(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalY = event.rawY - downY
                val delta = event.rawY - lastY
                lastY = event.rawY
                val vertical = abs(totalY) > touchSlop && abs(totalY) > abs(event.rawX - downX)
                val atEdge = totalY > 0 && !canScrollVertically(-1) ||
                    totalY < 0 && !canScrollVertically(1)
                if (!scrollingPaper && vertical && atEdge) {
                    if (pendingDown == null) {
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                    } else {
                        pendingDown?.recycle()
                        pendingDown = null
                    }
                    scrollingPaper = true
                }
                if (scrollingPaper) {
                    scrollParent(-delta)
                    return true
                }
                if (pendingDown != null) {
                    if (abs(totalY) <= touchSlop && abs(event.rawX - downX) <= touchSlop) return true
                    deliverDown()
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (scrollingPaper) {
                    scrollingPaper = false
                    return true
                }
                deliverDown()
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (pendingDown != null || scrollingPaper) {
                    pendingDown?.recycle()
                    pendingDown = null
                    scrollingPaper = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun deliverDown() {
        pendingDown?.let {
            super.onTouchEvent(it)
            it.recycle()
            pendingDown = null
        }
    }

    override fun onDetachedFromWindow() {
        pendingDown?.recycle()
        pendingDown = null
        super.onDetachedFromWindow()
    }
}

private class GuideChromeClient : WebChromeClient()

private class GuideWebClient(
    private val controller: GuideController,
    private val policy: SeasonGuidePolicy,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        // 顶级导航只允许策略内的官方地址；其余链接、intent、外部站一律拒绝，且不启动外部应用。
        val blocked = !policy.isAllowedTopLevel(request.url?.toString())
        if (blocked) controller.markFailed(view, "官方指南跳转被阻止，请重试。")
        return blocked
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        controller.markLoading(view)
    }


    override fun onPageFinished(view: WebView, url: String?) {
        controller.markReady(view)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (!request.isForMainFrame) return
        controller.markFailed(view, "官方指南加载失败（${error.errorCode}）。")
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame) return
        if (errorResponse.statusCode >= 400) {
            controller.markFailed(view, "官方指南暂时不可用（HTTP ${errorResponse.statusCode}）。")
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
        controller.onRendererGone(view)
        return true
    }
}
