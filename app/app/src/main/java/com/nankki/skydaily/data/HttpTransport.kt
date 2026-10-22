package com.nankki.skydaily.data

import com.nankki.skydaily.AppConfig
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

class OkHttpTransport(private val baseUrl: String = AppConfig.SITE_URL) : HttpTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun get(path: String, etag: String?): Fetch {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("accept", "application/json")
            .header("user-agent", AppConfig.USER_AGENT)
        if (etag != null) builder.header("if-none-match", etag)
        return try {
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 304 -> Fetch.NotModified
                    response.isSuccessful -> Fetch.Ok(response.body?.string().orEmpty(), response.header("etag"))
                    else -> Fetch.Failed("HTTP ${response.code}")
                }
            }
        } catch (error: Exception) {
            Fetch.Failed(error.message ?: "网络不可用")
        }
    }
}
