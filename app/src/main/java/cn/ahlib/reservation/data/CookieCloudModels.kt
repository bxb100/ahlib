package cn.ahlib.reservation.data

import com.google.gson.annotations.SerializedName
import java.net.URI

enum class CookieCloudCryptoType(
    val wireValue: String,
) {
    @SerializedName("legacy")
    LEGACY("legacy"),

    @SerializedName("aes-128-cbc-fixed")
    AES_128_CBC_FIXED("aes-128-cbc-fixed"),
    ;

    companion object {
        fun fromWireValue(value: String?): CookieCloudCryptoType? =
            entries.firstOrNull { cryptoType -> cryptoType.wireValue == value }
    }
}

data class CookieCloudConfig(
    val serverUrl: String,
    val userKey: String,
    val password: String,
    val cryptoType: CookieCloudCryptoType,
) {
    override fun toString(): String =
        "CookieCloudConfig(serverUrl=$serverUrl, userKey=<redacted>, " +
            "password=<redacted>, cryptoType=$cryptoType)"

    fun normalizedOrNull(): CookieCloudConfig? =
        normalizedOrNull(
            serverUrl = serverUrl,
            userKey = userKey,
            password = password,
            cryptoType = cryptoType,
        )

    companion object {
        fun normalizedOrNull(
            serverUrl: String,
            userKey: String,
            password: String,
            cryptoType: CookieCloudCryptoType,
        ): CookieCloudConfig? {
            val normalizedServerUrl = normalizeServerUrl(serverUrl) ?: return null
            val normalizedUserKey = normalizeUserKey(userKey) ?: return null
            if (
                password.isEmpty() ||
                !password.hasUtf8ByteLengthAtMost(MAX_PASSWORD_UTF8_BYTES)
            ) {
                return null
            }
            return CookieCloudConfig(
                serverUrl = normalizedServerUrl,
                userKey = normalizedUserKey,
                password = password,
                cryptoType = cryptoType,
            )
        }

        private fun normalizeServerUrl(value: String): String? {
            val trimmedValue = value.trim()
            if (
                trimmedValue.isEmpty() ||
                !trimmedValue.hasUtf8ByteLengthAtMost(MAX_SERVER_URL_UTF8_BYTES) ||
                trimmedValue.any(Character::isISOControl)
            ) {
                return null
            }

            val uri = try {
                URI(trimmedValue)
            } catch (_: Exception) {
                return null
            }
            if (
                uri.isOpaque ||
                !uri.scheme.equals(HTTPS_SCHEME, ignoreCase = true) ||
                uri.host.isNullOrEmpty() ||
                uri.rawUserInfo != null ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.port != -1 && uri.port !in MIN_PORT..MAX_PORT
            ) {
                return null
            }

            val rawPath = uri.rawPath.orEmpty()
            if (rawPath.isNotEmpty() && !rawPath.startsWith('/')) {
                return null
            }
            if (uri.path.orEmpty().split('/').any { segment -> segment == "." || segment == ".." }) {
                return null
            }

            val normalizedPath = rawPath.trimEnd('/')
            return buildString {
                append(HTTPS_SCHEME)
                append("://")
                append(uri.rawAuthority)
                append(normalizedPath)
            }
        }

        private fun normalizeUserKey(value: String): String? {
            val trimmedValue = value.trim()
            if (
                trimmedValue.isEmpty() ||
                !trimmedValue.hasUtf8ByteLengthAtMost(MAX_USER_KEY_UTF8_BYTES) ||
                trimmedValue == "." ||
                trimmedValue == ".." ||
                trimmedValue.any { character ->
                    Character.isISOControl(character) || character == '/' || character == '\\'
                }
            ) {
                return null
            }
            return trimmedValue
        }

        private const val HTTPS_SCHEME = "https"
        private const val MAX_SERVER_URL_UTF8_BYTES = 2_048
        private const val MAX_USER_KEY_UTF8_BYTES = 512
        private const val MAX_PASSWORD_UTF8_BYTES = 4_096
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535
    }
}

internal fun String.hasUtf8ByteLengthAtMost(maximumBytes: Int): Boolean {
    if (length > maximumBytes) {
        return false
    }
    val bytes = toByteArray(Charsets.UTF_8)
    return try {
        bytes.size <= maximumBytes
    } finally {
        bytes.fill(0)
    }
}

internal const val COOKIE_CLOUD_MAX_CONFIG_JSON_UTF8_BYTES = 16 * 1_024
