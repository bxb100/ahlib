package cn.ahlib.reservation.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderQrCodeNativeRepositoryTest {
    @Test
    fun `native success is returned for the authenticated session`() = runTest {
        val nativeClient = RecordingNativeClient(
            ReaderQrCodeResult.Success("opaque-reader-qr-content"),
        )
        val repository = ReaderQrCodeRepository(nativeClient)

        val result = repository.refresh("pc_token=secret")

        assertTrue(result is ReaderQrCodeResult.Success)
        assertEquals("pc_token=secret", nativeClient.cookieHeader)
        assertEquals(
            "opaque-reader-qr-content",
            (result as ReaderQrCodeResult.Success).content,
        )
    }

    @Test
    fun `blank authentication cookie is rejected before native fetch`() = runTest {
        val nativeClient = RecordingNativeClient(
            ReaderQrCodeResult.Success("opaque-reader-qr-content"),
        )
        val repository = ReaderQrCodeRepository(nativeClient)

        val result = repository.refresh("  ")

        assertEquals(
            ReaderQrCodeResult.Failure(ReaderQrCodeFailure.SESSION_EXPIRED),
            result,
        )
        assertNull(nativeClient.cookieHeader)
    }

    @Test
    fun `invalid native content is rejected`() = runTest {
        val repository = ReaderQrCodeRepository(
            RecordingNativeClient(ReaderQrCodeResult.Success("  ")),
        )

        val result = repository.refresh("pc_token=secret")

        assertEquals(
            ReaderQrCodeResult.Failure(ReaderQrCodeFailure.QR_CONTENT_INVALID),
            result,
        )
    }

    private class RecordingNativeClient(
        private val result: ReaderQrCodeResult,
    ) : ReaderQrNativeClient {
        var cookieHeader: String? = null

        override fun fetch(cookieHeader: String): ReaderQrCodeResult {
            this.cookieHeader = cookieHeader
            return result
        }
    }
}
