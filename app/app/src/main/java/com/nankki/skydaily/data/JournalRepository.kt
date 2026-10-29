package com.nankki.skydaily.data

import com.nankki.skydaily.core.BeijingClock

data class CachedToday(
    val envelope: Envelope,
    val fetchedAt: Long,
    val etag: String?,
    val url: String? = null,
    val route: DataRoute? = null,
)

sealed interface TodayLoad {
    data class Success(
        val envelope: Envelope,
        val fetchedAt: Long,
        val fromCache: Boolean,
        val notModified: Boolean = false,
        val route: DataRoute? = null,
        val elapsedMs: Long? = null,
    ) : TodayLoad

    data class Offline(
        val envelope: Envelope,
        val fetchedAt: Long,
        val reason: String,
    ) : TodayLoad

    data class Failure(
        val reason: String,
        val cached: Envelope? = null,
        val cachedFetchedAt: Long = 0,
    ) : TodayLoad
}

sealed interface NoticeLoad {
    data class Success(val notice: NoticeVersion?, val fromCache: Boolean) : NoticeLoad
    data class Offline(val notice: NoticeVersion?, val reason: String) : NoticeLoad
    data class Failure(val reason: String) : NoticeLoad
}

/** 公告缓存原文的线路类型：API 外壳与 Pages 归档必须按各自形状解码。 */
private enum class NoticeSource(val route: DataRoute) {
    API(DataRoute.API),
    PAGES(DataRoute.PAGES),
}

private data class NoticePayload(val source: NoticeSource, val version: NoticeVersion?, val valid: Boolean)

/**
 * 今日页面与公告的本地缓存：三条线路并发取回完整有效数据，胜者原文直接落盘。
 * 今日快照绑定来源 URL，304 只复用同一线路的缓存；全部失败时仍回退旧快照。
 */
