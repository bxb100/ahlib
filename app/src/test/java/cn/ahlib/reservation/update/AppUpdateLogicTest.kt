package cn.ahlib.reservation.update

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateLogicTest {

    private val gson = Gson()

    @Test
    fun `remote run number greater than current is newer`() {
        assertTrue(isRemoteVersionNewer("1.0.0-main.8", "1.0.0-main.9"))
    }

    @Test
    fun `remote run number lower than current is not newer`() {
        assertFalse(isRemoteVersionNewer("1.0.0-main.9", "1.0.0-main.8"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isRemoteVersionNewer("1.0.0-main.8", "1.0.0-main.8"))
    }

    @Test
    fun `higher base version is newer`() {
        assertTrue(isRemoteVersionNewer("1.0.0-main.42", "1.1.0-main.2"))
    }

    @Test
    fun `debug build without run number sees released build as newer`() {
        assertTrue(isRemoteVersionNewer("1.0.0-debug", "1.0.0-main.1"))
    }

    @Test
    fun `debug build skips automatic update check`() {
        assertFalse(
            shouldRunUpdateCheck(
                userInitiated = false,
                isDebugBuild = true,
            ),
        )
    }

    @Test
    fun `debug build allows manual update check`() {
        assertTrue(
            shouldRunUpdateCheck(
                userInitiated = true,
                isDebugBuild = true,
            ),
        )
    }

    @Test
    fun `release build allows automatic update check`() {
        assertTrue(
            shouldRunUpdateCheck(
                userInitiated = false,
                isDebugBuild = false,
            ),
        )
    }

    @Test
    fun `unparsable versions are never newer`() {
        assertFalse(isRemoteVersionNewer("1.0.0-main.8", "unknown"))
        assertFalse(isRemoteVersionNewer("unknown", "1.0.0-main.8"))
    }

    @Test
    fun `release json maps to update info`() {
        val release = parseGitHubRelease(RELEASE_JSON, gson)

        assertNotNull(release)
        val info = release?.toAppUpdateInfo()
        assertNotNull(info)
        assertEquals("1.0.0-main.8", info?.versionName)
        assertEquals("v1.0.0-main.8", info?.tagName)
        assertEquals(
            "https://example.com/AhlibReservation-1.0.0-main.8.apk",
            info?.apkDownloadUrl,
        )
        assertEquals(27_693_045L, info?.apkSizeBytes)
        assertEquals(
            "https://example.com/AhlibReservation-1.0.0-main.8.apk.sha256",
            info?.checksumDownloadUrl,
        )
    }

    @Test
    fun `release without apk asset maps to null`() {
        val release = GitHubRelease(
            tagName = "v1.0.0-main.8",
            assets = listOf(
                GitHubReleaseAsset(
                    name = "AhlibReservation-1.0.0-main.8.apk.sha256",
                    size = 130L,
                    contentType = "application/octet-stream",
                    browserDownloadUrl = "https://example.com/checksum",
                ),
            ),
        )

        assertNull(release.toAppUpdateInfo())
    }

    @Test
    fun `draft and prerelease releases map to null`() {
        val asset = GitHubReleaseAsset(
            name = "app.apk",
            size = 1L,
            contentType = "application/vnd.android.package-archive",
            browserDownloadUrl = "https://example.com/app.apk",
        )

        assertNull(
            GitHubRelease(tagName = "v2", isDraft = true, assets = listOf(asset))
                .toAppUpdateInfo(),
        )
        assertNull(
            GitHubRelease(tagName = "v2", isPrerelease = true, assets = listOf(asset))
                .toAppUpdateInfo(),
        )
    }

    @Test
    fun `sha256sum output parses to hash token`() {
        val hash = "a".repeat(64)

        assertEquals(
            hash,
            parseSha256Checksum("$hash  app/build/outputs/apk/release/app.apk\n"),
        )
    }

    @Test
    fun `uppercase checksum is normalised to lowercase`() {
        val hash = "AB".repeat(32)

        assertEquals(hash.lowercase(), parseSha256Checksum(hash))
    }

    @Test
    fun `malformed checksum content parses to null`() {
        assertNull(parseSha256Checksum(""))
        assertNull(parseSha256Checksum("not-a-hash file.apk"))
        assertNull(parseSha256Checksum("abc123"))
    }

    private companion object {
        val RELEASE_JSON = """
            {
              "tag_name": "v1.0.0-main.8",
              "name": "AhlibReservation 1.0.0-main.8",
              "draft": false,
              "prerelease": false,
              "body": "**Full Changelog**: https://example.com/compare",
              "assets": [
                {
                  "name": "AhlibReservation-1.0.0-main.8.apk",
                  "size": 27693045,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://example.com/AhlibReservation-1.0.0-main.8.apk"
                },
                {
                  "name": "AhlibReservation-1.0.0-main.8.apk.sha256",
                  "size": 130,
                  "content_type": "application/octet-stream",
                  "browser_download_url": "https://example.com/AhlibReservation-1.0.0-main.8.apk.sha256"
                }
              ]
            }
        """.trimIndent()
    }
}
