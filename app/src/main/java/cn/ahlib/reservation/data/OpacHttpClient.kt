package cn.ahlib.reservation.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.SSLException
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

internal interface OpacClient {
    fun fetchSearch(
        query: String,
        page: Int,
    ): OpacClientResult

    fun fetchHoldings(bookRecordNumbersCsv: String): OpacClientResult

    fun fetchCovers(isbnsCsv: String): OpacClientResult
}

internal sealed interface OpacClientResult {
    data class Success(val content: String) : OpacClientResult

    data class Failure(
        val reason: OpacSearchFailure,
        val message: String? = null,
    ) : OpacClientResult
}

internal class OkHttpOpacClient(
    private val callFactory: Call.Factory,
) : OpacClient {
    override fun fetchSearch(
        query: String,
        page: Int,
    ): OpacClientResult {
        val normalizedQuery = query.trim()
        if (!normalizedQuery.isValidSearchQuery() || page !in 1..MAX_SEARCH_PAGE) {
            return OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)
        }
        val url = SEARCH_URL.newBuilder()
            .addQueryParameter("q", normalizedQuery)
            .addQueryParameter("searchWay", "title")
            .addQueryParameter("sortWay", "score")
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("scWay", "dim")
            .addQueryParameter("searchSource", "reader")
            .addQueryParameter("page", page.toString())
            .build()
        return execute(
            Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build(),
        )
    }

    override fun fetchHoldings(bookRecordNumbersCsv: String): OpacClientResult {
        if (!bookRecordNumbersCsv.isValidBookRecordNumbersCsv()) {
            return OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)
        }
        val url = HOLDINGS_URL.newBuilder()
            .addQueryParameter("bookrecnos", bookRecordNumbersCsv)
            .addQueryParameter("curLibcodes", "")
            .addQueryParameter("return_fmt", "json")
            .build()
        return execute(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build(),
        )
    }

    override fun fetchCovers(isbnsCsv: String): OpacClientResult {
        if (!isbnsCsv.isValidIsbnsCsv()) {
            return OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)
        }
        val url = COVERS_URL.newBuilder()
            .addQueryParameter("glc", "P1AH0551031")
            .addQueryParameter("cmdACT", "getImages")
            .addQueryParameter("type", "0")
            .addQueryParameter("isbns", ",$isbnsCsv")
            .build()
        return execute(
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build(),
        )
    }

    private fun execute(request: Request): OpacClientResult {
        return try {
            callFactory.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@use OpacClientResult.Failure(
                        reason = OpacSearchFailure.HTTP,
                        message = "HTTP ${response.code}",
                    )
                }
                val content = response.readUtf8Body(MAX_RESPONSE_BYTES)
                    ?: return@use OpacClientResult.Failure(
                        OpacSearchFailure.INVALID_RESPONSE,
                    )
                OpacClientResult.Success(content)
            }
        } catch (error: SSLException) {
            OpacClientResult.Failure(
                reason = OpacSearchFailure.TLS,
                message = error.message,
            )
        } catch (error: IOException) {
            OpacClientResult.Failure(
                reason = OpacSearchFailure.NETWORK,
                message = error.message,
            )
        } catch (_: RuntimeException) {
            OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)
        }
    }

    private fun Response.readUtf8Body(maxBytes: Int): String? {
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) {
            return null
        }
        val output = ByteArrayOutputStream(
            contentLength.takeIf { length -> length in 1..maxBytes }
                ?.toInt()
                ?: DEFAULT_BUFFER_BYTES,
        )
        body.byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                if (output.size() + count > maxBytes) {
                    return null
                }
                output.write(buffer, 0, count)
            }
        }
        val content = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        }.getOrNull() ?: return null
        return content.takeIf { value ->
            value.isNotBlank() && value.none(::isForbiddenResponseControl)
        }
    }

    private fun String.isValidSearchQuery(): Boolean =
        isNotEmpty() &&
            toByteArray(Charsets.UTF_8).size <= MAX_SEARCH_QUERY_BYTES &&
            codePointCount(0, length) <= MAX_SEARCH_QUERY_CHARACTERS &&
            none(Character::isISOControl)

    private fun String.isValidBookRecordNumbersCsv(): Boolean =
        splitAndValidateCsv(MAX_HOLDING_ITEMS) { item ->
            item.length in 1..MAX_BOOK_RECORD_NUMBER_LENGTH &&
                item.all { character -> character in '0'..'9' }
        }

    private fun String.isValidIsbnsCsv(): Boolean =
        splitAndValidateCsv(MAX_COVER_ITEMS) { item ->
            item.normalizedOpacIsbn() == item
        }

    private fun String.splitAndValidateCsv(
        maxItems: Int,
        isValidItem: (String) -> Boolean,
    ): Boolean {
        if (isEmpty() || any(Character::isISOControl)) {
            return false
        }
        val items = split(',')
        return items.size in 1..maxItems &&
            items.distinct().size == items.size &&
            items.all(isValidItem)
    }

    private companion object {
        val SEARCH_URL: HttpUrl = "https://opac.ahlib.com/opac/search".toHttpUrl()
        val HOLDINGS_URL: HttpUrl =
            "https://opac.ahlib.com/opac/book/holdingPreviews".toHttpUrl()
        val COVERS_URL: HttpUrl =
            "https://book-resource.dataesb.com/websearch/metares".toHttpUrl()
        const val MAX_SEARCH_QUERY_BYTES = 800
        const val MAX_SEARCH_QUERY_CHARACTERS = 200
        const val MAX_SEARCH_PAGE = 10_000
        const val MAX_HOLDING_ITEMS = 50
        const val MAX_COVER_ITEMS = 30
        const val MAX_BOOK_RECORD_NUMBER_LENGTH = 20
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val DEFAULT_BUFFER_BYTES = 8 * 1024

        fun isForbiddenResponseControl(character: Char): Boolean =
            Character.isISOControl(character) &&
                character != '\t' &&
                character != '\n' &&
                character != '\r'
    }
}
