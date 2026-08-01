package cn.ahlib.reservation.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ahlib.reservation.R
import cn.ahlib.reservation.ui.theme.spacing
import cn.ahlib.reservation.update.UpdateDialogState

@Composable
fun AppUpdateDialog(
    dialogState: UpdateDialogState,
    onStartDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info = dialogState.info
    val isDownloading = dialogState is UpdateDialogState.Downloading
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.app_update_available_title, info.versionName))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                if (info.apkSizeBytes > 0) {
                    Text(
                        text = stringResource(
                            R.string.app_update_size,
                            Formatter.formatShortFileSize(context, info.apkSizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = info.releaseNotes.ifBlank {
                        stringResource(R.string.app_update_notes_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                )
                when (dialogState) {
                    is UpdateDialogState.Downloading -> {
                        val percent = dialogState.progressPercent
                        if (percent != null) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularWavyProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.app_update_downloading_percent,
                                    percent,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_update_downloading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is UpdateDialogState.ReadyToInstall -> Text(
                        text = stringResource(R.string.app_update_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is UpdateDialogState.Failed -> Text(
                        text = stringResource(R.string.app_update_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is UpdateDialogState.Available -> Unit
                }
            }
        },
        confirmButton = {
            when (dialogState) {
                is UpdateDialogState.Available -> Button(onClick = onStartDownload) {
                    Text(stringResource(R.string.app_update_download))
                }

                is UpdateDialogState.Downloading -> Unit

                is UpdateDialogState.ReadyToInstall -> Button(onClick = onInstall) {
                    Text(stringResource(R.string.app_update_install))
                }

                is UpdateDialogState.Failed -> Button(onClick = onStartDownload) {
                    Text(stringResource(R.string.retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (isDownloading) {
                            R.string.app_update_cancel_download
                        } else {
                            R.string.app_update_later
                        },
                    ),
                )
            }
        },
    )
}
