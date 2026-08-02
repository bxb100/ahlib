package cn.ahlib.reservation.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal fun encodeReaderQrCode(content: String): BitMatrix {
    require(content.isUsableReaderQrContent())
    return QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        1,
        1,
        mapOf(
            EncodeHintType.CHARACTER_SET to QR_CHARACTER_SET,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
        ),
    )
}

internal fun canEncodeReaderQrCode(content: String): Boolean {
    if (!content.isUsableReaderQrContent()) {
        return false
    }
    return try {
        encodeReaderQrCode(content)
        true
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: WriterException) {
        false
    }
}

internal fun String.isUsableReaderQrContent(): Boolean =
    isNotBlank() && length <= MAX_READER_QR_CONTENT_LENGTH

internal const val MAX_READER_QR_CONTENT_LENGTH = 4_096

private const val QR_CHARACTER_SET = "UTF-8"
private const val QR_QUIET_ZONE_MODULES = 4
