package com.nankki.skydaily.data

data class CachedToday(
    val envelope: Envelope,
    val fetchedAt: Long,
    val etag: String?,
)

sealed interface TodayLoad {
    data class Success(
        val envelope: Envelope,
        val fetchedAt: Long,
        val fromCache: Boolean,
        val notModified: Boolean = false,
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

/**
 * 今日页面与公告的本地缓存：先写磁盘，再走条件请求。
 * 今日数据只保留最后一份快照；公告保留完整归档（体积很小）。
 */
class JournalRepository(
    private val transport: HttpTransport,
    private val store: DiskStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun cachedToday(): CachedToday? {
        val raw = store.read(TODAY_BODY) ?: return null
        val envelope = runCatching { JournalJson.parseEnvelope(raw) }.getOrNull() ?: return null
        val fetchedAt = store.read(TODAY_FETCHED_AT)?.toLongOrNull() ?: 0L
        return CachedToday(envelope, fetchedAt, store.read(TODAY_ETAG))
    }

    fun refreshToday(): TodayLoad {
        val cached = cachedToday()
        return when (val fetch = transport.get("/data.json", cached?.etag)) {
            is Fetch.NotModified -> cached?.let {
                TodayLoad.Success(it.envelope, it.fetchedAt, fromCache = true, notModified = true)
            } ?: TodayLoad.Failure("服务器返回 304，但本地没有缓存")

            is Fetch.Ok -> {
                val envelope = runCatching { JournalJson.parseEnvelope(fetch.body) }.getOrNull()
                if (envelope == null) {
                    cached?.let { TodayLoad.Offline(it.envelope, it.fetchedAt, "数据解析失败") }
                        ?: TodayLoad.Failure("数据解析失败")
                } else {
                    val fetchedAt = now()
                    store.write(TODAY_BODY, fetch.body)
                    store.write(TODAY_FETCHED_AT, fetchedAt.toString())
                    if (fetch.etag != null) store.write(TODAY_ETAG, fetch.etag) else store.remove(TODAY_ETAG)
                    TodayLoad.Success(envelope, fetchedAt, fromCache = false)
                }
            }

            is Fetch.Failed -> cached?.let {
                TodayLoad.Offline(it.envelope, it.fetchedAt, fetch.reason)
            } ?: TodayLoad.Failure(fetch.reason)
        }
    }

    fun cachedNotice(): NoticeVersion? {
        val raw = store.read(NOTICE_BODY) ?: return null
        return runCatching { JournalJson.parseNotices(raw).currentVersion() }.getOrNull()
    }

    fun refreshNotice(): NoticeLoad {
        val fetch = transport.get("/history/notices.json", store.read(NOTICE_ETAG))
        return when (fetch) {
            is Fetch.NotModified -> NoticeLoad.Success(cachedNotice(), fromCache = true)

            is Fetch.Ok -> {
                val archive = runCatching { JournalJson.parseNotices(fetch.body) }.getOrNull()
                if (archive == null) {
                    NoticeLoad.Failure("公告解析失败")
                } else {
                    store.write(NOTICE_BODY, fetch.body)
                    if (fetch.etag != null) store.write(NOTICE_ETAG, fetch.etag) else store.remove(NOTICE_ETAG)
                    NoticeLoad.Success(archive.currentVersion(), fromCache = false)
                }
            }

            is Fetch.Failed -> cachedNotice()?.let { NoticeLoad.Offline(it, fetch.reason) }
                ?: NoticeLoad.Failure(fetch.reason)
        }
    }

    fun clearCache() {
        store.removeAll("today")
        store.removeAll("notices")
    }

    private companion object {
        const val TODAY_BODY = "today.json"
        const val TODAY_ETAG = "today.etag"
        const val TODAY_FETCHED_AT = "today.fetched-at"
        const val NOTICE_BODY = "notices.json"
        const val NOTICE_ETAG = "notices.etag"
    }
}
