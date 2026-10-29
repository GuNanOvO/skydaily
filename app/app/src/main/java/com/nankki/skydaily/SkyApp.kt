package com.nankki.skydaily

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.CheckState
import com.nankki.skydaily.core.DailyRefreshPolicy
import com.nankki.skydaily.core.isUnmeteredRefreshNetwork
import com.nankki.skydaily.data.DataRoutes
import com.nankki.skydaily.data.ArchiveImageInterceptor
import com.nankki.skydaily.data.DirectoryDiskStore
import com.nankki.skydaily.data.JournalRepository
import com.nankki.skydaily.data.JsonHttpClient
import com.nankki.skydaily.data.OkHttpTransport
import com.nankki.skydaily.ui.journal.JournalPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath

class SkyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { AndroidKeyValueStore(this) }
    private val routes by lazy { DataRoutes(preferences) }
    val presenter by lazy {
        JournalPresenter(
            JournalRepository(OkHttpTransport(), DirectoryDiskStore(this), routes),
            CheckState(preferences), DailyRefreshPolicy(preferences), routes, appScope,
            isUnmeteredNetwork = { isUnmeteredRefreshNetwork(this) },
        )
    }
    private val imageClient by lazy {
        JsonHttpClient.shared.newBuilder()
            .addInterceptor(ArchiveImageInterceptor(routes))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe(::imageLoader)
        presenter.initialize()
    }

    private fun imageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(192L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .build()
}
