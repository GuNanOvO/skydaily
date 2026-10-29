package com.nankki.skydaily.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** 保留 Coil 的原始缓存键，只在网络请求时切换已知归档图片的来源。 */
class ArchiveImageInterceptor(private val routes: DataRoutes) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val mapped = routes.imageUrl(original.url.toString())
        if (mapped == original.url.toString()) return chain.proceed(original)
        try {
            val response = chain.proceed(original.newBuilder().url(mapped).build())
            if (response.isSuccessful) return response
            response.close()
        } catch (error: IOException) {
            if (chain.call().isCanceled()) throw error
        }
        return chain.proceed(original)
    }
}
