package cn.ahlib.reservation.data

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object CookieCloudDecryptor {
    fun decryptPcToken(
        encrypted: String,
        config: CookieCloudConfig,
        nowEpochSeconds: Long = System.currentTimeMillis() / MILLIS_PER_SECOND,
    ): String? {
        val decryptedJson = decrypt(encrypted, config) ?: return null
        return extractPcToken(decryptedJson, nowEpochSeconds)
    }

    fun decrypt(
        encrypted: String,
        config: CookieCloudConfig,
    ): String? {
        val normalizedConfig = config.normalizedOrNull() ?: return null
        if (encrypted.isEmpty() || encrypted.length > MAX_ENCRYPTED_LENGTH) {
            return null
        }
        return try {
            when (normalizedConfig.cryptoType) {
                CookieCloudCryptoType.LEGACY -> decryptLegacy(encrypted, normalizedConfig)
                CookieCloudCryptoType.AES_128_CBC_FIXED -> decryptFixed(encrypted, normalizedConfig)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractPcToken(
        decryptedJson: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / MILLIS_PER_SECOND,
    ): String? {
        if (decryptedJson.isEmpty() || decryptedJson.length > MAX_JSON_CHARACTERS) {
            return null
        }
        return try {
            JsonReader(StringReader(decryptedJson)).use { reader ->
                reader.strictness = Strictness.STRICT
                val selection = CookieSelection(nowEpochSeconds)
                readPayload(reader, selection)
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    return null
                }
                selection.best?.value
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptFixed(
        encrypted: String,
        config: CookieCloudConfig,
    ): String? {
        val cipherText = decodeBase64(encrypted) ?: return null
        if (
            cipherText.isEmpty() ||
            cipherText.size > MAX_CIPHERTEXT_BYTES ||
            cipherText.size % AES_BLOCK_SIZE_BYTES != 0
        ) {
            cipherText.fill(0)
            return null
        }

        val keyMaterial = createKeyMaterial(config.userKey, config.password)
        return try {
            decryptAesCbc(
                cipherText = cipherText,
                key = keyMaterial,
                iv = ZERO_IV,
            )
        } finally {
            cipherText.fill(0)
            keyMaterial.fill(0)
        }
    }

    private fun decryptLegacy(
        encrypted: String,
        config: CookieCloudConfig,
    ): String? {
        val envelope = decodeBase64(encrypted) ?: return null
        if (
            envelope.size < LEGACY_HEADER_SIZE + AES_BLOCK_SIZE_BYTES ||
            envelope.size > MAX_CIPHERTEXT_BYTES + LEGACY_HEADER_SIZE ||
            !envelope.copyOfRange(0, LEGACY_MAGIC.size).contentEquals(LEGACY_MAGIC)
        ) {
            envelope.fill(0)
            return null
        }

        val cipherTextSize = envelope.size - LEGACY_HEADER_SIZE
        if (cipherTextSize % AES_BLOCK_SIZE_BYTES != 0) {
            envelope.fill(0)
            return null
        }

        val salt = envelope.copyOfRange(LEGACY_MAGIC.size, LEGACY_HEADER_SIZE)
        val cipherText = envelope.copyOfRange(LEGACY_HEADER_SIZE, envelope.size)
        val keyMaterial = createKeyMaterial(config.userKey, config.password)
        val derived = deriveLegacyKeyAndIv(keyMaterial, salt)
        return try {
            decryptAesCbc(
                cipherText = cipherText,
                key = derived.key,
                iv = derived.iv,
            )
        } finally {
            envelope.fill(0)
            salt.fill(0)
            cipherText.fill(0)
            keyMaterial.fill(0)
            derived.key.fill(0)
            derived.iv.fill(0)
        }
    }

    private fun decryptAesCbc(
        cipherText: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): String? {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            IvParameterSpec(iv),
        )
        val plainText = cipher.doFinal(cipherText)
        return try {
            if (plainText.size > MAX_JSON_UTF8_BYTES) {
                return null
            }
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plainText))
                .toString()
        } finally {
            plainText.fill(0)
        }
    }

    private fun decodeBase64(value: String): ByteArray? =
        try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun createKeyMaterial(
        userKey: String,
        password: String,
    ): ByteArray {
        val userKeyBytes = userKey.toByteArray(StandardCharsets.UTF_8)
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance(MD5_ALGORITHM)
        val hash = try {
            digest.update(userKeyBytes)
            digest.update(KEY_SEPARATOR)
            digest.digest(passwordBytes)
        } finally {
            userKeyBytes.fill(0)
            passwordBytes.fill(0)
        }

        return ByteArray(KEY_MATERIAL_SIZE_BYTES).also { keyMaterial ->
            repeat(KEY_MATERIAL_HASH_BYTES) { index ->
                val value = hash[index].toInt() and BYTE_MASK
                keyMaterial[index * 2] = LOWER_HEX[value ushr HEX_NIBBLE_BITS].code.toByte()
                keyMaterial[index * 2 + 1] = LOWER_HEX[value and HEX_NIBBLE_MASK].code.toByte()
            }
            hash.fill(0)
        }
    }

    private fun deriveLegacyKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
    ): LegacyKeyAndIv {
        val derivedBytes = ByteArray(LEGACY_KEY_SIZE_BYTES + AES_BLOCK_SIZE_BYTES)
        var previousBlock = ByteArray(0)
        var offset = 0
        while (offset < derivedBytes.size) {
            val digest = MessageDigest.getInstance(MD5_ALGORITHM)
            digest.update(previousBlock)
            digest.update(password)
            digest.update(salt)
            val nextBlock = digest.digest()
            previousBlock.fill(0)
            previousBlock = nextBlock
            val bytesToCopy = minOf(nextBlock.size, derivedBytes.size - offset)
            nextBlock.copyInto(derivedBytes, destinationOffset = offset, endIndex = bytesToCopy)
            offset += bytesToCopy
        }
        previousBlock.fill(0)

        return try {
            LegacyKeyAndIv(
                key = derivedBytes.copyOfRange(0, LEGACY_KEY_SIZE_BYTES),
                iv = derivedBytes.copyOfRange(LEGACY_KEY_SIZE_BYTES, derivedBytes.size),
            )
        } finally {
            derivedBytes.fill(0)
        }
    }

    private fun readPayload(
        reader: JsonReader,
        selection: CookieSelection,
    ) {
        requireToken(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        var sawCookieData = false
        while (reader.hasNext()) {
            val fieldName = reader.nextName()
            if (fieldName.length > MAX_JSON_FIELD_NAME_LENGTH) {
                throw CookieCloudFormatException()
            }
            when (fieldName) {
                COOKIE_DATA_FIELD -> {
                    if (sawCookieData) {
                        throw CookieCloudFormatException()
                    }
                    sawCookieData = true
                    readCookieData(reader, selection)
                }

                LOCAL_STORAGE_DATA_FIELD -> readLocalStorageData(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (!sawCookieData) {
            throw CookieCloudFormatException()
        }
    }

    private fun readCookieData(
        reader: JsonReader,
        selection: CookieSelection,
    ) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        requireToken(reader, JsonToken.BEGIN_OBJECT)
        reader.beginObject()
        while (reader.hasNext()) {
            selection.domainCount += 1
            if (selection.domainCount > MAX_DOMAIN_COUNT) {
                throw CookieCloudFormatException()
            }
            val domainBucket = reader.nextName()
            if (domainBucket.length > MAX_DOMAIN_LENGTH) {
                throw CookieCloudFormatException()
            }
            requireToken(reader, JsonToken.BEGIN_ARRAY)
            reader.beginArray()
            var bucketCookieCount = 0
            while (reader.hasNext()) {
                bucketCookieCount += 1
                selection.cookieCount += 1
                if (
                    bucketCookieCount > MAX_COOKIES_PER_DOMAIN ||
                    selection.cookieCount > MAX_COOKIE_COUNT
                ) {
                    throw CookieCloudFormatException()
                }
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    readCookie(reader, selection)?.let(selection::consider)
                } else {
                    reader.skipValue()
                }
            }
            reader.endArray()
        }
        reader.endObject()
    }

    private fun readCookie(
        reader: JsonReader,
        selection: CookieSelection,
    ): CookieCandidate? {
        reader.beginObject()
        var name: String? = null
        var value: String? = null
        var domain: String? = null
        var hostOnly = false
        var hostOnlyIsValid = true
        var path: String? = TARGET_PATH
        var expirationDate: Double? = null
        var expirationIsValid = true
        while (reader.hasNext()) {
            val fieldName = reader.nextName()
            if (fieldName.length > MAX_JSON_FIELD_NAME_LENGTH) {
                throw CookieCloudFormatException()
            }
            when (fieldName) {
                COOKIE_NAME_FIELD -> name = readBoundedString(reader, MAX_COOKIE_NAME_LENGTH)
                COOKIE_VALUE_FIELD -> value = readBoundedString(reader, MAX_COOKIE_VALUE_LENGTH)
                COOKIE_DOMAIN_FIELD -> domain = readBoundedString(reader, MAX_DOMAIN_LENGTH)
                COOKIE_HOST_ONLY_FIELD -> {
                    val parsedHostOnly = readBoolean(reader)
                    hostOnly = parsedHostOnly.value
                    hostOnlyIsValid = parsedHostOnly.isValid
                }

                COOKIE_PATH_FIELD -> path = readBoundedString(reader, MAX_COOKIE_PATH_LENGTH)
                COOKIE_EXPIRATION_FIELD -> {
                    val expiration = readExpiration(reader)
                    expirationDate = expiration.value
                    expirationIsValid = expiration.isValid
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val tokenValue = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val domainSpecificity = domainSpecificity(domain, hostOnly) ?: return null
        if (
            name != AUTHENTICATION_COOKIE_NAME ||
            !hostOnlyIsValid ||
            !pathMatches(path) ||
            !expirationIsValid ||
            expirationDate?.let { expiration -> expiration <= selection.nowEpochSeconds.toDouble() } == true
        ) {
            return null
        }
        return CookieCandidate(
            value = tokenValue,
            domainSpecificity = domainSpecificity,
            expirationDate = expirationDate,
        )
    }

    private fun readBoundedString(
        reader: JsonReader,
        maximumLength: Int,
    ): String? =
        when (reader.peek()) {
            JsonToken.STRING -> reader.nextString().takeIf { value -> value.length <= maximumLength }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            else -> {
                reader.skipValue()
                null
            }
        }

    private fun readExpiration(reader: JsonReader): ParsedExpiration =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                ParsedExpiration(value = null, isValid = true)
            }

            JsonToken.NUMBER,
            JsonToken.STRING,
            -> {
                val value = reader.nextString().toDoubleOrNull()
                ParsedExpiration(value = value, isValid = value?.isFinite() == true)
            }

            else -> {
                reader.skipValue()
                ParsedExpiration(value = null, isValid = false)
            }
        }

    private fun readBoolean(reader: JsonReader): ParsedBoolean =
        when (reader.peek()) {
            JsonToken.BOOLEAN -> ParsedBoolean(value = reader.nextBoolean(), isValid = true)
            JsonToken.STRING -> {
                when (reader.nextString().lowercase(Locale.ROOT)) {
                    "true" -> ParsedBoolean(value = true, isValid = true)
                    "false" -> ParsedBoolean(value = false, isValid = true)
                    else -> ParsedBoolean(value = false, isValid = false)
                }
            }

            else -> {
                reader.skipValue()
                ParsedBoolean(value = false, isValid = false)
            }
        }

    private fun readLocalStorageData(reader: JsonReader) {
        when (reader.peek()) {
            JsonToken.NULL -> reader.nextNull()
            JsonToken.BEGIN_OBJECT -> reader.skipValue()
            else -> throw CookieCloudFormatException()
        }
    }

    private fun requireToken(
        reader: JsonReader,
        expectedToken: JsonToken,
    ) {
        if (reader.peek() != expectedToken) {
            throw CookieCloudFormatException()
        }
    }

    private fun domainSpecificity(
        domain: String?,
        hostOnly: Boolean,
    ): Int? {
        val rawDomain = domain ?: return null
        if (
            rawDomain.isEmpty() ||
            rawDomain != rawDomain.trim() ||
            rawDomain.any(Character::isISOControl)
        ) {
            return null
        }
        val hasLeadingDot = rawDomain.startsWith('.')
        val normalizedDomain = rawDomain
            .lowercase(Locale.ROOT)
            .removePrefix(".")
        if (hostOnly && (hasLeadingDot || normalizedDomain != TARGET_HOST)) {
            return null
        }
        return when (normalizedDomain) {
            TARGET_HOST -> EXACT_DOMAIN_SPECIFICITY
            TARGET_PARENT_DOMAIN -> PARENT_DOMAIN_SPECIFICITY
            else -> null
        }
    }

    private fun pathMatches(path: String?): Boolean {
        val cookiePath = path ?: return false
        if (!cookiePath.startsWith('/')) {
            return false
        }
        if (cookiePath == TARGET_PATH) {
            return true
        }
        if (!TARGET_PATH.startsWith(cookiePath)) {
            return false
        }
        return cookiePath.endsWith('/') ||
            TARGET_PATH.length > cookiePath.length && TARGET_PATH[cookiePath.length] == '/'
    }

    private data class LegacyKeyAndIv(
        val key: ByteArray,
        val iv: ByteArray,
    )

    private data class ParsedExpiration(
        val value: Double?,
        val isValid: Boolean,
    )

    private data class ParsedBoolean(
        val value: Boolean,
        val isValid: Boolean,
    )

    private data class CookieCandidate(
        val value: String,
        val domainSpecificity: Int,
        val expirationDate: Double?,
    )

    private class CookieSelection(
        val nowEpochSeconds: Long,
    ) {
        var domainCount: Int = 0
        var cookieCount: Int = 0
        var best: CookieCandidate? = null

        fun consider(candidate: CookieCandidate) {
            val current = best
            if (
                current == null ||
                candidate.domainSpecificity > current.domainSpecificity ||
                candidate.domainSpecificity == current.domainSpecificity &&
                candidate.expirationRank > current.expirationRank
            ) {
                best = candidate
            }
        }

        private val CookieCandidate.expirationRank: Double
            get() = expirationDate ?: Double.POSITIVE_INFINITY
    }

    private class CookieCloudFormatException : Exception()

    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val MD5_ALGORITHM = "MD5"
    private const val KEY_SEPARATOR = '-'.code.toByte()
    private const val MILLIS_PER_SECOND = 1_000L
    private const val AES_BLOCK_SIZE_BYTES = 16
    private const val LEGACY_KEY_SIZE_BYTES = 32
    private const val LEGACY_HEADER_SIZE = 16
    private const val KEY_MATERIAL_SIZE_BYTES = 16
    private const val KEY_MATERIAL_HASH_BYTES = 8
    private const val BYTE_MASK = 0xff
    private const val HEX_NIBBLE_BITS = 4
    private const val HEX_NIBBLE_MASK = 0x0f
    private const val MAX_ENCRYPTED_LENGTH = 6 * 1_024 * 1_024
    private const val MAX_CIPHERTEXT_BYTES = 4 * 1_024 * 1_024 + AES_BLOCK_SIZE_BYTES
    private const val MAX_JSON_UTF8_BYTES = 4 * 1_024 * 1_024
    private const val MAX_JSON_CHARACTERS = 4 * 1_024 * 1_024
    private const val MAX_DOMAIN_COUNT = 2_048
    private const val MAX_COOKIE_COUNT = 16_384
    private const val MAX_COOKIES_PER_DOMAIN = 2_048
    private const val MAX_JSON_FIELD_NAME_LENGTH = 256
    private const val MAX_COOKIE_NAME_LENGTH = 256
    private const val MAX_COOKIE_VALUE_LENGTH = 16 * 1_024
    private const val MAX_COOKIE_PATH_LENGTH = 2_048
    private const val MAX_DOMAIN_LENGTH = 512
    private const val EXACT_DOMAIN_SPECIFICITY = 2
    private const val PARENT_DOMAIN_SPECIFICITY = 1
    private const val COOKIE_DATA_FIELD = "cookie_data"
    private const val LOCAL_STORAGE_DATA_FIELD = "local_storage_data"
    private const val COOKIE_NAME_FIELD = "name"
    private const val COOKIE_VALUE_FIELD = "value"
    private const val COOKIE_DOMAIN_FIELD = "domain"
    private const val COOKIE_HOST_ONLY_FIELD = "hostOnly"
    private const val COOKIE_PATH_FIELD = "path"
    private const val COOKIE_EXPIRATION_FIELD = "expirationDate"
    private const val TARGET_HOST = "www.lib.ah.cn"
    private const val TARGET_PARENT_DOMAIN = "lib.ah.cn"
    private const val TARGET_PATH = "/"
    private const val AUTHENTICATION_COOKIE_NAME = "pc_token"
    private const val LOWER_HEX = "0123456789abcdef"
    private val ZERO_IV = ByteArray(AES_BLOCK_SIZE_BYTES)
    private val LEGACY_MAGIC = "Salted__".toByteArray(StandardCharsets.US_ASCII)
}
