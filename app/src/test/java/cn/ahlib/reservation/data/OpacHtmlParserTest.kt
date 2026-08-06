package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpacHtmlParserTest {
    private val parser = OpacHtmlParser()

    @Test
    fun `normal results parse stable metadata and the first pager`() {
        val page = parser.parse(
            html = """
                <div id="search_meta">
                    &#x68C0;&#x7D22;&#x5230;: 1,234 &#x6761;&#x7ED3;&#x679C;
                </div>
                <div class="meneame">
                    <span class="disabled">&#x5171; 124 &#x9875;</span>
                    <a href="/opac/search?q=rust&amp;page=1">&#x9996;&#x9875;</a>
                    <a href="/opac/search?q=rust&amp;page=1">&lt;&#x4E0A;&#x4E00;&#x9875;</a>
                    <b>2</b>
                    <a href="/opac/search?q=rust&amp;page=3">&#x4E0B;&#x4E00;&#x9875;&gt;</a>
                    <a href="/opac/search?q=rust&amp;page=124">&#x5C3E;&#x9875;&gt;&gt;</a>
                </div>
                <div class="resultList">
                    <table class="resultTable">
                        <tr>
                            <td>
                                <img class="bookcover_img" isbn=" 978-7-115-21131-6 : ">
                            </td>
                            <td>
                                <div class="bookmeta" bookrecno=" book-1 ">
                                    <div>
                                        <a class="title-link">  Practical   Rust  </a>
                                        <span style="padding: 2px; float: right">
                                            &#x5DF2;&#x501F;1,205&#x6B21;.
                                        </span>
                                    </div>
                                    <div>
                                        <a class="author-link"> Alice </a>
                                        <a class="author-link">Bob</a>
                                        <a class="author-link">Alice</a>
                                    </div>
                                    <div>
                                        <a class="publisher-link"> Example Press </a>
                                        &#x51FA;&#x7248;&#x65E5;&#x671F;: 2026
                                    </div>
                                    <div>
                                        &#x6587;&#x732E;&#x7C7B;&#x578B;: Book,
                                        &#x7D22;&#x4E66;&#x53F7;:
                                        <span class="callnosSpan"> TP1 / 42 </span>
                                        <span class="callnosSpan">TP1 / 42</span>
                                    </div>
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td><img class="bookcover_img" isbn="ignored"></td>
                            <td>
                                <div class="bookmeta" bookrecno="book-1">
                                    <div><a class="title-link">Duplicate</a></div>
                                </div>
                            </td>
                        </tr>
                    </table>
                </div>
                <div class="meneame">
                    <span class="disabled">&#x5171; 999 &#x9875;</span>
                    <b>99</b>
                </div>
            """.trimIndent(),
            requestedPage = 2,
        )

        requireNotNull(page)
        assertEquals(1_234, page.total)
        assertEquals(2, page.page)
        assertEquals(124, page.totalPages)
        assertFalse(page.isFirstPage)
        assertFalse(page.isLastPage)
        assertEquals(1, page.items.size)
        assertEquals(
            OpacBook(
                id = "book-1",
                title = "Practical Rust",
                authors = listOf("Alice", "Bob"),
                publisher = "Example Press",
                publicationDate = "2026",
                documentType = "Book",
                callNumber = "TP1 / 42",
                isbn = "978-7-115-21131-6",
                borrowCount = 1_205,
            ),
            page.items.single(),
        )
    }

    @Test
    fun `explicit zero result metadata returns an empty page`() {
        val page = parser.parse(
            """
                <html><body>
                    <div id="search_meta">
                        &#x68C0;&#x7D22;&#x5230;: 0 &#x6761;&#x7ED3;&#x679C;
                    </div>
                </body></html>
            """.trimIndent(),
            requestedPage = 1,
        )

        requireNotNull(page)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
        assertEquals(1, page.page)
        assertEquals(0, page.totalPages)
        assertTrue(page.isFirstPage)
        assertTrue(page.isLastPage)
    }

    @Test
    fun `a zero result response for a non-first page is rejected`() {
        assertNull(
            parser.parse(
                html = """
                    <div id="search_meta">
                        &#x68C0;&#x7D22;&#x5230;: 0 &#x6761;&#x7ED3;&#x679C;
                    </div>
                """.trimIndent(),
                requestedPage = 2,
            ),
        )
    }

    @Test
    fun `missing optional book fields remain empty`() {
        val page = parser.parse(
            resultPageHtml(
                total = "1",
                rows = """
                    <tr>
                        <td>
                            <div class="bookmeta" bookrecno="minimal">
                                <div><a class="title-link">Minimal Book</a></div>
                            </div>
                        </td>
                    </tr>
                """.trimIndent(),
            ),
        )

        val book = requireNotNull(page).items.single()
        assertTrue(book.authors.isEmpty())
        assertNull(book.publisher)
        assertNull(book.publicationDate)
        assertNull(book.documentType)
        assertNull(book.callNumber)
        assertNull(book.isbn)
        assertNull(book.borrowCount)
    }

    @Test
    fun `malformed result pages are rejected`() {
        assertNull(
            parser.parse(
                """
                    <html><body><div id="unrelated"></div></body></html>
                """.trimIndent(),
            ),
        )
        assertNull(parser.parse("<html><body><h1>Server error</h1></body></html>"))
        assertNull(parser.parse(resultPageHtml(total = "4", rows = "")))
        assertNull(
            parser.parse(
                """
                    <div id="search_meta">Unexpected metadata</div>
                    <div class="resultList"></div>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a response for a different page is rejected`() {
        assertNull(
            parser.parse(
                html = resultPageHtml(
                    total = "1",
                    rows = """
                        <tr>
                            <td>
                                <div class="bookmeta" bookrecno="book-1">
                                    <div><a class="title-link">Book</a></div>
                                </div>
                            </td>
                        </tr>
                    """.trimIndent(),
                ),
                requestedPage = 2,
            ),
        )
    }

    @Test
    fun `pager URL targets distinguish first middle and last pages`() {
        val rows = """
            <tr>
                <td>
                    <div class="bookmeta" bookrecno="book-1">
                        <div><a class="title-link">Book</a></div>
                    </div>
                </td>
            </tr>
        """.trimIndent()

        val first = requireNotNull(
            parser.parse(
                resultPageHtml("2,100", rows, page = 1, totalPages = 210),
                requestedPage = 1,
            ),
        )
        val second = requireNotNull(
            parser.parse(
                resultPageHtml("2,100", rows, page = 2, totalPages = 210),
                requestedPage = 2,
            ),
        )
        val penultimate = requireNotNull(
            parser.parse(
                resultPageHtml("2,100", rows, page = 209, totalPages = 210),
                requestedPage = 209,
            ),
        )
        val last = requireNotNull(
            parser.parse(
                resultPageHtml("2,100", rows, page = 210, totalPages = 210),
                requestedPage = 210,
            ),
        )

        assertTrue(first.isFirstPage)
        assertFalse(first.isLastPage)
        assertFalse(second.isFirstPage)
        assertFalse(second.isLastPage)
        assertFalse(penultimate.isFirstPage)
        assertFalse(penultimate.isLastPage)
        assertFalse(last.isFirstPage)
        assertTrue(last.isLastPage)
    }

    @Test
    fun `inconsistent pager URL targets are rejected`() {
        val rows = """
            <tr>
                <td>
                    <div class="bookmeta" bookrecno="book-1">
                        <div><a class="title-link">Book</a></div>
                    </div>
                </td>
            </tr>
        """.trimIndent()

        assertNull(
            parser.parse(
                html = resultPageHtml(
                    total = "2,100",
                    rows = rows,
                    page = 2,
                    totalPages = 210,
                    nextPage = 4,
                ),
                requestedPage = 2,
            ),
        )
    }

    private fun resultPageHtml(
        total: String,
        rows: String,
        page: Int = 1,
        totalPages: Int = 1,
        homePage: Int = 1,
        previousPage: Int = (page - 1).coerceAtLeast(1),
        nextPage: Int = (page + 1).coerceAtMost(totalPages),
        lastPage: Int = totalPages,
    ): String = """
        <div id="search_meta">
            &#x68C0;&#x7D22;&#x5230;: $total &#x6761;&#x7ED3;&#x679C;
        </div>
        <div class="meneame">
            <span>&#x5171; $totalPages &#x9875;</span>
            <a href="/opac/search?q=test&amp;page=$homePage">&#x9996;&#x9875;</a>
            <a href="/opac/search?q=test&amp;page=$previousPage">
                &lt;&#x4E0A;&#x4E00;&#x9875;
            </a>
            <b>$page</b>
            <a href="/opac/search?q=test&amp;page=$nextPage">
                &#x4E0B;&#x4E00;&#x9875;&gt;
            </a>
            <a href="/opac/search?q=test&amp;page=$lastPage">
                &#x5C3E;&#x9875;&gt;&gt;
            </a>
        </div>
        <div class="resultList">
            <table class="resultTable">$rows</table>
        </div>
    """.trimIndent()
}
