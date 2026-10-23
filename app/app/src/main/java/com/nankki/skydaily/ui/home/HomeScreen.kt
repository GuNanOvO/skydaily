package com.nankki.skydaily.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.meals.MealReminderManager
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.core.openWebLink
import com.nankki.skydaily.ui.history.ArchivePage
import com.nankki.skydaily.ui.history.HistoryPage
import com.nankki.skydaily.ui.history.rememberHistoryRepository
import com.nankki.skydaily.ui.journal.DeferredImages
import com.nankki.skydaily.ui.journal.InformationPage
import com.nankki.skydaily.ui.journal.JournalPage
import com.nankki.skydaily.ui.journal.JournalPresenter
import com.nankki.skydaily.ui.journal.JournalUiState
import com.nankki.skydaily.ui.journal.rememberSeasonGuideSession
import com.nankki.skydaily.ui.paper.PaperButton
import com.nankki.skydaily.ui.settings.SettingsPage
import com.nankki.skydaily.ui.theme.LocalPaperPalette

@Composable
fun HomeScreen(
    presenter: JournalPresenter,
    state: JournalUiState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    overlayGranted: Boolean,
    overlayRunning: Boolean,
    onOverlayAction: () -> Unit,
    onClearCache: () -> Unit,
    mealManager: MealReminderManager,
    mealRequest: Long,
) {
    val palette = LocalPaperPalette.current
    val context = LocalContext.current
    val guideSession = rememberSeasonGuideSession()
    val historyRepository = rememberHistoryRepository()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var consumedMealRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(mealRequest) {
        if (mealRequest > 0 && mealRequest != consumedMealRequest) {
            route = null
            page = 1
            consumedMealRequest = mealRequest
        }
    }
    BackHandler(enabled = route != null) { route = null }
    DeferredImages(state)
    Box(Modifier.fillMaxSize().background(palette.page), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            if (route != null && route != "settings") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaperButton("返回记录", onClick = { route = null })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                when (val destination = route) {
                    "settings" -> SettingsPage(
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
                        onOpenSite = { openWebLink(context, AppConfig.SITE_URL) },
                        onOpenFeedback = { openWebLink(context, AppConfig.FEEDBACK_URL) },
                        onBack = { route = null },
                        mealManager = mealManager,
                    )
                    null -> Crossfade(targetState = page, animationSpec = tween(160), label = "main-pages") { target ->
                        when (target) {
                            0 -> JournalPage(
                                state = state,
                                onRefresh = {
                                    presenter.refresh()
                                    guideSession.refresh()
                                },
                                onToggleTask = presenter::toggle,
                                darkTheme = palette.dark,
                                guideSession = guideSession,
                                onToggleTheme = {
                                    onThemeModeChange(if (palette.dark) ThemeMode.Light else ThemeMode.Dark)
                                },
                            )
                            1 -> InformationPage(state, presenter::refresh, onMealSettings = { route = "settings" })
                            else -> HistoryPage(
                                repository = historyRepository,
                                onOpenDate = { route = it },
                                onSettings = { route = "settings" },
                                onRefresh = presenter::refresh,
                                overlayRunning = overlayRunning,
                                onOverlayAction = onOverlayAction,
                            )
                        }
                    }
                    else -> ArchivePage(
                        repository = historyRepository,
                        date = destination,
                        todayState = state,
                        onToggleTask = presenter::toggle,
                    )
                }
            }
            if (route == null) PaperBottomNav(
                selected = page,
                done = state.done.size,
                total = state.envelope?.data?.tasks?.size ?: 0,
                onSelect = { page = it },
            )
        }
    }
}
