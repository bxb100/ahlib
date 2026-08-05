package cn.ahlib.reservation.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookieCloudCryptoTest {
    @Test
    fun `hardcoded OpenSSL compatibility vectors remain supported`() {
        val fixedConfig = config(
            userKey = "short-id",
            password = "fixed-secret",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )
        val legacyConfig = config(
            userKey = "short-id",
            password = "legacy-secret",
            cryptoType = CookieCloudCryptoType.LEGACY,
        )

        assertEquals(
            "fixed-vector-token",
            CookieCloudDecryptor.decryptPcToken(
                FIXED_OPENSSL_VECTOR,
                fixedConfig,
                NOW_EPOCH_SECONDS,
            ),
        )
        assertEquals(
            "legacy-vector-token",
            CookieCloudDecryptor.decryptPcToken(
                LEGACY_OPENSSL_VECTOR,
                legacyConfig,
                NOW_EPOCH_SECONDS,
            ),
        )
    }

    @Test
    fun `fixed mode decrypts a deterministic Unicode compatibility vector`() {
        val config = config(
            userKey = "short-\u7528\u6237-key",
            password = "p\u00e4ssword-\u2603",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )
        val json = payload(
            cookie(
                value = "token-\u2603",
                domain = ".www.lib.ah.cn",
                expirationDate = 2_000_000_000.0,
            ),
        )
        val encrypted = encryptFixed(json, config.userKey, config.password)

        assertEquals(
            "token-\u2603",
            CookieCloudDecryptor.decryptPcToken(encrypted, config, NOW_EPOCH_SECONDS),
        )
    }

    @Test
    fun `legacy mode decrypts an OpenSSL salted compatibility vector`() {
        val config = config(
            userKey = "short-id",
            password = "legacy-secret",
            cryptoType = CookieCloudCryptoType.LEGACY,
        )
        val json = payload(
            cookie(
                value = "legacy-token",
                domain = ".lib.ah.cn",
                expirationDate = 2_000_000_000.0,
            ),
        )
        val encrypted = encryptLegacy(
            plainText = json,
            userKey = config.userKey,
            password = config.password,
            salt = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7),
        )

        assertEquals(
            "legacy-token",
            CookieCloudDecryptor.decryptPcToken(encrypted, config, NOW_EPOCH_SECONDS),
        )
    }

    @Test
    fun `wrong password and malformed ciphertext return null`() {
        val config = config(
            userKey = "short-id",
            password = "correct-secret",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )
        val encrypted = encryptFixed(
            payload(cookie("token", ".www.lib.ah.cn", 2_000_000_000.0)),
            config.userKey,
            config.password,
        )

        assertNull(
            CookieCloudDecryptor.decryptPcToken(
                encrypted = encrypted,
                config = config.copy(password = "wrong-secret"),
                nowEpochSeconds = NOW_EPOCH_SECONDS,
            ),
        )
        assertNull(CookieCloudDecryptor.decrypt("not-base64", config))
        assertNull(
            CookieCloudDecryptor.decrypt(
                Base64.getEncoder().encodeToString(ByteArray(17)),
                config.copy(cryptoType = CookieCloudCryptoType.LEGACY),
            ),
        )
    }

    @Test
    fun `target filtering rejects expired and malicious domains`() {
        val json = payload(
            cookie("expired", ".www.lib.ah.cn", NOW_EPOCH_SECONDS.toDouble()),
            cookie("suffix-attack", ".www.lib.ah.cn.evil", 9_000_000_000.0),
            cookie("prefix-attack", "evilwww.lib.ah.cn", 9_000_000_000.0),
            cookie("unrelated", ".example.com", 9_000_000_000.0),
            cookie("wrong-name", ".www.lib.ah.cn", 9_000_000_000.0, name = "token"),
        )

        assertNull(CookieCloudDecryptor.extractPcToken(json, NOW_EPOCH_SECONDS))
    }

    @Test
    fun `most specific domain wins before later expiration`() {
        val json = payload(
            cookie("parent-latest", ".lib.ah.cn", 9_000_000_000.0),
            cookie("specific-earlier", ".www.lib.ah.cn", 2_000_000_000.0),
            cookie("specific-later", "WWW.LIB.AH.CN", 3_000_000_000.0),
        )

        assertEquals(
            "specific-later",
            CookieCloudDecryptor.extractPcToken(json, NOW_EPOCH_SECONDS),
        )
    }

    @Test
    fun `cookie matching respects host only and path attributes`() {
        val json = payload(
            cookie(
                value = "invalid-host-only-parent",
                domain = ".lib.ah.cn",
                expirationDate = 9_000_000_000.0,
                hostOnly = true,
            ),
            cookie(
                value = "invalid-path",
                domain = "www.lib.ah.cn",
                expirationDate = 9_000_000_000.0,
                hostOnly = true,
                path = "/account",
            ),
            cookie(
                value = "valid-root",
                domain = "www.lib.ah.cn",
                expirationDate = 2_000_000_000.0,
                hostOnly = true,
                path = "/",
            ),
        )

        assertEquals(
            "valid-root",
            CookieCloudDecryptor.extractPcToken(json, NOW_EPOCH_SECONDS),
        )
    }

    @Test
    fun `session cookie without expiration remains eligible`() {
        val json = payload(
            cookie("persistent", ".www.lib.ah.cn", 9_000_000_000.0),
            cookie("session", ".www.lib.ah.cn", expirationDate = null),
        )

        assertEquals("session", CookieCloudDecryptor.extractPcToken(json, NOW_EPOCH_SECONDS))
    }

    @Test
    fun `payload metadata cannot override the configured crypto type`() {
        val config = config(
            userKey = "short-id",
            password = "secret",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )
        val json = payload(
            cookie("token", ".www.lib.ah.cn", 2_000_000_000.0),
            extraRootField = "\"crypto_type\":\"legacy\"",
        )
        val encrypted = encryptFixed(json, config.userKey, config.password)

        assertEquals(
            "token",
            CookieCloudDecryptor.decryptPcToken(encrypted, config, NOW_EPOCH_SECONDS),
        )
        assertNull(
            CookieCloudDecryptor.decryptPcToken(
                encrypted,
                config.copy(cryptoType = CookieCloudCryptoType.LEGACY),
                NOW_EPOCH_SECONDS,
            ),
        )
    }

    @Test
    fun `oversized JSON and collections are rejected`() {
        assertNull(
            CookieCloudDecryptor.extractPcToken(
                " ".repeat(4 * 1_024 * 1_024 + 1),
                NOW_EPOCH_SECONDS,
            ),
        )
        val domainBuckets = (0..2_048).joinToString(",") { index -> "\"d$index\":[]" }
        val json = "{\"cookie_data\":{$domainBuckets},\"local_storage_data\":{}}"

        assertNull(CookieCloudDecryptor.extractPcToken(json, NOW_EPOCH_SECONDS))
    }

    private fun config(
        userKey: String,
        password: String,
        cryptoType: CookieCloudCryptoType,
    ): CookieCloudConfig =
        checkNotNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example/api",
                userKey = userKey,
                password = password,
                cryptoType = cryptoType,
            ),
        )

    private fun payload(
        vararg cookies: String,
        extraRootField: String? = null,
    ): String {
        val extra = extraRootField?.let { field -> ",$field" }.orEmpty()
        return "{\"cookie_data\":{\"bucket\":[${cookies.joinToString(",")}]}," +
            "\"local_storage_data\":{}$extra}"
    }

    private fun cookie(
        value: String,
        domain: String,
        expirationDate: Double?,
        name: String = "pc_token",
        hostOnly: Boolean = false,
        path: String = "/",
    ): String {
        val expiration = expirationDate?.let { value -> ",\"expirationDate\":$value" }.orEmpty()
        return "{\"name\":\"$name\",\"value\":\"$value\",\"domain\":\"$domain\"," +
            "\"hostOnly\":$hostOnly,\"path\":\"$path\"$expiration}"
    }

    private fun encryptFixed(
        plainText: String,
        userKey: String,
        password: String,
    ): String {
        val keyMaterial = createKeyMaterial(userKey, password)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyMaterial, AES_ALGORITHM),
            IvParameterSpec(ByteArray(AES_BLOCK_SIZE_BYTES)),
        )
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    private fun encryptLegacy(
        plainText: String,
        userKey: String,
        password: String,
        salt: ByteArray,
    ): String {
        require(salt.size == LEGACY_SALT_SIZE_BYTES)
        val keyAndIv = deriveLegacyKeyAndIv(createKeyMaterial(userKey, password), salt)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyAndIv.copyOfRange(0, LEGACY_KEY_SIZE_BYTES), AES_ALGORITHM),
            IvParameterSpec(keyAndIv.copyOfRange(LEGACY_KEY_SIZE_BYTES, keyAndIv.size)),
        )
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val envelope = LEGACY_MAGIC + salt + cipherText
        return Base64.getEncoder().encodeToString(envelope)
    }

    private fun createKeyMaterial(
        userKey: String,
        password: String,
    ): ByteArray {
        val hash = MessageDigest.getInstance(MD5_ALGORITHM)
            .digest("$userKey-$password".toByteArray(StandardCharsets.UTF_8))
        val hex = buildString(hash.size * 2) {
            hash.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(LOWER_HEX[value ushr 4])
                append(LOWER_HEX[value and 0x0f])
            }
        }
        return hex.substring(0, 16).toByteArray(StandardCharsets.US_ASCII)
    }

    private fun deriveLegacyKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        val derived = ByteArray(LEGACY_KEY_SIZE_BYTES + AES_BLOCK_SIZE_BYTES)
        var previous = ByteArray(0)
        var offset = 0
        while (offset < derived.size) {
            val digest = MessageDigest.getInstance(MD5_ALGORITHM)
            digest.update(previous)
            digest.update(password)
            digest.update(salt)
            previous = digest.digest()
            val length = minOf(previous.size, derived.size - offset)
            previous.copyInto(derived, destinationOffset = offset, endIndex = length)
            offset += length
        }
        return derived
    }

    private companion object {
        const val NOW_EPOCH_SECONDS = 1_700_000_000L
        const val AES_ALGORITHM = "AES"
        const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val MD5_ALGORITHM = "MD5"
        const val AES_BLOCK_SIZE_BYTES = 16
        const val LEGACY_KEY_SIZE_BYTES = 32
        const val LEGACY_SALT_SIZE_BYTES = 8
        const val LOWER_HEX = "0123456789abcdef"
        const val FIXED_OPENSSL_VECTOR =
            "lUPVYTwGDS0i+Y/H0GSu9dAfKYrlsycvHxpt+xmars0+W/sq6n1VwBSij12N1hZqz8pJHDGz" +
                "NN2S0LFh+ljRwsvXDp4zv0WQN8ABlqqSCM88dAUGrIafrW9MvMgvmjkHyikMe/l/ACM3MAZg73" +
                "fh+KjM5MiVl9fEGEVXOYpOBd14sYl6OveLY4jtMsJ4qMLorTOrUgE/3+tUpZKuiSHQs19Hch" +
                "Zqh5xL9NE5xad9ryx9Q+oXI6giEsBWtCtkRpZa"
        const val LEGACY_OPENSSL_VECTOR =
            "U2FsdGVkX1/G4a0Tvmy7l1b8dJks1ewp6HeaihmFCrYJrxeMOmFSwGSFUGRCGliC96CAC4z+L" +
                "ssmSTMahQTgXzNdMreQK2rSKNvIsrXhcMP2q/dyKOMjx1J3g+XwdTExtgYbS58YEiwWV6Lc/" +
                "A8aOsC5mkJYmTPIylH+4jbujAd3edclJAhRW3/gpYmbhcDKKlwh8tEGYrMpznodJjaDeEAR2c" +
                "h9xQbWpHYForKYA3zjNE67fbFKV/L8oqkd5bcGA18XcysyHZ/nZdtUf1lL+w=="
        val LEGACY_MAGIC: ByteArray = "Salted__".toByteArray(StandardCharsets.US_ASCII)
    }
}
