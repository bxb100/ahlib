package cn.ahlib.reservation.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import cn.ahlib.reservation.data.ApiEnvelope
import cn.ahlib.reservation.data.AuthenticationTokenUpdateResult
import cn.ahlib.reservation.data.ClearableCookieJar
import cn.ahlib.reservation.data.CookieCloudConfig
import cn.ahlib.reservation.data.CookieCloudConfigStorage
import cn.ahlib.reservation.data.CookieCloudFailureReason
import cn.ahlib.reservation.data.CookieCloudPayloadResult
import cn.ahlib.reservation.data.CookieCloudPayloadSource
import cn.ahlib.reservation.data.CookieCloudSessionManager
import cn.ahlib.reservation.data.CookieCloudTokenDecoder
import cn.ahlib.reservation.data.CookieCloudTokenResult
import cn.ahlib.reservation.data.OpacBook
import cn.ahlib.reservation.data.OpacClient
import cn.ahlib.reservation.data.OpacClientResult
import cn.ahlib.reservation.data.OpacRepository
import cn.ahlib.reservation.data.OpacSearchFailure
import cn.ahlib.reservation.data.OpacSearchPage
import cn.ahlib.reservation.data.OpacSearchResult
import cn.ahlib.reservation.data.PasswordCipher
import cn.ahlib.reservation.data.ReaderQrCodeFailure
import cn.ahlib.reservation.data.ReaderQrCodeRepository
import cn.ahlib.reservation.data.ReaderQrCodeResult
import cn.ahlib.reservation.data.ReaderQrNativeClient
import cn.ahlib.reservation.data.ReservationApi
import cn.ahlib.reservation.data.ReservationRepository
import cn.ahlib.reservation.data.createAuthenticationCookie
import cn.ahlib.reservation.location.DeviceLocationProvider
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OpacSearchUiStateTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<ReservationViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { viewModel -> viewModel.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `later pages append new books without duplicating existing records`() {
        val first = OpacBook(id = "book-1", title = "First")
        val existing = OpacBook(id = "book-2", title = "Existing")
        val duplicateFirst = OpacBook(id = "book-1", title = "Duplicate first")
        val duplicate = OpacBook(id = "book-2", title = "Duplicate")
        val third = OpacBook(id = "book-3", title = "Third")
        val duplicateThird = OpacBook(id = "book-3", title = "Duplicate third")

        assertEquals(
            listOf(first, existing, third),
            appendDistinctOpacBooks(
                current = listOf(first, existing, duplicateFirst),
                incoming = listOf(duplicate, third, duplicateThird),
            ),
        )
    }

    @Test
    fun `pagination publishes only after the injected merge dispatcher runs`() = runBlocking {
        val mergeDispatcher = StandardTestDispatcher()
        val first = OpacBook(id = "book-1", title = "First")
        val duplicate = OpacBook(id = "book-1", title = "Duplicate")
        val second = OpacBook(id = "book-2", title = "Second")
        val viewModel = createViewModel(opacMergeDispatcher = mergeDispatcher) { _, page ->
            when (page) {
                1 -> successResult(first, total = 2, page = 1, totalPages = 2)
                2 -> successResult(
                    duplicate,
                    second,
                    total = 2,
                    page = 2,
                    totalPages = 2,
                )

                else -> error("Unexpected page: $page")
            }
        }

        viewModel.updateOpacSearchQuery("Query")
        viewModel.submitOpacSearch()
        awaitOpacIdle(viewModel, appliedQuery = "Query")

        viewModel.loadNextOpacPage()

        assertTrue(viewModel.uiState.value.opacSearch.isLoadingMore)
        assertEquals(listOf(first), viewModel.uiState.value.opacSearch.books)

        mergeDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.runCurrent()
        awaitOpacIdle(viewModel, appliedQuery = "Query")

        assertEquals(listOf(first, second), viewModel.uiState.value.opacSearch.books)
        assertEquals(2, viewModel.uiState.value.opacSearch.page)
    }

    @Test
    fun `new search discards an append waiting on the merge dispatcher`() = runBlocking {
        val mergeDispatcher = StandardTestDispatcher()
        val oldBook = OpacBook(id = "old-page-one", title = "Old page one")
        val staleBook = OpacBook(id = "old-page-two", title = "Old page two")
        val newBook = OpacBook(id = "new-page-one", title = "New page one")
        val viewModel = createViewModel(opacMergeDispatcher = mergeDispatcher) { query, page ->
            when (query to page) {
                "Old query" to 1 -> successResult(
                    oldBook,
                    total = 2,
                    page = 1,
                    totalPages = 2,
                )
                "Old query" to 2 -> successResult(
                    staleBook,
                    total = 2,
                    page = 2,
                    totalPages = 2,
                )
                "New query" to 1 -> successResult(
                    newBook,
                    total = 1,
                    page = 1,
                    totalPages = 1,
                )

                else -> error("Unexpected request: $query, page $page")
            }
        }

        viewModel.updateOpacSearchQuery("Old query")
        viewModel.submitOpacSearch()
        awaitOpacIdle(viewModel, appliedQuery = "Old query")

        viewModel.loadNextOpacPage()
        assertTrue(viewModel.uiState.value.opacSearch.isLoadingMore)

        viewModel.updateOpacSearchQuery("New query")
        viewModel.submitOpacSearch()
        awaitOpacIdle(viewModel, appliedQuery = "New query")

        mergeDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.runCurrent()

        assertEquals(listOf(newBook), viewModel.uiState.value.opacSearch.books)
        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
        assertEquals(1, viewModel.uiState.value.opacSearch.page)
        assertFalse(viewModel.uiState.value.opacSearch.isLoadingMore)
    }

    @Test
    fun `new search replaces a pending first page and ignores its late result`() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val oldResult = CompletableDeferred<OpacSearchResult>()
        val newResult = CompletableDeferred<OpacSearchResult>()
        val requests = mutableListOf<Pair<String, Int>>()
        val viewModel = createViewModel { query, page ->
            requests += query to page
            when (query) {
                "Old query" -> {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { oldResult.await() }
                }

                "New query" -> {
                    newStarted.complete(Unit)
                    newResult.await()
                }

                else -> error("Unexpected query: $query")
            }
        }

        viewModel.updateOpacSearchQuery("Old query")
        viewModel.submitOpacSearch()
        oldStarted.awaitSignal()
        assertTrue(viewModel.uiState.value.opacSearch.isLoading)

        viewModel.updateOpacSearchQuery("New query")
        viewModel.submitOpacSearch()
        newStarted.awaitSignal()

        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
        assertTrue(viewModel.uiState.value.opacSearch.books.isEmpty())
        assertTrue(viewModel.uiState.value.opacSearch.isLoading)

        val newBook = OpacBook(id = "new-book", title = "New result")
        newResult.complete(successResult(newBook, total = 1, page = 1, totalPages = 1))
        mainDispatcher.scheduler.runCurrent()
        awaitOpacIdle(viewModel, appliedQuery = "New query")

        oldResult.complete(
            successResult(
                OpacBook(id = "old-book", title = "Old result"),
                total = 1,
                page = 1,
                totalPages = 1,
            ),
        )
        mainDispatcher.scheduler.runCurrent()

        assertEquals(listOf("Old query" to 1, "New query" to 1), requests)
        assertEquals(listOf(newBook), viewModel.uiState.value.opacSearch.books)
        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
    }

    @Test
    fun `new search ignores a late first page failure`() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val oldResult = CompletableDeferred<OpacSearchResult>()
        val newResult = CompletableDeferred<OpacSearchResult>()
        val requests = mutableListOf<Pair<String, Int>>()
        val viewModel = createViewModel { query, page ->
            requests += query to page
            when (query) {
                "Old query" -> {
                    oldStarted.complete(Unit)
                    withContext(NonCancellable) { oldResult.await() }
                }

                "New query" -> {
                    newStarted.complete(Unit)
                    newResult.await()
                }

                else -> error("Unexpected query: $query")
            }
        }

        viewModel.updateOpacSearchQuery("Old query")
        viewModel.submitOpacSearch()
        oldStarted.awaitSignal()

        viewModel.updateOpacSearchQuery("New query")
        viewModel.submitOpacSearch()
        newStarted.awaitSignal()

        val newBook = OpacBook(id = "new-book", title = "New result")
        newResult.complete(successResult(newBook, total = 1, page = 1, totalPages = 1))
        mainDispatcher.scheduler.runCurrent()
        awaitOpacIdle(viewModel, appliedQuery = "New query")

        oldResult.complete(OpacSearchResult.Failure(OpacSearchFailure.HTTP))
        mainDispatcher.scheduler.runCurrent()

        assertEquals(listOf("Old query" to 1, "New query" to 1), requests)
        assertEquals(listOf(newBook), viewModel.uiState.value.opacSearch.books)
        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
        assertNull(viewModel.uiState.value.opacSearch.failedPage)
        assertNull(viewModel.uiState.value.opacSearch.error)
    }

    @Test
    fun `new search replaces a pending append and ignores its late result`() = runBlocking {
        val appendStarted = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val appendResult = CompletableDeferred<OpacSearchResult>()
        val newResult = CompletableDeferred<OpacSearchResult>()
        val requests = mutableListOf<Pair<String, Int>>()
        val oldBook = OpacBook(id = "old-page-one", title = "Old page one")
        val viewModel = createViewModel { query, page ->
            requests += query to page
            when (query to page) {
                "Old query" to 1 -> successResult(
                    oldBook,
                    total = 2,
                    page = 1,
                    totalPages = 2,
                )
                "Old query" to 2 -> {
                    appendStarted.complete(Unit)
                    withContext(NonCancellable) { appendResult.await() }
                }

                "New query" to 1 -> {
                    newStarted.complete(Unit)
                    newResult.await()
                }

                else -> error("Unexpected request: $query, page $page")
            }
        }

        viewModel.updateOpacSearchQuery("Old query")
        viewModel.submitOpacSearch()
        awaitOpacIdle(viewModel, appliedQuery = "Old query")
        assertEquals(listOf(oldBook), viewModel.uiState.value.opacSearch.books)

        viewModel.loadNextOpacPage()
        appendStarted.awaitSignal()
        assertTrue(viewModel.uiState.value.opacSearch.isLoadingMore)

        viewModel.updateOpacSearchQuery("New query")
        viewModel.submitOpacSearch()
        newStarted.awaitSignal()

        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
        assertTrue(viewModel.uiState.value.opacSearch.books.isEmpty())
        assertFalse(viewModel.uiState.value.opacSearch.isLoadingMore)
        assertTrue(viewModel.uiState.value.opacSearch.isLoading)

        val newBook = OpacBook(id = "new-page-one", title = "New page one")
        newResult.complete(successResult(newBook, total = 1, page = 1, totalPages = 1))
        mainDispatcher.scheduler.runCurrent()
        awaitOpacIdle(viewModel, appliedQuery = "New query")

        appendResult.complete(
            OpacSearchResult.Failure(OpacSearchFailure.NETWORK),
        )
        mainDispatcher.scheduler.runCurrent()

        assertEquals(
            listOf("Old query" to 1, "Old query" to 2, "New query" to 1),
            requests,
        )
        assertEquals(listOf(newBook), viewModel.uiState.value.opacSearch.books)
        assertEquals(1, viewModel.uiState.value.opacSearch.page)
        assertEquals("New query", viewModel.uiState.value.opacSearch.appliedSearchQuery)
        assertNull(viewModel.uiState.value.opacSearch.failedPage)
        assertNull(viewModel.uiState.value.opacSearch.error)
    }

    @Test
    fun `pagination failure retains its requested page and disables loading more`() {
        val state = OpacSearchUiState(
            books = listOf(OpacBook(id = "book-1", title = "Book")),
            page = 2,
            totalPages = 5,
            isFirstPage = false,
            isLastPage = false,
            failedPage = 3,
            hasSearched = true,
            error = UiText.Dynamic("Request failed"),
        )

        assertEquals(3, state.failedPage)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun `successful middle page enables loading more`() {
        val state = OpacSearchUiState(
            books = listOf(OpacBook(id = "book-1", title = "Book")),
            page = 2,
            totalPages = 5,
            isFirstPage = false,
            isLastPage = false,
            hasSearched = true,
        )

        assertNull(state.failedPage)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun `last page marker disables loading more before the numeric boundary`() {
        val state = OpacSearchUiState(
            page = 4,
            totalPages = 5,
            isFirstPage = false,
            isLastPage = true,
            hasSearched = true,
        )

        assertFalse(state.canLoadMore)
    }

    @Test
    fun `loading states disable loading more`() {
        val state = OpacSearchUiState(
            page = 2,
            totalPages = 5,
            isFirstPage = false,
            isLastPage = false,
            hasSearched = true,
        )

        assertFalse(state.copy(isLoading = true).canLoadMore)
        assertFalse(state.copy(isLoadingMore = true).canLoadMore)
    }

    @Test
    fun `initial failure retries from the first page`() {
        val state = OpacSearchUiState(
            hasSearched = true,
            error = UiText.Dynamic("Request failed"),
        )

        assertNull(state.failedPage)
        assertFalse(state.canLoadMore)
    }

    private suspend fun createViewModel(
        opacMergeDispatcher: CoroutineDispatcher = mainDispatcher,
        searchOpac: suspend (query: String, page: Int) -> OpacSearchResult,
    ): ReservationViewModel {
        val viewModel = ReservationViewModel(
            repository = createReservationRepository(),
            readerQrCodeRepository = ReaderQrCodeRepository(UnusedReaderQrClient),
            opacRepository = OpacRepository(UnusedOpacClient),
            locationProvider = DeviceLocationProvider(
                ApplicationProvider.getApplicationContext(),
            ),
            shouldUseMockLocation = { false },
            queueCalendarReminder = { _, _, _, _ -> },
            searchOpac = searchOpac,
            opacMergeDispatcher = opacMergeDispatcher,
        )
        viewModels += viewModel
        withTimeout(WAIT_TIMEOUT_MILLIS) {
            viewModel.uiState.first { state -> state.stage == AppStage.AUTHENTICATED }
        }
        return viewModel
    }

    private suspend fun awaitOpacIdle(
        viewModel: ReservationViewModel,
        appliedQuery: String,
    ) {
        withTimeout(WAIT_TIMEOUT_MILLIS) {
            viewModel.uiState.first { state ->
                val opacSearch = state.opacSearch
                opacSearch.appliedSearchQuery == appliedQuery &&
                    opacSearch.hasSearched &&
                    !opacSearch.isLoading &&
                    !opacSearch.isLoadingMore
            }
        }
    }

    private suspend fun CompletableDeferred<Unit>.awaitSignal() {
        withTimeout(WAIT_TIMEOUT_MILLIS) { await() }
    }

    private fun successResult(
        vararg books: OpacBook,
        total: Int,
        page: Int,
        totalPages: Int,
    ): OpacSearchResult = OpacSearchResult.Success(
        OpacSearchPage(
            items = books.toList(),
            total = total,
            page = page,
            totalPages = totalPages,
        ),
    )

    private fun createReservationRepository(): ReservationRepository {
        val cookieJar = FakeCookieJar(token = "test-token")
        val cookieCloudSessionManager = CookieCloudSessionManager(
            configStorage = EmptyCookieCloudConfigStorage,
            payloadSource = CookieCloudPayloadSource {
                CookieCloudPayloadResult.Failure(CookieCloudFailureReason.NETWORK)
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Missing
            },
            cookieJar = cookieJar,
            dispatcher = Dispatchers.IO,
        )
        return ReservationRepository(
            api = authenticatedApi(),
            passwordCipher = PasswordCipher(),
            cookieJar = cookieJar,
            cookieCloudSessionManager = cookieCloudSessionManager,
            gson = Gson(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun authenticatedApi(): ReservationApi =
        Proxy.newProxyInstance(
            ReservationApi::class.java.classLoader,
            arrayOf(ReservationApi::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "isLoggedIn" -> ApiEnvelope(code = 200, data = true)
                "getUserInfo" -> ApiEnvelope(
                    code = 200,
                    data = JsonParser.parseString(
                        """{"id":"user-id","readerId":"reader-id","mobileStatus":"1"}""",
                    ),
                )

                else -> ApiEnvelope<Any>(
                    code = 500,
                    errorMsg = "Unexpected API call: ${method.name}",
                )
            }
        } as ReservationApi

    private object EmptyCookieCloudConfigStorage : CookieCloudConfigStorage {
        override fun load(): CookieCloudConfig? = null

        override fun save(config: CookieCloudConfig): Boolean = true

        override fun clear() = Unit
    }

    private class FakeCookieJar(
        private var token: String?,
    ) : ClearableCookieJar {
        override fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean {
            this.token = token
            return true
        }

        override fun saveAuthenticationTokenIfCurrent(
            expectedToken: String?,
            token: String,
            retentionDays: Int,
        ): AuthenticationTokenUpdateResult {
            if (this.token != expectedToken) {
                return AuthenticationTokenUpdateResult.TOKEN_CHANGED
            }
            this.token = token
            return AuthenticationTokenUpdateResult.SAVED
        }

        override fun clear() {
            token = null
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            token?.let { value ->
                listOf(checkNotNull(createAuthenticationCookie(value, retentionDays = 30)))
            }.orEmpty()
    }

    private object UnusedReaderQrClient : ReaderQrNativeClient {
        override fun fetch(cookieHeader: String): ReaderQrCodeResult =
            ReaderQrCodeResult.Failure(ReaderQrCodeFailure.NETWORK)
    }

    private object UnusedOpacClient : OpacClient {
        override fun fetchSearch(query: String, page: Int): OpacClientResult =
            OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)

        override fun fetchHoldings(bookRecordNumbersCsv: String): OpacClientResult =
            OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)

        override fun fetchCovers(isbnsCsv: String): OpacClientResult =
            OpacClientResult.Failure(OpacSearchFailure.INVALID_RESPONSE)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
    }
}
