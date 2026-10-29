package com.nankki.skydaily.data

import com.nankki.skydaily.MemoryDiskStore
import com.nankki.skydaily.MemoryKeyValueStore
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.fixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Collections

class JournalRepositoryTest {
    private val clock = Instant.parse("2026-10-30T04:00:00Z").toEpochMilli()
    private val site = "https://skydaily.nankki.com"
    private val apiUrl = "$site/v1/daily/2026-10-30"
    private val pagesUrl = "$site/data.json"
    private val noticeUrl = "$site/v1/notice"
    private val noticesUrl = "$site/history/notices.json"
    private val template = fixture("sample-daily.json")
    private val notices = fixture("sample-notices.json")
    private val apiNotice = """
        {"schema_version":1,"date":"2026-10-30","timezone":"Asia/Shanghai",
         "meta":{"fetched_at":"2026-10-30T00:00:00Z","cached":true,"stale":false},"errors":[],
         "data":{"hash":"hash-1","title":"更新公告","message":"公告正文",
                 "published_at":"2026-10-30T00:00:00Z","first_seen_at":"2026-10-30T00:00:00Z"}}
    """.trimIndent()
    private val emptyArchive =
        """{"schema":1,"generated_at":"2026-10-30T00:00:00Z","current":null,"versions":[]}"""

    private fun daily(date: String): String = template.replace("2026-10-21", date)

    private fun repository(
        transport: AsyncHttpTransport,
        disk: DiskStore = MemoryDiskStore(),
        routes: DataRoutes = DataRoutes(MemoryKeyValueStore()),
        now: () -> Long = { clock },
    ): JournalRepository = JournalRepository(transport, disk, routes, now)

    @Test
    fun `fresh daily stores the winning source and reuses only its etag`() = runBlocking {
        var pagesReply: Fetch = Fetch.Ok(daily("2026-10-30"), "\"pages-1\"")
        val transport = RouteTransport { url, _ ->
            if (url == pagesUrl) pagesReply else Fetch.Failed("blocked")
        }
        val disk = MemoryDiskStore()
        val repository = repository(transport, disk)

        val first = repository.refreshToday() as TodayLoad.Success
        assertEquals(DataRoute.PAGES, first.route)
        assertFalse(first.fromCache)
        assertFalse(first.notModified)
        assertEquals(clock, first.fetchedAt)
        assertTrue(first.elapsedMs != null)
        assertEquals(1, transport.calls.count { it.first == pagesUrl })
        assertEquals(daily("2026-10-30"), disk.files["today.json"])
        assertEquals("2026-10-30", first.envelope.date)
        assertEquals("\"pages-1\"", disk.files["today.etag"])
        assertEquals(pagesUrl, disk.files["today.url"])
        assertEquals("PAGES", disk.files["today.route"])
        assertEquals(clock.toString(), disk.files["today.fetched-at"])

        pagesReply = Fetch.NotModified
        val second = repository.refreshToday() as TodayLoad.Success
        assertTrue(second.fromCache)
        assertTrue(second.notModified)
        assertEquals(DataRoute.PAGES, second.route)
        assertEquals(clock, second.fetchedAt)
        assertEquals(first.envelope, second.envelope)
        assertEquals(1, transport.calls.count { it.first == pagesUrl && it.second == "\"pages-1\"" })
        assertTrue(transport.calls.filter { it.first != pagesUrl }.all { it.second == null })
        assertEquals("\"pages-1\"", disk.files["today.etag"])
    }

