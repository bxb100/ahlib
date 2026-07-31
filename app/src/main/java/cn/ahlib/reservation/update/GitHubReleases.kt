package cn.ahlib.reservation.update

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val checksumDownloadUrl: String?,
)

internal data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("draft") val isDraft: Boolean = false,
    @SerializedName("prerelease") val isPrerelease: Boolean = false,
    @SerializedName("assets") val assets: List<GitHubReleaseAsset>? = null,
)

internal data class GitHubReleaseAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
)

internal fun parseGitHubRelease(json: String, gson: Gson): GitHubRelease? = try {
    gson.fromJson(json, GitHubRelease::class.java)
} catch (_: JsonSyntaxException) {
    null
}

internal fun GitHubRelease.toAppUpdateInfo(): AppUpdateInfo? {
    if (isDraft || isPrerelease) {
        return null
    }
    val tag = tagName?.trim().orEmpty()
    if (tag.isEmpty()) {
        return null
    }
    val releaseAssets = assets.orEmpty()
    val apkAsset = releaseAssets.firstOrNull { asset ->
        asset.contentType == APK_CONTENT_TYPE ||
            asset.name.orEmpty().endsWith(APK_SUFFIX, ignoreCase = true)
    } ?: return null
    val apkDownloadUrl = apkAsset.browserDownloadUrl?.trim().orEmpty()
    if (apkDownloadUrl.isEmpty()) {
        return null
    }
    val checksumAsset = releaseAssets.firstOrNull { asset ->
        asset.name == "${apkAsset.name}$CHECKSUM_SUFFIX"
    } ?: releaseAssets.firstOrNull { asset ->
        asset.name.orEmpty().endsWith(CHECKSUM_SUFFIX, ignoreCase = true)
    }
    return AppUpdateInfo(
        versionName = tag.removePrefix("v").removePrefix("V"),
        tagName = tag,
        releaseNotes = body?.trim().orEmpty(),
        apkDownloadUrl = apkDownloadUrl,
        apkSizeBytes = apkAsset.size,
        checksumDownloadUrl = checksumAsset?.browserDownloadUrl?.trim()
            ?.takeIf(String::isNotEmpty),
    )
}

internal fun isRemoteVersionNewer(
    currentVersion: String,
    remoteVersion: String,
): Boolean {
    val current = versionComponents(currentVersion)
    val remote = versionComponents(remoteVersion)
    if (current.isEmpty() || remote.isEmpty()) {
        return false
    }
    val length = maxOf(current.size, remote.size)
    for (index in 0 until length) {
        val currentPart = current.getOrElse(index) { 0L }
        val remotePart = remote.getOrElse(index) { 0L }
        if (remotePart != currentPart) {
            return remotePart > currentPart
        }
    }
    return false
}

internal fun parseSha256Checksum(content: String): String? =
    content.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.split(WHITESPACE_REGEX)
        ?.firstOrNull()
        ?.lowercase()
        ?.takeIf(SHA256_HEX_REGEX::matches)

private fun versionComponents(version: String): List<Long> =
    NUMBER_REGEX.findAll(version)
        .mapNotNull { match -> match.value.toLongOrNull() }
        .toList()

private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
private const val APK_SUFFIX = ".apk"
private const val CHECKSUM_SUFFIX = ".sha256"
private val NUMBER_REGEX = Regex("\\d+")
private val WHITESPACE_REGEX = Regex("\\s+")
private val SHA256_HEX_REGEX = Regex("[0-9a-f]{64}")
