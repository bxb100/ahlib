package cn.ahlib.reservation.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationRepositoryCookieCloudTest {
    @Test
    fun sessionExpired_syncsAndRetriesOnce() = runTest {
        val fixture = fixture(
            responses = listOf(
                sessionExpiredEnvelope(),
                ApiEnvelope(code = 200, data = true),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fixture.repository.isLoggedIn()

        assertTrue(result is ApiResult.Success && result.data)
        assertEquals(2, fixture.apiCallCount.get())
        assertEquals(1, fixture.fetchCount.get())
        assertEquals("fresh-token", fixture.cookieJar.token)
    }

    @Test
    fun failedSync_doesNotRetryRequest() = runTest {
        val fixture = fixture(
            responses = listOf(sessionExpiredEnvelope()),
            payloadResult = CookieCloudPayloadResult.Failure(
                CookieCloudFailureReason.NETWORK,
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fixture.repository.isLoggedIn()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorKind.SESSION_EXPIRED, (result as ApiResult.Failure).exception.kind)
        assertEquals(1, fixture.apiCallCount.get())
        assertEquals(1, fixture.fetchCount.get())
    }

    @Test
    fun retryThatIsStillExpired_returnsFinalSessionFailure() = runTest {
        val fixture = fixture(
            responses = listOf(
                sessionExpiredEnvelope(),
                sessionExpiredEnvelope(),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fixture.repository.isLoggedIn()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorKind.SESSION_EXPIRED, (result as ApiResult.Failure).exception.kind)
        assertEquals(2, fixture.apiCallCount.get())
        assertEquals(1, fixture.fetchCount.get())
    }

    @Test
    fun loggedOutResponse_syncsAndRetriesOnce() = runTest {
        val fixture = fixture(
            responses = listOf(
                ApiEnvelope(code = 200, data = false),
                ApiEnvelope(code = 200, data = true),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fixture.repository.isLoggedIn()

        assertTrue(result is ApiResult.Success && result.data)
        assertEquals(2, fixture.apiCallCount.get())
        assertEquals(1, fixture.fetchCount.get())
    }

    @Test
    fun http401_syncsAndRetriesOnce() = runTest {
        val apiCallCount = AtomicInteger()
        val fetchCount = AtomicInteger()
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            fetchCount = fetchCount,
            payloadResult = CookieCloudPayloadResult.Success("encrypted-payload"),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val api = proxyApi { methodName ->
            check(methodName == "isLoggedIn")
            if (apiCallCount.incrementAndGet() == 1) {
                throw HttpException(
                    Response.error<Any>(
                        401,
                        "{}".toResponseBody("application/json".toMediaType()),
                    ),
                )
            }
            ApiEnvelope(code = 200, data = true)
        }
        val repository = ReservationRepository(
            api = api,
            passwordCipher = PasswordCipher(),
            cookieJar = cookieJar,
            cookieCloudSessionManager = manager,
            gson = Gson(),
        )

        val result = repository.isLoggedIn()

        assertTrue(result is ApiResult.Success && result.data)
        assertEquals(2, apiCallCount.get())
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun nonSessionFailure_doesNotSyncOrRetry() = runTest {
        val fixture = fixture(
            responses = listOf(
                ApiEnvelope(
                    code = 500,
                    errorMsg = "Business failure",
                    data = null,
                ),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fixture.repository.isLoggedIn()

        assertTrue(result is ApiResult.Failure)
        assertEquals(ApiErrorKind.BUSINESS, (result as ApiResult.Failure).exception.kind)
        assertEquals(1, fixture.apiCallCount.get())
        assertEquals(0, fixture.fetchCount.get())
    }

    @Test
    fun anonymousUserInfo_syncsAndRetriesOnce() = runTest {
        val apiCallCount = AtomicInteger()
        val fetchCount = AtomicInteger()
        val responses = ArrayDeque<ApiEnvelope<JsonElement>>(
            listOf(
                ApiEnvelope(
                    code = 200,
                    data = JsonParser.parseString("false"),
                ),
                ApiEnvelope(
                    code = 200,
                    data = JsonParser.parseString("""{"id":"reader-id"}"""),
                ),
            ),
        )
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            fetchCount = fetchCount,
            payloadResult = CookieCloudPayloadResult.Success("encrypted-payload"),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val repository = ReservationRepository(
            api = proxyApi { methodName ->
                check(methodName == "getUserInfo")
                apiCallCount.incrementAndGet()
                responses.removeFirst()
            },
            passwordCipher = PasswordCipher(),
            cookieJar = cookieJar,
            cookieCloudSessionManager = manager,
            gson = Gson(),
        )

        val result = repository.getUserInfo()

        assertTrue(result is ApiResult.Success)
        assertEquals("reader-id", (result as ApiResult.Success).data?.id)
        assertEquals(2, apiCallCount.get())
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun captchaSessionFailure_doesNotTriggerRecovery() = runTest {
        val apiCallCount = AtomicInteger()
        val fetchCount = AtomicInteger()
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            fetchCount = fetchCount,
            payloadResult = CookieCloudPayloadResult.Success("encrypted-payload"),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val api = proxyApi { methodName ->
            check(methodName == "getCaptcha")
            apiCallCount.incrementAndGet()
            ApiEnvelope<Captcha>(
                code = 401,
                errorMsg = "Authentication required",
            )
        }
        val repository = ReservationRepository(
            api = api,
            passwordCipher = PasswordCipher(),
            cookieJar = cookieJar,
            cookieCloudSessionManager = manager,
            gson = Gson(),
        )

        val result = repository.getCaptcha()

        assertTrue(result is ApiResult.Failure)
        assertEquals(1, apiCallCount.get())
        assertEquals(0, fetchCount.get())
    }

    private fun fixture(
        responses: List<ApiEnvelope<Boolean>>,
        payloadResult: CookieCloudPayloadResult =
            CookieCloudPayloadResult.Success("encrypted-payload"),
        dispatcher: CoroutineDispatcher,
    ): RepositoryFixture {
        val apiCallCount = AtomicInteger()
        val fetchCount = AtomicInteger()
        val responseQueue = ArrayDeque(responses)
        val api = proxyApi { methodName ->
            check(methodName == "isLoggedIn")
            apiCallCount.incrementAndGet()
            responseQueue.removeFirst()
        }
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            fetchCount = fetchCount,
            payloadResult = payloadResult,
            dispatcher = dispatcher,
        )
        return RepositoryFixture(
            repository = ReservationRepository(
                api = api,
                passwordCipher = PasswordCipher(),
                cookieJar = cookieJar,
                cookieCloudSessionManager = manager,
                gson = Gson(),
            ),
            cookieJar = cookieJar,
            apiCallCount = apiCallCount,
            fetchCount = fetchCount,
        )
    }

    private fun manager(
        cookieJar: FakeCookieJar,
        fetchCount: AtomicInteger,
        payloadResult: CookieCloudPayloadResult,
        dispatcher: CoroutineDispatcher,
    ): CookieCloudSessionManager =
        CookieCloudSessionManager(
            configStorage = FakeConfigStorage(CONFIG),
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                payloadResult
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success("fresh-token")
            },
            cookieJar = cookieJar,
            dispatcher = dispatcher,
        )

    @Suppress("UNCHECKED_CAST")
    private fun proxyApi(handler: (String) -> Any): ReservationApi =
        Proxy.newProxyInstance(
            ReservationApi::class.java.classLoader,
            arrayOf(ReservationApi::class.java),
        ) { _, method, _ ->
            handler(method.name)
        } as ReservationApi

    private fun sessionExpiredEnvelope(): ApiEnvelope<Boolean> =
        ApiEnvelope(
            code = 401,
            errorMsg = "Authentication required",
        )

    private class FakeConfigStorage(
        private var config: CookieCloudConfig?,
    ) : CookieCloudConfigStorage {
        override fun load(): CookieCloudConfig? = config

        override fun save(config: CookieCloudConfig): Boolean {
            this.config = config
            return true
        }

        override fun clear() {
            config = null
        }
    }

    private class FakeCookieJar(
        var token: String?,
    ) : ClearableCookieJar {
        @Synchronized
        override fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean {
            this.token = token
            return true
        }

        @Synchronized
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

        @Synchronized
        override fun clear() {
            token = null
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            token?.let { tokenValue ->
                listOf(
                    checkNotNull(
                        createAuthenticationCookie(
                            token = tokenValue,
                            retentionDays = 30,
                        ),
                    ),
                )
            }.orEmpty()
    }

    private data class RepositoryFixture(
        val repository: ReservationRepository,
        val cookieJar: FakeCookieJar,
        val apiCallCount: AtomicInteger,
        val fetchCount: AtomicInteger,
    )

    private companion object {
        val CONFIG = checkNotNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example",
                userKey = "0bdb7ef1-b694-4c30-a383-7bf2f53c6572",
                password = "end-to-end-password",
                cryptoType = CookieCloudCryptoType.LEGACY,
            ),
        )
    }
}
