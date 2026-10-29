package com.nankki.skydaily.ui.journal

import com.nankki.skydaily.MemoryDiskStore
import com.nankki.skydaily.MemoryKeyValueStore
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.core.DailyRefreshPolicy
import com.nankki.skydaily.data.*
import com.nankki.skydaily.fixture
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.coroutines.CoroutineContext

class JournalCacheClearTest {
    @Test fun `manual refresh during cache clearing cannot revive an unpersisted 304 snapshot`() {
        val io = QueuedIo()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val prefs = MemoryKeyValueStore()
        val routes = DataRoutes(prefs)
        val policy = DailyRefreshPolicy(prefs)
        val now = Instant.parse("2026-10-30T04:00:00Z").toEpochMilli()
        val body = fixture("sample-daily.json").replace("2026-10-21", "2026-10-30")
        var held: CompletableDeferred<Unit>? = null
        val transport = AsyncHttpTransport { url, etag ->
            if (!url.endsWith("/data.json")) Fetch.Failed("offline") else {
                held?.await()
                if (etag == "v1") Fetch.NotModified else Fetch.Ok(body, "v1")
            }
        }
        val repository = JournalRepository(transport, MemoryDiskStore(), routes) { now }
        val presenter = JournalPresenter(repository, CheckState(prefs), policy, routes, scope, { true }, io, { now })
        try {
            presenter.initialize()
            io.drain()
            assertEquals("2026-10-30", presenter.state.completedDate)
            assertNotNull(repository.cachedToday())
            held = CompletableDeferred()
            presenter.clearCache()
            presenter.refresh()
            // 磁盘删除任务保持排队，其后请求先运行到响应等待点。
            while (io.pending > 1) io.run(1)
            io.run(0)
            held.complete(Unit)
            io.drain()
            assertEquals("2026-10-30", presenter.state.envelope?.date)
            assertEquals("2026-10-30", presenter.state.completedDate)
            assertNotNull(repository.cachedToday())
            assertEquals("2026-10-30", repository.cachedToday()?.envelope?.date)
        } finally { scope.cancel() }
    }

    private class QueuedIo : CoroutineDispatcher() {
        private val queue = mutableListOf<Runnable>()
        val pending: Int get() = queue.size
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun run(index: Int) { queue.removeAt(index).run() }
        fun drain() { while (queue.isNotEmpty()) run(0) }
    }
}
