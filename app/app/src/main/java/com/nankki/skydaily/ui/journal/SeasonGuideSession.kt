package com.nankki.skydaily.ui.journal

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nankki.skydaily.data.SeasonGuidePolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 官方指南 H5 在同一个 Activity 组合内的缓存。
 *
 * 只由 [rememberSeasonGuideSession] 创建，随承载它的组合（Activity 页面或悬浮窗）
 * 一起销毁。底部 Tab/路由切换时 [SeasonGuideSession] 仍然存活，已成功创建的
 * WebView 会从宿主 AndroidView 上摘下并暂停，再次回到同一 URL 时直接复用，
 * 不重新 loadUrl；只有 [refresh] 或新 URL 才会发起新的导航。
 *
 * 类本身不持有 Activity：文件资产使用 applicationContext，视图由 AndroidView 工厂
 * 用宿主 Context 创建，销毁时统一 destroy。
 */
class SeasonGuideSession internal constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val entries = LinkedHashMap<String, GuideEntry>()
    private var disposed = false
    private var lifecycleResumed = true
    private var autoMapScriptLoaded = false
    private var autoMapScript: String? = null

    /** 显式刷新：让已创建（包括当前未挂载）的视图用默认缓存策略重新校验 HTML。 */
    fun refresh() {
        if (disposed) return
        if (!isMainThread()) {
            handler.post { refreshNow() }
            return
        }
        refreshNow()
    }

    private fun refreshNow() {
        if (disposed) return
        entries.values.forEach { entry ->
            if (entry.evictAfterRelease) return@forEach
            entry.autoMapAttempts = 0
            cancelAutoMap(entry)
            if (entry.status is GuideStatus.Failed) {
                // 失败过的 WebView 不复用；重试/刷新都创建全新视图。
                retry(entry)
                return@forEach
            }
            val view = entry.view ?: return@forEach
            // 页面重新加载后回到官方默认地图，允许本轮自动选图重新执行一次。
            entry.appliedKey = null
            entry.autoMapGeneration += 1
            entry.status = GuideStatus.Loading
            runCatching { view.loadUrl(entry.url) }
        }
    }

    internal fun entry(url: String, policy: SeasonGuidePolicy): GuideEntry {
        check(!disposed) { "SeasonGuideSession already disposed" }
        val existing = entries[url]
        if (existing != null) {
            existing.evictAfterRelease = false
            return existing
        }
        val initialStatus =
            if (providerAvailable()) GuideStatus.Loading else GuideStatus.Failed(WEBVIEW_UNAVAILABLE_MESSAGE)
        return GuideEntry(url, policy, initialStatus).also { entries[url] = it }
    }

    /**
     * 取得该 entry 的 WebView。新页面才 loadUrl；已有视图直接复用并通过
     * onResume 恢复，保证底部 Tab 往返不会重新加载官方 H5。
     * 系统 WebView 缺失、被停用或初始化失败时返回 null，由界面显示原生失败卡片。
     */
    internal fun acquire(entry: GuideEntry, context: Context): WebView? {
        check(!disposed) { "SeasonGuideSession already disposed" }
        val cached = entry.view
        if (cached == null || entry.retryRequested || entry.status is GuideStatus.Failed) {
            destroyEntry(entry)
            entry.retryRequested = false
            entry.status = GuideStatus.Loading
            entry.autoMapAttempts = 0
            val created = createGuideWebView(context, entry)
            if (created == null) {
                reportUnavailable(entry)
                return null
            }
            entry.view = created
            entry.hostCount = 1
            entry.destroyed = false
            entry.viewResumed = true
            if (!lifecycleResumed) {
                runCatching { created.onPause() }
                entry.viewResumed = false
            }
            runCatching { created.loadUrl(entry.url) }
            scheduleAutoMap(entry, AUTO_MAP_INITIAL_DELAY_MS)
            return created
        }
        // 从上一个 AndroidView 宿主摘下，交给新的宿主重新挂载。
        runCatching { (cached.parent as? ViewGroup)?.removeView(cached) }
        cached.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        entry.hostCount += 1
        entry.autoMapAttempts = 0
        resumeIfNeeded(entry, cached)
        scheduleAutoMap(entry, AUTO_MAP_INITIAL_DELAY_MS)
        return cached
    }

    /** AndroidView 释放：会话仍存活时只摘下并暂停，不销毁缓存视图。 */
    internal fun release(entry: GuideEntry, view: WebView) {
        if (entry.destroyed) return
        if (entry.view !== view) {
            // 新一轮视图已经接管该 entry；旧的宿主视图不再有任何缓存引用，立即销毁。
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
            runCatching { view.onPause() }
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
            return
        }
        entry.hostCount = (entry.hostCount - 1).coerceAtLeast(0)
        if (entry.hostCount > 0) {
            // 短暂重叠的旧宿主先释放；新宿主仍持有同一个 WebView，不能摘下或暂停。
            return
        }
        cancelAutoMap(entry)
        val destroy = disposed ||
            entry.evictAfterRelease ||
            entry.retryRequested ||
            entry.status is GuideStatus.Failed
        if (destroy) {
            if (entry.evictAfterRelease) entries.remove(entry.url)
            destroyEntry(entry)
        } else {
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
            pauseIfNeeded(entry, view)
        }
    }

    internal fun retry(entry: GuideEntry) {
        if (disposed) return
        entry.retryRequested = true
        entry.status = GuideStatus.Loading
        entry.appliedKey = null
        entry.autoMapGeneration += 1
        entry.autoMapAttempts = 0
        cancelAutoMap(entry)
        if (!entry.attached) {
            destroyEntry(entry)
            entry.retryRequested = false
        }
    }

    /** 仅保留当前数据解析出的 URL；被移除的缓存视图在安全时立即销毁。 */
    internal fun retain(urls: Collection<String>) {
        if (disposed) return
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.url in urls) {
                entry.evictAfterRelease = false
            } else {
                entry.evictAfterRelease = true
                cancelAutoMap(entry)
                if (!entry.attached) {
                    iterator.remove()
                    destroyEntry(entry)
                }
            }
        }
    }

    /** 设置本轮自动选图目标；同一日期/地图只执行一次。 */
    internal fun setAutoMapTarget(entry: GuideEntry, date: String?, map: String?) {
        if (disposed) return
        val cleanMap = map?.trim()?.takeIf { it.isNotEmpty() }
        val key = "${date.orEmpty()}\u0000${cleanMap.orEmpty()}"
        if (entry.autoMapKey == key) return
        entry.autoMapKey = key
        entry.autoMapMap = cleanMap.orEmpty()
        entry.autoMapGeneration += 1
        entry.autoMapAttempts = 0
        cancelAutoMap(entry)
        if (key != entry.appliedKey) {
            scheduleAutoMap(entry, AUTO_MAP_INITIAL_DELAY_MS)
        }
    }

    internal fun onPageStarted(entry: GuideEntry, view: WebView) {
        if (entry.view !== view || entry.destroyed) return
        if (entry.status is GuideStatus.Failed) return
        entry.status = GuideStatus.Loading
        // 新的 document 重新回到官方默认页，本次 document 的自动选图标记作废。
        entry.appliedKey = null
        entry.autoMapGeneration += 1
        entry.autoMapAttempts = 0
        cancelAutoMap(entry)
    }

    internal fun onPageFinished(entry: GuideEntry, view: WebView) {
        if (entry.view !== view || entry.destroyed) return
        if (entry.status !is GuideStatus.Loading) return
        entry.status = GuideStatus.Ready
        scheduleAutoMap(entry, AUTO_MAP_INITIAL_DELAY_MS)
    }

    internal fun markFailed(entry: GuideEntry, view: WebView, message: String) {
        if (entry.view !== view || entry.destroyed) return
        if (entry.status is GuideStatus.Failed) return
        entry.status = GuideStatus.Failed(message)
        cancelAutoMap(entry)
        // 未挂载时没有 Compose 负责移除视图，主动销毁，避免失败视图进入缓存。
        if (!entry.attached) {
            handler.post {
                if (!disposed && entry.status is GuideStatus.Failed) destroyEntry(entry)
            }
        }
    }

    internal fun onPause() {
        if (disposed) return
        lifecycleResumed = false
        entries.values.forEach { entry ->
            cancelAutoMap(entry)
            if (entry.attached) {
                entry.view?.let { view -> pauseIfNeeded(entry, view) }
            }
        }
    }

    internal fun onResume() {
        if (disposed) return
        lifecycleResumed = true
        entries.values.forEach { entry ->
            if (entry.attached) {
                entry.view?.let { view -> resumeIfNeeded(entry, view) }
                scheduleAutoMap(entry, AUTO_MAP_INITIAL_DELAY_MS)
            }
        }
    }

    private fun resumeIfNeeded(entry: GuideEntry, view: WebView) {
        if (!lifecycleResumed || entry.viewResumed) return
        runCatching { view.onResume() }
        entry.viewResumed = true
    }

    private fun pauseIfNeeded(entry: GuideEntry, view: WebView) {
        if (!entry.viewResumed) return
        runCatching { view.onPause() }
        entry.viewResumed = false
    }

    /** 只在组合宿主真正卸载时调用；销毁所有缓存与已挂载视图且只执行一次。 */
    fun dispose() {
        if (disposed) return
        if (!isMainThread()) {
            handler.post { disposeNow() }
            return
        }
        disposeNow()
    }

    private fun disposeNow() {
        if (disposed) return
        disposed = true
        lifecycleResumed = false
        entries.values.forEach { entry ->
            entry.evictAfterRelease = true
            cancelAutoMap(entry)
            // 已挂载的视图仍归 AndroidView 宿主；等 onRelease 再销毁。
            if (!entry.attached) destroyEntry(entry)
        }
        entries.clear()
        handler.removeCallbacksAndMessages(null)
    }

    private fun scheduleAutoMap(entry: GuideEntry, delayMs: Long) {
        if (disposed || !lifecycleResumed || !entry.attached || entry.destroyed || entry.evictAfterRelease) return
        val key = entry.autoMapKey ?: return
        if (entry.appliedKey == key) return
        if (entry.status !is GuideStatus.Ready) return
        if (entry.autoMapAttempts >= AUTO_MAP_MAX_ATTEMPTS) return
        if (entry.autoMapRunnable != null) return
        val runnable = Runnable {
            entry.autoMapRunnable = null
            applyAutoMap(entry)
        }
        entry.autoMapRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun applyAutoMap(entry: GuideEntry) {
        if (disposed || !lifecycleResumed || !entry.attached || entry.destroyed || entry.evictAfterRelease) return
        val key = entry.autoMapKey ?: return
        val map = entry.autoMapMap ?: return
        if (entry.appliedKey == key) return
        if (entry.status !is GuideStatus.Ready) return
        if (entry.autoMapAttempts >= AUTO_MAP_MAX_ATTEMPTS) return
        val view = entry.view ?: return
        val script = loadAutoMapScript() ?: return
        entry.autoMapAttempts += 1
        val js = "($script)(${JsonPrimitive(map)})"
        val generation = entry.autoMapGeneration
        runCatching {
            view.evaluateJavascript(js) { raw ->
                onAutoMapResult(entry, view, key, generation, raw)
            }
        }
    }

    private fun onAutoMapResult(entry: GuideEntry, view: WebView, key: String, generation: Int, raw: String?) {
        if (disposed || entry.destroyed || entry.view !== view) return
        if (entry.autoMapKey != key || entry.autoMapGeneration != generation) return
        when (autoMapStatus(raw)) {
            "selected", "already-selected", "not-found" -> entry.appliedKey = key
            "not-ready" -> scheduleAutoMap(entry, AUTO_MAP_RETRY_DELAY_MS)
            else -> Unit
        }
    }

    private fun loadAutoMapScript(): String? {
        if (autoMapScriptLoaded) return autoMapScript
        autoMapScriptLoaded = true
        autoMapScript = runCatching {
            appContext.assets.open(AUTO_MAP_ASSET_PATH)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrNull()
            ?.trim()
            ?.removeSuffix(";")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return autoMapScript
    }

    private fun cancelAutoMap(entry: GuideEntry) {
        entry.autoMapRunnable?.let { handler.removeCallbacks(it) }
        entry.autoMapRunnable = null
    }

    private fun destroyEntry(entry: GuideEntry) {
        cancelAutoMap(entry)
        val view = entry.view
        entry.view = null
        entry.hostCount = 0
        entry.destroyed = true
        if (view != null) {
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
            pauseIfNeeded(entry, view)
            runCatching { view.stopLoading() }
            runCatching { view.destroy() }
        }
    }

    private fun reportUnavailable(entry: GuideEntry) {
        if (disposed) return
        // 视图工厂运行在应用阶段，延迟到下一次消息循环再改状态，避免组合期间写状态。
        handler.post {
            if (!disposed && entry.view == null && entry.status !is GuideStatus.Failed) {
                entry.status = GuideStatus.Failed(WEBVIEW_INIT_FAILED_MESSAGE)
                cancelAutoMap(entry)
            }
        }
    }

    /**
     * 系统 WebView 提供方存在且未被停用。提供方查询失败按不可用处理；
     * 包启用状态无法查询时仍交给构造检查，避免正常 ROM 被误判。
     */
    private fun providerAvailable(): Boolean {
        val packageInfo = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() ?: return false
        return runCatching {
            appContext.packageManager.getApplicationInfo(packageInfo.packageName, 0).enabled
        }.getOrDefault(true)
    }

    @Suppress("DEPRECATION")
    private fun createGuideWebView(context: Context, entry: GuideEntry): WebView? {
        val view = runCatching { WebView(context) }.getOrNull() ?: return null
        return runCatching {
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
                // 显式 refresh 也走服务器校验；不绕过 no-cache / ETag。
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 保留官方原始配色，不跟随 App 深色主题。
                    setAlgorithmicDarkeningAllowed(false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setForceDark(WebSettings.FORCE_DARK_OFF)
                }
            }
            view.webChromeClient = GuideChromeClient()
            view.webViewClient = GuideWebClient(this, entry)
            view
        }.getOrElse {
            runCatching { view.destroy() }
            null
        }
    }

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private class GuideChromeClient : WebChromeClient()

    private class GuideWebClient(
        private val session: SeasonGuideSession,
        private val entry: GuideEntry,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            // 顶级导航只允许策略内的官方地址；其余链接、intent、外部站一律拒绝，且不启动外部应用。
            val blocked = !entry.policy.isAllowedTopLevel(request.url?.toString())
            if (blocked) session.markFailed(entry, view, "官方指南跳转被阻止，请重试。")
            return blocked
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            session.onPageStarted(entry, view)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            session.onPageFinished(entry, view)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            session.markFailed(entry, view, "官方指南加载失败（${error.errorCode}）。")
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (!request.isForMainFrame) return
            if (errorResponse.statusCode >= 400) {
                session.markFailed(entry, view, "官方指南暂时不可用（HTTP ${errorResponse.statusCode}）。")
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            session.markFailed(entry, view, "官方指南渲染进程已退出，请重试。")
            return true
        }
    }

    private companion object {
        const val AUTO_MAP_ASSET_PATH = "season-guide-map.js"
        const val AUTO_MAP_INITIAL_DELAY_MS = 120L
        const val AUTO_MAP_RETRY_DELAY_MS = 250L
        const val AUTO_MAP_MAX_ATTEMPTS = 10
        const val WEBVIEW_UNAVAILABLE_MESSAGE =
            "系统 WebView 不可用，请安装或启用 Android System WebView 后重试。"
        const val WEBVIEW_INIT_FAILED_MESSAGE =
            "系统 WebView 初始化失败，请检查 Android System WebView 后重试。"
    }
}

