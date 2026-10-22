package com.nankki.skydaily.data

import com.nankki.skydaily.FakeTransport
import com.nankki.skydaily.MemoryDiskStore
import com.nankki.skydaily.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {
    private val indexJson = """
        {
          "from": "2026-09-05",
          "to": "2026-10-21",
          "date_count": 3,
          "dates": [
            {"date": "2026-10-19", "file": "daily/2026-10-19.json"},
            {"date": "2026-10-21", "file": "daily/2026-10-21.json"},
            {"date": "2026-10-20", "file": "daily/2026-10-20.json"}
          ]
        }
    """.trimIndent()

    private fun daily(date: String): String =
        fixture("sample-daily.json").replace("\"date\": \"2026-10-21\"", "\"date\": \"$date\"")

    @Test
    fun `index entries are validated and sorted newest first`() {
        val repository = HistoryRepository(
            FakeTransport(mutableListOf(Fetch.Ok(indexJson, "\"i1\""))),
            MemoryDiskStore(),
        )

        val result = repository.refreshIndex() as HistoryIndexLoad.Success

        assertFalse(result.fromCache)
        assertEquals(
            listOf("2026-10-21", "2026-10-20", "2026-10-19"),
            result.index.entries.map { it.date },
        )
    }

    @Test
    fun `index refresh reuses the cached etag and reports not modified`() {
        val transport = FakeTransport(
            mutableListOf(Fetch.Ok(indexJson, "\"i1\""), Fetch.NotModified),
        )
        val repository = HistoryRepository(transport, MemoryDiskStore())
        repository.refreshIndex()

        val result = repository.refreshIndex() as HistoryIndexLoad.Success

        assertTrue(result.fromCache)
        assertTrue(result.notModified)
        assertEquals("/history/index.json", transport.requests[1].first)
        assertEquals("\"i1\"", transport.requests[1].second)
    }

    @Test
    fun `malformed index keeps the previous cached index and etag`() {
        val transport = FakeTransport(
            mutableListOf(Fetch.Ok(indexJson, "\"i1\""), Fetch.Ok("{oops", null)),
        )
        val disk = MemoryDiskStore()
        val repository = HistoryRepository(transport, disk)
        repository.refreshIndex()

        val result = repository.refreshIndex() as HistoryIndexLoad.Offline

        assertEquals(3, result.index.entries.size)
        assertEquals(indexJson, disk.files["history-index.json"])
        assertEquals("\"i1\"", disk.files["history-index.etag"])
    }

    @Test
    fun `unsafe or mismatched index entries never replace the cache`() {
        val rejected = listOf(
            """{}""",
            """{"dates":[{"date":"2026-10-21"}]}""",
            """{"dates":[{"date":"2026-10-21","file":"../../data.json"}]}""",
            """{"dates":[{"date":"2026-10-21","file":"daily/2026-10-20.json"}]}""",
            """{"dates":[{"date":"2026-02-30","file":"daily/2026-02-30.json"}]}""",
            """{"dates":[{"date":"2026-10-21","file":"daily/2026-10-21.json"},{"date":"2026-10-21","file":"daily/2026-10-21.json"}]}""",
        )
        rejected.forEach { badIndex ->
            val transport = FakeTransport(
                mutableListOf(Fetch.Ok(indexJson, "\"i1\""), Fetch.Ok(badIndex, "\"bad\"")),
            )
            val disk = MemoryDiskStore()
            val repository = HistoryRepository(transport, disk)
            repository.refreshIndex()

            val result = repository.refreshIndex() as HistoryIndexLoad.Offline

            assertEquals(3, result.index.entries.size)
            assertEquals(indexJson, disk.files["history-index.json"])
            assertEquals("\"i1\"", disk.files["history-index.etag"])
        }
    }

    @Test
    fun `index network failure falls back to the cached index`() {
        val transport = FakeTransport(mutableListOf(Fetch.Ok(indexJson, "\"i1\"")))
        val repository = HistoryRepository(transport, MemoryDiskStore())
        repository.refreshIndex()

        transport.responses.add(Fetch.Failed("timeout"))
        val result = repository.refreshIndex() as HistoryIndexLoad.Offline

        assertEquals("timeout", result.reason)
        assertEquals(3, result.index.entries.size)
    }

    @Test
    fun `corrupt cached index is ignored and refetched without an etag`() {
        val disk = MemoryDiskStore()
        disk.files["history-index.json"] = "{oops"
        disk.files["history-index.etag"] = "\"bad\""
        val transport = FakeTransport(mutableListOf(Fetch.Ok(indexJson, "\"i2\"")))
        val repository = HistoryRepository(transport, disk)

        val result = repository.refreshIndex() as HistoryIndexLoad.Success

        assertEquals(3, result.index.entries.size)
        assertNull(transport.requests.single().second)
        assertEquals("\"i2\"", disk.files["history-index.etag"])
    }

    @Test
    fun `index failure without cache is reported`() {
        val repository = HistoryRepository(
            FakeTransport(mutableListOf(Fetch.Failed("unable to resolve host"))),
            MemoryDiskStore(),
        )

        val result = repository.refreshIndex() as HistoryIndexLoad.Failure

        assertEquals("unable to resolve host", result.reason)
    }

    @Test
    fun `empty index is a valid empty archive`() {
        val repository = HistoryRepository(
            FakeTransport(mutableListOf(Fetch.Ok("""{"dates":[]}""", null))),
            MemoryDiskStore(),
        )

        val result = repository.refreshIndex() as HistoryIndexLoad.Success

        assertTrue(result.index.entries.isEmpty())
    }

    @Test
    fun `dated record is validated, cached and revalidated by etag`() {
        val transport = FakeTransport(
            mutableListOf(Fetch.Ok(daily("2026-10-21"), "\"d1\""), Fetch.NotModified),
        )
        val repository = HistoryRepository(transport, MemoryDiskStore())

        val first = repository.loadDate("2026-10-21") as HistoryRecordLoad.Success
        val second = repository.loadDate("2026-10-21") as HistoryRecordLoad.Success

        assertFalse(first.fromCache)
        assertEquals(4, first.record.data.tasks.size)
        assertEquals("/history/daily/2026-10-21.json", transport.requests[0].first)
        assertTrue(second.fromCache)
        assertTrue(second.notModified)
        assertEquals("\"d1\"", transport.requests[1].second)
    }

    @Test
    fun `mismatched record date does not overwrite the good cached record`() {
        val transport = FakeTransport(
            mutableListOf(
                Fetch.Ok(daily("2026-10-21"), "\"d1\""),
                Fetch.Ok(daily("2026-10-20"), "\"d2\""),
            ),
        )
        val disk = MemoryDiskStore()
        val repository = HistoryRepository(transport, disk)
        repository.loadDate("2026-10-21")

        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Offline

        assertEquals("2026-10-21", result.record.date)
        assertEquals(daily("2026-10-21"), disk.files["history-record-2026-10-21.json"])
        assertEquals("\"d1\"", disk.files["history-record-2026-10-21.etag"])
    }

    @Test
    fun `mismatched record date without cache is a failure`() {
        val repository = HistoryRepository(
            FakeTransport(mutableListOf(Fetch.Ok(daily("2026-10-20"), null))),
            MemoryDiskStore(),
        )

        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Failure

        assertEquals("归档数据无效或日期不匹配", result.reason)
    }

    @Test
    fun `corrupt record payload keeps the previous cached payload`() {
        val transport = FakeTransport(
            mutableListOf(Fetch.Ok(daily("2026-10-21"), "\"d1\""), Fetch.Ok("{oops", null)),
        )
        val disk = MemoryDiskStore()
        val repository = HistoryRepository(transport, disk)
        repository.loadDate("2026-10-21")

        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Offline

        assertEquals("2026-10-21", result.record.date)
        assertEquals("\"d1\"", disk.files["history-record-2026-10-21.etag"])
    }

    @Test
    fun `record network failure falls back to the cached payload`() {
        val transport = FakeTransport(mutableListOf(Fetch.Ok(daily("2026-10-21"), "\"d1\"")))
        val repository = HistoryRepository(transport, MemoryDiskStore())
        repository.loadDate("2026-10-21")

        transport.responses.add(Fetch.Failed("offline"))
        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Offline

        assertEquals("offline", result.reason)
        assertEquals("2026-10-21", result.record.date)
        assertEquals(4, result.record.data.tasks.size)
    }

    @Test
    fun `corrupt cached record is ignored instead of being returned`() {
        val disk = MemoryDiskStore()
        disk.files["history-record-2026-10-21.json"] = "{oops"
        disk.files["history-record-2026-10-21.etag"] = "\"bad\""
        val transport = FakeTransport(mutableListOf(Fetch.Failed("offline")))
        val repository = HistoryRepository(transport, disk)

        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Failure

        assertEquals("offline", result.reason)
        assertNull(transport.requests.single().second)
    }

    @Test
    fun `record 304 without cache is reported`() {
        val repository = HistoryRepository(
            FakeTransport(mutableListOf(Fetch.NotModified)),
            MemoryDiskStore(),
        )

        val result = repository.loadDate("2026-10-21") as HistoryRecordLoad.Failure

        assertEquals("服务器返回 304，但本地没有归档缓存", result.reason)
    }

    @Test
    fun `invalid or traversal dates never reach the network`() {
        val transport = FakeTransport(mutableListOf())
        val repository = HistoryRepository(transport, MemoryDiskStore())

        listOf("../data", "2026-13-01", "2026-02-30", "daily/2026-10-21").forEach { date ->
            val result = repository.loadDate(date) as HistoryRecordLoad.Failure
            assertEquals("归档日期无效", result.reason)
        }

        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `date validation accepts only real iso dates`() {
        assertTrue(HistoryJson.isValidDate("2026-10-21"))
        assertTrue(HistoryJson.isValidDate("2028-02-29"))
        assertFalse(HistoryJson.isValidDate("2026-02-29"))
        assertFalse(HistoryJson.isValidDate("2026-2-01"))
        assertFalse(HistoryJson.isValidDate("2026-10-21T00:00:00"))
        assertFalse(HistoryJson.isValidDate("../2026-10-21"))
    }
}
