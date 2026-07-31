package cn.ahlib.reservation.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import cn.ahlib.reservation.BuildConfig
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateDialogState {
    val info: AppUpdateInfo

    data class Available(
        override val info: AppUpdateInfo,
    ) : UpdateDialogState

    data class Downloading(
        override val info: AppUpdateInfo,
        val progressPercent: Int?,
    ) : UpdateDialogState

    data class ReadyToInstall(
        override val info: AppUpdateInfo,
        val apkFile: File,
    ) : UpdateDialogState

    data class Failed(
        override val info: AppUpdateInfo,
    ) : UpdateDialogState
}

enum class UpdateNotice {
    UP_TO_DATE,
    CHECK_FAILED,
}

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val dialog: UpdateDialogState? = null,
    val notice: UpdateNotice? = null,
)

class AppUpdateManager(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val preferences by lazy {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var hasAutoChecked = false

    init {
        scope.launch(Dispatchers.IO) {
            updateDirectory().listFiles()?.forEach(File::delete)
        }
    }

    fun checkForUpdates(userInitiated: Boolean) {
        if (!userInitiated) {
            if (hasAutoChecked) {
                return
            }
            hasAutoChecked = true
        }
        if (checkJob?.isActive == true || downloadJob?.isActive == true) {
            return
        }
        checkJob = scope.launch(Dispatchers.IO) {
            if (userInitiated) {
                _state.update { state ->
                    state.copy(isChecking = true, notice = null)
                }
            }
            val update = try {
                fetchAvailableUpdate()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Update check failed", exception)
                _state.update { state ->
                    state.copy(
                        isChecking = false,
                        notice = if (userInitiated) {
                            UpdateNotice.CHECK_FAILED
                        } else {
                            state.notice
                        },
                    )
                }
                return@launch
            }
            _state.update { state ->
                when {
                    update == null -> state.copy(
                        isChecking = false,
                        notice = if (userInitiated) {
                            UpdateNotice.UP_TO_DATE
                        } else {
                            state.notice
                        },
                    )

                    !userInitiated && update.tagName == dismissedTag() -> state.copy(
                        isChecking = false,
                    )

                    state.dialog != null &&
                        state.dialog !is UpdateDialogState.Available -> state.copy(
                        isChecking = false,
                    )

                    else -> state.copy(
                        isChecking = false,
                        dialog = UpdateDialogState.Available(update),
                    )
                }
            }
        }
    }

    fun startDownload() {
        val info = when (val dialog = _state.value.dialog) {
            is UpdateDialogState.Available -> dialog.info
            is UpdateDialogState.Failed -> dialog.info
            else -> return
        }
        if (downloadJob?.isActive == true) {
            return
        }
        downloadJob = scope.launch(Dispatchers.IO) {
            _state.update { state ->
                state.copy(
                    dialog = UpdateDialogState.Downloading(info, progressPercent = null),
                )
            }
            val apkFile = File(updateDirectory(), info.apkFileName())
            try {
                val sha256 = downloadApk(info, apkFile)
                verifyChecksum(info, sha256)
                _state.update { state ->
                    state.copy(dialog = UpdateDialogState.ReadyToInstall(info, apkFile))
                }
                launchInstaller(apkFile)
            } catch (exception: CancellationException) {
                apkFile.delete()
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Update download failed", exception)
                apkFile.delete()
                _state.update { state ->
                    state.copy(dialog = UpdateDialogState.Failed(info))
                }
            }
        }
    }

    fun installReadyApk() {
        val dialog = _state.value.dialog as? UpdateDialogState.ReadyToInstall ?: return
        launchInstaller(dialog.apkFile)
    }

    fun dismissDialog() {
        when (val dialog = _state.value.dialog) {
            is UpdateDialogState.Available -> rememberDismissedTag(dialog.info.tagName)
            is UpdateDialogState.Downloading -> downloadJob?.cancel()
            else -> Unit
        }
        _state.update { state -> state.copy(dialog = null) }
    }

    fun consumeNotice() {
        _state.update { state -> state.copy(notice = null) }
    }

    private fun fetchAvailableUpdate(): AppUpdateInfo? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", GITHUB_ACCEPT_HEADER)
            .build()
        val json = httpClient.newCall(request).execute().use { response ->
            if (response.code == HTTP_NOT_FOUND) {
                return null
            }
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP ${response.code}")
            }
            response.body.string()
        }
        val release = parseGitHubRelease(json, gson) ?: return null
        val info = release.toAppUpdateInfo() ?: return null
        return info.takeIf { update ->
            isRemoteVersionNewer(BuildConfig.VERSION_NAME, update.versionName)
        }
    }

    private suspend fun downloadApk(info: AppUpdateInfo, target: File): String {
        val request = Request.Builder()
            .url(info.apkDownloadUrl)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP ${response.code}")
            }
            val body = response.body
            val totalBytes = body.contentLength()
                .takeIf { length -> length > 0 }
                ?: info.apkSizeBytes
            val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
            target.parentFile?.mkdirs()
            var downloadedBytes = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = (downloadedBytes * 100 / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                publishDownloadProgress(info, percent)
                            }
                        }
                    }
                }
            }
            return digest.digest()
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }

    private fun publishDownloadProgress(info: AppUpdateInfo, percent: Int) {
        _state.update { state ->
            val dialog = state.dialog
            if (dialog is UpdateDialogState.Downloading && dialog.info == info) {
                state.copy(dialog = dialog.copy(progressPercent = percent))
            } else {
                state
            }
        }
    }

    private fun verifyChecksum(info: AppUpdateInfo, actualSha256: String) {
        val checksumUrl = info.checksumDownloadUrl ?: return
        val request = Request.Builder()
            .url(checksumUrl)
            .build()
        val content = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP ${response.code}")
            }
            response.body.string()
        }
        val expectedSha256 = parseSha256Checksum(content)
            ?: throw IOException("Malformed checksum asset")
        if (!expectedSha256.equals(actualSha256, ignoreCase = true)) {
            throw IOException("APK checksum mismatch")
        }
    }

    private fun launchInstaller(apkFile: File) {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", appContext.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(settingsIntent)
            return
        }
        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}$FILE_PROVIDER_SUFFIX",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        appContext.startActivity(installIntent)
    }

    private fun updateDirectory(): File =
        File(appContext.cacheDir, UPDATE_DIRECTORY_NAME)

    private fun AppUpdateInfo.apkFileName(): String {
        val sanitizedVersion = versionName.replace(FILE_NAME_SANITIZER, "-")
        return "$APK_FILE_PREFIX$sanitizedVersion$APK_FILE_SUFFIX"
    }

    private fun dismissedTag(): String? =
        preferences.getString(KEY_DISMISSED_TAG, null)

    private fun rememberDismissedTag(tag: String) {
        preferences.edit { putString(KEY_DISMISSED_TAG, tag) }
    }

    private companion object {
        const val TAG = "AppUpdateManager"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/bxb100/ahlib/releases/latest"
        const val GITHUB_ACCEPT_HEADER = "application/vnd.github+json"
        const val PREFERENCES_NAME = "app_update"
        const val KEY_DISMISSED_TAG = "dismissed_tag"
        const val UPDATE_DIRECTORY_NAME = "updates"
        const val APK_FILE_PREFIX = "AhlibReservation-"
        const val APK_FILE_SUFFIX = ".apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val SHA_256_ALGORITHM = "SHA-256"
        const val HTTP_NOT_FOUND = 404
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        val FILE_NAME_SANITIZER = Regex("[^A-Za-z0-9._-]")
    }
}
