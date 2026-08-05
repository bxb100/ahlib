package cn.ahlib.reservation.data

import okhttp3.CookieJar

internal interface ClearableCookieJar : CookieJar {
    fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean

    fun saveAuthenticationTokenIfCurrent(
        expectedToken: String?,
        token: String,
        retentionDays: Int,
    ): AuthenticationTokenUpdateResult

    fun clear()
}

internal enum class AuthenticationTokenUpdateResult {
    SAVED,
    TOKEN_CHANGED,
    STORAGE_FAILED,
}
