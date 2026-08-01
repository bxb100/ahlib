package cn.ahlib.reservation.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReaderQrCodeCodecTest {
    @Test
    fun encodedQrCodeCanBeDecodedWithoutChangingContent() {
        val content = "https://example.com/reader?id=123&name=Jane%20Doe"
        val matrix = encodeReaderQrCode(content)
        val scale = 8
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = IntArray(width * height) { index ->
            val x = index % width / scale
            val y = index / width / scale
            if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )

        try {
            assertEquals(content, decodeReaderQrCode(bitmap))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun blankContentCannotBeEncoded() {
        assertFalse(canEncodeReaderQrCode("   "))
    }

    @Test
    fun normalContentCanBeEncoded() {
        assertTrue(canEncodeReaderQrCode("reader-token"))
    }
}
