package com.nankki.skydaily.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nankki.skydaily.MainActivity
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.ui.journal.JournalPage
import com.nankki.skydaily.ui.journal.rememberJournalPresenter
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import com.nankki.skydaily.ui.theme.PaperTheme
import com.nankki.skydaily.ui.theme.rememberDarkMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OverlayPanel(
    modeState: MutableState<ThemeMode>,
    onToggleTheme: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val mode by modeState
    val dark = rememberDarkMode(mode)
    PaperTheme(dark = dark) {
        val palette = LocalPaperPalette.current
        val context = LocalContext.current
        val presenter = rememberJournalPresenter(context)
        val scope = rememberCoroutineScope()
        var shown by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        val scrimAlpha by animateFloatAsState(if (shown) 1f else 0f, label = "panel-scrim")

        LaunchedEffect(Unit) { shown = true }

        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.scrim.copy(alpha = 0.42f * scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!closing) {
                                closing = true
                                shown = false
                                scope.launch {
                                    delay(180)
                                    onClose()
                                }
                            }
                        },
                    ),
            )
            AnimatedVisibility(
                visible = shown,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                val width = minOf(
                    LocalConfiguration.current.screenWidthDp.dp * 0.94f,
                    440.dp,
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    JournalPage(
                        state = presenter.state,
                        onRefresh = presenter::refresh,
                        onToggleTask = presenter::toggle,
                        onToggleTheme = onToggleTheme,
                        darkTheme = dark,
                        modifier = Modifier.fillMaxSize(),
                        onClose = {
                            if (!closing) {
                                closing = true
                                shown = false
                                scope.launch {
                                    delay(180)
                                    onClose()
                                }
                            }
                        },
                        onOpenApp = onOpenApp,
                    )
                }
            }
        }
    }
}

/** 面板窗口：整屏透明层 + 右侧纸面抽屉。 */
class PanelWindow(
    private val service: Service,
    private val modeState: MutableState<ThemeMode>,
    private val onToggleTheme: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val windowManager = service.getSystemService(Service.WINDOW_SERVICE) as WindowManager
    private var host: WindowComposeHost? = null
    private var view: ComposeView? = null

    fun show() {
        if (view != null) return
        val composeHost = WindowComposeHost(service)
        val composeView = composeHost.createView {
            OverlayPanel(
                modeState = modeState,
                onToggleTheme = onToggleTheme,
                onClose = { hide() },
                onOpenApp = {
                    val intent = Intent(service, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    service.startActivity(intent)
                    hide()
                },
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val added = runCatching { windowManager.addView(composeView, params) }.isSuccess
        if (!added) {
            composeHost.destroy()
            service.stopSelf()
            return
        }
        host = composeHost
        view = composeView
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        host?.destroy()
        host = null
        onDismiss()
    }
}
