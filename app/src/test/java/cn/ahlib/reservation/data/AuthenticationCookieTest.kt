package cn.ahlib.reservation.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Cookie

class AuthenticationCookieTest {
    @Test
    fun `authentication token becomes a persistent secure cookie`() {
        val now = 1_700_000_000_000L

        val cookie = createAuthenticationCookie(
            token = " token-value ",
            retentionDays = 15,
            currentTimeMillis = now,
        )

        assertNotNull(cookie)
        checkNotNull(cookie)
        assertEquals("pc_token", cookie.name)
        assertEquals("token-value", cookie.value)
        assertEquals("www.lib.ah.cn", cookie.domain)
        assertEquals("/", cookie.path)
        assertEquals(now + TimeUnit.DAYS.toMillis(15), cookie.expiresAt)
        assertTrue(cookie.hostOnly)
        assertTrue(cookie.persistent)
        assertTrue(cookie.secure)
    }

    @Test
    fun `blank token is rejected`() {
        assertNull(createAuthenticationCookie(" ", retentionDays = 15))
    }

    @Test
    fun `nonpositive retention is rejected`() {
        assertNull(createAuthenticationCookie("token", retentionDays = 0))
    }

    @Test
    fun `session refresh keeps previous authentication expiry`() {
        val now = 1_700_000_000_000L
        val previous = checkNotNull(
            createAuthenticationCookie(
                token = "old-token",
                retentionDays = 15,
                currentTimeMillis = now,
            ),
        )
        val incoming = sessionAuthenticationCookie("new-token")

        assertEquals(
            previous.expiresAt,
            authenticationCookieExpiryOverride(incoming, previous, now),
        )
    }

    @Test
    fun `blank authentication refresh does not keep previous expiry`() {
        val now = 1_700_000_000_000L
        val previous = checkNotNull(
            createAuthenticationCookie(
                token = "old-token",
                retentionDays = 15,
                currentTimeMillis = now,
            ),
        )

        assertNull(
            authenticationCookieExpiryOverride(
                incoming = sessionAuthenticationCookie(""),
                previous = previous,
                currentTimeMillis = now,
            ),
        )
    }

    private fun sessionAuthenticationCookie(token: String): Cookie =
        Cookie.Builder()
            .name(AUTHENTICATION_COOKIE_NAME)
            .value(token)
            .hostOnlyDomain(AUTHENTICATION_COOKIE_DOMAIN)
            .path("/")
            .secure()
            .build()
}
