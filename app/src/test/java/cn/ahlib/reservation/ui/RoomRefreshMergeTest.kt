package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.RoomSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RoomRefreshMergeTest {
    @Test
    fun unchangedRefreshReusesTheCurrentList() {
        val current = listOf(
            room(id = "first", available = 2),
            room(id = "second", available = 4),
        )
        val refreshed = current.map { room -> room.copy() }

        val merged = mergeRefreshedRooms(current, refreshed)

        assertSame(current, merged)
        assertSame(current[0], merged[0])
        assertSame(current[1], merged[1])
    }

    @Test
    fun refreshReplacesOnlyChangedRooms() {
        val current = listOf(
            room(id = "first", available = 2),
            room(id = "second", available = 4),
        )
        val refreshed = listOf(
            room(id = "first", available = 1),
            room(id = "second", available = 4),
        )

        val merged = mergeRefreshedRooms(current, refreshed)

        assertNotSame(current, merged)
        assertNotSame(current[0], merged[0])
        assertSame(current[1], merged[1])
        assertEquals(1, merged[0].ableNum)
    }

    @Test
    fun refreshUsesTheLatestOrderAndMembership() {
        val currentFirst = room(id = "first", available = 2)
        val currentSecond = room(id = "second", available = 4)
        val added = room(id = "third", available = 6)

        val merged = mergeRefreshedRooms(
            current = listOf(currentFirst, currentSecond),
            refreshed = listOf(currentSecond.copy(), added),
        )

        assertEquals(listOf("second", "third"), merged.map(RoomSummary::id))
        assertSame(currentSecond, merged[0])
        assertSame(added, merged[1])
    }

    private fun room(id: String, available: Int): RoomSummary =
        RoomSummary(
            id = id,
            roomName = "Room $id",
            totalNum = 10,
            ableNum = available,
        )
}
