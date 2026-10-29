package com.nankki.skydaily.data

import com.nankki.skydaily.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface Fetch {
    data object NotModified : Fetch
    data class Ok(val body: String, val etag: String?) : Fetch
    data class Failed(val reason: String) : Fetch
}

/** 窄传输接口：单测用内存假实现，生产用 OkHttp。 */
interface HttpTransport {
    fun get(path: String, etag: String?): Fetch
}

/**
 * 共享连接池与后台线程池。JSON 竞争的总时限由 RacingDataClient 控制，
 * 不限制持续有进度的 APK 下载。图片使用派生客户端，只改网络来源，
 * 保留 Coil 使用的原始 URL 缓存键。
 */
object JsonHttpClient {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

class OkHttpTransport(
    private val baseUrl: String = AppConfig.SITE_URL,
    private val client: OkHttpClient = JsonHttpClient.shared,
) : HttpTransport, AsyncHttpTransport {
    private fun request(path: String, etag: String?): Request {
        val builder = Request.Builder()
            .url(if (path.startsWith("https://") || path.startsWith("http://")) path else baseUrl.trimEnd('/') + path)
            .header("accept", "application/json")
            .header("user-agent", AppConfig.USER_AGENT)
        if (etag != null) builder.header("if-none-match", etag)
        return builder.build()
    }

    private fun read(response: Response): Fetch = response.use {
        when {
            it.code == 304 -> Fetch.NotModified
            it.isSuccessful -> Fetch.Ok(it.body.string(), it.header("etag"))
            else -> Fetch.Failed("HTTP ${it.code}")
        }
    }

    override suspend fun getAsync(url: String, etag: String?): Fetch = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request(url, etag))
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(Fetch.Failed(e.message ?: "网络不可用"))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    read(response)
                } catch (error: IOException) {
                    Fetch.Failed(error.message ?: "响应读取失败")
                }
                continuation.resume(result)
            }
        })
    }

    override fun get(path: String, etag: String?): Fetch {
        return try {
            read(client.newCall(request(path, etag)).execute())
        } catch (error: Exception) {
            Fetch.Failed(error.message ?: "网络不可用")
        }
    }
}
