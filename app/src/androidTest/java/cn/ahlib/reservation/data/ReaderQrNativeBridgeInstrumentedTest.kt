package cn.ahlib.reservation.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderQrNativeBridgeInstrumentedTest {
    @Test
    fun nativeLibraryInitializesAndRejectsMissingAuthenticationCookie() {
        val client = JniReaderQrNativeClient(
            context = ApplicationProvider.getApplicationContext(),
            gson = GsonFactory.create(),
        )

        val result = client.fetch("other=value")

        assertTrue(result is ReaderQrCodeResult.Failure)
        assertEquals(
            ReaderQrCodeFailure.SESSION_EXPIRED,
            (result as ReaderQrCodeResult.Failure).reason,
        )
    }
}
