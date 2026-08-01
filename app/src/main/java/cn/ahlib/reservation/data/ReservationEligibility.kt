package cn.ahlib.reservation.data

internal const val SIGN_STATE_PENDING = 0
internal const val SIGN_STATE_SIGNED_IN = 1
internal const val SIGN_STATE_SIGNED_OUT = 2

internal fun AvailabilitySlot.isSelectableForReservation(): Boolean =
    isOpen == 1 && bookFlag == 1 && (leftNum ?: 0) > 0

internal fun AvailabilityDay.isSelectableForReservation(): Boolean =
    isOpen == 1 &&
        (totalLeftNum ?: 0) > 0 &&
        list.any(AvailabilitySlot::isSelectableForReservation)

internal fun AppointmentRecord.isCancellationEligible(): Boolean =
    id.isNotBlank() && status != 0 && statusMerge == 1

internal fun AppointmentRecord.isPendingCheckIn(): Boolean =
    signState == SIGN_STATE_PENDING && statusMerge == 1

internal fun AppointmentRecord.isSignedIn(): Boolean =
    signState == SIGN_STATE_SIGNED_IN
