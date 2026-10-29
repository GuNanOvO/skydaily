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
import com.nankki.skydaily.SkyApp
import com.nankki.skydaily.core.BeijingClock
import com.nankki.skydaily.core.DailyRefreshPolicy
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.data.CachedToday
import com.nankki.skydaily.data.Envelope
import com.nankki.skydaily.data.DataRoute
import com.nankki.skydaily.data.DataRoutes
import com.nankki.skydaily.data.Mirror
import com.nankki.skydaily.data.JournalRepository
import com.nankki.skydaily.data.NoticeLoad
import com.nankki.skydaily.data.NoticeVersion
import com.nankki.skydaily.data.TodayLoad
import com.nankki.skydaily.data.isUsableFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    val autoRefreshEnabled: Boolean = true,
    val allowMeteredAutoRefresh: Boolean = false,
    val completedDate: String? = null,
    val mirror: Mirror = Mirror.JSDELIVR,
    val dataRoute: DataRoute? = null,
    val routeElapsedMs: Long? = null,
)

/** 进程内共享今日数据；界面可见性只控制公告轮询。 */
class JournalPresenter(
    private val repository: JournalRepository,
    private val checks: CheckState,
    private val policy: DailyRefreshPolicy,
    private val routes: DataRoutes,
    private val scope: CoroutineScope,
    private val isUnmeteredNetwork: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var loadJob: Job? = null
    private var todayJob: Job? = null
    private var noticeJob: Job? = null
    private var autoJob: Job? = null
    private val visibleOwners = mutableSetOf<Any>()
    private var warmed = false
    private var manualPending = false
    private var clearingCache = false
    private var lastNoticeAttempt: Long? = null

    var state by mutableStateOf(JournalUiState(
        today = BeijingClock.today(now()),
        autoRefreshEnabled = policy.enabled,
        allowMeteredAutoRefresh = policy.allowMetered,
        completedDate = policy.completedDate,
        mirror = routes.mirror,
        dataRoute = routes.lastDailyRoute,
    ))
        private set

    fun initialize() {
        if (warmed || loadJob?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val cached = readIo { repository.cachedToday() }
            if (cached != null) applyCache(cached)
            if (cached?.envelope?.isUsableFor(policy.completedDate.orEmpty()) != true) policy.clearCompletion()
            val notice = readIo { repository.cachedNotice() }
            if (notice != null) state = state.copy(notice = notice)
            warmed = true
            state = state.copy(loading = false, completedDate = policy.completedDate)
            if (manualPending) {
                manualPending = false
                refresh()
            } else refreshAutomatically()
        }
        loadJob = job
        job.start()
        if (autoJob?.isActive != true) autoJob = scope.launch {
            while (isActive) {
                delay(DATE_TICK_MS)
                refreshAutomatically()
            }
        }
    }

    fun attach(owner: Any) {
        if (!visibleOwners.add(owner)) return
        initialize()
        refreshAutomatically()
    }

    fun detach(owner: Any) {
        visibleOwners.remove(owner)
    }

    fun refreshAutomatically() {
        rollDate()
        if (!warmed || clearingCache) return
        if (policy.shouldAttempt(now()) && (policy.allowMetered || isUnmeteredNetwork())) requestToday()
        val last = lastNoticeAttempt
        if (visibleOwners.isNotEmpty() && (last == null || now() < last || now() - last >= NOTICE_INTERVAL_MS)) requestNotice()
    }

    fun refresh() {
        if (clearingCache) {
            manualPending = true
            return
        }
        if (!warmed) {
            manualPending = true
            initialize()
            return
        }
        rollDate()
        requestToday()
        requestNotice()
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        policy.enabled = enabled
        state = state.copy(autoRefreshEnabled = policy.enabled)
        if (enabled) refreshAutomatically()
    }

    fun setAllowMeteredAutoRefresh(allowed: Boolean) {
        policy.allowMetered = allowed
        state = state.copy(allowMeteredAutoRefresh = policy.allowMetered)
        if (allowed) refreshAutomatically()
    }

    fun setMirror(mirror: Mirror) {
        routes.mirror = mirror
        state = state.copy(mirror = mirror)
    }

    fun toggle(number: Int, done: Boolean) {
        rollDate()
        val envelope = state.envelope ?: return
        if (!JournalFormat.canEdit(envelope.date, state.today)) return
        checks.setDone(envelope.date, number, done)
        state = state.copy(done = checks.done(state.today))
    }

    fun clearCache() {
        if (clearingCache) return
        clearingCache = true
        scope.launch {
            try {
                loadJob?.join()
                todayJob?.cancelAndJoin()
                noticeJob?.cancelAndJoin()
                withContext(io) { repository.clearCache() }
                policy.clearCompletion()
                state = state.copy(envelope = null, notice = null, fetchedAt = 0, completedDate = null, message = "缓存已清除，正在重新获取")
            } finally {
                clearingCache = false
            }
            manualPending = false
            refresh()
        }
    }

    private fun rollDate() {
        val today = BeijingClock.today(now())
        if (today == state.today) return
        state = state.copy(today = today, done = checks.done(today), message = null)
        checks.prune(listOf(today))
    }

    private fun requestToday() {
        if (todayJob?.isActive == true) return
        policy.recordAttempt(now())
        state = state.copy(loading = state.envelope == null, refreshing = state.envelope != null)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    withContext(io) { repository.refreshToday() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    TodayLoad.Failure(error.message ?: "任务数据读取失败")
                }
                rollDate()
                apply(result)
            } finally {
                todayJob = null
                state = state.copy(loading = false, refreshing = noticeJob?.isActive == true)
            }
        }
        todayJob = job
        job.start()
    }

    private fun requestNotice() {
        if (noticeJob?.isActive == true) return
        lastNoticeAttempt = now()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    withContext(io) { repository.refreshNotice() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    NoticeLoad.Failure(error.message ?: "公告读取失败")
                }
                applyNotice(result)
            } finally {
                noticeJob = null
                state = state.copy(refreshing = todayJob?.isActive == true && state.envelope != null)
            }
        }
        noticeJob = job
        job.start()
    }

    private suspend fun <T> readIo(block: () -> T): T? = try {
        withContext(io) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun applyCache(cached: CachedToday) {
        val envelope = cached.envelope
        state = state.copy(
            envelope = envelope,
            fetchedAt = cached.fetchedAt,
            dataRoute = cached.route ?: state.dataRoute,
            done = checks.done(envelope.date),
            offline = false,
            loading = false,
        )
    }

    private fun apply(result: TodayLoad) {
        when (result) {
            is TodayLoad.Success -> {
                val envelope = result.envelope
                policy.recordSuccess(envelope, now())
                state = state.copy(
                    envelope = envelope,
                    fetchedAt = result.fetchedAt,
                    done = checks.done(envelope.date),
                    offline = false,
                    completedDate = policy.completedDate,
                    dataRoute = result.route,
                    routeElapsedMs = result.elapsedMs,
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
        const val NOTICE_INTERVAL_MS = 10 * 60_000L
    }
}

@androidx.compose.runtime.Composable
fun rememberJournalPresenter(context: Context): JournalPresenter {
    val presenter = (context.applicationContext as SkyApp).presenter
    val owner = remember(presenter) { Any() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(presenter, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> presenter.attach(owner)
                Lifecycle.Event.ON_RESUME -> presenter.refreshAutomatically()
                Lifecycle.Event.ON_STOP -> presenter.detach(owner)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            presenter.attach(owner)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            presenter.detach(owner)
        }
    }
    return presenter
}
