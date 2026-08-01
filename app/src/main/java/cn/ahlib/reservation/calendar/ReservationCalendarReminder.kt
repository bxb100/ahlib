package cn.ahlib.reservation.calendar

import cn.ahlib.reservation.automation.AUTOMATION_TIME_ZONE
import cn.ahlib.reservation.automation.calculateSignInDeadlineAt
import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.isPendingCheckIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

data class ReservationCalendarReminder(
    val id: String,
    val roomName: String,
    val venueName: String,
    val reservationDateTime: String,
    val eventStartAtMillis: Long,
    val eventEndAtMillis: Long,
    val deadlineAtMillis: Long,
)

internal fun createReservationCalendarReminder(
    roomName: String,
    venueName: String,
    reservationDateTime: String,
    createdAtMillis: Long,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): ReservationCalendarReminder? {
    if (reservationDateTime.isBlank()) {
        return null
    }
    val createdAt = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).apply {
        isLenient = false
        this.timeZone = timeZone
    }.format(Date(createdAtMillis))
    val deadlineAtMillis = calculateSignInDeadlineAt(
        record = AppointmentRecord(
            dateTime = reservationDateTime,
            createTime = createdAt,
        ),
        timeZone = timeZone,
    ) ?: return null
    return createReservationCalendarReminder(
        id = "$createdAtMillis:${reservationDateTime.hashCode()}:${roomName.hashCode()}",
        roomName = roomName,
        venueName = venueName,
        reservationDateTime = reservationDateTime,
        deadlineAtMillis = deadlineAtMillis,
        requestedAtMillis = createdAtMillis,
    )
}

internal fun createReservationCalendarReminder(
    record: AppointmentRecord,
    requestedAtMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): ReservationCalendarReminder? {
    if (!record.isPendingCheckIn()) {
        return null
    }
    val reservationDateTime = record.reservationDateTimeForCalendar()
        ?: return null
    val deadlineAtMillis = calculateSignInDeadlineAt(record, timeZone)
        ?: return null
    val id = record.id
        .takeIf(String::isNotBlank)
        ?: record.bookingId.takeIf(String::isNotBlank)
        ?: "$requestedAtMillis:${reservationDateTime.hashCode()}"
    return createReservationCalendarReminder(
        id = id,
        roomName = record.roomName.orEmpty(),
        venueName = record.venueName.orEmpty(),
        reservationDateTime = reservationDateTime,
        deadlineAtMillis = deadlineAtMillis,
        requestedAtMillis = requestedAtMillis,
    )
}

private fun createReservationCalendarReminder(
    id: String,
    roomName: String,
    venueName: String,
    reservationDateTime: String,
    deadlineAtMillis: Long,
    requestedAtMillis: Long,
): ReservationCalendarReminder? {
    if (
        deadlineAtMillis - requestedAtMillis <
        MINIMUM_EVENT_DURATION_MILLIS
    ) {
        return null
    }
    val desiredStartAtMillis =
        deadlineAtMillis - PREFERRED_REMINDER_LEAD_MILLIS
    val earliestUsefulStartAtMillis =
        requestedAtMillis + MINIMUM_FUTURE_EVENT_DELAY_MILLIS
    val latestUsefulStartAtMillis =
        deadlineAtMillis - MINIMUM_EVENT_DURATION_MILLIS
    val eventStartAtMillis = max(
        desiredStartAtMillis,
        earliestUsefulStartAtMillis,
    ).coerceAtMost(latestUsefulStartAtMillis)
    val eventEndAtMillis = min(
        eventStartAtMillis + DEFAULT_EVENT_DURATION_MILLIS,
        deadlineAtMillis,
    )
    return ReservationCalendarReminder(
        id = id,
        roomName = roomName,
        venueName = venueName,
        reservationDateTime = reservationDateTime,
        eventStartAtMillis = eventStartAtMillis,
        eventEndAtMillis = eventEndAtMillis,
        deadlineAtMillis = deadlineAtMillis,
    )
}

private fun AppointmentRecord.reservationDateTimeForCalendar(): String? {
    dateTime?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    val date = bookDate?.trim()?.takeIf(String::isNotBlank) ?: return null
    val start = startTime?.trim()?.takeIf(String::isNotBlank)
    val end = endTime?.trim()?.takeIf(String::isNotBlank)
    return when {
        start != null && end != null -> "$date $start~$end"
        start != null -> "$date $start"
        else -> date
    }
}

private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
private const val MINUTE_MILLIS = 60_000L
private const val PREFERRED_REMINDER_LEAD_MILLIS = 60L * MINUTE_MILLIS
private const val MINIMUM_FUTURE_EVENT_DELAY_MILLIS = MINUTE_MILLIS
private const val MINIMUM_EVENT_DURATION_MILLIS = MINUTE_MILLIS
private const val DEFAULT_EVENT_DURATION_MILLIS = 30L * MINUTE_MILLIS
