package cn.ahlib.reservation.automation

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AutomaticSignOutQrStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(
        appContext.filesDir,
        IMAGE_DIRECTORY,
    )
    private val imageFile = File(imageDirectory, IMAGE_FILE_NAME)

    suspend fun replaceImage(sourceUri: Uri): Uri = withContext(Dispatchers.IO) {
        check(imageDirectory.exists() || imageDirectory.mkdirs()) {
            "Unable to create the automatic sign-out image directory"
        }
        val temporaryFile = File.createTempFile(
            "sign-out-qr-",
            ".image",
            imageDirectory,
        )
        try {
            val input = appContext.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Unable to open the selected QR image")
            input.use { source ->
                FileOutputStream(temporaryFile).use { destination ->
                    source.copyTo(destination)
                }
            }
            Files.move(
                temporaryFile.toPath(),
                imageFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            Uri.fromFile(imageFile)
        } catch (exception: Exception) {
            temporaryFile.delete()
            throw exception
        }
    }

    fun clear() {
        runCatching { imageFile.delete() }
    }

    private companion object {
        const val IMAGE_DIRECTORY = "automation"
        const val IMAGE_FILE_NAME = "automatic-sign-out-qr.image"
    }
}
