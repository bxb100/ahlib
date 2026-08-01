package cn.ahlib.reservation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.encodeReaderQrCode
import cn.ahlib.reservation.ui.theme.spacing
import com.google.zxing.common.BitMatrix
import kotlin.math.ceil
import kotlin.math.floor

@Composable
internal fun ReaderQrCodeUnbound(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(112.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.reader_qr_unbound),
                modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun ReaderQrCode(
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { encodeReaderQrCode(content) }
    val description = stringResource(R.string.reader_qr_description)

    Surface(
        onClick = onClick,
        modifier = modifier.size(112.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        NativeReaderQrCode(
            matrix = matrix,
            contentDescription = description,
            modifier = Modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.extraSmall),
        )
    }
}

@Composable
internal fun ReaderQrCodeViewer(
    content: String,
    onDismiss: () -> Unit,
    onClearBinding: () -> Unit,
) {
    val matrix = remember(content) { encodeReaderQrCode(content) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(stringResource(R.string.reader_qr_title))
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.close),
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = onClearBinding,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(stringResource(R.string.reader_qr_clear_binding))
                            }
                        },
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ZoomableReaderQrCode(
                        matrix = matrix,
                        contentDescription = stringResource(
                            R.string.reader_qr_description_expanded,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    Text(
                        text = content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.screen),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.reader_qr_expiry_notice),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = MaterialTheme.spacing.extraSmall,
                                start = MaterialTheme.spacing.screen,
                                end = MaterialTheme.spacing.screen,
                                bottom = MaterialTheme.spacing.medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableReaderQrCode(
    matrix: BitMatrix,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember(matrix) { mutableStateOf(MIN_QR_SCALE) }
    var offset by remember(matrix) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size -> viewportSize = size }
            .pointerInput(matrix, viewportSize) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { change -> change.pressed } >= 2) {
                            val previousScale = scale
                            val nextScale = (previousScale * event.calculateZoom())
                                .coerceIn(MIN_QR_SCALE, MAX_QR_SCALE)
                            val viewportCenter = Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f,
                            )
                            val gestureCenter = event.calculateCentroid(
                                useCurrent = false,
                            ) - viewportCenter
                            val scaleChange = nextScale / previousScale
                            val transformedOffset =
                                (offset - gestureCenter) * scaleChange +
                                    gestureCenter +
                                    event.calculatePan()
                            val maxOffsetX = viewportSize.width * (nextScale - 1f) / 2f
                            val maxOffsetY = viewportSize.height * (nextScale - 1f) / 2f

                            scale = nextScale
                            offset = if (nextScale == MIN_QR_SCALE) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = transformedOffset.x.coerceIn(
                                        -maxOffsetX,
                                        maxOffsetX,
                                    ),
                                    y = transformedOffset.y.coerceIn(
                                        -maxOffsetY,
                                        maxOffsetY,
                                    ),
                                )
                            }
                            event.changes.forEach { change -> change.consume() }
                        }
                    } while (event.changes.any { change -> change.pressed })
                }
            },
    ) {
        NativeReaderQrCode(
            matrix = matrix,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun NativeReaderQrCode(
    matrix: BitMatrix,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        drawReaderQrCode(matrix)
    }
}

private fun DrawScope.drawReaderQrCode(matrix: BitMatrix) {
    drawRect(Color.White)
    val moduleSize = minOf(
        size.width / matrix.width,
        size.height / matrix.height,
    )
    val renderedWidth = moduleSize * matrix.width
    val renderedHeight = moduleSize * matrix.height
    val left = (size.width - renderedWidth) / 2f
    val top = (size.height - renderedHeight) / 2f

    repeat(matrix.height) { y ->
        var x = 0
        while (x < matrix.width) {
            if (!matrix[x, y]) {
                x++
                continue
            }
            val runStart = x
            while (x < matrix.width && matrix[x, y]) {
                x++
            }
            val runLeft = left + floor(runStart * moduleSize)
            val runRight = left + ceil(x * moduleSize)
            val runTop = top + floor(y * moduleSize)
            val runBottom = top + ceil((y + 1) * moduleSize)
            drawRect(
                color = Color.Black,
                topLeft = Offset(runLeft, runTop),
                size = Size(runRight - runLeft, runBottom - runTop),
            )
        }
    }
}

private const val MIN_QR_SCALE = 1f
private const val MAX_QR_SCALE = 5f

@Composable
internal fun ReaderQrBindingDialog(
    pageUrl: String,
    isSaving: Boolean,
    errorText: String?,
    onPageUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.reader_qr_bind)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.reader_qr_link_instructions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pageUrl,
                    onValueChange = onPageUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text(stringResource(R.string.reader_qr_page_url)) },
                    placeholder = {
                        Text(stringResource(R.string.reader_qr_page_url_hint))
                    },
                    supportingText = errorText?.let { message ->
                        { Text(message) }
                    },
                    isError = errorText != null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                    ),
                    minLines = 3,
                    maxLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = pageUrl.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (isSaving) {
                            R.string.reader_qr_saving
                        } else {
                            R.string.reader_qr_save
                        },
                    ),
                    modifier = if (isSaving) {
                        Modifier.padding(start = MaterialTheme.spacing.small)
                    } else {
                        Modifier
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
