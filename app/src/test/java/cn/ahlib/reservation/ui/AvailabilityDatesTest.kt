package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.AvailabilityDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AvailabilityDatesTest {
    @Test
    fun missingDatesAreIncludedAsClosedDays() {
        val result = completeAvailabilityDates(
            availability = listOf(
                AvailabilityDay(
                    date = "2026-08-01",
                    isOpen = 1,
                    totalLeftNum = 1,
                ),
                AvailabilityDay(
                    date = "2026-08-03",
                    isOpen = 1,
                    totalLeftNum = 1,
                ),
            ),
            today = LocalDate.of(2026, 8, 1),
        )

        assertEquals("2026-08-01", result.first().date)
        assertEquals("2026-08-08", result.last().date)
        val missingDay = result.single { day -> day.date == "2026-08-02" }
        assertEquals(0, missingDay.isOpen)
        assertEquals(0, missingDay.totalLeftNum)
        assertEquals(0, missingDay.list.size)
    }

    @Test
    fun unavailableDatesRemainVisible() {
        val unavailable = AvailabilityDay(
            date = "2026-08-02",
            isOpen = 0,
            totalLeftNum = 0,
        )

        val result = completeAvailabilityDates(
            availability = listOf(unavailable),
            today = LocalDate.of(2026, 8, 2),
        )

        assertEquals(7, result.size)
        val retainedDay = result.single { day -> day.date == unavailable.date }
        assertEquals(unavailable.isOpen, retainedDay.isOpen)
        assertEquals(unavailable.totalLeftNum, retainedDay.totalLeftNum)
    }

    @Test
    fun datesStartTodayAndPastDatesAreRemoved() {
        val today = LocalDate.of(2026, 8, 1)
        val result = completeAvailabilityDates(
            availability = listOf(
                AvailabilityDay(
                    date = "2026-07-31",
                    isOpen = 1,
                    totalLeftNum = 1,
                ),
                AvailabilityDay(
                    date = "2026-08-03",
                    isOpen = 1,
                    totalLeftNum = 1,
                ),
            ),
            today = today,
        )

        assertEquals(today.toString(), result.first().date)
        assertEquals(0, result.first().isOpen)
        assertFalse(result.any { day -> day.date < today.toString() })
    }

    @Test
    fun bookDateIsUsedWhenDateIsMissing() {
        val result = completeAvailabilityDates(
            availability = listOf(
                AvailabilityDay(
                    date = "",
                    bookDate = "2026-08-02",
                    isOpen = 1,
                    totalLeftNum = 1,
                ),
            ),
            today = LocalDate.of(2026, 8, 2),
        )

        assertEquals("2026-08-02", result.first().date)
        assertFalse(result.any { day -> day.date < "2026-08-02" })
    }
}
