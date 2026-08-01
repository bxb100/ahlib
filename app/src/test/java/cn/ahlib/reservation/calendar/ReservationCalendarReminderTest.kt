package cn.ahlib.reservation.calendar

import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.SIGN_STATE_PENDING
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

    @Test
    fun pendingRecordCreatesReminderFromServerFields() {
        val reminder = createReservationCalendarReminder(
            record = AppointmentRecord(
                id = "reservation-1",
                signState = SIGN_STATE_PENDING,
                statusMerge = 1,
                roomName = "Room",
                venueName = "Venue",
                bookDate = "2026-08-02",
                startTime = "13:00",
                endTime = "17:00",
                createTime = "2026-08-01 18:30:00",
            ),
            requestedAtMillis = timestamp("2026-08-01 19:00:00"),
            timeZone = timeZone,
        )

        assertEquals("reservation-1", reminder?.id)
        assertEquals(
            "2026-08-02 13:00~17:00",
            reminder?.reservationDateTime,
        )
        assertEquals(
            timestamp("2026-08-02 08:00:00"),
            reminder?.eventStartAtMillis,
        )
        assertEquals(
            timestamp("2026-08-02 09:00:00"),
            reminder?.deadlineAtMillis,
        )
    }

    @Test
    fun nonPendingRecordDoesNotCreateReminder() {
        val reminder = createReservationCalendarReminder(
            record = AppointmentRecord(
                id = "reservation-1",
                signState = 1,
                statusMerge = 1,
                dateTime = "2026-08-02 13:00~17:00",
                createTime = "2026-08-01 18:30:00",
            ),
            requestedAtMillis = timestamp("2026-08-01 19:00:00"),
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
