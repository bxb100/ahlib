package cn.ahlib.reservation.calendar

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReservationCalendarReminderTest {
    private val timeZone = TimeZone.getTimeZone("Asia/Shanghai")

    @Test
    fun advanceBookingStartsOneHourBeforeNineOClockDeadline() {
        val reminder = createReservationCalendarReminder(
            roomName = "Room",
            venueName = "Venue",
            reservationDateTime = "2026-08-02 13:00~17:00",
            createdAtMillis = timestamp("2026-08-01 18:30:00"),
            timeZone = timeZone,
        )

        assertEquals(
            timestamp("2026-08-02 08:00:00"),
            reminder?.eventStartAtMillis,
        )
        assertEquals(
            timestamp("2026-08-02 08:30:00"),
            reminder?.eventEndAtMillis,
        )
        assertEquals(
            timestamp("2026-08-02 09:00:00"),
            reminder?.deadlineAtMillis,
        )
    }

    @Test
    fun lateSameDayBookingUsesTheNextUsefulMinute() {
        val reminder = createReservationCalendarReminder(
            roomName = "Room",
            venueName = "Venue",
            reservationDateTime = "2026-08-02 13:00~17:00",
            createdAtMillis = timestamp("2026-08-02 08:45:00"),
            timeZone = timeZone,
        )

        assertEquals(
            timestamp("2026-08-02 08:46:00"),
            reminder?.eventStartAtMillis,
        )
        assertEquals(
            timestamp("2026-08-02 09:15:00"),
            reminder?.eventEndAtMillis,
        )
        assertEquals(
            timestamp("2026-08-02 09:15:00"),
            reminder?.deadlineAtMillis,
        )
    }

    @Test
    fun invalidReservationDateDoesNotCreateReminder() {
        val reminder = createReservationCalendarReminder(
            roomName = "Room",
            venueName = "Venue",
            reservationDateTime = "not-a-date",
            createdAtMillis = timestamp("2026-08-01 18:30:00"),
            timeZone = timeZone,
        )

        assertNull(reminder)
    }

    private fun timestamp(value: String): Long =
        checkNotNull(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                isLenient = false
                timeZone = this@ReservationCalendarReminderTest.timeZone
            }.parse(value),
        ).time
}
