package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionValidationTest {
    @Test
    fun `authenticated response keeps the session`() {
        assertEquals(
            SessionValidation.AUTHENTICATED,
            ApiResult.Success(true).toSessionValidation(),
        )
    }

    @Test
    fun `negative response confirms expiration`() {
        assertEquals(
            SessionValidation.EXPIRED,
            ApiResult.Success(false).toSessionValidation(),
        )
    }

    @Test
    fun `session error confirms expiration`() {
        assertEquals(
            SessionValidation.EXPIRED,
            ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.SESSION_EXPIRED,
                    message = "Session expired",
                ),
            ).toSessionValidation(),
        )
    }

    @Test
    fun `network error leaves the session unchanged`() {
        assertEquals(
            SessionValidation.INCONCLUSIVE,
            ApiResult.Failure(
                ApiException(
                    kind = ApiErrorKind.NETWORK,
                    message = "Network unavailable",
                ),
            ).toSessionValidation(),
        )
    }
}
