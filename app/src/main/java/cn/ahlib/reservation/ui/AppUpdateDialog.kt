package cn.ahlib.reservation.ui

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.ahlib.reservation.R
import cn.ahlib.reservation.ui.theme.spacing
import cn.ahlib.reservation.update.AppUpdateInfo
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
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(
                                    R.string.app_update_downloading_percent,
                                    percent,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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


@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_TYPE_APPLIANCE)
@Composable
fun PreviewDialog(modifier: Modifier = Modifier) {
    AppUpdateDialog(
        dialogState = UpdateDialogState.Available(
            info = AppUpdateInfo(
                versionName = "1.0.0",
                apkSizeBytes = 1024 * 1024 * 5,
                releaseNotes = "This is a sample release note for the app update.",
                apkDownloadUrl = "https://example.com/app.apk",
                checksumDownloadUrl = "https://example.com/app.apk.sha256",
                tagName = "v1.0.0"
            ),
        ),
        onStartDownload = {},
        onInstall = {},
        onDismiss = {},
    )
}
