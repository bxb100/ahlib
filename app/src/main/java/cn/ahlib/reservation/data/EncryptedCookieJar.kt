package cn.ahlib.reservation.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import okhttp3.Cookie
import okhttp3.HttpUrl

internal class EncryptedCookieJar(
    context: Context,
    private val gson: Gson = GsonFactory.create(),
) : ClearableCookieJar {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val cookies = mutableListOf<StoredCookie>()
    private var loaded = false
    private var cachedKey: SecretKey? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            ensureLoadedLocked()
            val persistentBefore = this.cookies.filter(StoredCookie::persistent)
            updateCookiesLocked(cookies, System.currentTimeMillis())
            val persistentAfter = this.cookies.filter(StoredCookie::persistent)
            if (persistentAfter.toSet() != persistentBefore.toSet()) {
                persistLocked()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        synchronized(lock) {
            ensureLoadedLocked()
            val persistentRemoved = removeExpiredLocked(System.currentTimeMillis())
            if (persistentRemoved) {
                persistLocked()
            }
            cookies.mapNotNull(StoredCookie::toCookie)
                .filter { cookie -> cookie.matches(url) }
        }

    override fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean {
        val cookie = createAuthenticationCookie(token, retentionDays) ?: return false
        return synchronized(lock) {
            ensureLoadedLocked()
            val persistentBefore = cookies.filter(StoredCookie::persistent)
            updateCookiesLocked(listOf(cookie), System.currentTimeMillis())
            val persistentAfter = cookies.filter(StoredCookie::persistent)
            if (persistentAfter.toSet() != persistentBefore.toSet()) {
                persistLocked()
            } else {
                true
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            loaded = true
            cookies.clear()
            preferences.edit { remove(COOKIES_KEY) }
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) {
            return
        }
        loaded = true
        cookies += readCookiesLocked()
        removeExpiredLocked(System.currentTimeMillis())
    }

    private fun removeExpiredLocked(now: Long): Boolean {
        var removedPersistent = false
        cookies.removeAll { storedCookie ->
            val expired = storedCookie.expiresAt <= now
            if (expired && storedCookie.persistent) {
                removedPersistent = true
            }
            expired
        }
        return removedPersistent
    }

    private fun updateCookiesLocked(
        responseCookies: List<Cookie>,
        now: Long,
    ) {
        removeExpiredLocked(now)
        responseCookies.forEach { cookie ->
            val previous = cookies.firstOrNull { storedCookie ->
                storedCookie.name == cookie.name &&
                    storedCookie.domain == cookie.domain &&
                    storedCookie.path == cookie.path
            }
            cookies.removeAll { storedCookie ->
                storedCookie.name == cookie.name &&
                    storedCookie.domain == cookie.domain &&
                    storedCookie.path == cookie.path
            }
            if (cookie.expiresAt > now) {
                val authenticationExpiry = authenticationCookieExpiryOverride(
                    incoming = cookie,
                    previous = previous?.toCookie(),
                    currentTimeMillis = now,
                )
                cookies += cookie.toStoredCookie(authenticationExpiry)
            }
        }
    }

    private fun readCookiesLocked(): List<StoredCookie> {
        val encryptedValue = preferences.getString(COOKIES_KEY, null) ?: return emptyList()
        return try {
            val json = decrypt(encryptedValue)
            gson.fromJson(json, StoredCookies::class.java)?.cookies.orEmpty()
        } catch (exception: Exception) {
            Log.w(TAG, "Stored cookies are unreadable; clearing cookie storage.", exception)
            preferences.edit { remove(COOKIES_KEY) }
            resetKey()
            emptyList()
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun persistLocked(): Boolean {
        val persistentCookies = cookies.filter(StoredCookie::persistent)
        if (persistentCookies.isEmpty()) {
            return preferences.edit().remove(COOKIES_KEY).commit()
        }
        val json = gson.toJson(StoredCookies(persistentCookies))
        val encryptedValue = try {
            encrypt(json)
        } catch (firstException: Exception) {
            resetKey()
            try {
                encrypt(json)
            } catch (secondException: Exception) {
                Log.w(TAG, "Failed to encrypt cookies; clearing cookie storage.", secondException)
                preferences.edit { remove(COOKIES_KEY) }
                return false
            }
        }
        return preferences.edit().putString(COOKIES_KEY, encryptedValue).commit()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherText, Base64.NO_WRAP),
        ).joinToString(SEPARATOR)
    }

    private fun decrypt(encryptedValue: String): String {
        val parts = encryptedValue.split(SEPARATOR, limit = ENCRYPTED_PART_COUNT)
        require(parts.size == ENCRYPTED_PART_COUNT && parts[0] == FORMAT_VERSION)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            cachedKey = existingKey
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey().also { cachedKey = it }
    }

    private fun resetKey() {
        cachedKey = null
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to reset the cookie encryption key.", exception)
        }
    }

    private fun Cookie.toStoredCookie(authenticationExpiry: Long? = null): StoredCookie =
        StoredCookie(
            name = name,
            value = value,
            expiresAt = authenticationExpiry ?: expiresAt,
            domain = domain,
            path = path,
            secure = secure,
            httpOnly = httpOnly,
            hostOnly = hostOnly,
            persistent = persistent || authenticationExpiry != null,
        )

    private data class StoredCookies(
        val cookies: List<StoredCookie> = emptyList(),
    )

    private data class StoredCookie(
        val name: String = "",
        val value: String = "",
        val expiresAt: Long = Long.MAX_VALUE,
        val domain: String = "",
        val path: String = "/",
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val hostOnly: Boolean = false,
        val persistent: Boolean = false,
    ) {
        fun toCookie(): Cookie? =
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                    .apply {
                        if (hostOnly) {
                            hostOnlyDomain(domain)
                        } else {
                            domain(domain)
                        }
                        if (persistent) {
                            expiresAt(expiresAt)
                        }
                        if (secure) {
                            secure()
                        }
                        if (httpOnly) {
                            httpOnly()
                        }
                    }
                    .build()
            } catch (exception: IllegalArgumentException) {
                null
            }
    }

    private companion object {
        const val TAG = "EncryptedCookieJar"
        const val PREFERENCES_NAME = "encrypted_session_cookies"
        const val COOKIES_KEY = "cookies"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cn.ahlib.reservation.session.cookies"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "1"
        const val SEPARATOR = "."
        const val ENCRYPTED_PART_COUNT = 3
        const val GCM_TAG_SIZE_BITS = 128
        const val KEY_SIZE_BITS = 256
        const val AAD = "cn.ahlib.reservation.data.cookies.v1"
    }
}
