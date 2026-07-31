package cn.ahlib.reservation.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderQrCodeRepositoryTest {
    private val pageUrl =
        "https://opac.ahlib.com/opac/m/reader/qrcode".toHttpUrl()

    @Test
    fun extractsRelativeQrImageUrlFromImageSource() {
        val html = """
            <img src="/opac/reader/qrCodeImage?qrcode=example-token">
        """.trimIndent()

        assertEquals(
            "https://opac.ahlib.com/opac/reader/qrCodeImage?qrcode=example-token",
            extractReaderQrImageUrl(pageUrl, html),
        )
    }

    @Test
    fun decodesHtmlEntitiesInQrImageUrl() {
        val html = """
            <img data-src="/opac/reader/qrCodeImage?qrcode=example&amp;size=320">
        """.trimIndent()

        assertEquals(
            "https://opac.ahlib.com/opac/reader/qrCodeImage?qrcode=example&size=320",
            extractReaderQrImageUrl(pageUrl, html),
        )
    }

    @Test
    fun rejectsQrImageUrlFromAnotherHost() {
        val html = """
            <img src="https://example.com/opac/reader/qrCodeImage?qrcode=example">
        """.trimIndent()

        assertNull(extractReaderQrImageUrl(pageUrl, html))
    }

    @Test
    fun ignoresUnrelatedImages() {
        val html = """
            <img src="/opac/assets/logo.png">
        """.trimIndent()

        assertNull(extractReaderQrImageUrl(pageUrl, html))
    }
}
