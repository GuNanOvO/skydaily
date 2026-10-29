package com.nankki.skydaily.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

fun interface AsyncHttpTransport {
    suspend fun getAsync(url: String, etag: String?): Fetch
}

data class RemoteCache<T>(val value: T, val body: String, val etag: String?, val url: String)
data class RemoteDocument<T>(
    val value: T,
    val body: String,
    val etag: String?,
    val request: RouteRequest,
    val elapsedMs: Long,
    val notModified: Boolean,
)
sealed interface RemoteLoad<out T> {
    data class Success<T>(val document: RemoteDocument<T>) : RemoteLoad<T>
    data class Failure(val reason: String) : RemoteLoad<Nothing>
}

/** 以完整下载、解码和有效性检查完成的时间择路；取消会传递至底层 HTTP Call。 */
class RacingDataClient(private val transport: AsyncHttpTransport) {
    suspend fun <T> load(
        requests: List<RouteRequest>,
        cache: RemoteCache<T>?,
        decode: (String, DataRoute) -> T,
        accept: (T) -> Boolean,
    ): RemoteLoad<T> = withTimeoutOrNull(30_000) {
        coroutineScope {
            val outcomes = Channel<RemoteLoad<T>>(requests.size)
            val jobs = requests.map { request ->
                launch {
                    val started = System.nanoTime()
                    val matching = cache?.takeIf { it.url == request.url }
                    val result = try {
                        when (val fetch = transport.getAsync(request.url, matching?.etag)) {
                            is Fetch.Failed -> RemoteLoad.Failure(fetch.reason)
                            is Fetch.NotModified -> if (matching != null && accept(matching.value)) {
                                RemoteLoad.Success(RemoteDocument(matching.value, matching.body, matching.etag, request, (System.nanoTime() - started) / 1_000_000, true))
                            } else RemoteLoad.Failure("缓存不适用于当前请求")
                            is Fetch.Ok -> {
                                val value = decode(fetch.body, request.route)
                                if (accept(value)) RemoteLoad.Success(RemoteDocument(value, fetch.body, fetch.etag, request, (System.nanoTime() - started) / 1_000_000, false))
                                else RemoteLoad.Failure("数据尚未完整更新")
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        RemoteLoad.Failure("数据解析失败")
                    }
                    outcomes.send(result)
                }
            }
            try {
                val failures = linkedSetOf<String>()
                repeat(requests.size) {
                    when (val result = outcomes.receive()) {
                        is RemoteLoad.Success -> return@coroutineScope result
                        is RemoteLoad.Failure -> failures.add(result.reason)
                    }
                }
                RemoteLoad.Failure(failures.joinToString("；").ifEmpty { "没有可用线路" })
            } finally {
                jobs.forEach { it.cancel() }
                outcomes.close()
            }
        }
    } ?: RemoteLoad.Failure("请求超时")
}
