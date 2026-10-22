package com.nankki.skydaily.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.ui.guides.GuidesPage
import com.nankki.skydaily.ui.journal.JournalPage
import com.nankki.skydaily.ui.journal.JournalPresenter
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.settings.SettingsPage
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun HomeScreen(
    presenter: JournalPresenter,
    state: JournalUiState,
    dark: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleTheme: () -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onOverlayAction: () -> Unit,
    onClearCache: () -> Unit,
) {
    val palette = LocalPaperPalette.current
    val uriHandler = LocalUriHandler.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageColor by animateColorAsState(palette.page, animationSpec = tween(220), label = "page-color")

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        presenter.refresh()
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Crossfade(
                    targetState = page,
                    animationSpec = tween(200),
                    label = "main-pages",
                ) { target ->
                    when (target) {
                        0 -> JournalPage(
                            state = state,
                            onRefresh = presenter::refresh,
                            onToggleTask = presenter::toggle,
                            onToggleTheme = onToggleTheme,
                            darkTheme = dark,
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            overlayCard = {
                                OverlayCard(
                                    granted = overlayGranted,
                                    running = overlayRunning,
                                    onAction = onOverlayAction,
                                )
                            },
                            onClearCache = {
                                presenter.clearCache()
                                onClearCache()
                            },
                        )

                        1 -> GuidesPage(
                            state = state,
                            onRefresh = presenter::refresh,
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )

                        else -> SettingsPage(
                            state = state,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            overlayGranted = overlayGranted,
                            overlayRunning = overlayRunning,
                            onOverlayAction = onOverlayAction,
                            onClearCache = {
                                presenter.clearCache()
                                onClearCache()
                            },
                            onOpenSite = { uriHandler.openUri(AppConfig.SITE_URL) },
                            onOpenFeedback = { uriHandler.openUri(AppConfig.FEEDBACK_URL) },
                            onRefresh = presenter::refresh,
                            onOpenToday = { page = 0 },
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PaperBottomNav(
                    selected = page,
                    done = state.done.size,
                    total = state.envelope?.data?.tasks?.size ?: 0,
                    onSelect = { page = it },
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                )
            }
        }
    }
}
