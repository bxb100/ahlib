package cn.ahlib.reservation.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpacRepositoryTest {
    @Test
    fun `search trims the query and parses client html`() = runTest {
        val client = RecordingOpacClient(
            searchResult = OpacClientResult.Success(singleResultHtml()),
            holdingsResult = OpacClientResult.Success(
                """
                    {
                      "previews": {
                        "1000051870": [{
                          "callno": "TP312/B-651/2012",
                          "curlibName": "Main Library",
                          "curlocalName": "Reading Room",
                          "copycount": 2,
                          "loanableCount": 1
                        }]
                      }
                    }
                """.trimIndent(),
            ),
            coversResult = OpacClientResult.Success(
                """
                    ({"result":[{
                      "isbn":"9787115211316",
                      "resourceLink":"https://book-resource.dataesb.com/cover/book.jpeg"
                    }]})
                """.trimIndent(),
            ),
        )
        val repository = OpacRepository(client, OpacHtmlParser())

        val result = repository.search("  Rust  ", 1)

        assertTrue(result is OpacSearchResult.Success)
        assertEquals("Rust", client.query)
        assertEquals(1, client.page)
        val book = (result as OpacSearchResult.Success).page.items.single()
        assertEquals("1000051870", book.id)
        assertEquals("1000051870", client.bookRecordNumbersCsv)
        assertEquals("9787115211316", client.isbnsCsv)
        assertEquals(
            "https://book-resource.dataesb.com/cover/book.jpeg",
            book.coverUrl,
        )
        assertEquals(1, book.holdings?.single()?.availableCopies)
    }

    @Test
    fun `invalid inputs are rejected before client fetch`() = runTest {
        val client = RecordingOpacClient(
            OpacClientResult.Success(singleResultHtml()),
        )
        val repository = OpacRepository(client, OpacHtmlParser())

        assertEquals(
            OpacSearchResult.Failure(OpacSearchFailure.INVALID_RESPONSE),
            repository.search("  ", 1),
        )
        assertEquals(
            OpacSearchResult.Failure(OpacSearchFailure.INVALID_RESPONSE),
            repository.search("Rust", 0),
        )
        assertNull(client.query)
    }

    @Test
    fun `client failures retain their reason and message`() = runTest {
        val repository = OpacRepository(
            RecordingOpacClient(
                OpacClientResult.Failure(
                    reason = OpacSearchFailure.NETWORK,
                    message = "request timed out",
                ),
            ),
            OpacHtmlParser(),
        )

        assertEquals(
            OpacSearchResult.Failure(
                reason = OpacSearchFailure.NETWORK,
                message = "request timed out",
            ),
            repository.search("Rust", 1),
        )
    }

    @Test
    fun `unexpected client html is rejected`() = runTest {
        val repository = OpacRepository(
            RecordingOpacClient(
                OpacClientResult.Success("<html><body>unexpected</body></html>"),
            ),
            OpacHtmlParser(),
        )

        assertEquals(
            OpacSearchResult.Failure(OpacSearchFailure.INVALID_RESPONSE),
            repository.search("Rust", 1),
        )
    }

    @Test
    fun `supplement failures do not discard search results`() = runTest {
        val client = RecordingOpacClient(
            searchResult = OpacClientResult.Success(singleResultHtml()),
        )
        val repository = OpacRepository(client)

        val result = repository.search("Rust", 1)

        assertTrue(result is OpacSearchResult.Success)
        val book = (result as OpacSearchResult.Success).page.items.single()
        assertNull(book.coverUrl)
        assertNull(book.holdings)
        assertEquals(listOf("1000051870"), client.bookRecordNumberRequests)
        assertEquals(listOf("9787115211316"), client.isbnRequests)
    }

    @Test
    fun `successful empty previews produce an empty holdings state`() = runTest {
        val client = RecordingOpacClient(
            searchResult = OpacClientResult.Success(singleResultHtml()),
            holdingsResult = OpacClientResult.Success("{\"previews\":{}}"),
        )
        val repository = OpacRepository(client)

        val result = repository.search("Rust", 1) as OpacSearchResult.Success

        assertEquals(emptyList<OpacHolding>(), result.page.items.single().holdings)
    }

    @Test
    fun `query limits accept two hundred four byte code points`() = runTest {
        val client = RecordingOpacClient(
            OpacClientResult.Success(singleResultHtml()),
        )
        val repository = OpacRepository(client)
        val maximumQuery = "\uD83D\uDE00".repeat(200)

        assertTrue(
            repository.search(
                maximumQuery,
                1,
            ) is OpacSearchResult.Success,
        )
        assertEquals(maximumQuery, client.query)
        assertEquals(1, client.callCount)

        assertEquals(
            OpacSearchResult.Failure(OpacSearchFailure.INVALID_RESPONSE),
            repository.search("x".repeat(201), 1),
        )
        assertEquals(1, client.callCount)
    }

    @Test
    fun `supplement requests are chunked at service limits`() = runTest {
        val client = RecordingOpacClient(
            searchResult = OpacClientResult.Success(manyResultsHtml(51)),
            holdingsResult = OpacClientResult.Success("{\"previews\":{}}"),
            coversResult = OpacClientResult.Success("({\"result\":[]})"),
        )
        val repository = OpacRepository(client)

        assertTrue(repository.search("Catalog", 1) is OpacSearchResult.Success)
        assertEquals(
            listOf(50, 1),
            client.bookRecordNumberRequests.map { request ->
                request.split(',').size
            },
        )
        assertEquals(
            listOf(30, 21),
            client.isbnRequests.map { request -> request.split(',').size },
        )
    }

    @Test
    fun `successful supplement chunks survive a later chunk failure`() = runTest {
        val firstIsbn = validIsbn13(0)
        val client = RecordingOpacClient(
            searchResult = OpacClientResult.Success(manyResultsHtml(51)),
            holdingsResults = listOf(
                OpacClientResult.Success("{\"previews\":{}}"),
                OpacClientResult.Failure(OpacSearchFailure.NETWORK),
            ),
            coversResults = listOf(
                OpacClientResult.Success(
                    """
                        ({"result":[{
                          "isbn":"$firstIsbn",
                          "resourceLink":"https://book-resource.dataesb.com/cover.jpg"
                        }]})
                    """.trimIndent(),
                ),
                OpacClientResult.Failure(OpacSearchFailure.NETWORK),
            ),
        )
        val repository = OpacRepository(client)

        val page = (repository.search("Catalog", 1) as OpacSearchResult.Success).page

        assertTrue(
            page.items.take(50).all { book ->
                book.holdings == emptyList<OpacHolding>()
            },
        )
        assertNull(page.items.last().holdings)
        assertEquals(
            "https://book-resource.dataesb.com/cover.jpg",
            page.items.first().coverUrl,
        )
    }

    private class RecordingOpacClient(
        private val searchResult: OpacClientResult,
        private val holdingsResult: OpacClientResult = OpacClientResult.Failure(
            OpacSearchFailure.NETWORK,
        ),
        private val coversResult: OpacClientResult = OpacClientResult.Failure(
            OpacSearchFailure.NETWORK,
        ),
        private val holdingsResults: List<OpacClientResult>? = null,
        private val coversResults: List<OpacClientResult>? = null,
    ) : OpacClient {
        var query: String? = null
        var page: Int? = null
        var callCount: Int = 0
        var bookRecordNumbersCsv: String? = null
        var isbnsCsv: String? = null
        val bookRecordNumberRequests = mutableListOf<String>()
        val isbnRequests = mutableListOf<String>()

        override fun fetchSearch(
            query: String,
            page: Int,
        ): OpacClientResult {
            callCount += 1
            this.query = query
            this.page = page
            return searchResult
        }

        override fun fetchHoldings(bookRecordNumbersCsv: String): OpacClientResult {
            this.bookRecordNumbersCsv = bookRecordNumbersCsv
            bookRecordNumberRequests += bookRecordNumbersCsv
            return holdingsResults?.getOrNull(bookRecordNumberRequests.lastIndex)
                ?: holdingsResult
        }

        override fun fetchCovers(isbnsCsv: String): OpacClientResult {
            this.isbnsCsv = isbnsCsv
            isbnRequests += isbnsCsv
            return coversResults?.getOrNull(isbnRequests.lastIndex)
                ?: coversResult
        }
    }

    private companion object {
        fun singleResultHtml(): String = """
            <div id="search_meta">
                &#x68C0;&#x7D22;&#x5230;: 1 &#x6761;&#x7ED3;&#x679C;
            </div>
            <div class="meneame">
                <span>&#x5171; 1 &#x9875;</span>
                <a href="/opac/search?q=rust&amp;page=1">&#x9996;&#x9875;</a>
                <a href="/opac/search?q=rust&amp;page=1">&lt;&#x4E0A;&#x4E00;&#x9875;</a>
                <b>1</b>
                <a href="/opac/search?q=rust&amp;page=1">&#x4E0B;&#x4E00;&#x9875;&gt;</a>
                <a href="/opac/search?q=rust&amp;page=1">&#x5C3E;&#x9875;&gt;&gt;</a>
            </div>
            <div class="resultList">
                <table class="resultTable">
                    <tr>
                        <td>
                            <img class="bookcover_img" isbn="9787115211316">
                            <div class="bookmeta" bookrecno="1000051870">
                                <div><a class="title-link">Rust</a></div>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
        """.trimIndent()

        fun manyResultsHtml(count: Int): String {
            val rows = (0 until count).joinToString(separator = "") { index ->
                """
                    <table class="resultTable">
                        <tr>
                            <td>
                                <img class="bookcover_img" isbn="${validIsbn13(index)}">
                                <div class="bookmeta" bookrecno="${1_000 + index}">
                                    <div><a class="title-link">Book $index</a></div>
                                </div>
                            </td>
                        </tr>
                    </table>
                """.trimIndent()
            }
            return """
                <div id="search_meta">
                    &#x68C0;&#x7D22;&#x5230;: $count &#x6761;&#x7ED3;&#x679C;
                </div>
                <div class="meneame">
                    <span>&#x5171; 1 &#x9875;</span>
                    <a href="/opac/search?q=catalog&amp;page=1">&#x9996;&#x9875;</a>
                    <a href="/opac/search?q=catalog&amp;page=1">
                        &lt;&#x4E0A;&#x4E00;&#x9875;
                    </a>
                    <b>1</b>
                    <a href="/opac/search?q=catalog&amp;page=1">
                        &#x4E0B;&#x4E00;&#x9875;&gt;
                    </a>
                    <a href="/opac/search?q=catalog&amp;page=1">
                        &#x5C3E;&#x9875;&gt;&gt;
                    </a>
                </div>
                <div class="resultList">$rows</div>
            """.trimIndent()
        }

        fun validIsbn13(index: Int): String {
            val body = "97812345${index.toString().padStart(4, '0')}"
            val sum = body.mapIndexed { digitIndex, character ->
                character.digitToInt() * if (digitIndex % 2 == 0) 1 else 3
            }.sum()
            return body + ((10 - sum % 10) % 10)
        }
    }
}
