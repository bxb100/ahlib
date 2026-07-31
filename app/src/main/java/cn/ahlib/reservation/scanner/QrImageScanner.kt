package cn.ahlib.reservation.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.StringRes
import cn.ahlib.reservation.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
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
    val (inputImage, bitmapToRecycle) = try {
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: throw IOException("Could not open input stream for $uri")

            val longestSide = if (options.outWidth > options.outHeight) {
                options.outWidth
            } else {
                options.outHeight
            }
            var inSampleSize = 1
            while (longestSide / inSampleSize > 2048) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: throw IOException("Could not open input stream for $uri")

            val rotationDegrees = contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0

            InputImage.fromBitmap(bitmap, rotationDegrees) to bitmap
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
            bitmapToRecycle.recycle()
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
                bitmapToRecycle.recycle()
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
