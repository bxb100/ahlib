package cn.ahlib.reservation.ui

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryImageRequestTest {
    @Test
    fun retryParametersPreserveExistingUrlParts() {
        val result = "https://www.lib.ah.cn/image.png?size=large#preview"
            .withLibraryImageRetryParameters(2)
            .toHttpUrl()

        assertEquals("large", result.queryParameter("size"))
        assertEquals("2", result.queryParameter("_app_image"))
        assertEquals("2", result.queryParameter("_app_image_retry"))
        assertEquals("preview", result.fragment)
    }

    @Test
    fun negativeRetryAttemptIsClamped() {
        val result = "https://www.lib.ah.cn/image.png"
            .withLibraryImageRetryParameters(-1)
            .toHttpUrl()

        assertEquals("0", result.queryParameter("_app_image_retry"))
    }
}
