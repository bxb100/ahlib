package cn.ahlib.reservation.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import cn.ahlib.reservation.R
import cn.ahlib.reservation.automation.AUTOMATION_TIME_ZONE
import cn.ahlib.reservation.calendar.ReservationCalendarReminder
import java.text.DateFormat
import java.util.Date

internal fun openCalendarReminderEditor(
    context: Context,
    reminder: ReservationCalendarReminder,
): Boolean {
    val deadline = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
    ).apply {
        timeZone = AUTOMATION_TIME_ZONE
    }.format(Date(reminder.deadlineAtMillis))
    val displayName = reminder.roomName
        .takeIf(String::isNotBlank)
        ?: reminder.venueName
    val intent = Intent(Intent.ACTION_INSERT)
        .setDataAndType(
            CalendarContract.Events.CONTENT_URI,
            CALENDAR_EVENT_MIME_TYPE,
        )
        .putExtra(
            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
            reminder.eventStartAtMillis,
        )
        .putExtra(
            CalendarContract.EXTRA_EVENT_END_TIME,
            reminder.eventEndAtMillis,
        )
        .putExtra(
            CalendarContract.Events.TITLE,
            context.getString(
                R.string.calendar_reminder_event_title,
                displayName,
            ),
        )
        .putExtra(
            CalendarContract.Events.DESCRIPTION,
            context.getString(
                R.string.calendar_reminder_event_description,
                reminder.reservationDateTime,
                deadline,
            ),
        )
        .putExtra(
            CalendarContract.Events.EVENT_LOCATION,
            reminder.venueName,
        )
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private const val CALENDAR_EVENT_MIME_TYPE =
    "vnd.android.cursor.dir/event"