    @Test
    fun `304 from another source never validates the cached snapshot`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == pagesUrl -> Fetch.Ok(daily("2026-10-30"), "\"pages-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == apiUrl -> Fetch.NotModified
                else -> Fetch.Failed("down")
            }
        }
        val repository = repository(transport)
        assertTrue(repository.refreshToday() is TodayLoad.Success)
        phase = 1

        val result = repository.refreshToday() as TodayLoad.Offline
        assertEquals("2026-10-30", result.envelope.date)
        assertTrue(transport.calls.filter { it.first == apiUrl }.all { it.second == null })
    }

    @Test
    fun `cached snapshot from an earlier day cannot answer today's 304`() = runBlocking {
        var now = clock
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == pagesUrl -> Fetch.Ok(daily("2026-10-30"), "\"pages-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == pagesUrl -> Fetch.NotModified
                else -> Fetch.Failed("down")
            }
        }
        val repository = repository(transport, now = { now })
        assertTrue(repository.refreshToday() is TodayLoad.Success)
        phase = 1
        now = Instant.parse("2026-10-31T04:00:00Z").toEpochMilli()

        val result = repository.refreshToday() as TodayLoad.Offline
        assertEquals("2026-10-30", result.envelope.date)
        assertEquals(
            listOf("\"pages-1\""),
            transport.calls.filter { it.first == pagesUrl && it.second != null }.map { it.second },
        )
    }

    @Test
    fun `all sources failing keeps the cached snapshot`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            if (phase == 0 && url == pagesUrl) Fetch.Ok(daily("2026-10-30"), "\"pages-1\"")
            else Fetch.Failed("unable to resolve host")
        }
        val disk = MemoryDiskStore()
        val repository = repository(transport, disk)
        val first = repository.refreshToday() as TodayLoad.Success
        val storedBody = disk.files["today.json"]
        phase = 1

        val offline = repository.refreshToday() as TodayLoad.Offline
        assertEquals(first.envelope, offline.envelope)
        assertEquals(clock, offline.fetchedAt)
        assertTrue(offline.reason.isNotBlank())
        assertEquals(storedBody, disk.files["today.json"])
    }

    @Test
    fun `failure without any snapshot is reported`() = runBlocking {
        val repository = repository(RouteTransport { _, _ -> Fetch.Failed("timeout") })
        val result = repository.refreshToday() as TodayLoad.Failure
        assertTrue(result.reason.contains("timeout"))
        assertNull(result.cached)
    }

    @Test
    fun `invalid or empty bodies from every route keep the previous snapshot`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 -> if (url == pagesUrl) Fetch.Ok(daily("2026-10-30"), null) else Fetch.Failed("offline")
                url == apiUrl -> Fetch.Ok("<html>blocked</html>", null)
                url == pagesUrl -> Fetch.Ok("{}", null)
                else -> Fetch.Ok("", null)
            }
        }
        val disk = MemoryDiskStore()
        val repository = repository(transport, disk)
        repository.refreshToday()
        val storedBody = disk.files["today.json"]
        phase = 1

        val offline = repository.refreshToday() as TodayLoad.Offline
        assertEquals("2026-10-30", offline.envelope.date)
        assertEquals(4, offline.envelope.data.tasks.size)
        assertEquals(storedBody, disk.files["today.json"])
        assertNull(disk.files["today.etag"])
    }

    @Test
    fun `midnight crossing rejects yesterday winner without resending`() = runBlocking {
        var now = clock
        var phase = 0
        val next = Instant.parse("2026-10-30T16:01:00Z").toEpochMilli()
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == pagesUrl -> Fetch.Ok(daily("2026-10-30"), "\"pages-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == apiUrl -> {
                    now = next
                    Fetch.Ok(daily("2026-10-30"), null)
                }
                else -> Fetch.Failed("down")
            }
        }
        val disk = MemoryDiskStore()
        val repository = repository(transport, disk, now = { now })
        assertTrue(repository.refreshToday() is TodayLoad.Success)
        val storedBody = disk.files["today.json"]
        val callsBefore = transport.calls.size
        now = Instant.parse("2026-10-30T15:59:00Z").toEpochMilli()
        phase = 1

        val result = repository.refreshToday() as TodayLoad.Offline
        assertEquals("2026-10-30", result.envelope.date)
        assertEquals(1, transport.calls.drop(callsBefore).count { it.first == apiUrl })
        assertTrue(transport.calls.none { it.first.contains("2026-10-31") })
        assertEquals(storedBody, disk.files["today.json"])
        assertEquals("2026-10-30", repository.cachedToday()?.envelope?.date)
    }

    @Test
    fun `API notice keeps its source tag and answers 304 after restart`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == noticeUrl -> Fetch.Ok(apiNotice, "\"notice-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == noticeUrl -> Fetch.NotModified
                else -> Fetch.Failed("down")
            }
        }
        val disk = MemoryDiskStore()
        val routes = DataRoutes(MemoryKeyValueStore())
        val first = repository(transport, disk, routes).refreshNotice() as NoticeLoad.Success
        assertEquals("更新公告", first.notice?.title)
        assertEquals("API", disk.files["notices.kind"])
        assertEquals(noticeUrl, disk.files["notices.url"])

        phase = 1
        val restarted = repository(transport, disk, routes)
        assertEquals("公告正文", restarted.cachedNotice()?.message)
        val cached = restarted.refreshNotice() as NoticeLoad.Success
        assertTrue(cached.fromCache)
        assertEquals("hash-1", cached.notice?.hash)
        assertEquals(
            "\"notice-1\"",
            transport.calls.last { it.first == noticeUrl }.second,
        )
        assertTrue(transport.calls.filter { it.first == noticesUrl }.all { it.second == null })
    }

    @Test
    fun `API notice with stale flag or section errors is not a candidate`() = runBlocking {
        val stale = apiNotice.replace("\"stale\":false", "\"stale\":true")
        val errored = apiNotice.replace("\"errors\":[]", "\"errors\":[{\"section\":\"notice\",\"code\":\"upstream\"}]")
        for (body in listOf(stale, errored, "{}", "<html>blocked</html>")) {
            val repository = repository(
                RouteTransport { url, _ -> if (url == noticeUrl) Fetch.Ok(body, null) else Fetch.Failed("offline") },
            )
            assertTrue("accepted: $body", repository.refreshNotice() is NoticeLoad.Failure)
        }
    }

    @Test
    fun `Pages notice rejects HTML and default model, accepts explicit empty archive`() = runBlocking {
        for (body in listOf("<html>blocked</html>", "{}", """{"current":{"hash":"h","title":"t"}}""")) {
            val repository = repository(
                RouteTransport { url, _ -> if (url == noticesUrl) Fetch.Ok(body, null) else Fetch.Failed("offline") },
            )
            assertTrue("accepted: $body", repository.refreshNotice() is NoticeLoad.Failure)
        }

        val emptyDisk = MemoryDiskStore()
        val emptyRepository = repository(
            RouteTransport { url, _ ->
                if (url == noticesUrl) Fetch.Ok(emptyArchive, "\"empty-1\"") else Fetch.Failed("offline")
            },
            emptyDisk,
        )
        val empty = emptyRepository.refreshNotice() as NoticeLoad.Success
        assertNull(empty.notice)
        assertFalse(empty.fromCache)
        assertNull(emptyRepository.cachedNotice())
        assertEquals("PAGES", emptyDisk.files["notices.kind"])
        assertEquals(emptyArchive, emptyDisk.files["notices.json"])
    }

    @Test
    fun `Pages archive replaces a cached API notice and survives restart`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == noticeUrl -> Fetch.Ok(apiNotice, "\"notice-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == noticesUrl -> Fetch.Ok(notices, "\"pages-1\"")
                else -> Fetch.Failed("down")
            }
        }
        val disk = MemoryDiskStore()
        val repository = repository(transport, disk)
        assertEquals("更新公告", (repository.refreshNotice() as NoticeLoad.Success).notice?.title)
        phase = 1

        val pages = repository.refreshNotice() as NoticeLoad.Success
        assertEquals("9月24日版本更新公告", pages.notice?.title)
        assertFalse(pages.fromCache)
        assertEquals("PAGES", disk.files["notices.kind"])
        assertEquals(noticesUrl, disk.files["notices.url"])
        assertEquals("9月24日版本更新公告", repository.cachedNotice()?.title)
        val restored = repository(RouteTransport { _, _ -> Fetch.Failed("offline") }, disk)
        assertEquals("9月24日版本更新公告", restored.cachedNotice()?.title)
    }

    @Test
    fun `notice 304 from another source does not reuse the cached API body`() = runBlocking {
        var phase = 0
        val transport = RouteTransport { url, _ ->
            when {
                phase == 0 && url == noticeUrl -> Fetch.Ok(apiNotice, "\"notice-1\"")
                phase == 0 -> Fetch.Failed("offline")
                url == noticesUrl -> Fetch.NotModified
                else -> Fetch.Failed("down")
            }
        }
        val repository = repository(transport)
        assertTrue(repository.refreshNotice() is NoticeLoad.Success)
        phase = 1

        val offline = repository.refreshNotice() as NoticeLoad.Offline
        assertEquals("hash-1", offline.notice?.hash)
        assertTrue(transport.calls.filter { it.first == noticesUrl }.all { it.second == null })
    }

    @Test
    fun `clearing the cache removes journal files only`() = runBlocking {
        val disk = MemoryDiskStore()
        val repository = repository(
            RouteTransport { url, _ ->
                when (url) {
                    pagesUrl -> Fetch.Ok(daily("2026-10-30"), "\"v1\"")
                    noticesUrl -> Fetch.Ok(notices, "\"n1\"")
                    else -> Fetch.Failed("offline")
                }
            },
            disk,
        )
        repository.refreshToday()
        repository.refreshNotice()
        assertTrue(disk.files.containsKey("today.json"))
        assertEquals("PAGES", disk.files["notices.kind"])

        repository.clearCache()
        assertFalse(disk.files.keys.any { it.startsWith("today") })
        assertFalse(disk.files.keys.any { it.startsWith("notices") })
        assertNull(repository.cachedToday())
        assertNull(repository.cachedNotice())
    }

    @Test
    fun `checks survive cache clearing`() = runBlocking {
        val store = MemoryKeyValueStore()
        val checks = CheckState(store)
        checks.setDone("2026-10-30", 1, true)
        val repository = repository(
            RouteTransport { url, _ -> if (url == pagesUrl) Fetch.Ok(daily("2026-10-30"), null) else Fetch.Failed("offline") },
        )
        repository.refreshToday()
        repository.clearCache()
        assertTrue(checks.isDone("2026-10-30", 1))
    }

    /** 记录每个线路请求；测试里响应都是同步返回，不需要真正的并发原语。 */
    private class RouteTransport(private val handler: (String, String?) -> Fetch) : AsyncHttpTransport {
        private val log = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
        val calls: List<Pair<String, String?>> get() = synchronized(log) { log.toList() }

        override suspend fun getAsync(url: String, etag: String?): Fetch {
            log += url to etag
            return handler(url, etag)
        }
    }
}
