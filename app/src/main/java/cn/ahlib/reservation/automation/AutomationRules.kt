package cn.ahlib.reservation.automation

import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.isSelectableForReservation
import cn.ahlib.reservation.data.isSignedIn
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class AutoBookingSelection(
    val day: AvailabilityDay,
    val slot: AvailabilitySlot,
)

internal data class AutoBookingWindow(
    val denseStartAtMillis: Long,
    val denseEndAtMillis: Long,
    val finishAtMillis: Long,
    val targetDate: String,
)

internal fun selectMatchingSlotForDate(
    availability: List<AvailabilityDay>,
    target: AutoBookingTarget,
    targetDate: String,
): AutoBookingSelection? =
    availability
        .asSequence()
        .filter { day ->
            day.date.normalizedDate() == targetDate &&
                day.isSelectableForReservation()
        }
        .mapNotNull { day ->
            day.list
                .firstOrNull { slot ->
                    slot.startTime == target.startTime &&
                        slot.endTime == target.endTime &&
                        slot.isSelectableForReservation()
                }
                ?.let { slot -> AutoBookingSelection(day, slot) }
        }
        .firstOrNull()

internal fun calculateAutoBookingWindow(
    nowMillis: Long,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): AutoBookingWindow {
    val denseStart = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, AUTO_BOOKING_DENSE_START_HOUR)
        set(Calendar.MINUTE, AUTO_BOOKING_DENSE_START_MINUTE)
        set(Calendar.SECOND, AUTO_BOOKING_DENSE_START_SECOND)
        set(Calendar.MILLISECOND, 0)
    }
    val denseEnd = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, AUTO_BOOKING_DENSE_END_HOUR)
        set(Calendar.MINUTE, AUTO_BOOKING_DENSE_END_MINUTE)
        set(Calendar.SECOND, AUTO_BOOKING_DENSE_END_SECOND)
        set(Calendar.MILLISECOND, 0)
    }
    val finish = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, AUTO_BOOKING_FINISH_HOUR)
        set(Calendar.MINUTE, AUTO_BOOKING_FINISH_MINUTE)
        set(Calendar.SECOND, AUTO_BOOKING_FINISH_SECOND)
        set(Calendar.MILLISECOND, 0)
    }
    val targetDate = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, AUTO_BOOKING_TARGET_DAY_OFFSET)
    }
    return AutoBookingWindow(
        denseStartAtMillis = denseStart.timeInMillis,
        denseEndAtMillis = denseEnd.timeInMillis,
        finishAtMillis = finish.timeInMillis,
        targetDate = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }.format(targetDate.time),
    )
}

internal fun calculateNextAutoBookingCheckAt(
    nowMillis: Long,
    allowImmediateDenseCheck: Boolean,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): Long {
    val window = calculateAutoBookingWindow(nowMillis, timeZone)
    if (nowMillis < window.denseStartAtMillis) {
        return window.denseStartAtMillis
    }
    if (nowMillis < window.denseEndAtMillis) {
        if (allowImmediateDenseCheck) {
            val immediateCheckAt = nowMillis + MINIMUM_AUTO_BOOKING_TRIGGER_DELAY_MILLIS
            if (immediateCheckAt < window.denseEndAtMillis) {
                return immediateCheckAt
            }
        }
        return window.denseEndAtMillis + AUTO_BOOKING_SPARSE_INTERVAL_MILLIS
    }
    if (nowMillis < window.finishAtMillis) {
        val elapsed = nowMillis - window.denseEndAtMillis
        val nextCheckAt = window.denseEndAtMillis +
            (elapsed / AUTO_BOOKING_SPARSE_INTERVAL_MILLIS + 1L) *
            AUTO_BOOKING_SPARSE_INTERVAL_MILLIS
        if (nextCheckAt < window.finishAtMillis) {
            return nextCheckAt
        }
    }
    return Calendar.getInstance(timeZone).apply {
        timeInMillis = window.denseStartAtMillis
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
}

internal fun calculateNextAutomaticSignOutAt(
    nowMillis: Long,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): Long {
    val trigger = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, AUTO_SIGN_OUT_HOUR)
        set(Calendar.MINUTE, AUTO_SIGN_OUT_MINUTE)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return trigger.timeInMillis
}

internal fun selectAutomaticSignOutReservation(
    records: List<AppointmentRecord>,
    currentRoomReservation: AppointmentRecord?,
): AppointmentRecord? = currentRoomReservation?.takeIf { current ->
    current.isSignedIn() && records.any { record ->
        record.id == current.id && record.isSignedIn()
    }
}

internal fun hasMatchingActiveReservation(
    records: List<AppointmentRecord>,
    target: AutoBookingTarget,
    targetDate: String,
): Boolean = records.any { record ->
    record.statusMerge == ACTIVE_RESERVATION_STATUS &&
        record.bookingDate() == targetDate &&
        record.startTime.matchesAutomationTime(target.startTime) &&
        record.endTime.matchesAutomationTime(target.endTime) &&
        (
            target.roomName.isBlank() ||
                record.roomName
                    ?.trim()
                    ?.equals(target.roomName.trim(), ignoreCase = true) == true
        )
}

