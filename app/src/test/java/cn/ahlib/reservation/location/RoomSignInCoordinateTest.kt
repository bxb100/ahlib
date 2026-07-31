package cn.ahlib.reservation.location

import cn.ahlib.reservation.data.RoomDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomSignInCoordinateTest {
    @Test
    fun `uses valid room coordinates unchanged`() {
        val coordinate = RoomDetail(
            latitude = 31.861184,
            longitude = 117.285692,
        ).roomSignInCoordinateOrNull()

        assertEquals(31.861184, coordinate?.latitude ?: 0.0, 0.0)
        assertEquals(117.285692, coordinate?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `rejects missing or invalid room coordinates`() {
        assertNull(RoomDetail(latitude = 31.861184).roomSignInCoordinateOrNull())
        assertNull(
            RoomDetail(
                latitude = 91.0,
                longitude = 117.285692,
            ).roomSignInCoordinateOrNull(),
        )
    }
}
