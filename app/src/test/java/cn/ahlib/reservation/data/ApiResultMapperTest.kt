package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultMapperTest {
    @Test
    fun required_returnsDataOnlyForCode200() {
        val result = ApiResultMapper.required(
            ApiEnvelope(
                code = 200,
                data = Captcha(img = "image", uniCode = "challenge"),
            ),
        )

        assertTrue(result is ApiResult.Success)
        assertEquals("challenge", (result as ApiResult.Success).data.uniCode)
    }

    @Test
    fun required_rejectsMissingDataEvenForCode200() {
        val result = ApiResultMapper.required<Captcha>(
            ApiEnvelope(code = 200, data = null),
        )

        assertTrue(result is ApiResult.Failure)
        assertEquals(
            ApiErrorKind.SERIALIZATION,
            (result as ApiResult.Failure).exception.kind,
        )
    }

    @Test
    fun unit_mapsBusiness401ToSessionExpired() {
        val result = ApiResultMapper.unit(
            ApiEnvelope<Any>(
                code = 401,
                errorMsg = "Authentication required",
            ),
        )

        assertTrue(result is ApiResult.Failure)
        val exception = (result as ApiResult.Failure).exception
        assertEquals(ApiErrorKind.SESSION_EXPIRED, exception.kind)
        assertTrue(exception.isSessionExpired)
        assertEquals(401, exception.code)
    }

    @Test
    fun unit_mapsNotLoggedInBusiness500ToSessionExpired() {
        val result = ApiResultMapper.unit(
            ApiEnvelope<Any>(
                code = 500,
                errorMsg = "\u7528\u6237\u672a\u767b\u5f55",
            ),
        )

        assertTrue(result is ApiResult.Failure)
        val exception = (result as ApiResult.Failure).exception
        assertEquals(ApiErrorKind.SESSION_EXPIRED, exception.kind)
        assertEquals(500, exception.code)
    }

    @Test
    fun unit_preservesBusinessErrorMessage() {
        val result = ApiResultMapper.unit(
            ApiEnvelope<Any>(
                code = 422,
                errorMsg = "Reservation is unavailable",
            ),
        )

        assertTrue(result is ApiResult.Failure)
        val exception = (result as ApiResult.Failure).exception
        assertEquals(ApiErrorKind.BUSINESS, exception.kind)
        assertEquals("Reservation is unavailable", exception.message)
    }
}
