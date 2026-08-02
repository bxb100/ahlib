package cn.ahlib.reservation.ui

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class LibraryWebResourceCache private constructor(
    private val client: OkHttpClient,
) {
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!shouldCacheLibraryWebResource(url, request.requestHeaders)) {
            return null
        }

        val networkRequest = buildRequest(url, request.requestHeaders) ?: return null
        val response = try {
            client.newCall(networkRequest).execute()
        } catch (_: IOException) {
            cachedResponse(networkRequest) ?: return null
        } catch (_: RuntimeException) {
            return null
        }
        if (response.code >= 500) {
            cachedResponse(networkRequest)?.let { cachedResponse ->
                response.close()
                return cachedResponse.toWebResourceResponse()
            }
        }
        return response.toWebResourceResponse()
    }

    private fun cachedResponse(request: Request): Response? {
        val cacheOnlyRequest = request.newBuilder()
            .cacheControl(CacheControl.FORCE_CACHE)
            .build()
        val response = try {
            client.newCall(cacheOnlyRequest).execute()
        } catch (_: IOException) {
            return null
        }
        if (response.code == HTTP_GATEWAY_TIMEOUT) {
            response.close()
            return null
        }
        return response
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
    ): Request? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val builder = Request.Builder().url(httpUrl)
        headers.forEach { (name, value) ->
            if (name.lowercase(Locale.US) !in SKIPPED_REQUEST_HEADERS) {
                builder.header(name, value)
            }
        }
        return builder.build()
    }

    private fun Response.toWebResourceResponse(): WebResourceResponse? {
        if (
            code in HTTP_REDIRECT_RANGE ||
            code in AUTHENTICATION_ERROR_CODES ||
            headers("Set-Cookie").isNotEmpty()
        ) {
            close()
            return null
        }
        val responseBody = body
        val contentType = responseBody.contentType()
        val mimeType = contentType
            ?.let { type -> "${type.type}/${type.subtype}" }
            ?: guessMimeType(request.url.encodedPath)
        val encoding = contentType?.charset()?.name()
        val responseHeaders = headers.toMultimap()
            .filterKeys { name ->
                name.lowercase(Locale.US) !in SKIPPED_RESPONSE_HEADERS
            }
            .mapValues { (_, values) -> values.joinToString(", ") }
        return WebResourceResponse(
            mimeType,
            encoding,
            code,
            message.ifBlank { "OK" },
            responseHeaders,
            responseBody.byteStream(),
        )
    }

    companion object {
        @Volatile
        private var instance: LibraryWebResourceCache? = null

        fun get(context: Context): LibraryWebResourceCache =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { cache ->
                    instance = cache
                }
            }

        private fun create(context: Context): LibraryWebResourceCache {
            val cache = Cache(
                directory = context.cacheDir.resolve(CACHE_DIRECTORY),
                maxSize = CACHE_SIZE_BYTES,
            )
            val client = OkHttpClient.Builder()
                .cache(cache)
                .followRedirects(false)
                .addNetworkInterceptor(StaticResourceCacheHeaderInterceptor())
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            return LibraryWebResourceCache(client)
        }

        private fun guessMimeType(path: String): String? {
            val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(extension.lowercase(Locale.US))
        }

        private const val CACHE_DIRECTORY = "library_web_resources"
        private const val CACHE_SIZE_BYTES = 64L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val HTTP_GATEWAY_TIMEOUT = 504
        private val HTTP_REDIRECT_RANGE = 300..399
        private val AUTHENTICATION_ERROR_CODES = setOf(401, 403)
        private val SKIPPED_REQUEST_HEADERS = setOf(
            "accept-encoding",
            "authorization",
            "cache-control",
            "connection",
            "cookie",
            "host",
            "pragma",
        )
        private val SKIPPED_RESPONSE_HEADERS = setOf(
            "content-encoding",
            "content-length",
            "connection",
            "transfer-encoding",
        )
    }
}

private class StaticResourceCacheHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (
            !response.isSuccessful ||
            response.request.method != "GET" ||
            response.header("Set-Cookie") != null
        ) {
            return response
        }

        val maxAgeSeconds = if (response.request.url.hasVersionedFileName()) {
            VERSIONED_RESOURCE_MAX_AGE_SECONDS
        } else {
            REGULAR_RESOURCE_MAX_AGE_SECONDS
        }
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAgeSeconds")
            .removeHeader("Pragma")
            .build()
    }

    private companion object {
        const val VERSIONED_RESOURCE_MAX_AGE_SECONDS = 30L * 24L * 60L * 60L
        const val REGULAR_RESOURCE_MAX_AGE_SECONDS = 24L * 60L * 60L
    }
}

internal fun shouldCacheLibraryWebResource(
    url: String,
    headers: Map<String, String> = emptyMap(),
): Boolean {
    val httpUrl = url.toHttpUrlOrNull() ?: return false
    if (!httpUrl.isHttps) {
        return false
    }
    if (
        httpUrl.queryParameterNames.any { name ->
            name.lowercase(Locale.US) in SENSITIVE_QUERY_PARAMETERS
        }
    ) {
        return false
    }
    if (
        headers.headerValue("Sec-Fetch-Dest")
            ?.equals("empty", ignoreCase = true) == true
    ) {
        return false
    }

    val path = httpUrl.encodedPath.lowercase(Locale.US)
    if (
        httpUrl.pathSegments.any { segment ->
            segment.equals("api-server", ignoreCase = true)
        } || path.endsWith(".json")
    ) {
        return false
    }
    if (STATIC_RESOURCE_EXTENSIONS.any(path::endsWith)) {
        return true
    }

    val accept = headers.headerValue("Accept")
        ?.lowercase(Locale.US)
        .orEmpty()
    return STATIC_ACCEPT_TYPES.any(accept::contains)
}

private fun Map<String, String>.headerValue(name: String): String? =
    entries.firstOrNull { (headerName, _) ->
        headerName.equals(name, ignoreCase = true)
    }?.value

private fun okhttp3.HttpUrl.hasVersionedFileName(): Boolean =
    VERSIONED_FILE_NAME_PATTERN.containsMatchIn(encodedPath)

private val VERSIONED_FILE_NAME_PATTERN = Regex("[._-][0-9a-f]{8,}[._-]")

private val STATIC_RESOURCE_EXTENSIONS = setOf(
    ".avif",
    ".css",
    ".eot",
    ".gif",
    ".ico",
    ".jpeg",
    ".jpg",
    ".js",
    ".mjs",
    ".otf",
    ".png",
    ".svg",
    ".ttf",
    ".webp",
    ".woff",
    ".woff2",
)

private val STATIC_ACCEPT_TYPES = setOf(
    "font/",
    "image/",
    "text/css",
)

private val SENSITIVE_QUERY_PARAMETERS = setOf(
    "access_token",
    "authorization",
    "signature",
    "token",
    "x-amz-signature",
)
