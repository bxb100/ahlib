package cn.ahlib.reservation.data

import java.util.concurrent.TimeUnit
import okhttp3.Cookie

internal fun createAuthenticationCookie(
    token: String,
    retentionDays: Int,
    currentTimeMillis: Long = System.currentTimeMillis(),
): Cookie? {
    val normalizedToken = token.trim()
    if (normalizedToken.isEmpty() || retentionDays <= 0) {
        return null
    }
    val expiresAt = try {
        Math.addExact(
            currentTimeMillis,
            TimeUnit.DAYS.toMillis(retentionDays.toLong()),
        )
    } catch (exception: ArithmeticException) {
        return null
    }
    return try {
        Cookie.Builder()
            .name(AUTHENTICATION_COOKIE_NAME)
            .value(normalizedToken)
            .hostOnlyDomain(AUTHENTICATION_COOKIE_DOMAIN)
            .path("/")
            .expiresAt(expiresAt)
            .secure()
            .build()
    } catch (exception: IllegalArgumentException) {
        null
    }
}

internal fun authenticationCookieExpiryOverride(
    incoming: Cookie,
    previous: Cookie?,
    currentTimeMillis: Long = System.currentTimeMillis(),
): Long? {
    if (
        incoming.name != AUTHENTICATION_COOKIE_NAME ||
        incoming.value.isBlank() ||
        incoming.persistent ||
        previous == null ||
        !previous.persistent ||
        previous.expiresAt <= currentTimeMillis ||
        previous.name != incoming.name ||
        previous.domain != incoming.domain ||
        previous.path != incoming.path
    ) {
        return null
    }
    return previous.expiresAt
}

internal const val AUTHENTICATION_COOKIE_NAME = "pc_token"
internal const val AUTHENTICATION_COOKIE_DOMAIN = "www.lib.ah.cn"
