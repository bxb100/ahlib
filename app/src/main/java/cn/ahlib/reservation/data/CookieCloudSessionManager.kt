package cn.ahlib.reservation.data

import com.google.gson.Gson
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.MalformedJsonException
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

enum class CookieCloudFailureReason {
    INVALID_CONFIG,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
    DECRYPTION_FAILED,
    TOKEN_MISSING,
    STORAGE_FAILED,
}

sealed interface CookieCloudSyncResult {
    data object Success : CookieCloudSyncResult

    data class Failure(
        val reason: CookieCloudFailureReason,
        val httpStatusCode: Int? = null,
    ) : CookieCloudSyncResult
}

internal class CookieCloudSessionManager(
    private val configStorage: CookieCloudConfigStorage,
    private val payloadSource: CookieCloudPayloadSource,
    private val tokenDecoder: CookieCloudTokenDecoder,
    private val cookieJar: ClearableCookieJar,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val syncMutex = Mutex()
    private val recentAttemptLock = Any()
    private val recentAttempts = linkedMapOf<RecoveryAttemptKey, RecoveryAttempt>()

    constructor(
        configStore: EncryptedCookieCloudConfigStore,
        client: OkHttpClient,
        cookieJar: ClearableCookieJar,
        gson: Gson = GsonFactory.create(),
    ) : this(
        configStorage = EncryptedCookieCloudConfigStorage(configStore),
        payloadSource = OkHttpCookieCloudPayloadSource(client, gson),
        tokenDecoder = DefaultCookieCloudTokenDecoder,
        cookieJar = cookieJar,
        currentTimeMillis = System::currentTimeMillis,
        dispatcher = Dispatchers.IO,
    )

    fun loadConfig(): CookieCloudConfig? =
        try {
            configStorage.load()?.normalizedOrNull()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    fun saveConfig(config: CookieCloudConfig): Boolean {
        val normalizedConfig = config.normalizedOrNull() ?: return false
        val saved = try {
            configStorage.save(normalizedConfig)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
        if (saved) {
            clearRecentAttempts()
        }
        return saved
    }

    fun clearConfig() {
        try {
            configStorage.clear()
        } finally {
            clearRecentAttempts()
        }
    }

    suspend fun syncNow(): CookieCloudSyncResult = withContext(dispatcher) {
        syncMutex.withLock {
            val config = loadConfigForSync()
                ?: return@withLock CookieCloudSyncResult.Failure(
                    CookieCloudFailureReason.INVALID_CONFIG,
                )
            val expectedToken = readAuthenticationToken()
                ?: return@withLock CookieCloudSyncResult.Failure(
                    CookieCloudFailureReason.STORAGE_FAILED,
                )
            syncLocked(config, expectedToken.value)
        }
    }

    suspend fun recoverIfConfigured(failedToken: String?): CookieCloudSyncResult =
        withContext(dispatcher) {
            syncMutex.withLock {
                val currentToken = readAuthenticationToken()
                    ?: return@withLock CookieCloudSyncResult.Failure(
                        CookieCloudFailureReason.STORAGE_FAILED,
                    )
                if (currentToken.value != failedToken) {
                    return@withLock CookieCloudSyncResult.Success
                }

                val config = loadConfigForSync()
                    ?: return@withLock CookieCloudSyncResult.Failure(
                        CookieCloudFailureReason.INVALID_CONFIG,
                    )
                val attemptKey = recoveryAttemptKey(failedToken, config)
                recentAttempt(attemptKey)?.let { attempt ->
                    return@withLock attempt.result
                }

                val result = syncLocked(
                    config = config,
                    expectedToken = failedToken,
                )
                recordAttempt(attemptKey, result)
                result
            }
        }

    private fun loadConfigForSync(): CookieCloudConfig? =
        try {
            configStorage.load()?.normalizedOrNull()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private suspend fun syncLocked(
        config: CookieCloudConfig,
        expectedToken: String?,
    ): CookieCloudSyncResult {
        val fetchResult = try {
            payloadSource.fetch(config)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            return CookieCloudSyncResult.Failure(CookieCloudFailureReason.NETWORK)
        } catch (_: Exception) {
            return CookieCloudSyncResult.Failure(CookieCloudFailureReason.NETWORK)
        }
        val encrypted = when (fetchResult) {
            is CookieCloudPayloadResult.Success -> fetchResult.encrypted
            is CookieCloudPayloadResult.Failure -> {
                return CookieCloudSyncResult.Failure(
                    reason = fetchResult.reason,
                    httpStatusCode = fetchResult.httpStatusCode,
                )
            }
        }
        val decodedToken = try {
            tokenDecoder.decode(encrypted, config)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            CookieCloudTokenResult.DecryptionFailed
        }
        val token = when (decodedToken) {
            is CookieCloudTokenResult.Success -> decodedToken.token
            CookieCloudTokenResult.DecryptionFailed -> {
                return CookieCloudSyncResult.Failure(
                    CookieCloudFailureReason.DECRYPTION_FAILED,
                )
            }

            CookieCloudTokenResult.Missing -> {
                return CookieCloudSyncResult.Failure(
                    CookieCloudFailureReason.TOKEN_MISSING,
                )
            }
        }

        if (!isConfigCurrent(config)) {
            return CookieCloudSyncResult.Failure(CookieCloudFailureReason.INVALID_CONFIG)
        }
        val updateResult = try {
            cookieJar.saveAuthenticationTokenIfCurrent(
                expectedToken = expectedToken,
                token = token,
                retentionDays = COOKIE_RETENTION_DAYS,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AuthenticationTokenUpdateResult.STORAGE_FAILED
        }
        return when (updateResult) {
            AuthenticationTokenUpdateResult.SAVED,
            AuthenticationTokenUpdateResult.TOKEN_CHANGED,
            -> CookieCloudSyncResult.Success

            AuthenticationTokenUpdateResult.STORAGE_FAILED ->
                CookieCloudSyncResult.Failure(CookieCloudFailureReason.STORAGE_FAILED)
        }
    }

    private fun isConfigCurrent(expected: CookieCloudConfig): Boolean =
        try {
            configStorage.load()?.normalizedOrNull() == expected
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }

    private fun readAuthenticationToken(): StoredAuthenticationToken? =
        try {
            StoredAuthenticationToken(
                cookieJar.loadForRequest(AUTHENTICATION_URL)
                    .firstOrNull { cookie -> cookie.name == AUTHENTICATION_COOKIE_NAME }
                    ?.value,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private fun recentAttempt(key: RecoveryAttemptKey): RecoveryAttempt? {
        val now = currentTimeMillis()
        return synchronized(recentAttemptLock) {
            pruneRecentAttemptsLocked(now)
            recentAttempts[key]?.takeIf { attempt ->
                now - attempt.recordedAtMillis in 0..RECOVERY_COOLDOWN_MILLIS
            }
        }
    }

    private fun recordAttempt(
        key: RecoveryAttemptKey,
        result: CookieCloudSyncResult,
    ) {
        val now = currentTimeMillis()
        synchronized(recentAttemptLock) {
            pruneRecentAttemptsLocked(now)
            recentAttempts.remove(key)
            recentAttempts[key] = RecoveryAttempt(result, now)
            while (recentAttempts.size > MAX_RECENT_ATTEMPTS) {
                recentAttempts.remove(recentAttempts.keys.first())
            }
        }
    }

    private fun clearRecentAttempts() {
        synchronized(recentAttemptLock) {
            recentAttempts.clear()
        }
    }

    private fun pruneRecentAttemptsLocked(now: Long) {
        recentAttempts.entries.removeAll { (_, attempt) ->
            now - attempt.recordedAtMillis !in 0..RECOVERY_COOLDOWN_MILLIS
        }
    }

    private fun recoveryAttemptKey(
        failedToken: String?,
        config: CookieCloudConfig,
    ): RecoveryAttemptKey =
        RecoveryAttemptKey(
            tokenFingerprint = fingerprint(failedToken.orEmpty()),
            configFingerprint = configFingerprint(config),
        )

    private fun configFingerprint(config: CookieCloudConfig): String {
        val digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM)
        updateDigest(digest, config.serverUrl)
        updateDigest(digest, config.userKey)
        updateDigest(digest, config.cryptoType.wireValue)
        updateDigest(digest, config.password)
        return Base64.getEncoder().withoutPadding().encodeToString(digest.digest())
    }

    private fun fingerprint(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return try {
            Base64.getEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance(FINGERPRINT_ALGORITHM).digest(bytes),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun updateDigest(
        digest: MessageDigest,
        value: String,
    ) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(FINGERPRINT_SEPARATOR)
            digest.update(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private companion object {
        val AUTHENTICATION_URL = "https://www.lib.ah.cn/".toHttpUrlOrNull()
            ?: error("Invalid authentication URL")
        const val COOKIE_RETENTION_DAYS = 30
        const val RECOVERY_COOLDOWN_MILLIS = 5_000L
        const val MAX_RECENT_ATTEMPTS = 8
        const val FINGERPRINT_ALGORITHM = "SHA-256"
        const val FINGERPRINT_SEPARATOR: Byte = 0
    }
}

internal interface CookieCloudConfigStorage {
    fun load(): CookieCloudConfig?

    fun save(config: CookieCloudConfig): Boolean

    fun clear()
}

private class EncryptedCookieCloudConfigStorage(
    private val store: EncryptedCookieCloudConfigStore,
) : CookieCloudConfigStorage {
    override fun load(): CookieCloudConfig? = store.load()

    override fun save(config: CookieCloudConfig): Boolean = store.save(config)

    override fun clear() = store.clear()
}

internal fun interface CookieCloudPayloadSource {
    suspend fun fetch(config: CookieCloudConfig): CookieCloudPayloadResult
}

internal sealed interface CookieCloudPayloadResult {
    data class Success(
        val encrypted: String,
    ) : CookieCloudPayloadResult

    data class Failure(
        val reason: CookieCloudFailureReason,
        val httpStatusCode: Int? = null,
    ) : CookieCloudPayloadResult
}

internal class OkHttpCookieCloudPayloadSource(
    private val client: OkHttpClient,
    private val gson: Gson,
) : CookieCloudPayloadSource {
    override suspend fun fetch(config: CookieCloudConfig): CookieCloudPayloadResult {
        val baseUrl = config.serverUrl.toHttpUrlOrNull()
            ?: return CookieCloudPayloadResult.Failure(
                CookieCloudFailureReason.INVALID_CONFIG,
            )
        val url = baseUrl.newBuilder()
            .addPathSegment(GET_PATH_SEGMENT)
            .addPathSegment(config.userKey)
            .apply {
                if (config.cryptoType == CookieCloudCryptoType.AES_128_CBC_FIXED) {
                    addQueryParameter(CRYPTO_TYPE_QUERY, config.cryptoType.wireValue)
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return CookieCloudPayloadResult.Failure(CookieCloudFailureReason.NETWORK)
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        response.use {
            if (!response.isSuccessful) {
                return CookieCloudPayloadResult.Failure(
                    reason = CookieCloudFailureReason.SERVER,
                    httpStatusCode = response.code,
                )
            }
            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > MAX_RESPONSE_BYTES) {
                return CookieCloudPayloadResult.Failure(
                    CookieCloudFailureReason.INVALID_RESPONSE,
                )
            }
            val encrypted = try {
                parseEncrypted(body.byteStream())
            } catch (_: CookieCloudResponseFormatException) {
                null
            } catch (_: CookieCloudResponseTooLargeException) {
                null
            } catch (_: MalformedJsonException) {
                null
            } catch (_: EOFException) {
                null
            } catch (_: CharacterCodingException) {
                null
            }
                ?: return CookieCloudPayloadResult.Failure(
                    CookieCloudFailureReason.INVALID_RESPONSE,
                )
            return CookieCloudPayloadResult.Success(encrypted)
        }
    }

    private fun parseEncrypted(input: InputStream): String =
        LimitedInputStream(input, MAX_RESPONSE_BYTES.toLong()).use { limitedInput ->
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            gson.newJsonReader(InputStreamReader(limitedInput, decoder)).use { reader ->
                reader.strictness = Strictness.STRICT
                requireToken(reader, JsonToken.BEGIN_OBJECT)
                reader.beginObject()
                var encrypted: String? = null
                var fieldCount = 0
                val budget = JsonReadBudget()
                while (reader.hasNext()) {
                    fieldCount += 1
                    if (fieldCount > MAX_ROOT_FIELD_COUNT) {
                        throw CookieCloudResponseFormatException()
                    }
                    val fieldName = reader.nextName()
                    if (fieldName.length > MAX_FIELD_NAME_LENGTH) {
                        throw CookieCloudResponseFormatException()
                    }
                    if (fieldName == ENCRYPTED_FIELD) {
                        if (encrypted != null || reader.peek() != JsonToken.STRING) {
                            throw CookieCloudResponseFormatException()
                        }
                        encrypted = reader.nextString().takeIf { value ->
                            value.isNotEmpty() && value.length <= MAX_ENCRYPTED_CHARACTERS
                        } ?: throw CookieCloudResponseFormatException()
                    } else {
                        skipBoundedValue(reader, depth = 0, budget)
                    }
                }
                reader.endObject()
                requireToken(reader, JsonToken.END_DOCUMENT)
                encrypted ?: throw CookieCloudResponseFormatException()
            }
        }

    private fun skipBoundedValue(
        reader: JsonReader,
        depth: Int,
        budget: JsonReadBudget,
    ) {
        if (depth > MAX_NESTING_DEPTH || ++budget.valueCount > MAX_SKIPPED_VALUE_COUNT) {
            throw CookieCloudResponseFormatException()
        }
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                var itemCount = 0
                while (reader.hasNext()) {
                    itemCount += 1
                    if (itemCount > MAX_CONTAINER_ENTRY_COUNT) {
                        throw CookieCloudResponseFormatException()
                    }
                    skipBoundedValue(reader, depth + 1, budget)
                }
                reader.endArray()
            }

            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                var fieldCount = 0
                while (reader.hasNext()) {
                    fieldCount += 1
                    if (fieldCount > MAX_CONTAINER_ENTRY_COUNT) {
                        throw CookieCloudResponseFormatException()
                    }
                    val fieldName = reader.nextName()
                    if (fieldName.length > MAX_FIELD_NAME_LENGTH) {
                        throw CookieCloudResponseFormatException()
                    }
                    skipBoundedValue(reader, depth + 1, budget)
                }
                reader.endObject()
            }

            JsonToken.STRING -> {
                if (reader.nextString().length > MAX_SKIPPED_STRING_LENGTH) {
                    throw CookieCloudResponseFormatException()
                }
            }

            JsonToken.NUMBER -> {
                if (reader.nextString().length > MAX_NUMBER_LENGTH) {
                    throw CookieCloudResponseFormatException()
                }
            }

            JsonToken.BOOLEAN -> when (reader.nextBoolean()) {
                true,
                false,
                -> Unit
            }

            JsonToken.NULL -> reader.nextNull()
            else -> throw CookieCloudResponseFormatException()
        }
    }

    private fun requireToken(
        reader: JsonReader,
        expected: JsonToken,
    ) {
        if (reader.peek() != expected) {
            throw CookieCloudResponseFormatException()
        }
    }

    private companion object {
        const val GET_PATH_SEGMENT = "get"
        const val CRYPTO_TYPE_QUERY = "crypto_type"
        const val ENCRYPTED_FIELD = "encrypted"
        const val MAX_RESPONSE_BYTES = 8 * 1_024 * 1_024
        const val MAX_ENCRYPTED_CHARACTERS = 6 * 1_024 * 1_024
        const val MAX_ROOT_FIELD_COUNT = 32
        const val MAX_FIELD_NAME_LENGTH = 256
        const val MAX_NESTING_DEPTH = 8
        const val MAX_CONTAINER_ENTRY_COUNT = 64
        const val MAX_SKIPPED_VALUE_COUNT = 256
        const val MAX_SKIPPED_STRING_LENGTH = 16 * 1_024
        const val MAX_NUMBER_LENGTH = 128
    }
}

private class LimitedInputStream(
    input: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value == -1) {
            return -1
        }
        bytesRead += 1
        if (bytesRead > maximumBytes) {
            throw CookieCloudResponseTooLargeException()
        }
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) {
            return 0
        }
        val remaining = maximumBytes - bytesRead
        if (remaining <= 0) {
            val extraByte = super.read()
            if (extraByte == -1) {
                return -1
            }
            throw CookieCloudResponseTooLargeException()
        }
        val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) {
            bytesRead += count
        }
        return count
    }
}

private class CookieCloudResponseTooLargeException : IOException()

private class CookieCloudResponseFormatException : RuntimeException()

private class JsonReadBudget(
    var valueCount: Int = 0,
)

internal fun interface CookieCloudTokenDecoder {
    fun decode(
        encrypted: String,
        config: CookieCloudConfig,
    ): CookieCloudTokenResult
}

internal sealed interface CookieCloudTokenResult {
    data class Success(
        val token: String,
    ) : CookieCloudTokenResult

    data object DecryptionFailed : CookieCloudTokenResult

    data object Missing : CookieCloudTokenResult
}

private object DefaultCookieCloudTokenDecoder : CookieCloudTokenDecoder {
    override fun decode(
        encrypted: String,
        config: CookieCloudConfig,
    ): CookieCloudTokenResult {
        val decryptedJson = CookieCloudDecryptor.decrypt(encrypted, config)
            ?: return CookieCloudTokenResult.DecryptionFailed
        val token = CookieCloudDecryptor.extractPcToken(decryptedJson)
            ?: return CookieCloudTokenResult.Missing
        return CookieCloudTokenResult.Success(token)
    }
}

private data class StoredAuthenticationToken(
    val value: String?,
)

private data class RecoveryAttemptKey(
    val tokenFingerprint: String,
    val configFingerprint: String,
)

private data class RecoveryAttempt(
    val result: CookieCloudSyncResult,
    val recordedAtMillis: Long,
)
