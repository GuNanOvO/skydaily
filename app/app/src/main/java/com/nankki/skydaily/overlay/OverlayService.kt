package com.nankki.skydaily.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
import com.nankki.skydaily.core.BallGeometry
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.core.ThemePreference
import com.nankki.skydaily.core.ThemeResolver
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
    private var ballView: ComposeView? = null
    private var ballHost: WindowComposeHost? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var lastTouch = 0L
    private var destroying = false

    private val sizePx: Int by lazy {
        (BALL_DP * resources.displayMetrics.density).toInt()
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
        panel = PanelWindow(
            service = this,
            modeState = modeState,
            onToggleTheme = ::toggleTheme,
            onDismiss = {
                if (!destroying) {
                    showBall()
                    refreshProgress()
                }
            },
        )
        showBall()
        lastTouch = System.currentTimeMillis()
        refreshProgress()
        handler.post(ticker)
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
        val metrics = resources.displayMetrics
        val defaultX = metrics.widthPixels - sizePx - dp(10)
        val defaultY = (metrics.heightPixels * 0.38f).toInt()
        val (snappedX, snappedY) = BallGeometry.snap(
            x = preferences.x ?: defaultX,
            y = preferences.y ?: defaultY,
            screenWidth = metrics.widthPixels,
            viewWidth = sizePx,
            margin = dp(2),
            screenHeight = metrics.heightPixels,
            viewHeight = sizePx,
        )
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
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
                    lastTouch = System.currentTimeMillis()
                    pressedState.value = true
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    lastTouch = System.currentTimeMillis()
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
                    lastTouch = System.currentTimeMillis()
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
    }

    private fun hideBall() {
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
        ballHost?.destroy()
        ballHost = null
        ballParams = null
    }

    private fun snapAndPersist(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val (x, y) = BallGeometry.snap(
            x = params.x,
            y = params.y,
            screenWidth = metrics.widthPixels,
            viewWidth = sizePx,
            margin = dp(2),
            screenHeight = metrics.heightPixels,
            viewHeight = sizePx,
        )
        params.x = x
        params.y = y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        preferences.x = x
        preferences.y = y
    }

    private fun openPanel() {
        hideBall()
        panel.show()
        refreshProgress()
    }

    private fun toggleTheme() {
        val next = ThemeResolver.toggle(modeState.value)
        themePreference.mode = next
        modeState.value = next
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

    private val ticker = object : Runnable {
        override fun run() {
            alphaState.floatValue = BallGeometry.idleAlpha(System.currentTimeMillis(), lastTouch)
            handler.postDelayed(this, TICK_MS)
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
        handler.removeCallbacks(ticker)
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
        private const val TICK_MS = 1_000L

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
