package cn.ahlib.reservation.data

import android.app.Application
import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class EncryptedCookieCloudConfigStoreTest {
    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `save encrypts the complete config and load validates it`() {
        val cipher = RecordingCipher()
        val store = EncryptedCookieCloudConfigStore(application, cipher = cipher)
        val config = CookieCloudConfig(
            serverUrl = "https://cookie.example/api/",
            userKey = " short-id ",
            password = "secret-value",
            cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
        )

        assertTrue(store.save(config))

        val storedValue = preferences().getString(CONFIG_KEY, null).orEmpty()
        assertFalse(storedValue.contains("short-id"))
        assertFalse(storedValue.contains("secret-value"))
        assertTrue(cipher.lastPlainText.orEmpty().contains("\"server_url\""))
        assertTrue(cipher.lastPlainText.orEmpty().contains("\"user_key\""))
        assertTrue(cipher.lastPlainText.orEmpty().contains("\"password\""))
        assertTrue(cipher.lastPlainText.orEmpty().contains("aes-128-cbc-fixed"))
        assertEquals(
            CookieCloudConfig(
                serverUrl = "https://cookie.example/api",
                userKey = "short-id",
                password = "secret-value",
                cryptoType = CookieCloudCryptoType.AES_128_CBC_FIXED,
            ),
            store.load(),
        )
    }

    @Test
    fun `corrupt config is cleared without exposing an exception`() {
        preferences().edit().putString(CONFIG_KEY, "corrupt-value").commit()
        val store = EncryptedCookieCloudConfigStore(application, cipher = RecordingCipher())

        assertNull(store.load())
        assertFalse(preferences().contains(CONFIG_KEY))
    }

    @Test
    fun `save retries once after resetting a failed cipher`() {
        val cipher = RecordingCipher(failFirstEncryption = true)
        val store = EncryptedCookieCloudConfigStore(application, cipher = cipher)

        assertTrue(store.save(validConfig()))
        assertEquals(2, cipher.encryptCalls)
        assertEquals(1, cipher.resetCalls)
        assertEquals(validConfig(), store.load())
    }

    @Test
    fun `clear removes the encrypted config`() {
        val store = EncryptedCookieCloudConfigStore(application, cipher = RecordingCipher())
        assertTrue(store.save(validConfig()))

        store.clear()

        assertNull(store.load())
        assertFalse(preferences().contains(CONFIG_KEY))
    }

    @Test
    fun `invalid config is not encrypted`() {
        val cipher = RecordingCipher()
        val store = EncryptedCookieCloudConfigStore(application, cipher = cipher)

        assertFalse(
            store.save(
                CookieCloudConfig(
                    serverUrl = "http://cookie.example",
                    userKey = "short-id",
                    password = "secret-value",
                    cryptoType = CookieCloudCryptoType.LEGACY,
                ),
            ),
        )
        assertEquals(0, cipher.encryptCalls)
        assertFalse(preferences().contains(CONFIG_KEY))
    }

    @Test
    fun `oversized UTF8 config does not reset the cipher or replace the previous value`() {
        val cipher = RecordingCipher()
        val store = EncryptedCookieCloudConfigStore(application, cipher = cipher)
        val previousConfig = validConfig()
        assertTrue(store.save(previousConfig))
        val encryptCallsBefore = cipher.encryptCalls

        assertFalse(
            store.save(
                previousConfig.copy(password = "\u754c".repeat(4_096)),
            ),
        )
        assertFalse(
            store.save(
                previousConfig.copy(password = "<".repeat(4_096)),
            ),
        )

        assertEquals(encryptCallsBefore, cipher.encryptCalls)
        assertEquals(0, cipher.resetCalls)
        assertEquals(previousConfig, store.load())
    }

    private fun validConfig(): CookieCloudConfig =
        CookieCloudConfig(
            serverUrl = "https://cookie.example/api",
            userKey = "short-id",
            password = "secret-value",
            cryptoType = CookieCloudCryptoType.LEGACY,
        )

    private fun preferences() =
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private class RecordingCipher(
        private val failFirstEncryption: Boolean = false,
    ) : CookieCloudConfigCipher {
        var encryptCalls: Int = 0
        var resetCalls: Int = 0
        var lastPlainText: String? = null

        override fun encrypt(plainText: String): String {
            encryptCalls += 1
            if (failFirstEncryption && encryptCalls == 1) {
                throw IllegalStateException()
            }
            lastPlainText = plainText
            return PREFIX + Base64.getEncoder().encodeToString(
                plainText.toByteArray(StandardCharsets.UTF_8),
            )
        }

        override fun decrypt(encryptedValue: String): String {
            require(encryptedValue.startsWith(PREFIX))
            val encodedValue = encryptedValue.removePrefix(PREFIX)
            return String(Base64.getDecoder().decode(encodedValue), StandardCharsets.UTF_8)
        }

        override fun reset() {
            resetCalls += 1
        }

        private companion object {
            const val PREFIX = "encrypted:"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_cookiecloud_config"
        const val CONFIG_KEY = "config"
    }
}
