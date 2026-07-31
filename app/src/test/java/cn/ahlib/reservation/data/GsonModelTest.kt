package cn.ahlib.reservation.data

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GsonModelTest {
    private val gson = GsonFactory.create()

    @Test
    fun appointmentRecord_acceptsStringAndNumericStateValues() {
        val type = object : TypeToken<ApiEnvelope<AppointmentRecord>>() {}.type
        val json = """
            {
              "code": "200",
              "data": {
                "id": 101,
                "bookingId": "slot-1",
                "signState": "1",
                "status": 2,
                "statusMerge": "3",
                "bookNum": "4",
                "canSign": "0"
              }
            }
        """.trimIndent()

        val envelope: ApiEnvelope<AppointmentRecord> = gson.fromJson(json, type)

        assertEquals(200, envelope.code)
        assertEquals("101", envelope.data?.id)
        assertEquals(1, envelope.data?.signState)
        assertEquals(2, envelope.data?.status)
        assertEquals(3, envelope.data?.statusMerge)
        assertEquals(4, envelope.data?.bookNum)
        assertEquals(0, envelope.data?.canSign)
    }

    @Test
    fun roomDetail_acceptsNumericStrings() {
        val room = gson.fromJson(
            """
                {
                  "id": 22,
                  "roomName": "Room",
                  "distance": "12.5",
                  "ableNum": "6",
                  "ableNums": 8
                }
            """.trimIndent(),
            RoomDetail::class.java,
        )

        assertEquals("22", room.id)
        assertEquals(12.5, room.distance!!, 0.0)
        assertEquals(6, room.ableNum)
        assertEquals(8, room.ableNums)
    }

    @Test
    fun categorySelector_recursesAndAcceptsNumericModelId() {
        val categories = gson.fromJson(
            """
                [
                  {
                    "id": "root",
                    "categoryName": "Service",
                    "childList": [
                      {
                        "id": "reservation",
                        "categoryName": "Reservation",
                        "idModel": 22,
                        "childList": []
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            Array<Category>::class.java,
        ).toList()

        val selected = categories.findReservationCategory()

        assertNotNull(selected)
        assertEquals("reservation", selected?.id)
    }
}
