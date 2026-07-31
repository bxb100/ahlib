package cn.ahlib.reservation.scanner

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import cn.ahlib.reservation.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface QrImageScanError {
    data object NoQrCode : QrImageScanError

    data class InvalidCode(val reason: QrCodeParseError) : QrImageScanError

    data class ImageFailure(val cause: Throwable) : QrImageScanError
}

sealed interface QrImageScanResult {
    data class Success(val code: ParsedQrCode) : QrImageScanResult

    data class Failure(val error: QrImageScanError) : QrImageScanResult
}

suspend fun scanQrCodeFromImage(
    context: Context,
    uri: Uri,
): QrImageScanResult {
    val inputImage = try {
        withContext(Dispatchers.IO) {
            InputImage.fromFilePath(context.applicationContext, uri)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        return QrImageScanResult.Failure(
            QrImageScanError.ImageFailure(exception),
        )
    }

    return suspendCancellableCoroutine { continuation ->
        val scanner = try {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        } catch (exception: Exception) {
            continuation.resume(
                QrImageScanResult.Failure(
                    QrImageScanError.ImageFailure(exception),
                ),
            )
            return@suspendCancellableCoroutine
        }
        val finished = AtomicBoolean(false)
        val complete: (QrImageScanResult) -> Unit = { result ->
            if (finished.compareAndSet(false, true)) {
                scanner.close()
                continuation.resume(result)
            }
        }

        continuation.invokeOnCancellation {
            if (finished.compareAndSet(false, true)) {
                scanner.close()
            }
        }

        try {
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    complete(
                        parseQrImageRawValues(
                            barcodes.map { barcode -> barcode.rawValue },
                        ),
                    )
                }
                .addOnFailureListener { exception ->
                    complete(
                        QrImageScanResult.Failure(
                            QrImageScanError.ImageFailure(exception),
                        ),
                    )
                }
        } catch (exception: Exception) {
            complete(
                QrImageScanResult.Failure(
                    QrImageScanError.ImageFailure(exception),
                ),
            )
        }
    }
}

internal fun parseQrImageRawValues(
    rawValues: List<String?>,
): QrImageScanResult {
    val parseResults = rawValues
        .asSequence()
        .filterNotNull()
        .filter(String::isNotBlank)
        .map(QrCodeParser::parse)
        .toList()

    parseResults
        .filterIsInstance<QrCodeParseResult.Success>()
        .firstOrNull()
        ?.let { result ->
            return QrImageScanResult.Success(result.code)
        }

    val parseError = parseResults
        .filterIsInstance<QrCodeParseResult.Failure>()
        .firstOrNull()
        ?.error
    return if (parseError == null) {
        QrImageScanResult.Failure(QrImageScanError.NoQrCode)
    } else {
        QrImageScanResult.Failure(
            QrImageScanError.InvalidCode(parseError),
        )
    }
}

@StringRes
fun QrImageScanError.messageResource(): Int = when (this) {
    QrImageScanError.NoQrCode -> R.string.scanner_image_no_qr_code
    is QrImageScanError.InvalidCode -> reason.messageResource()
    is QrImageScanError.ImageFailure -> R.string.scanner_image_load_error
}
