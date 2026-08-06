package cn.ahlib.reservation.data

import java.io.IOException
import javax.net.ssl.SSLException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpacHttpClientTest {
    @Test
    fun `search uses the anonymous title query contract`() {
        val requests = mutableListOf<Request>()
        val client = clientWithInterceptor { chain ->
            requests += chain.request()
            response(chain.request(), "<html>results</html>")
        }

        val result = OkHttpOpacClient(client).fetchSearch("Java & Kotlin", 3)

        assertEquals(
            OpacClientResult.Success("<html>results</html>"),
            result,
        )
        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("opac.ahlib.com", request.url.host)
        assertEquals("/opac/search", request.url.encodedPath)
        assertEquals("Java & Kotlin", request.url.queryParameter("q"))
        assertEquals("title", request.url.queryParameter("searchWay"))
        assertEquals("score", request.url.queryParameter("sortWay"))
        assertEquals("desc", request.url.queryParameter("sortOrder"))
        assertEquals("dim", request.url.queryParameter("scWay"))
        assertEquals("reader", request.url.queryParameter("searchSource"))
        assertEquals("3", request.url.queryParameter("page"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun `supplement requests use anonymous service contracts`() {
        val requests = mutableListOf<Request>()
        val client = clientWithInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = if (request.url.host == "opac.ahlib.com") {
                "{\"previews\":{}}"
            } else {
                "({\"result\":[]})"
            }
            response(request, body)
        }
        val opacClient = OkHttpOpacClient(client)

        assertTrue(opacClient.fetchHoldings("123,456") is OpacClientResult.Success)
        assertTrue(
            opacClient.fetchCovers("9787115211316,0321330242") is
                OpacClientResult.Success,
        )

        val holdingsRequest = requests[0]
        assertEquals("123,456", holdingsRequest.url.queryParameter("bookrecnos"))
        assertEquals("", holdingsRequest.url.queryParameter("curLibcodes"))
        assertEquals("json", holdingsRequest.url.queryParameter("return_fmt"))
        val coversRequest = requests[1]
        assertEquals("book-resource.dataesb.com", coversRequest.url.host)
        assertEquals("P1AH0551031", coversRequest.url.queryParameter("glc"))
        assertEquals("getImages", coversRequest.url.queryParameter("cmdACT"))
        assertEquals("0", coversRequest.url.queryParameter("type"))
        assertEquals(
            ",9787115211316,0321330242",
            coversRequest.url.queryParameter("isbns"),
        )
        requests.forEach { request ->
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
        }
    }

    @Test
    fun `invalid inputs are rejected before an HTTP call`() {
        var callCount = 0
        val client = clientWithInterceptor { chain ->
            callCount += 1
            response(chain.request(), "unused")
        }
        val opacClient = OkHttpOpacClient(client)

        assertInvalid(opacClient.fetchSearch(" ", 1))
        assertInvalid(opacClient.fetchSearch("Catalog", 0))
        assertInvalid(opacClient.fetchHoldings("123,123"))
        assertInvalid(opacClient.fetchHoldings("\u0661\u0662\u0663"))
        assertInvalid(opacClient.fetchCovers("9787115211310"))
        assertInvalid(opacClient.fetchCovers("9787115211316,9787115211316"))
        assertEquals(0, callCount)
    }

    @Test
    fun `transport and HTTP failures retain distinct reasons`() {
        val tlsClient = clientWithInterceptor { throw SSLException("tls") }
        val networkClient = clientWithInterceptor { throw IOException("offline") }
        val httpClient = clientWithInterceptor { chain ->
            response(chain.request(), "unavailable", code = 503)
        }

        assertEquals(
            OpacSearchFailure.TLS,
            (OkHttpOpacClient(tlsClient).fetchSearch("Catalog", 1) as
                OpacClientResult.Failure).reason,
        )
        assertEquals(
            OpacSearchFailure.NETWORK,
            (OkHttpOpacClient(networkClient).fetchSearch("Catalog", 1) as
                OpacClientResult.Failure).reason,
        )
        assertEquals(
            OpacSearchFailure.HTTP,
            (OkHttpOpacClient(httpClient).fetchSearch("Catalog", 1) as
                OpacClientResult.Failure).reason,
        )
    }

    @Test
    fun `malformed UTF eight response is rejected`() {
        val client = clientWithInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    byteArrayOf(0xC3.toByte()).toResponseBody(
                        "text/html".toMediaType(),
                    ),
                )
                .build()
        }

        assertInvalid(OkHttpOpacClient(client).fetchSearch("Catalog", 1))
    }

    private fun assertInvalid(result: OpacClientResult) {
        assertTrue(result is OpacClientResult.Failure)
        assertEquals(
            OpacSearchFailure.INVALID_RESPONSE,
            (result as OpacClientResult.Failure).reason,
        )
    }

    private fun clientWithInterceptor(
        interceptor: (Interceptor.Chain) -> Response,
    ): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(interceptor)
        .build()

    private fun response(
        request: Request,
        body: String,
        code: Int = 200,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Error")
        .body(body.toResponseBody("text/plain; charset=utf-8".toMediaType()))
        .build()
}
