package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CookieCloudConfigTest {
    @Test
    fun `https server URL with API root is normalized`() {
        val config = CookieCloudConfig.normalizedOrNull(
            serverUrl = "  HTTPS://cookie.example/api/root///  ",
            userKey = " short-id_123 ",
            password = " password with spaces ",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )

        checkNotNull(config)
        assertEquals("https://cookie.example/api/root", config.serverUrl)
        assertEquals("short-id_123", config.userKey)
        assertEquals(" password with spaces ", config.password)
    }

    @Test
    fun `short and Unicode user keys are accepted`() {
        val config = CookieCloudConfig.normalizedOrNull(
            serverUrl = "https://cookie.example",
            userKey = "short-\u7528\u6237-key",
            password = "secret",
            cryptoType = CookieCloudCryptoType.LEGACY,
        )

        assertEquals("short-\u7528\u6237-key", config?.userKey)
    }

    @Test
    fun `unsafe server URLs are rejected`() {
        val invalidUrls = listOf(
            "http://cookie.example",
            "https://user:secret@cookie.example",
            "https://cookie.example/api?mode=full",
            "https://cookie.example/api#section",
            "https://cookie.example/api/../private",
            "https://cookie.example:0/api",
            "https:///missing-host",
        )

        invalidUrls.forEach { serverUrl ->
            assertNull(
                CookieCloudConfig.normalizedOrNull(
                    serverUrl = serverUrl,
                    userKey = "short-id",
                    password = "secret",
                    cryptoType = CookieCloudCryptoType.LEGACY,
                ),
            )
        }
    }

    @Test
    fun `unsafe user keys are rejected without requiring UUID format`() {
        val invalidUserKeys = listOf("", " ", ".", "..", "path/segment", "path\\segment", "key\nvalue")

        invalidUserKeys.forEach { userKey ->
            assertNull(
                CookieCloudConfig.normalizedOrNull(
                    serverUrl = "https://cookie.example",
                    userKey = userKey,
                    password = "secret",
                    cryptoType = CookieCloudCryptoType.LEGACY,
                ),
            )
        }
    }

    @Test
    fun `empty password is rejected without trimming valid password characters`() {
        assertNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example",
                userKey = "short-id",
                password = "",
                cryptoType = CookieCloudCryptoType.LEGACY,
            ),
        )
        assertEquals(
            " secret ",
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example",
                userKey = "short-id",
                password = " secret ",
                cryptoType = CookieCloudCryptoType.LEGACY,
            )?.password,
        )
    }

    @Test
    fun `credential limits are measured in UTF8 bytes`() {
        assertNull(
            CookieCloudConfig.normalizedOrNull(
                serverUrl = "https://cookie.example",
                userKey = "short-id",
                password = "\u754c".repeat(1_366),
                cryptoType = CookieCloudCryptoType.LEGACY,
            ),
        )
    }

    @Test
    fun `crypto type wire values are stable`() {
        assertEquals("legacy", CookieCloudCryptoType.LEGACY.wireValue)
        assertEquals("aes-128-cbc-fixed", CookieCloudCryptoType.AES_128_CBC_FIXED.wireValue)
        assertEquals(
            CookieCloudCryptoType.AES_128_CBC_FIXED,
            CookieCloudCryptoType.fromWireValue("aes-128-cbc-fixed"),
        )
        assertNull(CookieCloudCryptoType.fromWireValue("fixed"))
    }

    @Test
    fun `config string representation redacts credentials`() {
        val config = CookieCloudConfig(
            serverUrl = "https://cookie.example",
            userKey = "sensitive-user-key",
            password = "sensitive-password",
            cryptoType = CookieCloudCryptoType.LEGACY,
        )

        assertFalse(config.toString().contains(config.userKey))
        assertFalse(config.toString().contains(config.password))
    }
}
