package com.nankki.skydaily.ui.journal

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun shouldRequestOriginal(
    thumbUrl: String,
    originalUrl: String,
    originalCached: Boolean,
    dataSaver: Boolean,
): Boolean = originalUrl.isNotBlank() &&
    originalUrl != thumbUrl &&
    (originalCached || !dataSaver)

/** 只在原图已缓存或未开启流量节省时拉取高清图；判定与网页端预取一致。 */
@Composable
internal fun rememberOriginalImageAllowed(thumbUrl: String, originalUrl: String): Boolean {
    val context = LocalContext.current.applicationContext
    var allowed by remember(thumbUrl, originalUrl) { mutableStateOf(false) }
    LaunchedEffect(thumbUrl, originalUrl) {
        val cached = withContext(Dispatchers.IO) {
            runCatching {
                SingletonImageLoader.get(context)
                    .diskCache
                    ?.openSnapshot(originalUrl)
                    ?.use { true } ?: false
            }.getOrDefault(false)
        }
        allowed = shouldRequestOriginal(
            thumbUrl = thumbUrl,
            originalUrl = originalUrl,
            originalCached = cached,
            dataSaver = isDataSaverEnabled(context),
        )
    }
    return allowed
}

private fun isDataSaverEnabled(context: Context): Boolean =
    (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
        ?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

/** 高清图加载完成后覆盖在缩略图上，失败时保持缩略图可见。 */
@Composable
internal fun UpgradedImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "image-upgrade",
    )
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        onSuccess = { loaded = true },
        loading = {},
        error = {},
    )
}
