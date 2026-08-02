package cn.ahlib.reservation.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWebResourceCacheTest {
    @Test
    fun cachesVersionedStaticResources() {
        assertTrue(
            shouldCacheLibraryWebResource(
                "https://www.lib.ah.cn/static/js/app.d0558476.js",
            ),
        )
        assertTrue(
            shouldCacheLibraryWebResource(
                "https://www.lib.ah.cn/static/css/app.76ddc24f.css?theme=light",
            ),
        )
    }

    @Test
    fun cachesExternalStylesFontsAndImages() {
        assertTrue(
            shouldCacheLibraryWebResource(
                url = "https://fonts.googleapis.com/css?family=Noto+Sans+SC",
                headers = mapOf("Accept" to "text/css,*/*;q=0.1"),
            ),
        )
        assertTrue(
            shouldCacheLibraryWebResource(
                "https://fonts.gstatic.com/s/notosanssc/v1/font.woff2",
            ),
        )
        assertTrue(
            shouldCacheLibraryWebResource(
                url = "https://cdn.example.com/asset?id=logo",
                headers = mapOf("accept" to "image/avif,image/webp,image/*"),
            ),
        )
    }

    @Test
    fun doesNotCacheHtmlOrApiResponses() {
        assertFalse(
            shouldCacheLibraryWebResource(
                "https://www.lib.ah.cn/myLibrary?menuIndex=1",
            ),
        )
        assertFalse(
            shouldCacheLibraryWebResource(
                url = "https://www.lib.ah.cn/api-server/pc/room/list",
                headers = mapOf("Accept" to "application/json"),
            ),
        )
        assertFalse(
            shouldCacheLibraryWebResource(
                url = "https://www.lib.ah.cn/api-server/pc/avatar.png",
                headers = mapOf("Accept" to "image/png"),
            ),
        )
        assertFalse(
            shouldCacheLibraryWebResource(
                url = "https://www.lib.ah.cn/api-server",
                headers = mapOf("Accept" to "image/png"),
            ),
        )
    }

    @Test
    fun doesNotCacheFetchResponsesThatLookLikeStaticResources() {
        assertFalse(
            shouldCacheLibraryWebResource(
                url = "https://cdn.example.com/dynamic/avatar.png",
                headers = mapOf(
                    "Accept" to "image/png",
                    "Sec-Fetch-Dest" to "empty",
                ),
            ),
        )
    }

    @Test
    fun onlyCachesSecureResources() {
        assertFalse(
            shouldCacheLibraryWebResource(
                "http://www.lib.ah.cn/static/js/app.d0558476.js",
            ),
        )
        assertFalse(shouldCacheLibraryWebResource("not-a-url"))
    }

    @Test
    fun doesNotCacheResourcesWithCredentialsInUrl() {
        assertFalse(
            shouldCacheLibraryWebResource(
                "https://cdn.example.com/private/image.png?access_token=secret",
            ),
        )
        assertFalse(
            shouldCacheLibraryWebResource(
                "https://cdn.example.com/private/image.png?X-Amz-Signature=secret",
            ),
        )
    }
}
