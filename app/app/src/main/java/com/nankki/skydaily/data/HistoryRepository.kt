package com.nankki.skydaily.data

data class CachedHistoryIndex(
    val index: HistoryIndex,
    val etag: String?,
)

data class CachedHistoryRecord(
    val envelope: Envelope,
    val etag: String?,
)

sealed interface HistoryIndexLoad {
    data class Success(
        val index: HistoryIndex,
        val fromCache: Boolean,
        val notModified: Boolean = false,
    ) : HistoryIndexLoad

    data class Offline(val index: HistoryIndex, val reason: String) : HistoryIndexLoad

    data class Failure(val reason: String) : HistoryIndexLoad
}

sealed interface HistoryRecordLoad {
    data class Success(
        val record: Envelope,
        val fromCache: Boolean,
        val notModified: Boolean = false,
    ) : HistoryRecordLoad

    data class Offline(val record: Envelope, val reason: String) : HistoryRecordLoad

    data class Failure(val reason: String) : HistoryRecordLoad
}

/**
 * Pages 静态归档的按需读取：索引与单日记录各自缓存正文和 ETag。
 * 所有方法都是阻塞的同步调用，界面层负责放到 IO 调度器并取消过期请求。
 * 只有通过校验的数据才会写入磁盘，解析失败时保留上一份可用缓存。
 */
class HistoryRepository(
    private val transport: HttpTransport,
    private val store: DiskStore,
) {
    fun cachedIndex(): CachedHistoryIndex? {
        val (raw, etag) = readIndexCache() ?: return null
        val index = HistoryJson.parseIndex(raw) ?: return null
        return CachedHistoryIndex(index, etag)
    }

    fun refreshIndex(): HistoryIndexLoad {
        val cached = cachedIndex()
        return when (val fetch = transport.get(INDEX_PATH, cached?.etag)) {
            is Fetch.NotModified -> cached?.let {
                HistoryIndexLoad.Success(it.index, fromCache = true, notModified = true)
            } ?: HistoryIndexLoad.Failure(NO_CACHE_REASON)

            is Fetch.Ok -> {
                val index = HistoryJson.parseIndex(fetch.body)
                if (index == null) {
                    cached?.let { HistoryIndexLoad.Offline(it.index, INVALID_INDEX_REASON) }
                        ?: HistoryIndexLoad.Failure(INVALID_INDEX_REASON)
                } else {
                    writeIndexCache(fetch.body, fetch.etag)
                    HistoryIndexLoad.Success(index, fromCache = false)
                }
            }

            is Fetch.Failed -> cached?.let {
                HistoryIndexLoad.Offline(it.index, fetch.reason)
            } ?: HistoryIndexLoad.Failure(fetch.reason)
        }
    }

    fun cachedDate(date: String): CachedHistoryRecord? {
        if (!HistoryJson.isValidDate(date)) return null
        val (raw, etag) = readRecordCache(date) ?: return null
        val envelope = runCatching { JournalJson.parseEnvelope(raw) }.getOrNull() ?: return null
        if (envelope.date != date) return null
        return CachedHistoryRecord(envelope, etag)
    }

    fun loadDate(date: String): HistoryRecordLoad {
        if (!HistoryJson.isValidDate(date)) return HistoryRecordLoad.Failure(INVALID_DATE_REASON)
        val cached = cachedDate(date)
        val fetch = transport.get("/history/daily/$date.json", cached?.etag)
        return when (fetch) {
            is Fetch.NotModified -> cached?.let {
                HistoryRecordLoad.Success(it.envelope, fromCache = true, notModified = true)
            } ?: HistoryRecordLoad.Failure(NO_CACHE_REASON)

            is Fetch.Ok -> {
                val record = runCatching { JournalJson.parseEnvelope(fetch.body) }.getOrNull()
                if (record == null || record.date != date) {
                    cached?.let { HistoryRecordLoad.Offline(it.envelope, INVALID_RECORD_REASON) }
                        ?: HistoryRecordLoad.Failure(INVALID_RECORD_REASON)
                } else {
                    writeRecordCache(date, fetch.body, fetch.etag)
                    HistoryRecordLoad.Success(record, fromCache = false)
                }
            }

            is Fetch.Failed -> cached?.let {
                HistoryRecordLoad.Offline(it.envelope, fetch.reason)
            } ?: HistoryRecordLoad.Failure(fetch.reason)
        }
    }

    private fun readIndexCache(): Pair<String, String?>? = synchronized(CACHE_LOCK) {
        val body = store.read(INDEX_BODY) ?: return null
        body to store.read(INDEX_ETAG)
    }

    private fun writeIndexCache(body: String, etag: String?) = synchronized(CACHE_LOCK) {
        store.write(INDEX_BODY, body)
        writeEtag(INDEX_ETAG, etag)
    }

    private fun readRecordCache(date: String): Pair<String, String?>? = synchronized(CACHE_LOCK) {
        val body = store.read(recordBody(date)) ?: return null
        body to store.read(recordEtag(date))
    }

    private fun writeRecordCache(date: String, body: String, etag: String?) = synchronized(CACHE_LOCK) {
        store.write(recordBody(date), body)
        writeEtag(recordEtag(date), etag)
    }

    private fun writeEtag(name: String, etag: String?) {
        if (etag != null) store.write(name, etag) else store.remove(name)
    }

    private fun recordBody(date: String) = "$RECORD_PREFIX$date.json"

    private fun recordEtag(date: String) = "$RECORD_PREFIX$date.etag"

    private companion object {
        /** 正文与 ETag 必须成对读写；历史仓库可能被多个界面实例并发调用。 */
        val CACHE_LOCK = Any()
        const val INDEX_PATH = "/history/index.json"
        const val INDEX_BODY = "history-index.json"
        const val INDEX_ETAG = "history-index.etag"
        const val RECORD_PREFIX = "history-record-"
        const val INVALID_INDEX_REASON = "归档索引无效"
        const val INVALID_RECORD_REASON = "归档数据无效或日期不匹配"
        const val INVALID_DATE_REASON = "归档日期无效"
        const val NO_CACHE_REASON = "服务器返回 304，但本地没有归档缓存"
    }
}
