package cn.ahlib.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpacSupplementParserTest {
    private val parser = OpacSupplementParser()

    @Test
    fun `holdings parse locations and aggregate-ready counts`() {
        val json = """
            {
              "previews": {
                "1000051870": [
                  {
                    "callno": "TP312/B-651/2012",
                    "curlibName": "\u5b89\u5fbd\u7701\u9986",
                    "curlocalName": "\u5178\u501f\u5ba4",
                    "copycount": 2,
                    "loanableCount": 2
                  },
                  {
                    "callno": "TP312/B-651/2012",
                    "curlibName": "\u5b89\u5fbd\u7701\u9986",
                    "curlocalName": "\u5178\u9605\u5ba4",
                    "copycount": 1,
                    "loanableCount": 4
                  }
                ]
              }
            }
        """.trimIndent()

        val holdings = parser.parseHoldings(json)?.getValue("1000051870")

        assertEquals(2, holdings?.size)
        assertEquals(3, holdings?.sumOf(OpacHolding::totalCopies))
        assertEquals(3, holdings?.sumOf(OpacHolding::availableCopies))
        assertEquals("\u5178\u501f\u5ba4", holdings?.first()?.locationName)
    }

    @Test
    fun `empty previews is a valid empty holdings response`() {
        assertEquals(
            emptyMap<String, List<OpacHolding>>(),
            parser.parseHoldings("{\"previews\":{}}"),
        )
    }

    @Test
    fun `missing previews is rejected and malformed book is isolated`() {
        assertNull(parser.parseHoldings("{}"))
        assertEquals(
            mapOf<String, List<OpacHolding>?>("123" to null),
            parser.parseHoldings("{\"previews\":{\"123\":{}}}"),
        )
    }

    @Test
    fun `malformed holding invalidates only its book record`() {
        val parsed = parser.parseHoldings(
            """
                {
                  "previews": {
                    "bad": [{"copycount": 1}],
                    "good": [{"copycount": 1, "loanableCount": 1}]
                  }
                }
            """.trimIndent(),
        )

        assertNull(parsed?.get("bad"))
        assertEquals(1, parsed?.get("good")?.single()?.availableCopies)
    }

    @Test
    fun `covers prefer safe resource links from jsonp`() {
        val jsonp = """
            ({
              "result": [
                {
                  "isbn": "9787115211316",
                  "coverlink": "https://img3.doubanio.com/view/subject/m/public/s1.jpg",
                  "resourceLink": "https://book-resource.dataesb.com/cover/p/book.jpeg"
                },
                {
                  "isbn": "0321330242",
                  "coverlink": "http://example.com/insecure.jpg",
                  "resourceLink": "https://image-2.openbookscan.com.cn:1235/bookcover/book.jpg"
                },
                {
                  "isbn": "9787115211316",
                  "coverlink": "https://example.com/untrusted.jpg",
                  "resourceLink": ""
                }
              ]
            })
        """.trimIndent()

        assertEquals(
            mapOf(
                "9787115211316" to
                    "https://book-resource.dataesb.com/cover/p/book.jpeg",
                "0321330242" to
                    "https://image-2.openbookscan.com.cn:1235/bookcover/book.jpg",
            ),
            parser.parseCovers(jsonp),
        )
    }

    @Test
    fun `covers reject unsafe endpoints and use a safe fallback`() {
        val jsonp = """
            ({
              "result": [
                {
                  "isbn": "9787115211316",
                  "resourceLink": "http://book-resource.dataesb.com/cover.jpg"
                },
                {
                  "isbn": "0321330242",
                  "resourceLink": "https://book-resource.dataesb.com.evil.example/cover.jpg"
                },
                {
                  "isbn": "7505345524",
                  "resourceLink": "https://user:pass@book-resource.dataesb.com/cover.jpg"
                },
                {
                  "isbn": "097522980X",
                  "resourceLink": "https://book-resource.dataesb.com/cover.jpg#fragment"
                },
                {
                  "isbn": "0306406152",
                  "resourceLink": "https://book-resource.dataesb.com:1235/cover.jpg"
                },
                {
                  "isbn": "9787115546081",
                  "resourceLink": "http://example.com/unsafe.jpg",
                  "coverlink": "https://img3.doubanio.com/view/subject/m/public/s1.jpg"
                }
              ]
            })
        """.trimIndent()

        assertEquals(
            mapOf(
                "9787115546081" to
                    "https://img3.doubanio.com/view/subject/m/public/s1.jpg",
            ),
            parser.parseCovers(jsonp),
        )
    }

    @Test
    fun `isbn normalization validates structure and checksum`() {
        assertEquals("9787115211316", "9787115211316 :".normalizedOpacIsbn())
        assertEquals("7505345524", "7-5053-4552-4".normalizedOpacIsbn())
        assertEquals("0321330242", "ISBN-10: 0321330242".normalizedOpacIsbn())
        assertEquals("097522980X", "isbn 097522980x".normalizedOpacIsbn())
        assertNull("ISRC CN-E01-96-0131-0".normalizedOpacIsbn())
        assertNull("9787115211310".normalizedOpacIsbn())
        assertNull("0975229800".normalizedOpacIsbn())
        assertNull("4006381333931".normalizedOpacIsbn())
        assertNull("978711521131\u0666".normalizedOpacIsbn())
    }
}