/** 同一 URL 的 WebView 与加载状态；状态由 Compose 读取并驱动状态 UI。 */
internal class GuideEntry internal constructor(
    val url: String,
    val policy: SeasonGuidePolicy,
    initialStatus: GuideStatus = GuideStatus.Loading,
) {
    var status by mutableStateOf<GuideStatus>(initialStatus)
    var view: WebView? = null
    var hostCount: Int = 0
    val attached: Boolean get() = hostCount > 0
    var destroyed: Boolean = false
    var viewResumed: Boolean = false
    var evictAfterRelease: Boolean = false
    var retryRequested: Boolean = false

    var appliedKey: String? = null
    var autoMapKey: String? = null
    var autoMapMap: String? = null
    var autoMapAttempts: Int = 0
    var autoMapGeneration: Int = 0
    var autoMapRunnable: Runnable? = null
}

internal sealed interface GuideStatus {
    data object Loading : GuideStatus
    data object Ready : GuideStatus
    data class Failed(val message: String) : GuideStatus
}

/**
 * Activity/悬浮窗组合级会话。宿主（如 HomeScreen）在页面切换之外 hoist 一次，
 * 子页面复用同一实例即可让官方 H5 在底部 Tab 往返时保持原页面。
 */
@Composable
fun rememberSeasonGuideSession(): SeasonGuideSession {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context) { SeasonGuideSession(context) }

    DisposableEffect(session) {
        onDispose { session.dispose() }
    }
    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> session.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            session.onResume()
        } else {
            session.onPause()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return session
}

private fun autoMapStatus(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
    val objectValue = root as? JsonObject ?: (root as? JsonPrimitive)?.contentOrNull?.let { inner ->
        runCatching { Json.parseToJsonElement(inner) }.getOrNull() as? JsonObject
    }
    return (objectValue?.get("status") as? JsonPrimitive)?.contentOrNull
}