internal fun calculateSignInDeadlineAt(
    record: AppointmentRecord,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): Long? {
    val bookingDate = parseBookingDate(record, timeZone) ?: return null
    val nineOClock = Calendar.getInstance(timeZone).apply {
        time = bookingDate
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val createdAt = parseDateTime(record.createTime, timeZone)
    return if (
        createdAt != null &&
        isSameDay(createdAt, bookingDate, timeZone)
    ) {
        maxOf(nineOClock, createdAt.time + THIRTY_MINUTES_MILLIS)
    } else {
        nineOClock
    }
}

internal fun AppointmentRecord.signInDeadlineForDisplay(
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): String? = calculateSignInDeadlineAt(this, timeZone)
    ?.let { deadlineAtMillis ->
        formatSignInDeadlineTime(deadlineAtMillis, timeZone)
    }

internal fun formatSignInDeadlineTime(
    deadlineAtMillis: Long,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): String = SimpleDateFormat(TIME_DISPLAY_PATTERN, Locale.US).apply {
    this.timeZone = timeZone
}.format(Date(deadlineAtMillis))

internal fun calculateCancellationCheckAt(
    record: AppointmentRecord,
    leadMinutes: Int,
    timeZone: TimeZone = AUTOMATION_TIME_ZONE,
): Long? {
    val deadline = calculateSignInDeadlineAt(record, timeZone) ?: return null
    return deadline - leadMinutes
        .coerceIn(MIN_CANCELLATION_LEAD_MINUTES, MAX_CANCELLATION_LEAD_MINUTES) *
        MINUTE_MILLIS
}

private fun parseBookingDate(
    record: AppointmentRecord,
    timeZone: TimeZone,
): Date? {
    val value = record.bookDate
        ?.takeIf(String::isNotBlank)
        ?: record.dateTime
            ?.takeIf(String::isNotBlank)
            ?.substringBefore(' ')
        ?: return null
    return parseExactly(value.normalizedDate(), DATE_PATTERN, timeZone)
}

private fun parseDateTime(value: String?, timeZone: TimeZone): Date? {
    val source = value?.takeIf(String::isNotBlank) ?: return null
    return DATE_TIME_PATTERNS.firstNotNullOfOrNull { pattern ->
        parseExactly(source, pattern, timeZone)
    }
}

private fun parseExactly(
    value: String,
    pattern: String,
    timeZone: TimeZone,
): Date? {
    val position = ParsePosition(0)
    val parsed = SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        this.timeZone = timeZone
    }.parse(value, position)
    return parsed?.takeIf { position.index == value.length }
}

private fun isSameDay(first: Date, second: Date, timeZone: TimeZone): Boolean {
    val firstCalendar = Calendar.getInstance(timeZone).apply { time = first }
    val secondCalendar = Calendar.getInstance(timeZone).apply { time = second }
    return firstCalendar.get(Calendar.ERA) == secondCalendar.get(Calendar.ERA) &&
        firstCalendar.get(Calendar.YEAR) == secondCalendar.get(Calendar.YEAR) &&
        firstCalendar.get(Calendar.DAY_OF_YEAR) == secondCalendar.get(Calendar.DAY_OF_YEAR)
}

private const val DATE_PATTERN = "yyyy-MM-dd"
private const val DATE_PATTERN_LENGTH = 10
private const val TIME_DISPLAY_PATTERN = "HH:mm"
private val DATE_TIME_PATTERNS = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss",
)
private const val MINUTE_MILLIS = 60_000L
private const val THIRTY_MINUTES_MILLIS = 30L * MINUTE_MILLIS
private const val AUTO_BOOKING_DENSE_START_HOUR = 6
private const val AUTO_BOOKING_DENSE_START_MINUTE = 59
private const val AUTO_BOOKING_DENSE_START_SECOND = 50
private const val AUTO_BOOKING_DENSE_END_HOUR = 7
private const val AUTO_BOOKING_DENSE_END_MINUTE = 1
private const val AUTO_BOOKING_DENSE_END_SECOND = 0
private const val AUTO_BOOKING_FINISH_HOUR = 10
private const val AUTO_BOOKING_FINISH_MINUTE = 0
private const val AUTO_SIGN_OUT_HOUR = 21
private const val AUTO_SIGN_OUT_MINUTE = 50
private const val AUTO_BOOKING_FINISH_SECOND = 0
private const val MINIMUM_AUTO_BOOKING_TRIGGER_DELAY_MILLIS = 1_000L
internal const val AUTO_BOOKING_SPARSE_INTERVAL_MILLIS = 3L * MINUTE_MILLIS
internal const val AUTO_BOOKING_TARGET_DAY_OFFSET = 2
internal val AUTOMATION_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
private const val ACTIVE_RESERVATION_STATUS = 1

private fun String.normalizedDate(): String =
    trim()
        .substringBefore('T')
        .substringBefore(' ')
        .take(DATE_PATTERN_LENGTH)
        .replace('/', '-')
        .replace('.', '-')
        .let { value ->
            runCatching { LocalDate.parse(value).toString() }
                .getOrDefault(value)
        }

private fun AppointmentRecord.bookingDate(): String? =
    bookDate
        ?.takeIf(String::isNotBlank)
        ?.normalizedDate()
        ?: dateTime
            ?.takeIf(String::isNotBlank)
            ?.normalizedDate()

private fun String?.matchesAutomationTime(other: String): Boolean {
    val first = this
        ?.trim()
        ?.toAutomationTimeOrNull()
        ?: return false
    val second = other.trim().toAutomationTimeOrNull() ?: return false
    return first == second
}

private fun String.toAutomationTimeOrNull(): LocalTime? {
    val parts = split(':')
    if (parts.size !in 2..3) {
        return null
    }
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
}
