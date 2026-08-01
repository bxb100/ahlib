package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderQrNativeBridgeTest {
    @Test
    fun `native error kinds map to repository failures`() {
        assertEquals(
            ReaderQrCodeFailure.SESSION_EXPIRED,
            "session_expired".toReaderQrCodeFailure(),
        )
        assertEquals(
            ReaderQrCodeFailure.NETWORK,
            "network".toReaderQrCodeFailure(),
        )
        assertEquals(
            ReaderQrCodeFailure.TLS,
            "tls".toReaderQrCodeFailure(),
        )
        assertEquals(
            ReaderQrCodeFailure.HTTP,
            "http".toReaderQrCodeFailure(),
        )
        assertEquals(
            ReaderQrCodeFailure.BUSINESS,
            "business".toReaderQrCodeFailure(),
        )
    }

    @Test
    fun `unknown native error kind is rejected as an invalid response`() {
        assertEquals(
            ReaderQrCodeFailure.INVALID_RESPONSE,
            "unexpected".toReaderQrCodeFailure(),
        )
        assertEquals(
            ReaderQrCodeFailure.INVALID_RESPONSE,
            null.toReaderQrCodeFailure(),
        )
    }
}
