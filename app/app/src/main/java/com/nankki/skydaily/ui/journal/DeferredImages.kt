package com.nankki.skydaily.ui.journal

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.data.DailyData
import com.nankki.skydaily.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal fun deferredImageUrls(data: DailyData, baseUrl: String): List<String> {
    val details = data.tasks.mapNotNull { task -> JournalFormat.taskDetailFor(task.keyword, data.taskDetails) }
    val taskImages = details.flatMap { detail -> detail.images + detail.variants.flatMap { it.images } }
    val otherImages = data.weather?.images.orEmpty() + data.calendar?.images.orEmpty()
    return buildList {
        taskImages.forEach { add(Media.absolute(baseUrl, it)) }
        otherImages.forEach { add(Media.absolute(baseUrl, Media.thumb(it))) }
        otherImages.forEach { add(Media.absolute(baseUrl, it)) }
    }.filter { it.isNotBlank() }.distinct()
}

/** 首帧后串行预取；换天、离开前台或数据变化时取消。 */
@Composable
fun DeferredImages(state: JournalUiState) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val data = state.envelope?.data
    LaunchedEffect(data, lifecycle) {
        if (data == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withFrameNanos { }
            delay(600)
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (manager.isActiveNetworkMetered ||
                manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            ) return@repeatOnLifecycle
            val loader = SingletonImageLoader.get(context)
            withContext(Dispatchers.IO) {
                for (url in deferredImageUrls(data, AppConfig.SITE_URL)) {
                    coroutineContext.ensureActive()
                    if (manager.isActiveNetworkMetered) break
                    val cached = loader.diskCache?.openSnapshot(url)
                    if (cached != null) {
                        cached.close()
                        continue
                    }
                    loader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(480)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build(),
                    )
                    delay(100)
                }
            }
        }
    }
}
