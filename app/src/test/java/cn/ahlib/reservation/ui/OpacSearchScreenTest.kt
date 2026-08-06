package cn.ahlib.reservation.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.OpacBook
import cn.ahlib.reservation.data.OpacHolding
import cn.ahlib.reservation.ui.theme.ReservationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OpacSearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun quickActionFabIconTracksTheViewportThreshold() {
        var hasScrolledBeyondViewport by mutableStateOf(false)
        val state = ReservationUiState(
            selectedTab = AuthenticatedTab.OPAC,
            opacSearch = OpacSearchUiState(
                books = listOf(OpacBook(id = "book-id", title = "Book")),
            ),
        )

        composeRule.setContent {
            ReservationTheme {
                QuickActionFabIcon(
                    state = state,
                    hasScrolledBeyondViewport = hasScrolledBeyondViewport,
                    fabMenuExpanded = false,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.open_quick_actions),
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            hasScrolledBeyondViewport = true
        }
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.scroll_to_top))
            .assertIsDisplayed()

        composeRule.runOnIdle {
            hasScrolledBeyondViewport = false
        }
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.open_quick_actions),
            )
            .assertIsDisplayed()
    }

    @Test
    fun initialStateExplainsThatSearchUsesTheBookTitle() {
        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = emptyList(),
                    searchQuery = "",
                    total = 0,
                    hasSearched = false,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.opac_search_hint))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.opac_initial_hint))
            .assertIsDisplayed()
    }

    @Test
    fun resultDisplaysEveryProvidedBookField() {
        val authors = listOf("Ada Lovelace", "Charles Babbage")
        val book = OpacBook(
            id = "book-id",
            title = "Analytical Engines",
            authors = authors,
            publisher = "Example Press",
            publicationDate = "2026",
            documentType = "Monograph",
            callNumber = "TP391.1",
            isbn = "9780000000000",
            borrowCount = 12,
            holdings = listOf(
                OpacHolding(
                    libraryName = "Main Library",
                    locationName = "Reading Room",
                    callNumber = "TP391.2",
                    availableCopies = 1,
                    totalCopies = 2,
                ),
                OpacHolding(
                    libraryName = "Main Library",
                    locationName = "Stacks",
                    availableCopies = 0,
                    totalCopies = 1,
                ),
            ),
        )

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = listOf(book),
                    searchQuery = "Analytical",
                    total = 1,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        val authorText = authors.joinToString(
            separator = context.getString(R.string.opac_author_separator),
        )
        val expectedTexts = listOf(
            context.getString(R.string.opac_result_count, 1),
            book.title,
            context.getString(R.string.opac_authors_value, authorText),
            context.getString(
                R.string.opac_publisher_value,
                requireNotNull(book.publisher),
            ),
            context.getString(
                R.string.opac_publication_date_value,
                requireNotNull(book.publicationDate),
            ),
            context.getString(
                R.string.opac_document_type_value,
                requireNotNull(book.documentType),
            ),
            context.getString(
                R.string.opac_call_number_value,
                requireNotNull(book.callNumber),
            ),
            context.getString(
                R.string.opac_isbn_value,
                requireNotNull(book.isbn),
            ),
            context.getString(
                R.string.opac_borrow_count_value,
                requireNotNull(book.borrowCount),
            ),
            context.getString(R.string.opac_holdings_title),
            context.getString(R.string.opac_holdings_summary, 1, 3),
            "Main Library \u00b7 Reading Room",
            context.getString(R.string.opac_holding_counts, 1, 2),
            "Main Library \u00b7 Stacks",
            context.getString(R.string.opac_holding_counts, 0, 1),
        )

        expectedTexts.forEach { text ->
            composeRule
                .onNodeWithText(text)
                .performScrollTo()
                .assertIsDisplayed()
        }
        val cover = composeRule.onNodeWithContentDescription(
            context.getString(R.string.opac_book_cover, book.title),
        )
        cover.performScrollTo().assertIsDisplayed()
        val coverBounds = cover.fetchSemanticsNode().boundsInRoot
        assertEquals(coverBounds.width, coverBounds.height, 0.5f)
    }

    @Test
    fun trailingIconsInvokeSearchAndClearCallbacks() {
        var searchQuery by mutableStateOf("Atlas")
        var searchCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = emptyList(),
                    searchQuery = searchQuery,
                    total = 0,
                    hasSearched = false,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = { searchCount += 1 },
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.search))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, searchCount)
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.clear_search))
            .performClick()
        composeRule.runOnIdle {
            assertEquals("", searchQuery)
        }
    }

    @Test
    fun zeroResultStateExplainsThatNothingMatched() {
        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = emptyList(),
                    searchQuery = "Missing title",
                    total = 0,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.opac_zero_results))
            .assertIsDisplayed()
    }

    @Test
    fun holdingsDistinguishNoRecordsFromUnavailableData() {
        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = listOf(
                        OpacBook(
                            id = "empty",
                            title = "No holdings",
                            holdings = emptyList(),
                        ),
                        OpacBook(
                            id = "unavailable",
                            title = "Unavailable holdings",
                            holdings = null,
                        ),
                    ),
                    searchQuery = "Holdings",
                    total = 2,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.opac_no_holdings))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.opac_holdings_unavailable))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun resultsDoNotDisplayPaginationControls() {
        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = listOf(OpacBook(id = "book-id", title = "Search result")),
                    searchQuery = "Search",
                    total = 2_098,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onAllNodesWithText("2 / 210", substring = true)
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText(context.getString(R.string.load_more))
            .assertCountEquals(0)
    }

    @Test
    fun userScrollNearTheBottomLoadsMoreOnce() {
        val books = (1..24).map { index ->
            OpacBook(id = "book-$index", title = "Book $index")
        }
        var loadMoreCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = books,
                    searchQuery = "Book",
                    total = 2_098,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = { loadMoreCount += 1 },
                    listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = books.size - 4,
                    ),
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(0, loadMoreCount)
        }
        composeRule
            .onNodeWithTag(OPAC_RESULTS_TEST_TAG)
            .performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, loadMoreCount)
        }
    }

    @Test
    fun userScrollTowardTheTopDoesNotLoadMore() {
        val books = (1..24).map { index ->
            OpacBook(id = "book-$index", title = "Book $index")
        }
        var loadMoreCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = books,
                    searchQuery = "Book",
                    total = 2_098,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = { loadMoreCount += 1 },
                    listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = books.size,
                    ),
                )
            }
        }

        composeRule
            .onNodeWithTag(OPAC_RESULTS_TEST_TAG)
            .performTouchInput { swipeDown() }
        composeRule.runOnIdle {
            assertEquals(0, loadMoreCount)
        }
    }

    @Test
    fun userSwipeOnShortResultListLoadsMoreOnce() {
        var loadMoreCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = listOf(OpacBook(id = "book-id", title = "Book")),
                    searchQuery = "Book",
                    total = 20,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = { loadMoreCount += 1 },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(0, loadMoreCount)
        }
        composeRule
            .onNodeWithTag(OPAC_RESULTS_TEST_TAG)
            .performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(1, loadMoreCount)
        }
    }

    @Test
    fun loadingMorePreventsAnotherRequestWhileScrolling() {
        val books = (1..24).map { index ->
            OpacBook(id = "book-$index", title = "Book $index")
        }
        var loadMoreCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = books,
                    searchQuery = "Book",
                    total = 2_098,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = true,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = { loadMoreCount += 1 },
                    listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = books.size - 4,
                    ),
                )
            }
        }

        composeRule
            .onNodeWithTag(OPAC_RESULTS_TEST_TAG)
            .performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertEquals(0, loadMoreCount)
        }
        composeRule
            .onNodeWithTag(OPAC_RESULTS_TEST_TAG)
            .performScrollToIndex(books.size + 1)
        composeRule
            .onNodeWithText(context.getString(R.string.loading_more))
            .assertIsDisplayed()
    }

    @Test
    fun programmaticAnimatedScrollToTopDoesNotLoadMore() {
        val books = (1..160).map { index ->
            OpacBook(id = "book-$index", title = "Book $index")
        }
        var loadMoreCount = 0
        lateinit var listState: LazyListState
        lateinit var coroutineScope: CoroutineScope

        composeRule.setContent {
            listState = rememberLazyListState(
                initialFirstVisibleItemIndex = books.size,
            )
            coroutineScope = rememberCoroutineScope()
            ReservationTheme {
                OpacSearchScreen(
                    books = books,
                    searchQuery = "Book",
                    total = 2_098,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = true,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = { loadMoreCount += 1 },
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithText(books.last().title).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, loadMoreCount)
            coroutineScope.launch {
                listState.animateOpacScrollToTop()
            }
        }
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(books.first().title)
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, loadMoreCount)
        }
    }

    @Test
    fun userScrollReportsEnoughConsumedPixelsToCrossAndRestoreViewportThreshold() {
        val books = (1..24).map { index ->
            OpacBook(id = "book-$index", title = "Book $index")
        }
        val tracker = OpacScrollThresholdTracker()
        lateinit var listState: LazyListState

        composeRule.setContent {
            listState = rememberLazyListState()
            ReservationTheme {
                OpacSearchScreen(
                    books = books,
                    searchQuery = "Book",
                    total = books.size,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = null,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = {},
                    onLoadMore = {},
                    onScrollConsumed = { consumedY ->
                        tracker.onScrollConsumed(
                            consumedY = consumedY,
                            canScrollBackward = listState.canScrollBackward,
                        )
                    },
                    listState = listState,
                )
            }
        }

        val results = composeRule.onNodeWithTag(OPAC_RESULTS_TEST_TAG)
        composeRule.runOnIdle {
            tracker.updateViewportHeight(listState.layoutInfo.viewportSize.height)
        }
        repeat(3) {
            results.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertTrue(tracker.isBeyondViewport)
        }

        repeat(6) {
            results.performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertFalse(listState.canScrollBackward)
            assertEquals(0.0, tracker.scrollDistancePx, 0.0)
            assertFalse(tracker.isBeyondViewport)
        }
    }

    @Test
    fun loadMoreErrorKeepsBooksAndInvokesRetry() {
        val book = OpacBook(id = "book-id", title = "Existing result")
        val error = "Request failed"
        var retryCount = 0

        composeRule.setContent {
            ReservationTheme {
                OpacSearchScreen(
                    books = listOf(book),
                    searchQuery = "Existing",
                    total = 20,
                    hasSearched = true,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = false,
                    errorText = error,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onRetry = { retryCount += 1 },
                    onLoadMore = {},
                )
            }
        }

        composeRule
            .onNodeWithText(book.title)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(error)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.retry))
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, retryCount)
        }
    }
}
