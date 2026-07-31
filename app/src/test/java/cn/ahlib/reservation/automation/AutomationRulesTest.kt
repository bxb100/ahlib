package cn.ahlib.reservation.automation

import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRulesTest {
    private val timeZone = TimeZone.getTimeZone("Asia/Shanghai")

    @Test
    fun matchingSlotUsesOnlyTheRequestedDate() {
        val target = AutoBookingTarget(
            roomId = "room",
            roomName = "Room",
            venueName = "Venue",
            startTime = "09:00",
            endTime = "12:00",
        )
        val selection = selectMatchingSlotForDate(
            availability = listOf(
                availableDay("2026-08-01", "09:00", "12:00"),
                availableDay("2026-08-03", "13:00", "17:00"),
                availableDay("2026-08-02", "09:00", "12:00"),
            ),
            target = target,
            targetDate = "2026-08-02",
        )

        assertEquals("2026-08-02", selection?.day?.date)
        assertEquals("09:00", selection?.slot?.startTime)
    }

    @Test
    fun bookingWindowTargetsTheDayAfterTomorrow() {
        val window = calculateAutoBookingWindow(
            nowMillis = timestamp("2026-08-01 07:00:10"),
            timeZone = timeZone,
        )

        assertEquals(
            timestamp("2026-08-01 06:59:50"),
            window.denseStartAtMillis,
        )
        assertEquals(
            timestamp("2026-08-01 07:01:00"),
            window.denseEndAtMillis,
        )
        assertEquals(timestamp("2026-08-01 10:00:00"), window.finishAtMillis)
        assertEquals("2026-08-03", window.targetDate)
    }

    @Test
    fun nextCheckStartsAtDenseWindow() {
        val nextCheckAt = calculateNextAutoBookingCheckAt(
            nowMillis = timestamp("2026-08-01 06:00:00"),
            allowImmediateDenseCheck = true,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-01 06:59:50"), nextCheckAt)
    }

    @Test
    fun activeDenseWindowCanScheduleAnImmediateCheck() {
        val nextCheckAt = calculateNextAutoBookingCheckAt(
            nowMillis = timestamp("2026-08-01 07:00:10"),
            allowImmediateDenseCheck = true,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-01 07:00:11"), nextCheckAt)
    }

    @Test
    fun denseDispatchSchedulesFirstSparseCheck() {
        val nextCheckAt = calculateNextAutoBookingCheckAt(
            nowMillis = timestamp("2026-08-01 06:59:50"),
            allowImmediateDenseCheck = false,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-01 07:04:00"), nextCheckAt)
    }

    @Test
    fun sparseChecksRepeatEveryThreeMinutes() {
        val nextCheckAt = calculateNextAutoBookingCheckAt(
            nowMillis = timestamp("2026-08-01 07:04:00"),
            allowImmediateDenseCheck = false,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-01 07:07:00"), nextCheckAt)
    }

    @Test
    fun checksStopAtTenAndResumeTheNextDay() {
        val nextCheckAt = calculateNextAutoBookingCheckAt(
            nowMillis = timestamp("2026-08-01 09:59:00"),
            allowImmediateDenseCheck = false,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 06:59:50"), nextCheckAt)
    }

    @Test
    fun matchingReservationNormalizesDateAndTime() {
        val target = AutoBookingTarget(
            roomId = "room",
            roomName = "Room",
            venueName = "Venue",
            startTime = "08:30:00",
            endTime = "22:00:00",
        )

        assertTrue(
            hasMatchingActiveReservation(
                records = listOf(
                    AppointmentRecord(
                        statusMerge = 1,
                        bookDate = " 2026/08/02 00:00:00 ",
                        startTime = "08:30",
                        endTime = "22:00",
                        roomName = " Room ",
                    ),
                ),
                target = target,
                targetDate = "2026-08-02",
            ),
        )
    }

    @Test
    fun matchingReservationRejectsInactiveOrDifferentBookings() {
        val target = AutoBookingTarget(
            roomId = "room",
            roomName = "Room",
            venueName = "Venue",
            startTime = "08:30:00",
            endTime = "22:00:00",
        )
        val records = listOf(
            AppointmentRecord(
                statusMerge = 0,
                bookDate = "2026-08-02",
                startTime = "08:30:00",
                endTime = "22:00:00",
                roomName = "Room",
            ),
            AppointmentRecord(
                statusMerge = 1,
                bookDate = "2026-08-02",
                startTime = "09:00:00",
                endTime = "22:00:00",
                roomName = "Room",
            ),
            AppointmentRecord(
                statusMerge = 1,
                bookDate = "2026-08-02",
                startTime = "08:30:00",
                endTime = "22:00:00",
                roomName = "Another Room",
            ),
        )

        assertFalse(
            hasMatchingActiveReservation(
                records = records,
                target = target,
                targetDate = "2026-08-02",
            ),
        )
    }

    @Test
    fun priorBookingUsesNineOClockDeadline() {
        val checkAt = calculateCancellationCheckAt(
            record = AppointmentRecord(
                id = "reservation",
                bookDate = "2026-08-02",
                createTime = "2026-08-01 18:30:00",
            ),
            leadMinutes = 5,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 08:55:00"), checkAt)
    }

    @Test
    fun futureReservationCreatedTodayUsesAdvanceBookingDeadline() {
        val deadlineAt = calculateSignInDeadlineAt(
            record = AppointmentRecord(
                bookDate = "2026/08/02 00:00:00",
                createTime = "2026-07-31 09:10:00",
            ),
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 09:00:00"), deadlineAt)
    }

    @Test
    fun sameDayBookingAfterEightThirtyUsesThirtyMinuteDeadline() {
        val deadlineAt = calculateSignInDeadlineAt(
            record = AppointmentRecord(
                bookDate = "2026-08-02",
                createTime = "2026-08-02 08:45:00",
            ),
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 09:15:00"), deadlineAt)
        assertEquals(
            "09:15",
            AppointmentRecord(
                bookDate = "2026-08-02",
                createTime = "2026-08-02 08:45:00",
            ).signInDeadlineForDisplay(timeZone),
        )
    }

    @Test
    fun sameDayBookingAtEightThirtyUsesNineOClockDeadline() {
        val deadlineAt = calculateSignInDeadlineAt(
            record = AppointmentRecord(
                bookDate = "2026-08-02",
                createTime = "2026-08-02 08:30:00",
            ),
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 09:00:00"), deadlineAt)
    }

    @Test
    fun sameDayBookingUsesThirtyMinutesWhenLaterThanNine() {
        val checkAt = calculateCancellationCheckAt(
            record = AppointmentRecord(
                id = "reservation",
                bookDate = "2026-08-02",
                createTime = "2026-08-02 09:10:00",
            ),
            leadMinutes = 5,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 09:35:00"), checkAt)
    }

    @Test
    fun sameDayBookingNeverUsesDeadlineBeforeNine() {
        val checkAt = calculateCancellationCheckAt(
            record = AppointmentRecord(
                id = "reservation",
                bookDate = "2026-08-02",
                createTime = "2026-08-02 08:00:00",
            ),
            leadMinutes = 5,
            timeZone = timeZone,
        )

        assertEquals(timestamp("2026-08-02 08:55:00"), checkAt)
    }

    @Test
    fun invalidBookingDateHasNoCancellationDeadline() {
        val checkAt = calculateCancellationCheckAt(
            record = AppointmentRecord(
                id = "reservation",
                bookDate = "not-a-date",
            ),
            leadMinutes = 5,
            timeZone = timeZone,
        )

        assertNull(checkAt)
    }

    private fun availableDay(
        date: String,
        startTime: String,
        endTime: String,
    ): AvailabilityDay = AvailabilityDay(
        date = date,
        list = listOf(
            AvailabilitySlot(
                id = "$date-$startTime",
                startTime = startTime,
                endTime = endTime,
                leftNum = 1,
                isOpen = 1,
                bookFlag = 1,
            ),
        ),
        isOpen = 1,
        totalLeftNum = 1,
    )

    private fun timestamp(value: String): Long =
        checkNotNull(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                isLenient = false
                timeZone = this@AutomationRulesTest.timeZone
            }.parse(value),
        ).time
}
