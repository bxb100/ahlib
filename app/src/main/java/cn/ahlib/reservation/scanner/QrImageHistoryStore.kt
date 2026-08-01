package cn.ahlib.reservation.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Keep
internal data class QrImageHistoryEntry(
    val uriString: String,
    val scannedAtMillis: Long,
)

internal class QrImageHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson by lazy { Gson() }
    private val capturedImageDirectory = File(
        appContext.filesDir,
        CAPTURED_IMAGE_DIRECTORY,
    )

    suspend fun load(): List<QrImageHistoryEntry> = withContext(Dispatchers.IO) {
        loadFromStorage()
    }

    private fun loadFromStorage(): List<QrImageHistoryEntry> {
        val serialized = preferences.getString(KEY_ENTRIES, null)
            ?: return emptyList()
        return runCatching {
            gson.fromJson(
                serialized,
                Array<QrImageHistoryEntry>::class.java,
            )
                ?.toList()
                .orEmpty()
                .filter { entry -> entry.uriString.isNotBlank() }
                .distinctBy(QrImageHistoryEntry::uriString)
                .sortedByDescending(QrImageHistoryEntry::scannedAtMillis)
                .take(QR_IMAGE_HISTORY_LIMIT)
        }.getOrElse {
            preferences.edit { remove(KEY_ENTRIES) }
            emptyList()
        }
    }

    suspend fun record(
        uri: Uri,
        scannedAtMillis: Long = System.currentTimeMillis(),
    ): List<QrImageHistoryEntry> = withContext(Dispatchers.IO) {
        try {
            persistReadPermission(uri)
            val previous = loadFromStorage()
            val updated = updateQrImageHistory(
                entries = previous,
                newEntry = QrImageHistoryEntry(
                    uriString = uri.toString(),
                    scannedAtMillis = scannedAtMillis,
                ),
            )
            releaseRemovedPermissions(previous, updated)
            save(updated)
            updated
        } catch (exception: Exception) {
            deleteManagedImage(uri)
            throw exception
        }
    }

    suspend fun saveCapturedQrImage(
        bitmap: Bitmap,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): Uri = withContext(Dispatchers.IO) {
        check(
            capturedImageDirectory.exists() ||
                capturedImageDirectory.mkdirs(),
        ) {
            "Unable to create the captured QR image directory"
        }
        val file = File(
            capturedImageDirectory,
            "qr-$capturedAtMillis-${System.nanoTime()}.jpg",
        )
        try {
            FileOutputStream(file).use { output ->
                check(
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_COMPRESSION_QUALITY,
                        output,
                    ),
                ) {
                    "Unable to encode the captured QR image"
                }
            }
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
        Uri.fromFile(file)
    }

    suspend fun remove(uriString: String): List<QrImageHistoryEntry> =
        withContext(Dispatchers.IO) {
            val previous = loadFromStorage()
            val updated = previous.filterNot { entry ->
                entry.uriString == uriString
            }
            releaseRemovedPermissions(previous, updated)
            save(updated)
            updated
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val previous = loadFromStorage()
        previous.forEach { entry ->
            releaseReadPermission(entry.uriString.toUri())
        }
        preferences.edit { remove(KEY_ENTRIES) }
    }

    private fun save(entries: List<QrImageHistoryEntry>) {
        preferences.edit {
            putString(KEY_ENTRIES, gson.toJson(entries))
        }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun releaseRemovedPermissions(
        previous: List<QrImageHistoryEntry>,
        updated: List<QrImageHistoryEntry>,
    ) {
        val retainedUris = updated
            .map(QrImageHistoryEntry::uriString)
            .toSet()
        previous
            .filterNot { entry -> entry.uriString in retainedUris }
            .forEach { entry ->
                releaseReadPermission(entry.uriString.toUri())
            }
    }

    private fun releaseReadPermission(uri: Uri) {
        deleteManagedImage(uri)
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun deleteManagedImage(uri: Uri) {
        if (uri.scheme != Uri.fromFile(File("")).scheme) {
            return
        }
        val path = uri.path ?: return
        runCatching {
            val directory = capturedImageDirectory.canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == directory) {
                file.delete()
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "qr_image_history"
        const val KEY_ENTRIES = "entries"
        const val CAPTURED_IMAGE_DIRECTORY = "qr-scan-history"
        const val JPEG_COMPRESSION_QUALITY = 85
    }
}

internal fun updateQrImageHistory(
    entries: List<QrImageHistoryEntry>,
    newEntry: QrImageHistoryEntry,
    limit: Int = QR_IMAGE_HISTORY_LIMIT,
): List<QrImageHistoryEntry> =
    (listOf(newEntry) + entries)
        .distinctBy(QrImageHistoryEntry::uriString)
        .sortedByDescending(QrImageHistoryEntry::scannedAtMillis)
        .take(limit.coerceAtLeast(0))

internal const val QR_IMAGE_HISTORY_LIMIT = 8
