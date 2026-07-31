package cn.ahlib.reservation.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImageScannerTest {
    @Test
    fun noQrValuesReturnsNotFound() {
        val result = parseQrImageRawValues(listOf(null, "", "   "))

        assertEquals(
            QrImageScanResult.Failure(QrImageScanError.NoQrCode),
            result,
        )
    }

    @Test
    fun validHttpsCodeIsReturned() {
        val rawValue = "https://www.lib.ah.cn/reservation?id=room-101"

        val result = parseQrImageRawValues(listOf(rawValue))

        assertEquals(
            QrImageScanResult.Success(
                ParsedQrCode(
                    roomId = "room-101",
                    scanType = null,
                    rawValue = rawValue,
                ),
            ),
            result,
        )
    }

    @Test
    fun validCodeIsPreferredWhenImageContainsMultipleQrCodes() {
        val result = parseQrImageRawValues(
            listOf(
                "https://example.com/reservation",
                "https://hdy.hopshine.net/scan?roomId=trusted",
            ),
        )

        assertTrue(result is QrImageScanResult.Success)
        assertEquals(
            "trusted",
            (result as QrImageScanResult.Success).code.roomId,
        )
    }

    @Test
    fun invalidCodePreservesValidationReason() {
        val result = parseQrImageRawValues(
            listOf("https://example.com/reservation"),
        )

        assertEquals(
            QrImageScanResult.Failure(
                QrImageScanError.InvalidCode(QrCodeParseError.MISSING_ROOM_ID),
            ),
            result,
        )
    }
}
