package cn.ahlib.reservation.scanner

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.ahlib.reservation.R

sealed interface QrScannerError {
    data class InvalidCode(val reason: QrCodeParseError) : QrScannerError

    data class CameraFailure(val cause: Throwable) : QrScannerError
}

/**
 * Camera preview that detects and parses reservation QR codes. Scanning
 * pauses after every detection or camera failure; errors are surfaced only
 * through [onError] so the caller can present them without being covered by
 * other overlays. Toggling [enabled] resumes a paused scanner.
 */
@Composable
fun QrCodeScanner(
    enabled: Boolean,
    onScan: (ParsedQrCode, Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
    onError: (QrScannerError) -> Unit = {},
) {
    var isPaused by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        isPaused = false
    }

    QrCameraPreview(
        enabled = enabled && !isPaused,
        onQrCodeDetected = { rawValue, capturedImage ->
            isPaused = true
            when (val result = QrCodeParser.parse(rawValue)) {
                is QrCodeParseResult.Success ->
                    onScan(result.code, capturedImage)

                is QrCodeParseResult.Failure -> {
                    capturedImage?.recycle()
                    onError(QrScannerError.InvalidCode(result.error))
                }
            }
        },
        modifier = modifier,
        onError = { cause ->
            if (!isPaused) {
                isPaused = true
                onError(QrScannerError.CameraFailure(cause))
            }
        },
    )
}

@StringRes
internal fun QrScannerError.messageResource(): Int = when (this) {
    is QrScannerError.CameraFailure -> R.string.scanner_error_camera
    is QrScannerError.InvalidCode -> reason.messageResource()
}

@StringRes
internal fun QrCodeParseError.messageResource(): Int = when (this) {
    QrCodeParseError.EMPTY_VALUE -> R.string.scanner_error_empty_value
    QrCodeParseError.MALFORMED_URL -> R.string.scanner_error_malformed_url
    QrCodeParseError.UNSUPPORTED_SCHEME -> R.string.scanner_error_unsupported_scheme
    QrCodeParseError.UNTRUSTED_AUTHORITY -> R.string.scanner_error_untrusted_authority
    QrCodeParseError.MISSING_ROOM_ID -> R.string.scanner_error_missing_room_id
    QrCodeParseError.EMPTY_ROOM_ID -> R.string.scanner_error_empty_room_id
    QrCodeParseError.AMBIGUOUS_ROOM_ID -> R.string.scanner_error_ambiguous_room_id
    QrCodeParseError.AMBIGUOUS_SCAN_TYPE -> R.string.scanner_error_ambiguous_scan_type
}
