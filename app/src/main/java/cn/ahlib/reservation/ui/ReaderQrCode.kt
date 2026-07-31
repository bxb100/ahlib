package cn.ahlib.reservation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import cn.ahlib.reservation.R
import cn.ahlib.reservation.ui.theme.spacing

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
internal fun ReaderQrCodeImage(
    imageUrl: String,
    onClearBinding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExpanded by rememberSaveable { mutableStateOf(false) }
    var imageState by remember(imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val request = rememberReaderQrImageRequest(imageUrl)

    Surface(
        onClick = { showExpanded = true },
        modifier = modifier.size(112.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.reader_qr_description),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.extraSmall),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                onState = { state -> imageState = state },
            )
            when (imageState) {
                AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading,
                -> CircularProgressIndicator(modifier = Modifier.size(24.dp))

                is AsyncImagePainter.State.Error -> Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is AsyncImagePainter.State.Success -> Unit
            }
        }
    }

    if (showExpanded) {
        AlertDialog(
            onDismissRequest = { showExpanded = false },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = stringResource(
                                R.string.reader_qr_description_expanded,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(MaterialTheme.spacing.medium),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                        )
                    }
                    Text(
                        text = stringResource(R.string.reader_qr_expiry_notice),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExpanded = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExpanded = false
                        onClearBinding()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.reader_qr_clear_binding))
                }
            },
        )
    }
}

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
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
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

@Composable
private fun rememberReaderQrImageRequest(imageUrl: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, imageUrl) {
        val headers = NetworkHeaders.Builder()
            .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", "https://opac.ahlib.com/opac/m/reader/qrcode")
            .set(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            )
            .build()
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .allowHardware(false)
            .memoryCacheKey("reader-qr:$imageUrl")
            .diskCacheKey("reader-qr:$imageUrl")
            .httpHeaders(headers)
            .build()
    }
}
