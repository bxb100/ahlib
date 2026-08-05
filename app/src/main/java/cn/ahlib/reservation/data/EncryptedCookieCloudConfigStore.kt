package cn.ahlib.reservation.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@SuppressLint("ApplySharedPref", "UseKtx")
internal class EncryptedCookieCloudConfigStore(
    context: Context,
    private val gson: Gson = GsonFactory.create(),
    private val cipher: CookieCloudConfigCipher = AndroidKeyStoreCookieCloudConfigCipher(),
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): CookieCloudConfig? =
        synchronized(lock) {
            val encryptedValue = preferences.getString(CONFIG_KEY, null) ?: return@synchronized null
            if (encryptedValue.length > MAX_ENCRYPTED_CONFIG_LENGTH) {
                clearStoredValue()
                return@synchronized null
            }
            try {
                val json = cipher.decrypt(encryptedValue)
                if (!json.hasUtf8ByteLengthAtMost(COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES)) {
                    throw IllegalArgumentException()
                }
                val storedConfig = gson.fromJson(json, StoredCookieCloudConfig::class.java)
                    ?: throw IllegalArgumentException()
                val cryptoType = CookieCloudCryptoType.fromWireValue(storedConfig.cryptoType)
                    ?: throw IllegalArgumentException()
                CookieCloudConfig.normalizedOrNull(
                    serverUrl = storedConfig.serverUrl ?: throw IllegalArgumentException(),
                    userKey = storedConfig.userKey ?: throw IllegalArgumentException(),
                    password = storedConfig.password ?: throw IllegalArgumentException(),
                    cryptoType = cryptoType,
                ) ?: throw IllegalArgumentException()
            } catch (_: Exception) {
                clearStoredValue()
                null
            }
        }

    fun save(config: CookieCloudConfig): Boolean =
        synchronized(lock) {
            val normalizedConfig = config.normalizedOrNull() ?: return@synchronized false
            val json = gson.toJson(
                StoredCookieCloudConfig(
                    serverUrl = normalizedConfig.serverUrl,
                    userKey = normalizedConfig.userKey,
                    password = normalizedConfig.password,
                    cryptoType = normalizedConfig.cryptoType.wireValue,
                ),
            )
            if (!json.hasUtf8ByteLengthAtMost(COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES)) {
                return@synchronized false
            }

            val encryptedValue = encryptWithRecovery(json) ?: return@synchronized false
            if (encryptedValue.length > MAX_ENCRYPTED_CONFIG_LENGTH) {
                return@synchronized false
            }
            preferences.edit()
                .putString(CONFIG_KEY, encryptedValue)
                .commit()
        }

    fun clear() {
        synchronized(lock) {
            clearStoredValue()
        }
    }

    private fun encryptWithRecovery(json: String): String? =
        try {
            cipher.encrypt(json)
        } catch (_: Exception) {
            cipher.reset()
            try {
                cipher.encrypt(json)
            } catch (_: Exception) {
                clearStoredValue()
                null
            }
        }

    private fun clearStoredValue() {
        preferences.edit().remove(CONFIG_KEY).commit()
    }

    private data class StoredCookieCloudConfig(
        @SerializedName("server_url")
        val serverUrl: String? = null,
        @SerializedName("user_key")
        val userKey: String? = null,
        val password: String? = null,
        @SerializedName("crypto_type")
        val cryptoType: String? = null,
    )

    private companion object {
        const val PREFERENCES_NAME = "encrypted_cookiecloud_config"
        const val CONFIG_KEY = "config"
        const val MAX_ENCRYPTED_CONFIG_LENGTH = 32 * 1_024
    }
}

internal interface CookieCloudConfigCipher {
    fun encrypt(plainText: String): String

    fun decrypt(encryptedValue: String): String

    fun reset()
}

private class AndroidKeyStoreCookieCloudConfigCipher : CookieCloudConfigCipher {
    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plainText: String): String {
        val plainTextBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        return try {
            require(plainTextBytes.size <= COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            require(iv.size == GCM_IV_SIZE_BYTES)
            cipher.updateAAD(AAD_BYTES)
            val cipherText = cipher.doFinal(plainTextBytes)
            require(cipherText.size <= MAX_CIPHERTEXT_BYTES)
            listOf(
                FORMAT_VERSION,
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
            ).joinToString(SEPARATOR)
        } finally {
            plainTextBytes.fill(0)
        }
    }

    override fun decrypt(encryptedValue: String): String {
        require(encryptedValue.length <= MAX_ENCRYPTED_CONFIG_LENGTH)
        val parts = encryptedValue.split(SEPARATOR, limit = ENCRYPTED_PART_COUNT)
        require(parts.size == ENCRYPTED_PART_COUNT && parts[0] == FORMAT_VERSION)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
        require(iv.size == GCM_IV_SIZE_BYTES)
        require(cipherText.size in GCM_TAG_SIZE_BYTES..MAX_CIPHERTEXT_BYTES)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
        )
        cipher.updateAAD(AAD_BYTES)
        val plainText = cipher.doFinal(cipherText)
        return try {
            require(plainText.size <= COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES)
            String(plainText, StandardCharsets.UTF_8)
        } finally {
            plainText.fill(0)
        }
    }

    override fun reset() {
        synchronized(KEY_STORE_LOCK) {
            cachedKey = null
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
            } catch (_: Exception) {
                // A later operation will retry the keystore and report failure to the caller.
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(KEY_STORE_LOCK) {
            cachedKey?.let { return@synchronized it }
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existingKey != null) {
                cachedKey = existingKey
                return@synchronized existingKey
            }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE,
            )
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
            keyGenerator.generateKey().also { generatedKey -> cachedKey = generatedKey }
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cn.ahlib.reservation.cookiecloud.config"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "1"
        const val SEPARATOR = "."
        const val ENCRYPTED_PART_COUNT = 3
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / 8
        const val KEY_SIZE_BITS = 256
        const val MAX_CIPHERTEXT_BYTES =
            COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES + GCM_TAG_SIZE_BYTES
        const val MAX_ENCRYPTED_CONFIG_LENGTH = 32 * 1_024
        const val AAD = "cn.ahlib.reservation.data.cookiecloud.config.v1"
        val AAD_BYTES: ByteArray = AAD.toByteArray(StandardCharsets.UTF_8)
        val KEY_STORE_LOCK = Any()
    }
}
