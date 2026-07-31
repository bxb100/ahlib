package cn.ahlib.reservation.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInfoResultMapperTest {
    private val mapper = UserInfoResultMapper(GsonFactory.create())

    @Test
    fun map_treatsBooleanFalseAsAnonymousUser() {
        val result = mapper.map(
            ApiEnvelope(
                code = 200,
                data = JsonParser.parseString("false"),
            ),
        )

        assertTrue(result is ApiResult.Success)
        assertNull((result as ApiResult.Success).data)
    }

    @Test
    fun map_readsUserObject() {
        val result = mapper.map(
            ApiEnvelope(
                code = 200,
                data = JsonParser.parseString(
                    """{"id": 7, "mobileStatus": 0, "readerStatus": "2"}""",
                ),
            ),
        )

        assertTrue(result is ApiResult.Success)
        val user = (result as ApiResult.Success).data
        assertEquals("7", user?.id)
        assertEquals("0", user?.mobileStatus)
        assertEquals("2", user?.readerStatus)
    }
}
