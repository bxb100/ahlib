package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.SIGN_STATE_PENDING
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_IN
import cn.ahlib.reservation.data.SIGN_STATE_SIGNED_OUT
import org.junit.Assert.assertEquals
import org.junit.Test

class ReservationStatusFilterTest {

    @Test
    fun `pending record with active status maps to pending check in`() {
        val record = AppointmentRecord(signState = SIGN_STATE_PENDING, statusMerge = 1)

        assertEquals(
            ReservationStatusFilter.PENDING_CHECK_IN,
            record.reservationStatusFilter(),
        )
    }

    @Test
    fun `signed in record maps to signed in`() {
        val record = AppointmentRecord(signState = SIGN_STATE_SIGNED_IN, statusMerge = 1)

        assertEquals(
            ReservationStatusFilter.SIGNED_IN,
            record.reservationStatusFilter(),
        )
    }

    @Test
    fun `signed out record maps to signed out`() {
        val record = AppointmentRecord(signState = SIGN_STATE_SIGNED_OUT, statusMerge = 1)

        assertEquals(
            ReservationStatusFilter.SIGNED_OUT,
            record.reservationStatusFilter(),
        )
    }

    @Test
    fun `pending record with inactive status maps to other`() {
        val record = AppointmentRecord(signState = SIGN_STATE_PENDING, statusMerge = 2)

        assertEquals(
            ReservationStatusFilter.OTHER,
            record.reservationStatusFilter(),
        )
    }

    @Test
    fun `record without sign state maps to other`() {
        val record = AppointmentRecord(signState = null, statusMerge = null)

        assertEquals(
            ReservationStatusFilter.OTHER,
            record.reservationStatusFilter(),
        )
    }

    @Test
    fun `default selection keeps pending check in and signed in`() {
        assertEquals(
            setOf(
                ReservationStatusFilter.PENDING_CHECK_IN,
                ReservationStatusFilter.SIGNED_IN,
            ),
            ReservationStatusFilter.DEFAULT_SELECTION,
        )
    }

    @Test
    fun `default selection filters reservation list as expected`() {
        val pending = AppointmentRecord(id = "1", signState = SIGN_STATE_PENDING, statusMerge = 1)
        val signedIn = AppointmentRecord(id = "2", signState = SIGN_STATE_SIGNED_IN, statusMerge = 1)
        val signedOut = AppointmentRecord(id = "3", signState = SIGN_STATE_SIGNED_OUT, statusMerge = 1)
        val cancelled = AppointmentRecord(id = "4", signState = SIGN_STATE_PENDING, statusMerge = 2)
        val reservations = listOf(pending, signedIn, signedOut, cancelled)

        val visible = reservations.filter { record ->
            record.reservationStatusFilter() in ReservationStatusFilter.DEFAULT_SELECTION
        }

        assertEquals(listOf(pending, signedIn), visible)
    }
}
