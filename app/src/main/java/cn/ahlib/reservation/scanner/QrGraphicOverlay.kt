package cn.ahlib.reservation.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal data class QrCodeGraphic(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isConfirmed: Boolean,
)

internal data class QrTrackingBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun calculateQrTrackingBounds(
    previewWidth: Float,
    previewHeight: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): QrTrackingBounds? {
    if (
        previewWidth <= 0f ||
        previewHeight <= 0f ||
        right <= left ||
        bottom <= top
    ) {
        return null
    }

    val clampedLeft = left.coerceIn(0f, previewWidth)
    val clampedTop = top.coerceIn(0f, previewHeight)
    val clampedRight = right.coerceIn(0f, previewWidth)
    val clampedBottom = bottom.coerceIn(0f, previewHeight)
    if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
        return null
    }
    return QrTrackingBounds(
        left = clampedLeft,
        top = clampedTop,
        right = clampedRight,
        bottom = clampedBottom,
    )
}

@Composable
internal fun QrGraphicOverlay(
    graphics: List<QrCodeGraphic>,
    modifier: Modifier = Modifier,
    trackingColor: Color = QR_HUD_TRACKING_COLOR,
    lockedColor: Color = QR_HUD_LOCKED_COLOR,
) {
    Canvas(modifier = modifier) {
        graphics.forEach { graphic ->
            val trackedBounds = calculateQrTrackingBounds(
                previewWidth = size.width,
                previewHeight = size.height,
                left = graphic.left,
                top = graphic.top,
                right = graphic.right,
                bottom = graphic.bottom,
            ) ?: return@forEach

            val trackedWidth = trackedBounds.right - trackedBounds.left
            val trackedHeight = trackedBounds.bottom - trackedBounds.top
            val padding = (
                min(trackedWidth, trackedHeight) * QR_TRACKING_PADDING_FRACTION
                ).coerceAtMost(QR_TRACKING_MAX_PADDING.toPx())
            val left = (trackedBounds.left - padding).coerceAtLeast(0f)
            val top = (trackedBounds.top - padding).coerceAtLeast(0f)
            val right = (trackedBounds.right + padding).coerceAtMost(size.width)
            val bottom = (trackedBounds.bottom + padding).coerceAtMost(size.height)
            val hudBounds = QrTrackingBounds(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            )
            val minimumDimension = min(right - left, bottom - top)
            val maximumCornerLength = min(
                QR_HUD_MAX_CORNER_LENGTH.toPx(),
                minimumDimension / 2f,
            )
            val minimumCornerLength = min(
                QR_HUD_MIN_CORNER_LENGTH.toPx(),
                maximumCornerLength,
            )
            val cornerLength = (
                minimumDimension * QR_HUD_CORNER_LENGTH_FRACTION
                ).coerceIn(minimumCornerLength, maximumCornerLength)
            val frameColor = if (graphic.isConfirmed) {
                lockedColor
            } else {
                trackingColor
            }
            drawHudCornerFrame(
                bounds = hudBounds,
                color = Color.Black.copy(alpha = QR_TRACKING_SHADOW_ALPHA),
                cornerLength = cornerLength,
                strokeWidth = QR_TRACKING_SHADOW_WIDTH.toPx(),
            )
            drawHudCornerFrame(
                bounds = hudBounds,
                color = frameColor,
                cornerLength = cornerLength,
                strokeWidth = QR_TRACKING_STROKE_WIDTH.toPx(),
            )
            if (graphic.isConfirmed) {
                drawHudLockTicks(
                    bounds = hudBounds,
                    color = Color.Black.copy(alpha = QR_TRACKING_SHADOW_ALPHA),
                    tickLength = QR_HUD_LOCK_TICK_LENGTH.toPx(),
                    strokeWidth = QR_TRACKING_SHADOW_WIDTH.toPx(),
                )
                drawHudLockTicks(
                    bounds = hudBounds,
                    color = frameColor,
                    tickLength = QR_HUD_LOCK_TICK_LENGTH.toPx(),
                    strokeWidth = QR_TRACKING_STROKE_WIDTH.toPx(),
                )
            }
        }
    }
}

private fun DrawScope.drawHudCornerFrame(
    bounds: QrTrackingBounds,
    color: Color,
    cornerLength: Float,
    strokeWidth: Float,
) {
    val path = Path().apply {
        moveTo(bounds.left + cornerLength, bounds.top)
        lineTo(bounds.left, bounds.top)
        lineTo(bounds.left, bounds.top + cornerLength)

        moveTo(bounds.right - cornerLength, bounds.top)
        lineTo(bounds.right, bounds.top)
        lineTo(bounds.right, bounds.top + cornerLength)

        moveTo(bounds.right, bounds.bottom - cornerLength)
        lineTo(bounds.right, bounds.bottom)
        lineTo(bounds.right - cornerLength, bounds.bottom)

        moveTo(bounds.left + cornerLength, bounds.bottom)
        lineTo(bounds.left, bounds.bottom)
        lineTo(bounds.left, bounds.bottom - cornerLength)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Square,
            join = StrokeJoin.Miter,
        ),
    )
}

private fun DrawScope.drawHudLockTicks(
    bounds: QrTrackingBounds,
    color: Color,
    tickLength: Float,
    strokeWidth: Float,
) {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    val strokeCap = StrokeCap.Square
    drawLine(
        color = color,
        start = Offset(centerX, bounds.top),
        end = Offset(centerX, bounds.top + tickLength),
        strokeWidth = strokeWidth,
        cap = strokeCap,
    )
    drawLine(
        color = color,
        start = Offset(bounds.right, centerY),
        end = Offset(bounds.right - tickLength, centerY),
        strokeWidth = strokeWidth,
        cap = strokeCap,
    )
    drawLine(
        color = color,
        start = Offset(centerX, bounds.bottom),
        end = Offset(centerX, bounds.bottom - tickLength),
        strokeWidth = strokeWidth,
        cap = strokeCap,
    )
    drawLine(
        color = color,
        start = Offset(bounds.left, centerY),
        end = Offset(bounds.left + tickLength, centerY),
        strokeWidth = strokeWidth,
        cap = strokeCap,
    )
}

private const val QR_TRACKING_PADDING_FRACTION = 0.08f
private const val QR_TRACKING_SHADOW_ALPHA = 0.55f
private const val QR_HUD_CORNER_LENGTH_FRACTION = 0.28f
private val QR_HUD_TRACKING_COLOR = Color(0xFF70FF6B)
private val QR_HUD_LOCKED_COLOR = Color(0xFFFF453A)
private val QR_TRACKING_MAX_PADDING = 12.dp
private val QR_TRACKING_SHADOW_WIDTH = 6.dp
private val QR_TRACKING_STROKE_WIDTH = 3.dp
private val QR_HUD_MIN_CORNER_LENGTH = 18.dp
private val QR_HUD_MAX_CORNER_LENGTH = 40.dp
private val QR_HUD_LOCK_TICK_LENGTH = 10.dp
