package cn.ahlib.reservation.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import cn.ahlib.reservation.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "QrImageScanner"

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
            val decoded = decodeQrImageForScanning(context.contentResolver, uri)
            InputImage.fromBitmap(
                decoded.bitmap,
                decoded.rotationDegrees,
            ) to decoded.bitmap
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Log.w(TAG, "Could not decode picked image: $uri", exception)
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
                    Log.w(TAG, "Barcode detection failed for: $uri", exception)
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

internal class DecodedQrImage(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
)

/**
 * Abstraction over [BitmapFactory.decodeStream] that lets tests enforce the
 * real device contract, where the bounds-only pass always returns null.
 */
internal fun interface BitmapStreamDecoder {
    fun decode(
        inputStream: InputStream,
        options: BitmapFactory.Options,
    ): Bitmap?
}

private val DefaultBitmapStreamDecoder = BitmapStreamDecoder { inputStream, options ->
    BitmapFactory.decodeStream(inputStream, null, options)
}

private const val MAX_QR_IMAGE_DIMENSION = 2048

internal fun decodeQrImageForScanning(
    contentResolver: ContentResolver,
    uri: Uri,
    decoder: BitmapStreamDecoder = DefaultBitmapStreamDecoder,
): DecodedQrImage {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    openImageInputStream(contentResolver, uri).use { inputStream ->
        // The result is deliberately ignored: with inJustDecodeBounds set,
        // decodeStream always returns null and only fills in the bounds.
        decoder.decode(inputStream, boundsOptions)
    }
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        throw IOException("Could not decode image bounds for $uri")
    }

    val longestSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
    var inSampleSize = 1
    while (longestSide / inSampleSize > MAX_QR_IMAGE_DIMENSION) {
        inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
    }
    val bitmap = openImageInputStream(contentResolver, uri).use { inputStream ->
        decoder.decode(inputStream, decodeOptions)
    } ?: throw IOException("Could not decode image for $uri")

    return DecodedQrImage(bitmap, readRotationDegrees(contentResolver, uri))
}

private fun openImageInputStream(
    contentResolver: ContentResolver,
    uri: Uri,
): InputStream = contentResolver.openInputStream(uri)
    ?: throw IOException("Could not open input stream for $uri")

private fun readRotationDegrees(
    contentResolver: ContentResolver,
    uri: Uri,
): Int = try {
    openImageInputStream(contentResolver, uri).use { inputStream ->
        val orientation = ExifInterface(inputStream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
} catch (_: Exception) {
    // Orientation metadata is best effort: images without a readable EXIF
    // segment must not fail the scan itself.
    0
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
