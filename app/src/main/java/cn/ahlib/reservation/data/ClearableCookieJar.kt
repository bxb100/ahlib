package cn.ahlib.reservation.data

import okhttp3.CookieJar

internal interface ClearableCookieJar : CookieJar {
    fun saveAuthenticationToken(token: String, retentionDays: Int): Boolean

    fun clear()
}
