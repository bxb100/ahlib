package cn.ahlib.reservation.data

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderQrCodeNativeRepositoryTest {
    @Test
    fun `native success is cached for the authenticated reader`() = runTest {
        val nativeClient = RecordingNativeClient(
            ReaderQrCodeResult.Success("opaque-reader-qr-content"),
        )
        val repository = ReaderQrCodeRepository(
            context = RuntimeEnvironment.getApplication(),
            nativeClient = nativeClient,
        )

        val result = repository.refreshAndCache(
            readerId = "reader-123",
            cookieHeader = "pc_token=secret",
        )

        assertTrue(result is ReaderQrCodeResult.Success)
        assertEquals("pc_token=secret", nativeClient.cookieHeader)
        assertEquals(
            "opaque-reader-qr-content",
            repository.cachedContent("reader-123"),
        )
        nativeClient.result = ReaderQrCodeResult.Failure(ReaderQrCodeFailure.NETWORK)
        repository.refreshAndCache(
            readerId = "reader-123",
            cookieHeader = "pc_token=secret",
        )
        assertEquals(
            "opaque-reader-qr-content",
            repository.cachedContent("reader-123"),
        )
        repository.clearCachedContent("reader-123")
    }

    private class RecordingNativeClient(
        var result: ReaderQrCodeResult,
    ) : ReaderQrNativeClient {
        var cookieHeader: String? = null

        override fun fetch(cookieHeader: String): ReaderQrCodeResult {
            this.cookieHeader = cookieHeader
            return result
        }
    }
}
