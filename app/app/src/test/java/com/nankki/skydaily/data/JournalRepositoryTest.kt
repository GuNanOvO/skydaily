package com.nankki.skydaily.data

import com.nankki.skydaily.MemoryDiskStore
import com.nankki.skydaily.FakeTransport
import com.nankki.skydaily.fixture
import com.nankki.skydaily.MemoryKeyValueStore
import com.nankki.skydaily.core.CheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalRepositoryTest {
    private val daily = fixture("sample-daily.json")
    private val notices = fixture("sample-notices.json")

    @Test
    fun `fresh response is written to disk and etag is reused`() {
        val transport = FakeTransport(
            mutableListOf(
                Fetch.Ok(daily, "\"etag-1\""),
                Fetch.NotModified,
            ),
        )
        val disk = MemoryDiskStore()
        val repository = JournalRepository(transport, disk) { 1_000L }

        val first = repository.refreshToday() as TodayLoad.Success
        assertFalse(first.fromCache)
        assertEquals(4, first.envelope.data.tasks.size)
        assertEquals("1000", disk.files["today.fetched-at"])

        val second = repository.refreshToday() as TodayLoad.Success
        assertTrue(second.fromCache)
        assertTrue(second.notModified)
        assertEquals("\"etag-1\"", transport.requests[1].second)
    }

    @Test
    fun `network failure falls back to the cached snapshot`() {
        val disk = MemoryDiskStore()
        val transport = FakeTransport(mutableListOf(Fetch.Ok(daily, "\"v1\"")))
        val repository = JournalRepository(transport, disk) { 1_000L }
        repository.refreshToday()

        transport.responses.add(Fetch.Failed("unable to resolve host"))
        val result = repository.refreshToday() as TodayLoad.Offline
        assertEquals(4, result.envelope.data.tasks.size)
        assertEquals(1_000L, result.fetchedAt)
    }

    @Test
    fun `failure without cache is reported`() {
        val repository = JournalRepository(FakeTransport(mutableListOf(Fetch.Failed("timeout"))), MemoryDiskStore())
        val result = repository.refreshToday() as TodayLoad.Failure
        assertEquals("timeout", result.reason)
    }

    @Test
    fun `bad payload keeps the previous snapshot`() {
        val disk = MemoryDiskStore()
        val transport = FakeTransport(mutableListOf(Fetch.Ok(daily, null), Fetch.Ok("{oops", null)))
        val repository = JournalRepository(transport, disk) { 1_000L }
        repository.refreshToday()
        val result = repository.refreshToday() as TodayLoad.Offline
        assertEquals(4, result.envelope.data.tasks.size)
    }

    @Test
    fun `notice refresh caches the archive`() {
        val disk = MemoryDiskStore()
        val transport = FakeTransport(mutableListOf(Fetch.Ok(notices, "\"n1\""), Fetch.NotModified))
        val repository = JournalRepository(transport, disk) { 1_000L }

        val first = repository.refreshNotice() as NoticeLoad.Success
        assertEquals("9月24日版本更新公告", first.notice?.title)
        val second = repository.refreshNotice() as NoticeLoad.Success
        assertTrue(second.fromCache)
        assertEquals("\"n1\"", transport.requests[1].second)
    }

    @Test
    fun `clearing the cache removes journal files only`() {
        val disk = MemoryDiskStore()
        val transport = FakeTransport(mutableListOf(Fetch.Ok(daily, "\"v1\"")))
        val repository = JournalRepository(transport, disk) { 1_000L }
        repository.refreshToday()
        assertTrue(disk.files.containsKey("today.json"))
        repository.clearCache()
        assertFalse(disk.files.containsKey("today.json"))
        assertEquals(null, repository.cachedToday())
    }

    @Test
    fun `checks survive cache clearing`() {
        val store = MemoryKeyValueStore()
        val checks = CheckState(store)
        checks.setDone("2026-10-21", 1, true)
        val disk = MemoryDiskStore()
        val repository = JournalRepository(FakeTransport(mutableListOf(Fetch.Ok(daily, null))), disk)
        repository.refreshToday()
        repository.clearCache()
        assertTrue(checks.isDone("2026-10-21", 1))
    }
}
