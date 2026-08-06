package cn.ahlib.reservation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class OpacRepository internal constructor(
    private val client: OpacClient,
    private val parser: OpacHtmlParser = OpacHtmlParser(),
    private val supplementParser: OpacSupplementParser = OpacSupplementParser(),
) {
    suspend fun search(
        query: String,
        page: Int,
    ): OpacSearchResult = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (!normalizedQuery.isValidSearchQuery() || page !in 1..MAX_SEARCH_PAGE) {
            return@withContext OpacSearchResult.Failure(
                OpacSearchFailure.INVALID_RESPONSE,
            )
        }

        when (
            val clientResult = runCatching {
                client.fetchSearch(normalizedQuery, page)
            }.getOrNull()
                ?: return@withContext OpacSearchResult.Failure(
                    OpacSearchFailure.INVALID_RESPONSE,
                )
        ) {
            is OpacClientResult.Failure -> OpacSearchResult.Failure(
                reason = clientResult.reason,
                message = clientResult.message,
            )

            is OpacClientResult.Success -> {
                val parsedPage = runCatching {
                    parser.parse(clientResult.content, requestedPage = page)
                }.getOrNull()
                    ?: return@withContext OpacSearchResult.Failure(
                        OpacSearchFailure.INVALID_RESPONSE,
                    )
                OpacSearchResult.Success(enrichPage(parsedPage))
            }
        }
    }

    private suspend fun enrichPage(page: OpacSearchPage): OpacSearchPage = coroutineScope {
        val holdingIds = page.items
            .map(OpacBook::id)
            .filter { bookRecordNumber -> bookRecordNumber.isValidBookRecordNumber() }
            .distinct()
        val isbnKeys = page.items
            .mapNotNull { book -> book.isbn?.normalizedOpacIsbn() }
            .distinct()
        val holdings = async { fetchHoldings(holdingIds) }
        val covers = async { fetchCovers(isbnKeys) }
        val holdingsResult = holdings.await()
        val coversByIsbn = covers.await()
        val requestedHoldingIds = holdingIds.toSet()

        page.copy(
            items = page.items.map { book ->
                val isbnKey = book.isbn?.normalizedOpacIsbn()
                book.copy(
                    coverUrl = isbnKey?.let { key -> coversByIsbn[key] },
                    holdings = when {
                        book.id !in requestedHoldingIds -> null
                        book.id !in holdingsResult.completedBookIds -> null
                        else -> holdingsResult.values[book.id].orEmpty()
                    },
                )
            },
        )
    }

    private fun fetchHoldings(bookRecordNumbers: List<String>): HoldingsFetchResult {
        if (bookRecordNumbers.isEmpty()) {
            return HoldingsFetchResult()
        }
        val holdings = linkedMapOf<String, List<OpacHolding>>()
        val completedBookIds = linkedSetOf<String>()
        bookRecordNumbers.chunked(MAX_HOLDING_ITEMS).forEach chunkLoop@{ chunk ->
            val result = runCatching {
                client.fetchHoldings(chunk.joinToString(","))
            }.getOrNull() as? OpacClientResult.Success ?: return@chunkLoop
            val parsed = runCatching {
                supplementParser.parseHoldings(result.content)
            }.getOrNull() ?: return@chunkLoop
            completedBookIds += chunk
            val chunkIds = chunk.toSet()
            parsed.forEach parsedLoop@{ (bookId, values) ->
                if (bookId !in chunkIds) {
                    return@parsedLoop
                }
                if (values == null) {
                    completedBookIds -= bookId
                } else {
                    holdings[bookId] = values
                }
            }
        }
        return HoldingsFetchResult(
            values = holdings,
            completedBookIds = completedBookIds,
        )
    }

    private fun fetchCovers(isbns: List<String>): Map<String, String> {
        if (isbns.isEmpty()) {
            return emptyMap()
        }
        val covers = linkedMapOf<String, String>()
        isbns.chunked(MAX_COVER_ITEMS).forEach chunkLoop@{ chunk ->
            val result = runCatching {
                client.fetchCovers(chunk.joinToString(","))
            }.getOrNull() as? OpacClientResult.Success ?: return@chunkLoop
            val parsed = runCatching {
                supplementParser.parseCovers(result.content)
            }.getOrNull() ?: return@chunkLoop
            parsed.forEach { (isbn, coverUrl) ->
                covers.putIfAbsent(isbn, coverUrl)
            }
        }
        return covers
    }

    private fun String.isValidSearchQuery(): Boolean =
        isNotEmpty() &&
            toByteArray(Charsets.UTF_8).size <= MAX_SEARCH_QUERY_BYTES &&
            codePointCount(0, length) <= MAX_SEARCH_QUERY_CHARACTERS &&
            none(Character::isISOControl)

    private fun String.isValidBookRecordNumber(): Boolean =
        length in 1..MAX_BOOK_RECORD_NUMBER_LENGTH &&
            all { character -> character in '0'..'9' }

    private companion object {
        const val MAX_SEARCH_QUERY_BYTES = 800
        const val MAX_SEARCH_QUERY_CHARACTERS = 200
        const val MAX_SEARCH_PAGE = 10_000
        const val MAX_BOOK_RECORD_NUMBER_LENGTH = 20
        const val MAX_HOLDING_ITEMS = 50
        const val MAX_COVER_ITEMS = 30
    }

    private data class HoldingsFetchResult(
        val values: Map<String, List<OpacHolding>> = emptyMap(),
        val completedBookIds: Set<String> = emptySet(),
    )
}
