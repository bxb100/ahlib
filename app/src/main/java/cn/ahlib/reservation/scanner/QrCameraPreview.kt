package cn.ahlib.reservation.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrCameraPreview(
    enabled: Boolean,
    onQrCodeDetected: (String, Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCodeDetected by rememberUpdatedState(onQrCodeDetected)
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    var trackingGraphics by remember { mutableStateOf(emptyList<QrCodeGraphic>()) }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }
    val previewView = remember(context, cameraController) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            controller = cameraController
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.matchParentSize(),
        )
        QrGraphicOverlay(
            graphics = trackingGraphics,
            modifier = Modifier.matchParentSize(),
        )
    }

    DisposableEffect(enabled, lifecycleOwner, cameraController, previewView) {
        if (!enabled) {
            trackingGraphics = emptyList()
            return@DisposableEffect onDispose {}
        }

        val active = AtomicBoolean(true)
        val terminalDelivered = AtomicBoolean(false)
        val pendingDetection = AtomicReference<PendingQrDetection?>(null)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val mainHandler = Handler(Looper.getMainLooper())
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAllPotentialBarcodes()
                .build(),
        )
        var missedTrackingFrames = 0
        val deliverPendingDetection = Runnable {
            val detection = pendingDetection.getAndSet(null)
                ?: return@Runnable
            if (active.get()) {
                currentOnQrCodeDetected(
                    detection.rawValue,
                    detection.capturedImage,
                )
            } else {
                detection.capturedImage?.recycle()
            }
        }
        val deliverError: (Throwable) -> Unit = { throwable ->
            if (active.get() && terminalDelivered.compareAndSet(false, true)) {
                currentOnError(throwable)
            }
        }
        val analyzer = MlKitAnalyzer(
            listOf(barcodeScanner),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor,
        ) { result ->
            if (!active.get() || terminalDelivered.get()) {
                return@MlKitAnalyzer
            }
            result.getThrowable(barcodeScanner)?.let { throwable ->
                deliverError(throwable)
                return@MlKitAnalyzer
            }

            val barcodes = result.getValue(barcodeScanner).orEmpty()
            val detectedBarcode = barcodes.firstOrNull { barcode ->
                barcode.isConfirmedQrCode()
            }
            val currentGraphics = barcodes.mapNotNull { barcode ->
                barcode.boundingBox?.toCodeGraphic(
                    isConfirmed = barcode.isConfirmedQrCode(),
                )
            }
            if (currentGraphics.isNotEmpty()) {
                missedTrackingFrames = 0
                trackingGraphics = currentGraphics
            } else {
                missedTrackingFrames++
                if (
                    missedTrackingFrames >= QR_TRACKING_MISSED_FRAME_TOLERANCE &&
                    !terminalDelivered.get()
                ) {
                    trackingGraphics = emptyList()
                }
            }

            if (
                detectedBarcode != null &&
                terminalDelivered.compareAndSet(false, true)
            ) {
                scope.launch {
                    val capturedImage = captureQrCodeImage(
                        previewView = previewView,
                        boundingBox = detectedBarcode.boundingBox,
                    )
                    pendingDetection.set(
                        PendingQrDetection(
                            rawValue = detectedBarcode.rawValue.orEmpty(),
                            capturedImage = capturedImage,
                        ),
                    )
                    mainHandler.postDelayed(
                        deliverPendingDetection,
                        QR_TRACKING_CONFIRMATION_DELAY_MILLIS,
                    )
                }
            }
        }

        cameraController.setImageAnalysisAnalyzer(
            analysisExecutor,
            analyzer,
        )
        cameraController.initializationFuture.addListener(
            {
                if (!active.get()) {
                    return@addListener
                }
                try {
                    cameraController.cameraSelector = when {
                        cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> throw IllegalStateException("No camera is available")
                    }
                    cameraController.bindToLifecycle(lifecycleOwner)
                } catch (exception: Exception) {
                    deliverError(exception)
                }
            },
            mainExecutor,
        )

        onDispose {
            active.set(false)
            mainHandler.removeCallbacks(deliverPendingDetection)
            pendingDetection.getAndSet(null)?.capturedImage?.recycle()
            trackingGraphics = emptyList()
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }
}

private fun Rect.toCodeGraphic(
    isConfirmed: Boolean,
): QrCodeGraphic = QrCodeGraphic(
    left = left.toFloat(),
    top = top.toFloat(),
    right = right.toFloat(),
    bottom = bottom.toFloat(),
    isConfirmed = isConfirmed,
)

private fun Barcode.isConfirmedQrCode(): Boolean = isConfirmedQrCode(
    format = format,
    rawValue = rawValue,
)

internal fun isConfirmedQrCode(
    format: Int,
    rawValue: String?,
): Boolean = format == Barcode.FORMAT_QR_CODE && !rawValue.isNullOrBlank()

private data class PendingQrDetection(
    val rawValue: String,
    val capturedImage: Bitmap?,
)

internal data class QrCropBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal fun calculateQrCropBounds(
    imageWidth: Int,
    imageHeight: Int,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
): QrCropBounds? {
    if (
        imageWidth <= 0 ||
        imageHeight <= 0 ||
        right <= left ||
        bottom <= top
    ) {
        return null
    }
    val padding = max(right - left, bottom - top) / QR_CROP_PADDING_DIVISOR
    val cropLeft = (left - padding).coerceIn(0, imageWidth)
    val cropTop = (top - padding).coerceIn(0, imageHeight)
    val cropRight = (right + padding).coerceIn(0, imageWidth)
    val cropBottom = (bottom + padding).coerceIn(0, imageHeight)
    if (cropRight <= cropLeft || cropBottom <= cropTop) {
        return null
    }
    return QrCropBounds(
        left = cropLeft,
        top = cropTop,
        width = cropRight - cropLeft,
        height = cropBottom - cropTop,
    )
}

private suspend fun captureQrCodeImage(
    previewView: PreviewView,
    boundingBox: Rect?,
): Bitmap? {
    val box = boundingBox ?: return null
    val source = previewView.bitmap ?: return null
    val previewWidth = previewView.width
    val previewHeight = previewView.height
    return withContext(Dispatchers.Default) {
        val cropped = runCatching {
            if (previewWidth <= 0 || previewHeight <= 0) {
                return@runCatching null
            }
            val horizontalScale = source.width.toFloat() / previewWidth
            val verticalScale = source.height.toFloat() / previewHeight
            val bounds = calculateQrCropBounds(
                imageWidth = source.width,
                imageHeight = source.height,
                left = (box.left * horizontalScale).roundToInt(),
                top = (box.top * verticalScale).roundToInt(),
                right = (box.right * horizontalScale).roundToInt(),
                bottom = (box.bottom * verticalScale).roundToInt(),
            ) ?: return@runCatching null
            Bitmap.createBitmap(
                source,
                bounds.left,
                bounds.top,
                bounds.width,
                bounds.height,
            )
        }.getOrNull()
        if (cropped !== source) {
            source.recycle()
        }
        cropped
    }
}

private const val QR_CROP_PADDING_DIVISOR = 5
private const val QR_TRACKING_MISSED_FRAME_TOLERANCE = 3
private const val QR_TRACKING_CONFIRMATION_DELAY_MILLIS = 180L
