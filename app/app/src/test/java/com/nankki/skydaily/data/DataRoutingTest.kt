package com.nankki.skydaily.data

import com.nankki.skydaily.MemoryKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DataRoutingTest {
    @Test fun `invalid fast body cannot beat valid complete data and losers cancel`() = runBlocking {
        val replies = List(3) { CompletableDeferred<Fetch>() }
        val cancelled = mutableSetOf<Int>()
        val transport = AsyncHttpTransport { url, _ ->
            val index = url.last().digitToInt()
            try { replies[index].await() } finally { if (!replies[index].isCompleted) cancelled.add(index) }
        }
        val requests = listOf(DataRoute.API, DataRoute.PAGES, DataRoute.JSDELIVR)
            .mapIndexed { index, route -> RouteRequest(route, "https://example.test/$index") }
        var decodes = 0
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            RacingDataClient(transport).load(requests, null, { body, _ -> decodes++; body.toInt() }, { it > 0 })
        }
        yield()
        replies[0].complete(Fetch.Ok("<html>blocked</html>", null))
        yield()
        assertFalse(result.isCompleted)
        replies[1].complete(Fetch.Ok("42", "p1"))
        val winner = result.await() as RemoteLoad.Success
        assertEquals(42, winner.document.value)
        assertEquals(DataRoute.PAGES, winner.document.request.route)
        assertEquals(2, decodes)
        assertEquals(setOf(2), cancelled)
    }

    @Test fun `etag from one origin never validates another origin cache`() = runBlocking {
        val cache = RemoteCache(42, "42", "etag", "https://a.test/data")
        val result = RacingDataClient(AsyncHttpTransport { _, _ -> Fetch.NotModified }).load(
            listOf(RouteRequest(DataRoute.PAGES, "https://b.test/data")), cache,
            { body, _ -> body.toInt() }, { it > 0 },
        )
        assertTrue(result is RemoteLoad.Failure)
    }

    @Test fun `matching origin 304 preserves valid cached document`() = runBlocking {
        val result = RacingDataClient(AsyncHttpTransport { _, _ -> Fetch.NotModified }).load(
            listOf(RouteRequest(DataRoute.PAGES, "https://a.test/data")),
            RemoteCache(42, "42", "etag", "https://a.test/data"),
            { body, _ -> body.toInt() }, { it > 0 },
        ) as RemoteLoad.Success
        assertEquals(42, result.document.value)
        assertTrue(result.document.notModified)
    }

    @Test fun `mirror selection replaces third candidate and persists`() {
        val store = MemoryKeyValueStore()
        val routes = DataRoutes(store)
        routes.mirror = Mirror.GHFAST
        val restored = DataRoutes(store)
        val candidates = restored.daily("2026-10-30")
        assertEquals(listOf(DataRoute.API, DataRoute.PAGES, DataRoute.GHFAST), candidates.map { it.route })
        assertEquals("https://ghfast.top/https://raw.githubusercontent.com/GuNanOvO/skydaily/data/history/daily/2026-10-30.json", candidates.last().url)
        assertEquals(listOf(DataRoute.API, DataRoute.PAGES), restored.notices().map { it.route })
    }

    @Test fun `only canonical archive images move to selected mirror`() {
        val routes = DataRoutes(MemoryKeyValueStore())
        routes.recordWinner(DataRoute.JSDELIVR)
        val root = "https://skydaily.nankki.com"
        val image = "/history/media/0123456789abcdef01234567.webp"
        assertEquals("https://cdn.jsdelivr.net/gh/GuNanOvO/skydaily@data$image", routes.imageUrl(root + image))
        for (url in listOf("$root/v1/media/" + "a".repeat(64), "$root/releases/latest.json", "https://other.test$image", "$root/history/media/../latest.json")) {
            assertEquals(url, routes.imageUrl(url))
        }
    }
}
