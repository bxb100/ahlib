package cn.ahlib.reservation.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderQrCodeCodecTest {
    @Test
    fun encodedQrCodeContainsDarkModules() {
        val content = "https://example.com/reader?id=123&name=Jane%20Doe"
        val matrix = encodeReaderQrCode(content)

        assertTrue(matrix.width > 0)
        assertTrue(matrix.height > 0)
        assertTrue(matrix.topLeftOnBit != null)
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
