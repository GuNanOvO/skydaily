package com.nankki.skydaily.core

import com.nankki.skydaily.MemoryKeyValueStore
import com.nankki.skydaily.data.DailyData
import com.nankki.skydaily.data.Envelope
import com.nankki.skydaily.data.Meta
import com.nankki.skydaily.data.SectionError
import com.nankki.skydaily.data.Task
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRefreshPolicyTest {
    private val midnight = Instant.parse("2026-10-28T16:00:00Z").toEpochMilli()
    private val retryInterval = 10 * 60_000L

    private fun snapshot(
        date: String = "2026-10-29",
        stale: Boolean = false,
        errors: List<SectionError> = emptyList(),
        tasks: List<Task> = listOf(Task(1, "向一位朋友招手")),
    ) = Envelope(date = date, data = DailyData(tasks = tasks), meta = Meta(stale = stale), errors = errors)

    @Test
    fun `failed attempt retries at ten minutes rather than every tick`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        assertTrue(policy.shouldAttempt(midnight))
        policy.recordAttempt(midnight)

        assertFalse(policy.shouldAttempt(midnight + retryInterval - 1))
        assertTrue(policy.shouldAttempt(midnight + retryInterval))
    }

    @Test
    fun `Beijing midnight bypasses the previous day cooldown`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight - 30_000)

        assertFalse(policy.shouldAttempt(midnight - 1))
        assertTrue(policy.shouldAttempt(midnight))
    }

    @Test
    fun `complete current day remains finished after process recreation`() {
        val store = MemoryKeyValueStore()
        val policy = DailyRefreshPolicy(store)
        policy.enabled = true
        policy.recordAttempt(midnight)
        assertTrue(policy.recordSuccess(snapshot(), midnight))
        assertFalse(policy.shouldAttempt(midnight + 12 * 60 * 60_000L))

        val restored = DailyRefreshPolicy(store)
        assertTrue(restored.enabled)
        assertEquals("2026-10-29", restored.completedDate)
        assertFalse(restored.shouldAttempt(midnight + 23 * 60 * 60_000L))
        assertTrue(restored.shouldAttempt(midnight + 24 * 60 * 60_000L))
    }

    @Test
    fun `yesterday response finishing after midnight does not finish today`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight - 1_000)

        assertFalse(policy.recordSuccess(snapshot(date = "2026-10-28"), midnight + 1_000))
        assertEquals(null, policy.completedDate)
        assertTrue(policy.shouldAttempt(midnight + 1_000))
    }

    @Test
    fun `future date response cannot mark either day complete`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight)

        assertFalse(policy.recordSuccess(snapshot(date = "2026-10-30"), midnight))
        assertEquals(null, policy.completedDate)
        assertTrue(policy.shouldAttempt(midnight + retryInterval))
        assertTrue(policy.shouldAttempt(midnight + 24 * 60 * 60_000L))
    }

    @Test
    fun `stale response is retried instead of treated as fresh today`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight)

        assertFalse(policy.recordSuccess(snapshot(stale = true), midnight))
        assertTrue(policy.shouldAttempt(midnight + retryInterval))
    }

    @Test
    fun `partial section failure does not permanently stop refresh`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight)

        assertFalse(policy.recordSuccess(snapshot(errors = listOf(SectionError("tasks", "timeout"))), midnight))
        assertTrue(policy.shouldAttempt(midnight + retryInterval))
    }

    @Test
    fun `nonblocking image failure still completes valid daily tasks`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.recordAttempt(midnight)
        assertTrue(policy.recordSuccess(snapshot(errors = listOf(SectionError("media", "image_unavailable"))), midnight))
        assertFalse(policy.shouldAttempt(midnight + retryInterval))
    }

    @Test
    fun `empty daily tasks do not finish the day`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight)

        assertFalse(policy.recordSuccess(snapshot(tasks = emptyList()), midnight))
        assertTrue(policy.shouldAttempt(midnight + retryInterval))
    }

    @Test
    fun `disabled choice persists and reenable retries an unfinished day immediately`() {
        val store = MemoryKeyValueStore()
        val policy = DailyRefreshPolicy(store)
        policy.enabled = true
        policy.recordAttempt(midnight)
        policy.enabled = false

        assertFalse(policy.shouldAttempt(midnight + retryInterval))
        val restored = DailyRefreshPolicy(store)
        assertFalse(restored.enabled)
        assertFalse(restored.shouldAttempt(midnight + 24 * 60 * 60_000L))

        policy.enabled = true
        assertTrue(policy.shouldAttempt(midnight + 1_000))
    }

    @Test
    fun `manual success while disabled prevents automatic refetch on reenable`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = false

        assertTrue(policy.recordSuccess(snapshot(), midnight))
        policy.enabled = true
        assertFalse(policy.shouldAttempt(midnight + 1_000))
    }

    @Test
    fun `clearing cached completion permits reacquiring today's data`() {
        val store = MemoryKeyValueStore()
        val policy = DailyRefreshPolicy(store)
        policy.enabled = true
        policy.recordAttempt(midnight)
        policy.recordSuccess(snapshot(), midnight)
        policy.clearCompletion()

        assertEquals(null, policy.completedDate)
        assertTrue(policy.shouldAttempt(midnight + 1_000))
        assertTrue(DailyRefreshPolicy(store).shouldAttempt(midnight + 1_000))
    }

    @Test
    fun `clock rollback does not postpone retry until the old time is reached`() {
        val policy = DailyRefreshPolicy(MemoryKeyValueStore())
        policy.enabled = true
        policy.recordAttempt(midnight + 120_000)

        assertTrue(policy.shouldAttempt(midnight + 60_000))
    }
}
