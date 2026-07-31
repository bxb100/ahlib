package cn.ahlib.reservation.ui

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_LIBRARY_IMAGE_AUTO_RETRIES = 2

internal fun buildLibraryImageRequest(
    context: Context,
    url: String,
    retryAttempt: Int,
): ImageRequest {
    val safeRetryAttempt = retryAttempt.coerceAtLeast(0)
    val headers = NetworkHeaders.Builder()
        .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        .set("Cache-Control", "no-cache")
        .set("Pragma", "no-cache")
        .set("Referer", LIBRARY_REFERER)
        .set("User-Agent", IMAGE_USER_AGENT)
        .build()
    return ImageRequest.Builder(context)
        .data(url.withLibraryImageRetryParameters(safeRetryAttempt))
        .crossfade(true)
        .allowHardware(false)
        .memoryCacheKey("$IMAGE_CACHE_NAMESPACE:$url")
        .diskCacheKey("$IMAGE_CACHE_NAMESPACE:$url:$safeRetryAttempt")
        .httpHeaders(headers)
        .build()
}

internal fun String.withLibraryImageRetryParameters(retryAttempt: Int): String {
    val url = toHttpUrlOrNull() ?: return this
    return url.newBuilder()
        .setQueryParameter(IMAGE_VERSION_QUERY, IMAGE_CACHE_VERSION)
        .setQueryParameter(
            IMAGE_RETRY_QUERY,
            retryAttempt.coerceAtLeast(0).toString(),
        )
        .build()
        .toString()
}

private const val IMAGE_CACHE_VERSION = "2"
private const val IMAGE_CACHE_NAMESPACE = "library-image-v$IMAGE_CACHE_VERSION"
private const val IMAGE_VERSION_QUERY = "_app_image"
private const val IMAGE_RETRY_QUERY = "_app_image_retry"
private const val LIBRARY_REFERER = "https://www.lib.ah.cn/"
private const val IMAGE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
