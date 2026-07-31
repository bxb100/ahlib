package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.isCancellationEligible
import cn.ahlib.reservation.data.isPendingCheckIn
import cn.ahlib.reservation.data.isSelectableForReservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationFiltersTest {
    @Test
    fun pendingCheckInRequiresAnActiveUnsignedReservation() {
        assertTrue(
            AppointmentRecord(
                signState = 0,
                statusMerge = 1,
            ).isPendingCheckIn(),
        )
        assertFalse(
            AppointmentRecord(
                signState = 1,
                statusMerge = 1,
            ).isPendingCheckIn(),
        )
        assertFalse(
            AppointmentRecord(
                signState = 0,
                statusMerge = 0,
            ).isPendingCheckIn(),
        )
    }

    @Test
    fun cancellationRequiresAnActiveReservationWithAnId() {
        assertTrue(
            AppointmentRecord(
                id = "reservation-id",
                status = 1,
                statusMerge = 1,
            ).isCancellationEligible(),
        )
        assertFalse(
            AppointmentRecord(
                id = "",
                status = 1,
                statusMerge = 1,
            ).isCancellationEligible(),
        )
        assertFalse(
            AppointmentRecord(
                id = "reservation-id",
                status = 0,
                statusMerge = 1,
            ).isCancellationEligible(),
        )
    }

    @Test
    fun availabilityDayRequiresAtLeastOneSelectableSlot() {
        val selectableSlot = AvailabilitySlot(
            isOpen = 1,
            bookFlag = 1,
            leftNum = 1,
        )
        val unavailableSlot = selectableSlot.copy(leftNum = 0)

        assertTrue(
            AvailabilityDay(
                isOpen = 1,
                totalLeftNum = 1,
                list = listOf(selectableSlot),
            ).isSelectableForReservation(),
        )
        assertFalse(
            AvailabilityDay(
                isOpen = 1,
                totalLeftNum = 0,
                list = listOf(selectableSlot),
            ).isSelectableForReservation(),
        )
        assertFalse(
            AvailabilityDay(
                isOpen = 1,
                totalLeftNum = 1,
                list = listOf(unavailableSlot),
            ).isSelectableForReservation(),
        )
    }

    @Test
    fun reservationsAreSortedByDateAndStartTimeAscending() {
        val reservations = listOf(
            AppointmentRecord(
                id = "latest",
                bookDate = "2026-08-03",
                startTime = "08:00",
            ),
            AppointmentRecord(
                id = "same-day-later",
                bookDate = "2026-08-01",
                startTime = "14:00",
            ),
            AppointmentRecord(
                id = "same-day-earlier",
                bookDate = "2026-08-01",
                startTime = "7:00",
            ),
        )

        assertEquals(
            listOf("same-day-earlier", "same-day-later", "latest"),
            sortReservationsByDate(reservations).map(AppointmentRecord::id),
        )
    }

    @Test
    fun reservationsWithoutValidDatesArePlacedLast() {
        val reservations = listOf(
            AppointmentRecord(id = "missing"),
            AppointmentRecord(
                id = "dated",
                bookDate = "2026/08/01",
            ),
            AppointmentRecord(
                id = "fallback",
                dateTime = "2026.08.02 09:00",
            ),
        )

        assertEquals(
            listOf("dated", "fallback", "missing"),
            sortReservationsByDate(reservations).map(AppointmentRecord::id),
        )
    }

    @Test
    fun reservationListDateDoesNotIncludeTime() {
        val record = AppointmentRecord(
            bookDate = "2026-08-01 09:30:00",
        )

        assertEquals("2026-08-01", record.reservationDateForDisplay())
    }

    @Test
    fun reservationListDateFallsBackToDateTime() {
        val record = AppointmentRecord(
            dateTime = "2026.08.02 09:00",
        )

        assertEquals("2026-08-02", record.reservationDateForDisplay())
    }
}
