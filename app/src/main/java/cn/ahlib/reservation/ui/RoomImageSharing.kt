package cn.ahlib.reservation.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import cn.ahlib.reservation.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ShareableRoomImage(
    val uri: Uri,
    val mimeType: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShareableRoomImageBox(
    drawable: Drawable?,
    shareName: String?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPreparingShare by remember(drawable) { mutableStateOf(false) }
    val shareLabel = stringResource(R.string.share_room_image)
    val chooserTitle = stringResource(R.string.share_room_image_chooser)
    val preparingMessage = stringResource(R.string.room_image_share_preparing)
    val failureMessage = stringResource(R.string.room_image_share_failed)
    val shareModifier = if (drawable != null && shareName != null) {
        Modifier.combinedClickable(
            enabled = !isPreparingShare,
            onClick = {},
            onLongClickLabel = shareLabel,
            onLongClick = {
                isPreparingShare = true
                Toast.makeText(
                    context,
                    preparingMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                coroutineScope.launch {
                    try {
                        val shareableImage = withContext(Dispatchers.IO) {
                            cacheRoomImageForSharing(context, drawable)
                        }
                        openRoomImageShareSheet(
                            context = context,
                            image = shareableImage,
                            chooserTitle = chooserTitle,
                            roomName = shareName,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            failureMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } finally {
                        isPreparingShare = false
                    }
                }
            },
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(shareModifier),
        contentAlignment = contentAlignment,
    ) {
        content()
        if (isPreparingShare) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

internal fun cacheRoomImageForSharing(
    context: Context,
    drawable: Drawable,
): ShareableRoomImage {
    val directory = File(context.cacheDir, SHARE_DIRECTORY).apply {
        check(exists() || mkdirs()) { "Unable to create image share directory." }
    }
    directory.listFiles()
        .orEmpty()
        .filter(File::isFile)
        .forEach(File::delete)

    val file = File(directory, "$SHARE_FILE_PREFIX${System.currentTimeMillis()}.png")
    val bitmap = drawable.toBitmap()
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Unable to encode room image."
        }
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    return ShareableRoomImage(
        uri = uri,
        mimeType = "image/png",
    )
}

internal fun openRoomImageShareSheet(
    context: Context,
    image: ShareableRoomImage,
    chooserTitle: String,
    roomName: String,
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = image.mimeType
        putExtra(Intent.EXTRA_STREAM, image.uri)
        putExtra(Intent.EXTRA_SUBJECT, roomName)
        clipData = ClipData.newUri(
            context.contentResolver,
            roomName,
            image.uri,
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(sendIntent, chooserTitle)
    if (context !is Activity) {
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooserIntent)
}

private const val SHARE_DIRECTORY = "shared-images"
private const val SHARE_FILE_PREFIX = "room-image-"
