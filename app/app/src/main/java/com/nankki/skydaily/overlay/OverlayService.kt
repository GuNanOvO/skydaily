package com.nankki.skydaily.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.MainActivity
import com.nankki.skydaily.R
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.BallArea
import com.nankki.skydaily.core.BallGeometry
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.core.ThemePreference
import com.nankki.skydaily.data.DirectoryDiskStore
import com.nankki.skydaily.data.JournalRepository
import com.nankki.skydaily.data.OkHttpTransport
import com.nankki.skydaily.ui.theme.PaperTheme
import com.nankki.skydaily.ui.theme.rememberDarkMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 前台服务承载悬浮球与今日面板。球可拖动、吸附左右边缘、闲置半透明，
 * 位置写入 SharedPreferences；点按打开右侧纸面面板。
 */
class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modeState = mutableStateOf(ThemeMode.Auto)
    private val progressState = mutableFloatStateOf(0f)
    private val alphaState = mutableFloatStateOf(1f)
    private val pressedState = mutableStateOf(false)

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: BallPreferences
    private lateinit var themePreference: ThemePreference
    private lateinit var repository: JournalRepository
    private lateinit var checks: CheckState
    private lateinit var panel: PanelWindow
    private var displayManager: DisplayManager? = null
    private var ballView: ComposeView? = null
    private var ballHost: WindowComposeHost? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var destroying = false

    private val idleRunnable = Runnable {
        if (!destroying) alphaState.floatValue = IDLE_ALPHA
    }

    private val fitRunnable = Runnable { fitBallToDisplay() }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) requestFitBall()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val store = AndroidKeyValueStore(this)
        preferences = BallPreferences(store)
        themePreference = ThemePreference(store)
        modeState.value = themePreference.mode
        repository = JournalRepository(OkHttpTransport(AppConfig.SITE_URL), DirectoryDiskStore(this))
        checks = CheckState(store)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        displayManager = (getSystemService(DISPLAY_SERVICE) as DisplayManager).also {
            it.registerDisplayListener(displayListener, handler)
        }
        panel = PanelWindow(
            service = this,
            modeState = modeState,
            onDismiss = {
                if (!destroying) {
                    showBall()
                    refreshProgress()
                }
            },
        )
        showBall()
        refreshProgress()
        OverlayState.running = true
    }

    private fun showBall() {
        if (ballView != null) return
        val host = WindowComposeHost(this)
        val view = host.createView {
            val mode = modeState.value
            PaperTheme(dark = rememberDarkMode(mode)) {
                FloatingBall(
                    progress = progressState.floatValue,
                    alpha = alphaState.floatValue,
                    pressed = pressedState.value,
                )
            }
        }
        val size = ballSizePx()
        val area = displayArea()
        val defaultX = area.right - size - dp(10)
        val defaultY = area.top + (area.height * 0.38f).toInt()
        val (snappedX, snappedY) = BallGeometry.snapInArea(
            x = preferences.x ?: defaultX,
            y = preferences.y ?: defaultY,
            area = area,
            viewWidth = size,
            viewHeight = size,
            margin = dp(2),
        )
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                layoutFlag,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 与 displayArea() 共用完整显示坐标，系统栏和刘海只扣除一次。
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
            gravity = Gravity.TOP or Gravity.START
            x = snappedX
            y = snappedY
        }
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var dragging = false
        var downRawX = 0f
        var downRawY = 0f

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    noteInteraction()
                    pressedState.value = true
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    noteInteraction()
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        downRawX = event.rawX
                        downRawY = event.rawY
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    noteInteraction()
                    pressedState.value = false
                    if (dragging) {
                        snapAndPersist(params)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        openPanel()
                    }
                    true
                }

                else -> false
            }
        }

        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            host.destroy()
            Toast.makeText(this, "悬浮球添加失败，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        ballView = view
        ballHost = host
        ballParams = params
        noteInteraction()
    }

    private fun hideBall() {
        handler.removeCallbacks(idleRunnable)
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
        ballHost?.destroy()
        ballHost = null
        ballParams = null
    }

    private fun snapAndPersist(params: WindowManager.LayoutParams) {
        val size = ballSizePx()
        val (x, y) = BallGeometry.snapInArea(
            x = params.x,
            y = params.y,
            area = displayArea(),
            viewWidth = size,
            viewHeight = size,
            margin = dp(2),
        )
        params.x = x
        params.y = y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        preferences.x = x
        preferences.y = y
    }

    /** 触摸或拖拽后恢复不透明，并把闲置变淡重新计时；只在到期时执行一次。 */
    private fun noteInteraction() {
        alphaState.floatValue = 1f
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, IDLE_AFTER_MS)
    }

    private fun ballSizePx(): Int = (BALL_DP * resources.displayMetrics.density).toInt()

    /**
     * 安全显示区域。Android 11+ 使用窗口指标扣除系统栏与刘海；
     * Android 9/10 没有该 API，退回整个显示区域（旧行为），不会崩溃。
     */
    private fun displayArea(): BallArea {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val area = runCatching {
                val metrics = windowManager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout(),
                )
                BallArea(
                    left = insets.left,
                    top = insets.top,
                    right = metrics.bounds.width() - insets.right,
                    bottom = metrics.bounds.height() - insets.bottom,
                )
            }.getOrNull()
            if (area != null) return area
        }
        val metrics = resources.displayMetrics
        return BallArea(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun requestFitBall() {
        handler.removeCallbacks(fitRunnable)
        handler.post(fitRunnable)
    }

    /** 旋转、折叠或显示参数变化后，按新尺寸/安全区重新贴合，并写回位置。 */
    private fun fitBallToDisplay() {
        if (destroying) return
        val params = ballParams ?: return
        val view = ballView ?: return
        val size = ballSizePx()
        params.width = size
        params.height = size
        val (x, y) = BallGeometry.snapInArea(
            x = params.x,
            y = params.y,
            area = displayArea(),
            viewWidth = size,
            viewHeight = size,
            margin = dp(2),
        )
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(view, params) }
        preferences.x = x
        preferences.y = y
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        requestFitBall()
    }

    private fun openPanel() {
        modeState.value = themePreference.mode
        hideBall()
        panel.show()
        refreshProgress()
    }


    private fun refreshProgress() {
        scope.launch {
            val progress = withContext(Dispatchers.IO) {
                val today = BeijingClock.today()
                val cached = repository.cachedToday()
                val total = cached?.envelope?.data?.tasks?.size?.takeIf { it > 0 } ?: 4
                val done = if (cached?.envelope?.date == today) checks.done(today).size else 0
                done.toFloat() / total
            }
            progressState.floatValue = BallGeometry.clamp01(progress)
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.overlay_channel_description)
                },
            )
        }
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(intent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        destroying = true
        handler.removeCallbacks(idleRunnable)
        handler.removeCallbacks(fitRunnable)
        runCatching { displayManager?.unregisterDisplayListener(displayListener) }
        displayManager = null
        if (::panel.isInitialized) panel.hide()
        hideBall()
        scope.cancel()
        OverlayState.running = false
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "skydaily-overlay"
        private const val NOTIFICATION_ID = 1001
        private const val BALL_DP = 60
        private const val IDLE_AFTER_MS = 4_000L
        private const val IDLE_ALPHA = 0.55f

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun permissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
    }
}
