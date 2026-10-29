package com.nankki.skydaily.data

import com.nankki.skydaily.MemoryDiskStore
import com.nankki.skydaily.MemoryKeyValueStore
import com.nankki.skydaily.fixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class JournalRoutesTest {
    private val today = Instant.parse("2026-10-30T04:00:00Z").toEpochMilli()
    private val daily = fixture("sample-daily.json").replace("2026-10-21", "2026-10-30")

    @Test fun `yesterday snapshot cannot complete current request`() = runBlocking {
        val repository = JournalRepository(
            AsyncHttpTransport { url, _ -> Fetch.Ok(if (url.contains("cdn.jsdelivr")) daily else daily.replace("2026-10-30", "2026-10-29"), null) },
            MemoryDiskStore(), DataRoutes(MemoryKeyValueStore()), { today },
        )
        val result = repository.refreshToday() as TodayLoad.Success
        assertEquals("2026-10-30", result.envelope.date)
        assertEquals(DataRoute.JSDELIVR, result.route)
    }

    @Test fun `unsupported schema cannot win a daily request`() = runBlocking {
        val body = daily.replace("\"schema_version\": 1", "\"schema_version\": 2")
        val repository = JournalRepository(
            AsyncHttpTransport { _, _ -> Fetch.Ok(body, null) }, MemoryDiskStore(),
            DataRoutes(MemoryKeyValueStore()), { today },
        )
        assertTrue(repository.refreshToday() is TodayLoad.Failure)
    }

    @Test fun `published nonblocking media error remains usable today`() = runBlocking {
        val envelope = JournalJson.parseEnvelope(daily).copy(errors = listOf(SectionError("media", "image_unavailable")))
        val body = kotlinx.serialization.json.Json.encodeToString(Envelope.serializer(), envelope)
        val repository = JournalRepository(
            AsyncHttpTransport { _, _ -> Fetch.Ok(body, null) }, MemoryDiskStore(),
            DataRoutes(MemoryKeyValueStore()), { today },
        )
        val result = repository.refreshToday()
        assertTrue(result is TodayLoad.Success)
        assertEquals("2026-10-30", (result as TodayLoad.Success).envelope.date)
        assertEquals("image_unavailable", result.envelope.errors.single().code)
    }

    @Test fun `API notice remains readable from cache after restart`() = runBlocking {
        val disk = MemoryDiskStore()
        val routes = DataRoutes(MemoryKeyValueStore())
        val raw = """{"schema_version":1,"date":"2026-10-30","timezone":"Asia/Shanghai","meta":{"stale":false},"errors":[],"data":{"hash":"hash-1","title":"更新公告","message":"公告正文","published_at":"2026-10-30","first_seen_at":"2026-10-30"}}"""
        val transport = AsyncHttpTransport { url, _ -> if (url.endsWith("/v1/notice")) Fetch.Ok(raw, "notice-1") else Fetch.Failed("timeout") }
        val first = JournalRepository(transport, disk, routes) { today }.refreshNotice() as NoticeLoad.Success
        assertEquals("更新公告", first.notice?.title)
        val restored = JournalRepository(AsyncHttpTransport { _, _ -> Fetch.Failed("offline") }, disk, routes) { today }
        assertEquals("公告正文", restored.cachedNotice()?.message)
        assertEquals("hash-1", (restored.refreshNotice() as NoticeLoad.Offline).notice?.hash)
    }
}
