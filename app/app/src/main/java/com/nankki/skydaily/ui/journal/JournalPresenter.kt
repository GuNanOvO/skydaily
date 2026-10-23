package com.nankki.skydaily.ui.journal

import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
 * 自动刷新由 [rememberJournalPresenter] 跟随宿主 LifecycleOwner 进入/离开 STARTED 启停。
 */
class JournalPresenter(
    private val repository: JournalRepository,
    private val checks: CheckState,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var autoJob: Job? = null
    private var running = false
    private var warmed = false

    var state by mutableStateOf(JournalUiState(today = BeijingClock.today(now())))
        private set

    /**
     * 生命周期进入 STARTED 时调用：首次在 IO 读取今日与公告缓存后再刷新；
     * 之后只恢复自动刷新，前台刷新由 [refresh] 在 ON_RESUME 触发。重复调用无副作用。
     */
    fun start() {
        if (running) return
        running = true
        if (!warmed && loadJob?.isActive != true) {
            loadJob = scope.launch {
                val cached = readIo { repository.cachedToday() }
                // 只有在界面还没有任何数据时才用磁盘缓存，避免覆盖已完成的刷新结果。
                if (cached != null && state.envelope == null) applyCache(cached)
                val notice = readIo { repository.cachedNotice() }
                if (notice != null && state.notice == null) state = state.copy(notice = notice)
                warmed = true
                loadJob = null
                // 缓存装载期间生命周期已停止时不发起网络请求，下次可见时再刷新。
                if (running) refresh()
            }
        }
        startAutoRefresh()
    }

    /** 生命周期离开 STARTED 时调用：停掉周期与跨日检查，已在途的单次刷新允许完成。 */
    fun stop() {
        running = false
        autoJob?.cancel()
        autoJob = null
    }

    fun refresh() {
        // 首次缓存装载仍在进行：它结束后一定会刷新，本次请求合并进去。
        if (loadJob?.isActive == true) return
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
            try {
                // 今日与公告互相独立：各自完成后立即应用，慢的一方不阻塞另一方。
                supervisorScope {
                    launch { loadToday() }
                    launch { loadNotice() }
                }
            } finally {
                state = state.copy(loading = false, refreshing = false)
            }
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
        running = false
        autoJob?.cancel()
        loadJob?.cancel()
        refreshJob?.cancel()
        autoJob = null
        loadJob = null
        refreshJob = null
        scope.cancel()
    }

    private fun startAutoRefresh() {
        if (autoJob?.isActive == true) return
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

    private suspend fun loadToday() {
        val result = try {
            withContext(Dispatchers.IO) { repository.refreshToday() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TodayLoad.Failure(error.message ?: "任务数据读取失败")
        }
        apply(result)
        state = state.copy(loading = false)
    }

    private suspend fun loadNotice() {
        val result = try {
            withContext(Dispatchers.IO) { repository.refreshNotice() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NoticeLoad.Failure(error.message ?: "公告读取失败")
        }
        applyNotice(result)
    }

    private suspend fun <T> readIo(block: () -> T): T? = try {
        withContext(Dispatchers.IO) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private fun applyCache(cached: CachedToday) {
        val envelope = cached.envelope
        state = state.copy(
            envelope = envelope,
            fetchedAt = cached.fetchedAt,
            done = checks.done(envelope.date),
            offline = false,
            loading = false,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    DisposableEffect(presenter, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> presenter.start()
                Lifecycle.Event.ON_RESUME -> presenter.refresh()
                Lifecycle.Event.ON_STOP -> presenter.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            presenter.start()
            presenter.refresh()
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            presenter.start()
        } else {
            presenter.stop()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            presenter.stop()
        }
    }
    return presenter
}
