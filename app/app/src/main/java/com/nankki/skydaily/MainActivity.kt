package com.nankki.skydaily

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import coil3.SingletonImageLoader
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.ThemePreference
import com.nankki.skydaily.core.ThemeResolver
import com.nankki.skydaily.overlay.OverlayService
import com.nankki.skydaily.overlay.OverlayState
import com.nankki.skydaily.ui.home.HomeScreen
import com.nankki.skydaily.ui.journal.rememberJournalPresenter
import com.nankki.skydaily.ui.theme.LocalPaperPalette
import com.nankki.skydaily.ui.theme.PaperTheme
import com.nankki.skydaily.ui.theme.rememberDarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = remember { ThemePreference(AndroidKeyValueStore(context)) }
    var mode by remember { mutableStateOf(preferences.mode) }
    val dark = rememberDarkMode(mode)
    val presenter = rememberJournalPresenter(context)
    val state = presenter.state
    val scope = rememberCoroutineScope()

    var overlayGranted by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayGranted = android.provider.Settings.canDrawOverlays(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayGranted = android.provider.Settings.canDrawOverlays(context)
    }

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    PaperTheme(dark = dark) {
        val palette = LocalPaperPalette.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.page)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HomeScreen(
                presenter = presenter,
                state = state,
                themeMode = mode,
                onThemeModeChange = { next ->
                    mode = next
                    preferences.mode = next
                },
                overlayGranted = overlayGranted,
                overlayRunning = OverlayState.running,
                onOverlayAction = {
                    if (!overlayGranted) {
                        permissionLauncher.launch(OverlayService.permissionIntent(context))
                    } else if (OverlayState.running) {
                        OverlayService.stop(context)
                    } else {
                        OverlayService.start(context)
                    }
                },
                onClearCache = {
                    scope.launch(Dispatchers.IO) {
                        val loader = SingletonImageLoader.get(context)
                        loader.diskCache?.clear()
                        loader.memoryCache?.clear()
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        // 权限页返回后，等待系统设置生效再复核一次。
        kotlinx.coroutines.delay(300)
        overlayGranted = android.provider.Settings.canDrawOverlays(context)
    }
}
