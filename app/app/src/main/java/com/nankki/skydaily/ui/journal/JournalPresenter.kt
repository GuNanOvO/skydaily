package com.nankki.skydaily.ui.journal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.data.CachedToday
import com.nankki.skydaily.data.DirectoryDiskStore
import com.nankki.skydaily.data.Envelope
import com.nankki.skydaily.data.JournalRepository
import com.nankki.skydaily.data.NoticeLoad
import com.nankki.skydaily.data.NoticeVersion
import com.nankki.skydaily.data.OkHttpTransport
import com.nankki.skydaily.data.TodayLoad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JournalUiState(
    val envelope: Envelope? = null,
    val notice: NoticeVersion? = null,
    val today: String = BeijingClock.today(),
    val done: Set<Int> = emptySet(),
    val fetchedAt: Long = 0L,
    val offline: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
)

/**
 * 今日页面的状态装配：先把磁盘缓存交给界面，再在后台做条件刷新。
 * 只在「数据日期 = 北京今天」时允许勾选。
 */
class JournalPresenter(
    private val repository: JournalRepository,
    private val checks: CheckState,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var autoJob: Job? = null

    var state by mutableStateOf(JournalUiState(today = BeijingClock.today(now())))
        private set

    fun start() {
        val cached = repository.cachedToday()
        if (cached != null) applyCache(cached)
        refresh()
        autoJob?.cancel()
        autoJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(DATE_TICK_MS)
                ticks++
                val today = BeijingClock.today(now())
                if (today != state.today) {
                    state = state.copy(today = today, done = checks.done(today), message = null)
                    checks.prune(listOf(today))
                    refresh()
                } else if (ticks % NETWORK_TICKS == 0) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        val today = BeijingClock.today(now())
        if (today != state.today) {
            state = state.copy(today = today, done = checks.done(today), message = null)
            checks.prune(listOf(today))
        }
        if (refreshJob?.isActive == true) return
        state = state.copy(
            loading = state.envelope == null,
            refreshing = state.envelope != null,
        )
        refreshJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val today = repository.refreshToday()
                val notice = repository.refreshNotice()
                today to notice
            }
            apply(result.first)
            applyNotice(result.second)
            state = state.copy(loading = false, refreshing = false)
        }
    }

    fun toggle(number: Int, done: Boolean) {
        val envelope = state.envelope ?: return
        if (!JournalFormat.canEdit(envelope.date, state.today)) return
        checks.setDone(envelope.date, number, done)
        state = state.copy(done = checks.done(state.today))
    }

    fun clearCache() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearCache() }
            state = state.copy(envelope = null, fetchedAt = 0, message = "缓存已清除，正在重新获取")
            refresh()
        }
    }

    fun close() {
        autoJob?.cancel()
        refreshJob?.cancel()
        scope.cancel()
    }

    private fun applyCache(cached: CachedToday) {
        val envelope = cached.envelope
        state = state.copy(
            envelope = envelope,
            fetchedAt = cached.fetchedAt,
            done = checks.done(envelope.date),
            offline = false,
        )
    }

    private fun apply(result: TodayLoad) {
        when (result) {
            is TodayLoad.Success -> {
                val envelope = result.envelope
                state = state.copy(
                    envelope = envelope,
                    fetchedAt = result.fetchedAt,
                    done = checks.done(envelope.date),
                    offline = false,
                    message = if (result.notModified) state.message else null,
                )
                checks.prune(listOf(state.today))
            }

            is TodayLoad.Offline -> {
                state = state.copy(
                    envelope = result.envelope,
                    fetchedAt = result.fetchedAt,
                    done = checks.done(result.envelope.date),
                    offline = true,
                    message = JournalFormat.humanizeReason(result.reason),
                )
            }

            is TodayLoad.Failure -> {
                state = state.copy(
                    envelope = result.cached,
                    fetchedAt = result.cachedFetchedAt,
                    offline = result.cached != null,
                    message = JournalFormat.humanizeReason(result.reason),
                )
            }
        }
    }

    private fun applyNotice(result: NoticeLoad) {
        when (result) {
            is NoticeLoad.Success -> state = state.copy(notice = result.notice)
            is NoticeLoad.Offline -> state = state.copy(notice = result.notice ?: state.notice)
            is NoticeLoad.Failure -> Unit
        }
    }

    private companion object {
        const val DATE_TICK_MS = 30_000L
        const val NETWORK_TICKS = 20
    }
}

@androidx.compose.runtime.Composable
fun rememberJournalPresenter(context: Context): JournalPresenter {
    val presenter = remember(context.applicationContext) {
        val app = context.applicationContext
        val store = AndroidKeyValueStore(app)
        JournalPresenter(
            repository = JournalRepository(OkHttpTransport(AppConfig.SITE_URL), DirectoryDiskStore(app)),
            checks = CheckState(store),
        )
    }
    androidx.compose.runtime.DisposableEffect(presenter) {
        presenter.start()
        onDispose { presenter.close() }
    }
    return presenter
}
