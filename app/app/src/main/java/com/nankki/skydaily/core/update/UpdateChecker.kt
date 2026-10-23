package com.nankki.skydaily.core.update

import android.os.Build
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.data.JsonHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 一次检查的结论；失败绝不会被当成「已是最新版本」。 */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
    data class Incompatible(val release: AppRelease) : UpdateCheck
    data class Failed(val message: String, val retryable: Boolean) : UpdateCheck
}

/** 只按 versionCode 判定升级，发布信息要求的系统版本更高时报告不兼容。 */
internal fun compareRelease(release: AppRelease, installedCode: Int, deviceSdk: Int): UpdateCheck {
    if (release.versionCode <= installedCode) return UpdateCheck.UpToDate
    if (release.minSdk > deviceSdk) return UpdateCheck.Incompatible(release)
    return UpdateCheck.Available(release)
}

internal class UpdateRequestException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)

internal val updateHttpClient: OkHttpClient by lazy {
    JsonHttpClient.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * 只有配置站点的 HTTPS 同源地址才可用；主机或端口不同、降级为 HTTP 都拒绝。
 * 抽成纯函数，便于在没有网络的情况下覆盖重定向判定。
 */
internal fun isSameOriginHttps(url: HttpUrl, origin: HttpUrl): Boolean =
    url.isHttps && url.host == origin.host && url.port == origin.port

internal fun resolveSameOriginHttps(origin: HttpUrl, base: HttpUrl, link: String): HttpUrl {
    val resolved = base.resolve(link)
        ?: throw UpdateRequestException("更新地址无效", retryable = false)
    if (!isSameOriginHttps(resolved, origin)) {
        throw UpdateRequestException("更新地址超出站点范围", retryable = false)
    }
    return resolved
}

/**
 * 站内请求：手动跟随重定向，每一跳都要求仍是配置站点的 HTTPS 地址。
 * 响应体由调用方消费并关闭。
 */
internal class SameOriginClient(
    client: OkHttpClient,
    siteUrl: String = AppConfig.SITE_URL,
) {
    private val origin: HttpUrl = siteUrl.trimEnd('/').toHttpUrl()
    private val direct: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun open(path: String, accept: String, onCall: (Call) -> Unit = {}): Response {
        if (!origin.isHttps) throw UpdateRequestException("更新地址必须是 HTTPS", retryable = false)
        var url = resolveSameOriginHttps(origin, origin, path)
        var hops = 0
        while (true) {
            if (!isSameOriginHttps(url, origin)) {
                throw UpdateRequestException("更新地址超出站点范围", retryable = false)
            }
            val request = Request.Builder()
                .url(url)
                .header("accept", accept)
                .header("user-agent", AppConfig.USER_AGENT)
                .header("cache-control", "no-store")
                .header("pragma", "no-cache")
                .build()
            val call = direct.newCall(request)
            onCall(call)
            val response = try {
                call.execute()
            } catch (error: IOException) {
                throw UpdateRequestException("网络不可用", retryable = true, cause = error)
            }
            if (response.code in 300..399) {
                val location = response.header("location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw UpdateRequestException("更新地址重定向无效", retryable = false)
                }
                if (hops >= MAX_REDIRECTS) {
                    throw UpdateRequestException("更新地址重定向过多", retryable = false)
                }
                hops += 1
                url = resolveSameOriginHttps(origin, url, location)
                continue
            }
            return response
        }
    }

    fun readText(path: String, maxBytes: Long, accept: String): String {
        open(path, accept).use { response ->
            if (!response.isSuccessful) throw statusFailure(response.code)
            val contentType = response.body.contentType()?.toString().orEmpty()
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw UpdateRequestException("更新信息返回了网页内容", retryable = true)
            }
            val declared = response.body.contentLength()
            if (declared > maxBytes) throw UpdateRequestException("更新信息过大", retryable = false)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER)
            var total = 0L
            response.body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw UpdateRequestException("更新信息过大", retryable = false)
                    }
                    output.write(buffer, 0, read)
                }
            }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val READ_BUFFER = 16 * 1024
    }
}

internal fun statusFailure(code: Int): UpdateRequestException = UpdateRequestException(
    message = "服务器返回 HTTP $code",
    retryable = code == 408 || code == 429 || code >= 500,
)

/**
 * 读取站点发布信息并与当前安装版本比较。
 * 安装的版本名只作诊断，升级判定以 versionCode 为准。
 */
class UpdateChecker(
    private val installedPackage: String,
    client: OkHttpClient = updateHttpClient,
    siteUrl: String = AppConfig.SITE_URL,
    private val deviceSdk: Int = Build.VERSION.SDK_INT,
) {
    private val http = SameOriginClient(client, siteUrl)

    fun check(
        installedCode: Int,
        @Suppress("UNUSED_PARAMETER") installedName: String,
    ): UpdateCheck {
        val body = try {
            http.readText(MANIFEST_PATH, MAX_MANIFEST_BYTES, "application/json")
        } catch (error: UpdateRequestException) {
            return UpdateCheck.Failed(error.message ?: "更新信息获取失败", error.retryable)
        } catch (error: IOException) {
            return UpdateCheck.Failed("网络不可用", retryable = true)
        }
        val release = parseAppRelease(body, installedPackage)
            ?: return UpdateCheck.Failed("更新信息不完整", retryable = true)
        return compareRelease(release, installedCode, deviceSdk)
    }

    companion object {
        const val MANIFEST_PATH = "/app/latest.json"
    }
}
