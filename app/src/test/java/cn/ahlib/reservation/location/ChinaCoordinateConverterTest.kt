package cn.ahlib.reservation.location

import org.junit.Assert.assertEquals
import org.junit.Test

class ChinaCoordinateConverterTest {
    @Test
    fun `converts Beijing coordinate to GCJ-02`() {
        val converted = ChinaCoordinateConverter.wgs84ToGcj02(
            latitude = 39.908823,
            longitude = 116.397470,
        )

        assertEquals(39.910226, converted.latitude, 0.000001)
        assertEquals(116.403714, converted.longitude, 0.000001)
    }

    @Test
    fun `leaves coordinate outside mainland China unchanged`() {
        val converted = ChinaCoordinateConverter.wgs84ToGcj02(
            latitude = 51.5074,
            longitude = -0.1278,
        )

        assertEquals(51.5074, converted.latitude, 0.0)
        assertEquals(-0.1278, converted.longitude, 0.0)
    }
}