class JournalRepository(
    private val transport: AsyncHttpTransport,
    private val store: DiskStore,
    private val routes: DataRoutes,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val client = RacingDataClient(transport)

    fun cachedToday(): CachedToday? {
        val raw = store.read(TODAY_BODY) ?: return null
        val envelope = runCatching { JournalJson.parseEnvelope(raw) }.getOrNull() ?: return null
        val fetchedAt = store.read(TODAY_FETCHED_AT)?.toLongOrNull() ?: 0L
        val route = DataRoute.entries.firstOrNull { it.name == store.read(TODAY_ROUTE) }
        return CachedToday(envelope, fetchedAt, store.read(TODAY_ETAG), store.read(TODAY_URL), route)
    }

    suspend fun refreshToday(): TodayLoad {
        val cached = cachedToday()
        val expectedDate = BeijingClock.today(now())
        val result = client.load(
            requests = routes.daily(expectedDate),
            cache = cached?.let(::remoteToday),
            decode = { body, _ -> JournalJson.parseEnvelope(body) },
            accept = { envelope -> envelope.isUsableFor(expectedDate) },
        )
        return when (result) {
            is RemoteLoad.Success -> {
                val document = result.document
                // 请求发出后跨过北京时间零点时，昨天的胜者不能当作今天的数据落盘；
                // 本次直接失败，由进程内自动刷新按新日期重新发起。
                if (document.value.date != BeijingClock.today(now())) {
                    return fallbackToday(cached, "数据日期与当前日期不一致")
                }
                val fetchedAt = if (document.notModified) cached?.fetchedAt ?: now() else now()
                if (!document.notModified) persistToday(document, fetchedAt)
                routes.recordWinner(document.request.route)
                TodayLoad.Success(
                    envelope = document.value,
                    fetchedAt = fetchedAt,
                    fromCache = document.notModified,
                    notModified = document.notModified,
                    route = document.request.route,
                    elapsedMs = document.elapsedMs,
                )
            }

            is RemoteLoad.Failure -> fallbackToday(cached, result.reason)
        }
    }

    fun cachedNotice(): NoticeVersion? {
        val raw = store.read(NOTICE_BODY) ?: return null
        return when (cachedNoticeSource()) {
            NoticeSource.API -> JournalJson.parseNoticeApi(raw)
            NoticeSource.PAGES -> JournalJson.parseNoticeArchive(raw)?.currentVersion()
        }
    }

    suspend fun refreshNotice(): NoticeLoad {
        val result = client.load(
            requests = routes.notices(),
            cache = remoteNotice(),
            decode = ::decodeNotice,
            accept = { payload -> payload.valid },
        )
        return when (result) {
            is RemoteLoad.Success -> {
                val document = result.document
                if (!document.notModified) persistNotice(document)
                NoticeLoad.Success(document.value.version, fromCache = document.notModified)
            }

            is RemoteLoad.Failure ->
                if (store.read(NOTICE_BODY) != null) NoticeLoad.Offline(cachedNotice(), result.reason)
                else NoticeLoad.Failure(result.reason)
        }
    }

    fun clearCache() {
        store.removeAll("today")
        store.removeAll("notices")
    }

    private fun remoteToday(cached: CachedToday): RemoteCache<Envelope>? {
        val url = cached.url ?: return null
        val body = store.read(TODAY_BODY) ?: return null
        return RemoteCache(cached.envelope, body, cached.etag, url)
    }

    private fun persistToday(document: RemoteDocument<Envelope>, fetchedAt: Long) {
        store.write(TODAY_BODY, document.body)
        store.write(TODAY_FETCHED_AT, fetchedAt.toString())
        store.write(TODAY_URL, document.request.url)
        store.write(TODAY_ROUTE, document.request.route.name)
        if (document.etag != null) store.write(TODAY_ETAG, document.etag) else store.remove(TODAY_ETAG)
    }

    private fun fallbackToday(cached: CachedToday?, reason: String): TodayLoad =
        if (cached != null) TodayLoad.Offline(cached.envelope, cached.fetchedAt, reason)
        else TodayLoad.Failure(reason)

    private fun decodeNotice(body: String, route: DataRoute): NoticePayload = when (route) {
        DataRoute.API -> JournalJson.parseNoticeApi(body)
            .let { NoticePayload(NoticeSource.API, it, valid = it != null) }

        DataRoute.PAGES -> JournalJson.parseNoticeArchive(body)
            .let { NoticePayload(NoticeSource.PAGES, it?.currentVersion(), valid = it != null) }

        else -> NoticePayload(NoticeSource.PAGES, null, valid = false)
    }

    private fun remoteNotice(): RemoteCache<NoticePayload>? {
        val url = store.read(NOTICE_URL) ?: return null
        val raw = store.read(NOTICE_BODY) ?: return null
        val source = store.read(NOTICE_KIND)?.let { name -> NoticeSource.entries.firstOrNull { it.name == name } }
            ?: return null
        return RemoteCache(decodeNotice(raw, source.route), raw, store.read(NOTICE_ETAG), url)
    }

    private fun persistNotice(document: RemoteDocument<NoticePayload>) {
        store.write(NOTICE_BODY, document.body)
        store.write(NOTICE_KIND, document.value.source.name)
        store.write(NOTICE_URL, document.request.url)
        if (document.etag != null) store.write(NOTICE_ETAG, document.etag) else store.remove(NOTICE_ETAG)
    }

    /** 旧版本只缓存过 Pages 归档，缺少来源标记时按归档解释。 */
    private fun cachedNoticeSource(): NoticeSource =
        store.read(NOTICE_KIND)?.let { name -> NoticeSource.entries.firstOrNull { it.name == name } }
            ?: NoticeSource.PAGES

    private companion object {
        const val TODAY_BODY = "today.json"
        const val TODAY_ETAG = "today.etag"
        const val TODAY_FETCHED_AT = "today.fetched-at"
        const val TODAY_URL = "today.url"
        const val TODAY_ROUTE = "today.route"
        const val NOTICE_BODY = "notices.json"
        const val NOTICE_ETAG = "notices.etag"
        const val NOTICE_KIND = "notices.kind"
        const val NOTICE_URL = "notices.url"
    }
}
