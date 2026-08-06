package cn.ahlib.reservation.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OpacHtmlParser {
    fun parse(
        html: String,
        requestedPage: Int = 1,
    ): OpacSearchPage? {
        if (html.isBlank() || requestedPage < 1) {
            return null
        }
        val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return null
        val searchMeta = document.selectFirst("#search_meta") ?: return null
        val total = TOTAL_PATTERN.find(searchMeta.text())
            ?.groupValues
            ?.getOrNull(1)
            ?.toCount()
            ?: return null
        val items = document
            .select(".resultList table.resultTable tr")
            .mapNotNull(::parseBook)
            .distinctBy(OpacBook::id)

        if (total == 0) {
            return if (requestedPage == 1 && items.isEmpty()) {
                OpacSearchPage(
                    items = emptyList(),
                    total = 0,
                    page = 1,
                    totalPages = 0,
                    isFirstPage = true,
                    isLastPage = true,
                )
            } else {
                null
            }
        }
        if (items.isEmpty()) {
            return null
        }

        val pager = document.selectFirst(".meneame") ?: return null
        val totalPages = TOTAL_PAGES_PATTERN.find(pager.text())
            ?.groupValues
            ?.getOrNull(1)
            ?.toPositiveCount()
            ?: return null
        val page = pager.select("b")
            .asSequence()
            .mapNotNull { element -> element.text().cleanText()?.toPositiveCount() }
            .firstOrNull()
            ?: return null
        if (page > totalPages || page != requestedPage) {
            return null
        }
        val homeUrl = pager.pagerUrl(HOME_PAGE_LABEL) ?: return null
        val previousUrl = pager.pagerUrl(PREVIOUS_PAGE_LABEL) ?: return null
        val nextUrl = pager.pagerUrl(NEXT_PAGE_LABEL) ?: return null
        val lastUrl = pager.pagerUrl(LAST_PAGE_LABEL) ?: return null
        val homePage = homeUrl.pageNumber() ?: return null
        val previousPage = previousUrl.pageNumber() ?: return null
        val nextPage = nextUrl.pageNumber() ?: return null
        val lastPage = lastUrl.pageNumber() ?: return null
        if (
            homePage != 1 ||
            previousPage != (page - 1).coerceAtLeast(1) ||
            nextPage != (page + 1).coerceAtMost(totalPages) ||
            lastPage != totalPages
        ) {
            return null
        }
        val isFirstPage = homeUrl == previousUrl && previousPage == page
        val isLastPage = nextUrl == lastUrl && nextPage == page
        if (isFirstPage != (page == 1) || isLastPage != (page == totalPages)) {
            return null
        }

        return OpacSearchPage(
            items = items,
            total = total,
            page = page,
            totalPages = totalPages,
            isFirstPage = isFirstPage,
            isLastPage = isLastPage,
        )
    }

    private fun parseBook(row: Element): OpacBook? {
        val metadata = row.selectFirst(".bookmeta[bookrecno]") ?: return null
        val id = metadata.attr("bookrecno").cleanText() ?: return null
        val title = metadata.selectFirst(".title-link")
            ?.text()
            ?.cleanText()
            ?: return null
        val authors = metadata.select(".author-link")
            .mapNotNull { element -> element.text().cleanText() }
            .distinct()
        val publishers = metadata.select(".publisher-link")
            .mapNotNull { element -> element.text().cleanText() }
            .distinct()
        val callNumbers = metadata.select(".callnosSpan")
            .mapNotNull { element -> element.text().cleanText() }
            .distinct()
        val isbn = row.select(".bookcover_img[isbn]")
            .asSequence()
            .mapNotNull { element -> element.attr("isbn").cleanIsbnDisplay() }
            .distinct()
            .firstOrNull()
        val firstLine = metadata.children().firstOrNull()
        val borrowCount = firstLine
            ?.select("span[style*=float]")
            ?.asSequence()
            ?.mapNotNull { element ->
                BORROW_COUNT_PATTERN.find(element.text())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toCount()
            }
            ?.firstOrNull()

        return OpacBook(
            id = id,
            title = title,
            authors = authors,
            publisher = publishers.joinDistinctValues(),
            publicationDate = PUBLICATION_DATE_PATTERN.find(metadata.text())
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanText(),
            documentType = DOCUMENT_TYPE_PATTERN.find(metadata.text())
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanText(),
            callNumber = callNumbers.joinDistinctValues(),
            isbn = isbn,
            borrowCount = borrowCount,
        )
    }

    private fun List<String>.joinDistinctValues(): String? =
        takeIf(List<String>::isNotEmpty)?.joinToString(" / ")

    private fun String.cleanText(): String? =
        trim()
            .replace(WHITESPACE_PATTERN, " ")
            .takeIf(String::isNotEmpty)

    private fun String.cleanIsbnDisplay(): String? =
        cleanText()
            ?.trimEnd(':', '\uFF1A')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.takeIf { value -> value.normalizedOpacIsbn() != null }

    private fun String.toCount(): Int? =
        replace(",", "").toIntOrNull()?.takeIf { value -> value >= 0 }

    private fun String.toPositiveCount(): Int? =
        toCount()?.takeIf { value -> value > 0 }

    private fun Element.pagerUrl(label: String): String? =
        select("a[href]")
            .firstOrNull { link -> link.text().contains(label) }
            ?.attr("href")
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun String.pageNumber(): Int? =
        PAGE_QUERY_PATTERN.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toPositiveCount()

    private companion object {
        val WHITESPACE_PATTERN = Regex("[\\s\\u00a0]+")
        val TOTAL_PATTERN = Regex(
            "\u68c0\u7d22\u5230\\s*[:\uFF1A]?\\s*([\\d,]+)\\s*\u6761\u7ed3\u679c",
        )
        val TOTAL_PAGES_PATTERN = Regex("\u5171\\s*([\\d,]+)\\s*\u9875")
        val PAGE_QUERY_PATTERN = Regex("(?:[?&])page=([\\d,]+)(?:&|$)")
        val BORROW_COUNT_PATTERN = Regex("\u5df2\u501f\\s*([\\d,]+)\\s*\u6b21")
        val PUBLICATION_DATE_PATTERN = Regex(
            "\u51fa\u7248\u65e5\u671f\\s*[:\uFF1A]?\\s*([^\\s,\uFF0C]+)",
        )
        val DOCUMENT_TYPE_PATTERN = Regex(
            "\u6587\u732e\u7c7b\u578b\\s*[:\uFF1A]?\\s*(.+?)" +
                "(?=\\s*[,\uFF0C]|\\s*\u7d22\u4e66\u53f7\\s*[:\uFF1A]|$)",
        )
        const val HOME_PAGE_LABEL = "\u9996\u9875"
        const val PREVIOUS_PAGE_LABEL = "\u4e0a\u4e00\u9875"
        const val NEXT_PAGE_LABEL = "\u4e0b\u4e00\u9875"
        const val LAST_PAGE_LABEL = "\u5c3e\u9875"
    }
}
