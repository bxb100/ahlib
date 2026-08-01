package cn.ahlib.reservation.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.edit
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ReaderQrCodeFailure {
    INVALID_PAGE_URL,
    SESSION_EXPIRED,
    NETWORK,
    TLS,
    HTTP,
    INVALID_RESPONSE,
    BUSINESS,
    NATIVE_UNAVAILABLE,
    QR_IMAGE_NOT_FOUND,
    QR_CONTENT_INVALID,
}

sealed interface ReaderQrCodeResult {
    data class Success(val content: String) : ReaderQrCodeResult

    data class Failure(
        val reason: ReaderQrCodeFailure,
        val message: String? = null,
    ) : ReaderQrCodeResult
}

class ReaderQrCodeRepository internal constructor(
    context: Context,
    private val nativeClient: ReaderQrNativeClient,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(InMemoryCookieJar())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun cachedContent(readerId: String): String? =
        withContext(Dispatchers.IO) {
            val normalizedReaderId = readerId.trim()
            if (
                normalizedReaderId.isEmpty() ||
                preferences.getString(KEY_READER_ID, null) != normalizedReaderId
            ) {
                return@withContext null
            }
            preferences
                .getString(KEY_CONTENT, null)
                ?.takeIf(::canEncodeReaderQrCode)
        }

    fun clearCachedContent(readerId: String) {
        val normalizedReaderId = readerId.trim()
        if (
            normalizedReaderId.isEmpty() ||
            preferences.getString(KEY_READER_ID, null) != normalizedReaderId
        ) {
            return
        }
        preferences.edit {
            remove(KEY_READER_ID)
            remove(KEY_CONTENT)
            remove(KEY_IMAGE_URL)
        }
    }

    suspend fun refreshAndCache(
        readerId: String,
        cookieHeader: String,
    ): ReaderQrCodeResult = withContext(Dispatchers.IO) {
        val normalizedReaderId = readerId.trim()
        if (normalizedReaderId.isEmpty() || cookieHeader.isBlank()) {
            return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.SESSION_EXPIRED,
            )
        }
        when (val result = nativeClient.fetch(cookieHeader)) {
            is ReaderQrCodeResult.Success -> {
                if (!canEncodeReaderQrCode(result.content)) {
                    return@withContext ReaderQrCodeResult.Failure(
                        ReaderQrCodeFailure.QR_CONTENT_INVALID,
                    )
                }
                preferences.edit {
                    putString(KEY_READER_ID, normalizedReaderId)
                    putString(KEY_CONTENT, result.content)
                    remove(KEY_IMAGE_URL)
                }
                result
            }

            is ReaderQrCodeResult.Failure -> result
        }
    }

    suspend fun resolveAndCache(
        readerId: String,
        pageUrl: String,
    ): ReaderQrCodeResult = withContext(Dispatchers.IO) {
        val normalizedReaderId = readerId.trim()
        val parsedPageUrl = pageUrl
            .trim()
            .toHttpUrlOrNull()
            ?.takeIf(HttpUrl::isAllowedOpacUrl)
            ?: return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.INVALID_PAGE_URL,
            )
        if (normalizedReaderId.isEmpty()) {
            return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.INVALID_PAGE_URL,
            )
        }

        try {
            val page = fetchPage(parsedPageUrl, MAX_REDIRECTS)
                ?: return@withContext ReaderQrCodeResult.Failure(
                    ReaderQrCodeFailure.HTTP,
                )
            val imageUrl = extractReaderQrImageUrl(
                pageUrl = page.url,
                html = page.html,
            ) ?: return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.QR_IMAGE_NOT_FOUND,
            )
            val imageBytes = fetchQrImage(
                url = imageUrl.toHttpUrlOrNull()
                    ?: return@withContext ReaderQrCodeResult.Failure(
                        ReaderQrCodeFailure.QR_IMAGE_NOT_FOUND,
                    ),
                referer = page.url,
                redirectsRemaining = MAX_REDIRECTS,
            ) ?: return@withContext ReaderQrCodeResult.Failure(
                ReaderQrCodeFailure.HTTP,
            )
            val content = decodeReaderQrImage(imageBytes)
                ?.takeIf(::canEncodeReaderQrCode)
                ?: return@withContext ReaderQrCodeResult.Failure(
                    ReaderQrCodeFailure.QR_CONTENT_INVALID,
                )
            preferences.edit {
                putString(KEY_READER_ID, normalizedReaderId)
                putString(KEY_CONTENT, content)
                remove(KEY_IMAGE_URL)
            }
            ReaderQrCodeResult.Success(content)
        } catch (_: IOException) {
            ReaderQrCodeResult.Failure(ReaderQrCodeFailure.NETWORK)
        }
    }

    private fun fetchPage(
        url: HttpUrl,
        redirectsRemaining: Int,
    ): ReaderQrPage? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                if (redirectsRemaining <= 0) {
                    return null
                }
                val redirectUrl = response.header("Location")
                    ?.let(url::resolve)
                    ?.takeIf(HttpUrl::isAllowedOpacUrl)
                    ?: return null
                return fetchPage(redirectUrl, redirectsRemaining - 1)
            }
            if (!response.isSuccessful) {
                return null
            }
            val body = response.body
            val bytes = body.byteStream().readNBytes(MAX_PAGE_BYTES + 1)
            if (bytes.size > MAX_PAGE_BYTES) {
                return null
            }
            val charset = body.contentType()
                ?.charset(StandardCharsets.UTF_8)
                ?: StandardCharsets.UTF_8
            return ReaderQrPage(
                url = response.request.url,
                html = String(bytes, charset),
            )
        }
    }

    private fun fetchQrImage(
        url: HttpUrl,
        referer: HttpUrl,
        redirectsRemaining: Int,
    ): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .header("Referer", referer.toString())
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                if (redirectsRemaining <= 0) {
                    return null
                }
                val redirectUrl = response.header("Location")
                    ?.let(url::resolve)
                    ?.takeIf(HttpUrl::isAllowedQrImageUrl)
                    ?: return null
                return fetchQrImage(
                    url = redirectUrl,
                    referer = referer,
                    redirectsRemaining = redirectsRemaining - 1,
                )
            }
            if (!response.isSuccessful) {
                return null
            }
            val bytes = response.body.byteStream().readNBytes(MAX_IMAGE_BYTES + 1)
            return bytes.takeIf { image -> image.size <= MAX_IMAGE_BYTES }
        }
    }

    private fun decodeReaderQrImage(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (
            bounds.outWidth !in 1..MAX_IMAGE_DIMENSION ||
            bounds.outHeight !in 1..MAX_IMAGE_DIMENSION
        ) {
            return null
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return null
        return try {
            decodeReaderQrCode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { newCookie ->
                this.cookies.removeAll { current ->
                    current.name == newCookie.name &&
                        current.domain == newCookie.domain &&
                        current.path == newCookie.path
                }
                if (newCookie.expiresAt > System.currentTimeMillis()) {
                    this.cookies += newCookie
                }
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { cookie -> cookie.expiresAt <= now }
            return cookies.filter { cookie -> cookie.matches(url) }
        }
    }

    private data class ReaderQrPage(
        val url: HttpUrl,
        val html: String,
    )

    private companion object {
        const val PREFERENCES_NAME = "reader_qr_code"
        const val KEY_READER_ID = "reader_id"
        const val KEY_CONTENT = "content"
        const val KEY_IMAGE_URL = "image_url"
        const val REQUEST_TIMEOUT_SECONDS = 15L
        const val MAX_REDIRECTS = 5
        const val MAX_PAGE_BYTES = 1_000_000
        const val MAX_IMAGE_BYTES = 4_000_000
        const val MAX_IMAGE_DIMENSION = 2_048
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal fun extractReaderQrImageUrl(
    pageUrl: HttpUrl,
    html: String,
): String? {
    val candidates = QR_IMAGE_REFERENCE.findAll(html)
        .map { match -> match.groupValues[2] }
    return candidates
        .map(String::decodeHtmlUrl)
        .mapNotNull(pageUrl::resolve)
        .firstOrNull(HttpUrl::isAllowedQrImageUrl)
        ?.toString()
}

private fun String.decodeHtmlUrl(): String =
    replace("&amp;", "&", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\/", "/")

private fun HttpUrl.isAllowedOpacUrl(): Boolean =
    isHttps && host.equals(OPAC_HOST, ignoreCase = true)

private fun HttpUrl.isAllowedQrImageUrl(): Boolean =
    isAllowedOpacUrl() &&
        encodedPath.replace("//", "/") == QR_IMAGE_PATH &&
        !queryParameter("qrcode").isNullOrBlank()

private val QR_IMAGE_REFERENCE = Regex(
    pattern = """(?is)(["'])([^"']*qrCodeImage\?[^"']*)\1""",
)

private const val OPAC_HOST = "opac.ahlib.com"
private const val QR_IMAGE_PATH = "/opac/reader/qrCodeImage"
