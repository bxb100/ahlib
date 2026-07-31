package cn.ahlib.reservation.scanner

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ahlib.reservation.R

sealed interface QrScannerError {
    data class InvalidCode(val reason: QrCodeParseError) : QrScannerError

    data class CameraFailure(val cause: Throwable) : QrScannerError
}

@Composable
fun QrCodeScanner(
    enabled: Boolean,
    onScan: (ParsedQrCode, Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
    onError: (QrScannerError) -> Unit = {},
) {
    var isPaused by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<QrScannerError?>(null) }

    LaunchedEffect(enabled) {
        isPaused = false
        scannerError = null
    }

    Box(modifier = modifier) {
        QrCameraPreview(
            enabled = enabled && !isPaused,
            onQrCodeDetected = { rawValue, capturedImage ->
                isPaused = true
                when (val result = QrCodeParser.parse(rawValue)) {
                    is QrCodeParseResult.Success ->
                        onScan(result.code, capturedImage)

                    is QrCodeParseResult.Failure -> {
                        capturedImage?.recycle()
                        val error = QrScannerError.InvalidCode(result.error)
                        scannerError = error
                        onError(error)
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
            onError = { cause ->
                if (!isPaused) {
                    isPaused = true
                    val error = QrScannerError.CameraFailure(cause)
                    scannerError = error
                    onError(error)
                }
            },
        )

        scannerError?.let { error ->
            ScannerErrorCard(
                error = error,
                onRetry = {
                    scannerError = null
                    isPaused = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
private fun ScannerErrorCard(
    error: QrScannerError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(error.messageResource()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.scan_again))
            }
        }
    }
}

@StringRes
private fun QrScannerError.messageResource(): Int = when (this) {
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
