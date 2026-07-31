package cn.ahlib.reservation.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeParserTest {
    @Test
    fun `accepts id from library host`() {
        val rawValue = "https://www.lib.ah.cn/reservation?id=room-101"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
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
    fun `accepts roomId and scanType from hopshine host`() {
        val rawValue =
            "https://hdy.hopshine.net/scan?roomId=reading%20room&scanType=checkIn"

        val result = QrCodeParser.parse(rawValue)

        assertTrue(result is QrCodeParseResult.Success)
        val code = (result as QrCodeParseResult.Success).code
        assertEquals("reading room", code.roomId)
        assertEquals("checkIn", code.scanType)
        assertEquals(rawValue, code.rawValue)
    }

    @Test
    fun `accepts missing optional scanType`() {
        val result = QrCodeParser.parse(
            "https://hdy.hopshine.net/scan?roomId=42",
        )

        assertTrue(result is QrCodeParseResult.Success)
        assertNull((result as QrCodeParseResult.Success).code.scanType)
    }

    @Test
    fun `rejects non HTTPS scheme`() {
        val result = QrCodeParser.parse(
            "http://www.lib.ah.cn/reservation?id=room-101",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.UNSUPPORTED_SCHEME),
            result,
        )
    }

    @Test
    fun `accepts HTTPS payload without relying on a host allowlist`() {
        val rawValue = "https://booking.example/reservation?id=room-101"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
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
    fun `rejects HTTPS URL without a host`() {
        val result = QrCodeParser.parse(
            "https:///reservation?id=room-101",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.MALFORMED_URL),
            result,
        )
    }

    @Test
    fun `rejects empty id`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn/reservation?id=",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.EMPTY_ROOM_ID),
            result,
        )
    }

    @Test
    fun `rejects conflicting room id parameters`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn/reservation?id=one&roomId=two",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.AMBIGUOUS_ROOM_ID),
            result,
        )
    }

    @Test
    fun `rejects repeated roomId parameter`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn/reservation?roomId=one&roomId=one",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.AMBIGUOUS_ROOM_ID),
            result,
        )
    }

    @Test
    fun `rejects repeated scanType parameter`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn/reservation?id=one&scanType=in&scanType=out",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.AMBIGUOUS_SCAN_TYPE),
            result,
        )
    }

    @Test
    fun `rejects user info in authority`() {
        val result = QrCodeParser.parse(
            "https://someone@www.lib.ah.cn/reservation?id=room-101",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.UNTRUSTED_AUTHORITY),
            result,
        )
    }

    @Test
    fun `rejects non default HTTPS port`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn:8443/reservation?id=room-101",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.UNTRUSTED_AUTHORITY),
            result,
        )
    }

    @Test
    fun `accepts explicit default HTTPS port and uppercase authority`() {
        val rawValue = "HTTPS://WWW.LIB.AH.CN:443/reservation?id=room-101"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
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
    fun `accepts room id from hash route query`() {
        val rawValue =
            "https://booking.example/#/serveIndex?id=room-101&scanType=checkIn"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
                ParsedQrCode(
                    roomId = "room-101",
                    scanType = "checkIn",
                    rawValue = rawValue,
                ),
            ),
            result,
        )
    }

    @Test
    fun `accepts generated sign out payload`() {
        val rawValue =
            "https://www.lib.ah.cn/serveIndex?" +
                "idCategory=1729757107858&id=1856158531544678400&scanType=1"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
                ParsedQrCode(
                    roomId = "1856158531544678400",
                    scanType = "1",
                    rawValue = rawValue,
                ),
            ),
            result,
        )
    }

    @Test
    fun `rejects malformed percent encoding`() {
        val result = QrCodeParser.parse(
            "https://www.lib.ah.cn/reservation?id=%ZZ",
        )

        assertEquals(
            QrCodeParseResult.Failure(QrCodeParseError.MALFORMED_URL),
            result,
        )
    }

    @Test
    fun `does not split encoded query delimiter`() {
        val rawValue =
            "https://www.lib.ah.cn/reservation?id=room%26scanType%3Dforged"

        val result = QrCodeParser.parse(rawValue)

        assertEquals(
            QrCodeParseResult.Success(
                ParsedQrCode(
                    roomId = "room&scanType=forged",
                    scanType = null,
                    rawValue = rawValue,
                ),
            ),
            result,
        )
    }
}
