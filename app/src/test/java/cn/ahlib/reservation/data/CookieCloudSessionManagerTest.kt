package cn.ahlib.reservation.data

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CookieCloudSessionManagerTest {
    @Test
    fun syncNow_savesDecodedAuthenticationToken() = runTest {
        val cookieJar = FakeCookieJar()
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success("fresh-token")
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = manager.syncNow()

        assertSame(CookieCloudSyncResult.Success, result)
        assertEquals("fresh-token", cookieJar.token)
        assertEquals(30, cookieJar.retentionDays)
    }

    @Test
    fun syncNow_doesNotSuppressFirstRecoveryForInstalledToken() = runTest {
        val fetchCount = AtomicInteger()
        val cookieJar = FakeCookieJar()
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success(
                    if (fetchCount.get() == 1) "token-b" else "token-c",
                )
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertSame(CookieCloudSyncResult.Success, manager.syncNow())
        assertEquals("token-b", cookieJar.token)
        assertSame(CookieCloudSyncResult.Success, manager.recoverIfConfigured("token-b"))

        assertEquals(2, fetchCount.get())
        assertEquals("token-c", cookieJar.token)
    }

    @Test
    fun recoverIfConfigured_coalescesConcurrentRecovery() = runTest {
        val fetchCount = AtomicInteger()
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                delay(100)
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success("fresh-token")
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val first = async { manager.recoverIfConfigured("expired-token") }
        val second = async { manager.recoverIfConfigured("expired-token") }
        advanceUntilIdle()

        assertSame(CookieCloudSyncResult.Success, first.await())
        assertSame(CookieCloudSyncResult.Success, second.await())
        assertEquals(1, fetchCount.get())
        assertEquals("fresh-token", cookieJar.token)
    }

    @Test
    fun recoverIfConfigured_reusesRecentFailureWithinCooldown() = runTest {
        val fetchCount = AtomicInteger()
        var nowMillis = 10_000L
        val manager = manager(
            cookieJar = FakeCookieJar(token = "expired-token"),
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                CookieCloudPayloadResult.Failure(CookieCloudFailureReason.NETWORK)
            },
            currentTimeMillis = { nowMillis },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val first = manager.recoverIfConfigured("expired-token")
        nowMillis += 500
        val second = manager.recoverIfConfigured("expired-token")

        assertEquals(first, second)
        assertEquals(1, fetchCount.get())
        assertEquals(
            CookieCloudFailureReason.NETWORK,
            (second as CookieCloudSyncResult.Failure).reason,
        )
    }

    @Test
    fun recoverIfConfigured_doesNotRedownloadRecentlyInstalledInvalidToken() = runTest {
        val fetchCount = AtomicInteger()
        var nowMillis = 20_000L
        val cookieJar = FakeCookieJar(token = "invalid-token")
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success("invalid-token")
            },
            currentTimeMillis = { nowMillis },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertSame(
            CookieCloudSyncResult.Success,
            manager.recoverIfConfigured("invalid-token"),
        )
        nowMillis += 500
        assertSame(
            CookieCloudSyncResult.Success,
            manager.recoverIfConfigured("invalid-token"),
        )

        assertEquals(1, fetchCount.get())
    }

    @Test
    fun recoverIfConfigured_allowsRecoveryForNewlyFailedReplacementToken() = runTest {
        val fetchCount = AtomicInteger()
        val cookieJar = FakeCookieJar(token = "token-a")
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success(
                    if (fetchCount.get() == 1) "token-b" else "token-c",
                )
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertSame(CookieCloudSyncResult.Success, manager.recoverIfConfigured("token-a"))
        assertEquals("token-b", cookieJar.token)
        assertSame(CookieCloudSyncResult.Success, manager.recoverIfConfigured("token-b"))

        assertEquals(2, fetchCount.get())
        assertEquals("token-c", cookieJar.token)
    }

    @Test
    fun syncNow_distinguishesDecryptionAndMissingTokenFailures() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val decryptionFailure = manager(
            cookieJar = FakeCookieJar(),
            payloadSource = CookieCloudPayloadSource {
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.DecryptionFailed
            },
            dispatcher = dispatcher,
        ).syncNow()
        val missingToken = manager(
            cookieJar = FakeCookieJar(),
            payloadSource = CookieCloudPayloadSource {
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Missing
            },
            dispatcher = dispatcher,
        ).syncNow()

        assertEquals(
            CookieCloudFailureReason.DECRYPTION_FAILED,
            (decryptionFailure as CookieCloudSyncResult.Failure).reason,
        )
        assertEquals(
            CookieCloudFailureReason.TOKEN_MISSING,
            (missingToken as CookieCloudSyncResult.Failure).reason,
        )
    }

    @Test
    fun recoverIfConfigured_skipsDownloadWhenTokenWasAlreadyReplaced() = runTest {
        val fetchCount = AtomicInteger()
        val manager = manager(
            cookieJar = FakeCookieJar(token = "replacement-token"),
            payloadSource = CookieCloudPayloadSource {
                fetchCount.incrementAndGet()
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = manager.recoverIfConfigured("expired-token")

        assertSame(CookieCloudSyncResult.Success, result)
        assertEquals(0, fetchCount.get())
    }

    @Test
    fun recoverIfConfigured_doesNotRestoreTokenClearedDuringFetch() = runTest {
        val cookieJar = FakeCookieJar(token = "expired-token")
        val manager = manager(
            cookieJar = cookieJar,
            payloadSource = CookieCloudPayloadSource {
                cookieJar.clear()
                CookieCloudPayloadResult.Success("encrypted-payload")
            },
            tokenDecoder = CookieCloudTokenDecoder { _, _ ->
                CookieCloudTokenResult.Success("fresh-token")
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = manager.recoverIfConfigured("expired-token")

        assertSame(CookieCloudSyncResult.Success, result)
        assertEquals(null, cookieJar.token)
        assertEquals(0, cookieJar.saveCount)
    }

    @Test
    fun payloadSource_encodesPathAndAddsOnlyFixedCryptoQuery() = runTest {
        val requests = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"encrypted":"ciphertext"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                    .build()
            }
            .build()
        val source = OkHttpCookieCloudPayloadSource(client, GsonFactory.create())
        val fixedConfig = checkNotNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example/base/",
                userKey = "key with?value",
                password = "password-not-for-the-server",
                cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
            ),
        )
        val legacyConfig = fixedConfig.copy(cryptoType = CookieCloudCryptoType.LEGACY)

        assertEquals(
            CookieCloudPayloadResult.Success("ciphertext"),
            source.fetch(fixedConfig),
        )
        assertEquals(
            CookieCloudPayloadResult.Success("ciphertext"),
            source.fetch(legacyConfig),
        )

        assertEquals(
            "https://cookie.example/base/get/key%20with%3Fvalue" +
                "?crypto_type=aes-128-cbc-fixed",
            requests[0],
        )
        assertEquals(
            "https://cookie.example/base/get/key%20with%3Fvalue",
            requests[1],
        )
        requests.forEach { requestUrl ->
            assertEquals(false, requestUrl.contains(fixedConfig.password))
        }
    }

    @Test
    fun payloadSource_rejectsExcessiveResponseStructure() = runTest {
        val responseJson = buildString {
            append('{')
            repeat(33) { index ->
                if (index > 0) {
                    append(',')
                }
                append('"')
                append("field")
                append(index)
                append("\":null")
            }
            append(",\"encrypted\":\"ciphertext\"}")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val source = OkHttpCookieCloudPayloadSource(client, GsonFactory.create())

        val result = source.fetch(CONFIG)

        assertEquals(
            CookieCloudPayloadResult.Failure(CookieCloudFailureReason.INVALID_RESPONSE),
            result,
        )
    }

    private fun manager(
        cookieJar: FakeCookieJar,
        payloadSource: CookieCloudPayloadSource,
        tokenDecoder: CookieCloudTokenDecoder = CookieCloudTokenDecoder { _, _ ->
            CookieCloudTokenResult.Success("fresh-token")
        },
        currentTimeMillis: () -> Long = { 1_000L },
        dispatcher: CoroutineDispatcher,
    ): CookieCloudSessionManager =
        CookieCloudSessionManager(
            configStorage = FakeConfigStorage(CONFIG),
            payloadSource = payloadSource,
            tokenDecoder = tokenDecoder,
            cookieJar = cookieJar,
            currentTimeMillis = currentTimeMillis,
            dispatcher = dispatcher,
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
        var token: String? = null,
        var saveSucceeds: Boolean = true,
    ) : ClearableCookieJar {
        var saveCount: Int = 0
            private set
        var retentionDays: Int? = null
            private set

        @Synchronized
        override fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean {
            saveCount += 1
            if (!saveSucceeds) {
                return false
            }
            this.token = token
            this.retentionDays = retentionDays
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
            return if (saveAuthenticationToken(token, retentionDays)) {
                AuthenticationTokenUpdateResult.SAVED
            } else {
                AuthenticationTokenUpdateResult.STORAGE_FAILED
            }
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
                            retentionDays = retentionDays ?: 30,
                        ),
                    ),
                )
            }.orEmpty()
    }

    private companion object {
        val CONFIG = checkNotNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example",
                userKey = "0bdb7ef1-b694-4c30-a383-7bf2f53c6572",
                password = "end-to-end-password",
                cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
            ),
        )
    }
}
