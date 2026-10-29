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

class JournalPresenterTest {
    @Test fun `success stops automatic traffic but not manual refresh or next day`() {
        val harness = Harness()
        try {
            harness.presenter.initialize()
            assertEquals(3, harness.dailyRequests)
            assertEquals("2026-10-30", harness.presenter.state.completedDate)
            harness.presenter.refreshAutomatically()
            assertEquals(3, harness.dailyRequests)
            harness.presenter.refresh()
            assertEquals(6, harness.dailyRequests)
            harness.now += 86_400_000
            harness.presenter.refreshAutomatically()
            assertEquals(9, harness.dailyRequests)
            assertEquals("2026-10-31", harness.presenter.state.completedDate)
        } finally { harness.scope.cancel() }
    }

    @Test fun `disabled process still permits manual refresh and keeps completion on reenable`() {
        val harness = Harness(enabled = false)
        try {
            harness.presenter.initialize()
            assertEquals(0, harness.dailyRequests)
            harness.presenter.refresh()
            assertEquals(3, harness.dailyRequests)
            harness.presenter.setAutoRefreshEnabled(true)
            assertEquals(3, harness.dailyRequests)
            assertEquals("2026-10-30", harness.presenter.state.completedDate)
        } finally { harness.scope.cancel() }
    }

    @Test fun `failure retries after cooldown and notices only poll with visible owner`() {
        val harness = Harness()
        harness.fail = true
        try {
            harness.presenter.initialize()
            harness.presenter.refreshAutomatically()
            assertEquals(3, harness.dailyRequests)
            assertEquals(0, harness.noticeRequests)
            harness.now += 600_000
            harness.presenter.refreshAutomatically()
            assertEquals(6, harness.dailyRequests)
            val owner = Any()
            harness.presenter.attach(owner)
            harness.presenter.attach(owner)
            assertEquals(2, harness.noticeRequests)
            harness.presenter.detach(owner)
            harness.now += 600_000
            harness.presenter.refreshAutomatically()
            assertEquals(2, harness.noticeRequests)
            harness.fail = false
            harness.presenter.refresh()
            assertEquals("2026-10-30", harness.presenter.state.completedDate)
        } finally { harness.scope.cancel() }
    }

    private class Harness(enabled: Boolean = true) {
        var now = Instant.parse("2026-10-30T04:00:00Z").toEpochMilli()
        var dailyRequests = 0
        var noticeRequests = 0
        var fail = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = MemoryKeyValueStore()
        val policy = DailyRefreshPolicy(store).also { it.enabled = enabled }
        val routes = DataRoutes(store)
        val template = fixture("sample-daily.json")
        val transport = AsyncHttpTransport { url, _ ->
            if (url.contains("notice")) {
                noticeRequests++
                Fetch.Failed("offline")
            } else {
                dailyRequests++
                if (fail) Fetch.Failed("offline") else Fetch.Ok(template.replace("2026-10-21", com.nankki.skydaily.core.BeijingClock.today(now)), null)
            }
        }
        val presenter = JournalPresenter(
            JournalRepository(transport, MemoryDiskStore(), routes) { now }, CheckState(store), policy, routes,
            scope, Dispatchers.Unconfined, { now },
        )
    }
}
