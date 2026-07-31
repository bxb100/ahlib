package cn.ahlib.reservation.calendar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalendarReminderStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val _pendingReminder = MutableStateFlow(read())

    val pendingReminder: StateFlow<ReservationCalendarReminder?> =
        _pendingReminder.asStateFlow()

    @Synchronized
    fun queue(
        roomName: String,
        venueName: String,
        reservationDateTime: String,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): ReservationCalendarReminder? {
        val reminder = createReservationCalendarReminder(
            roomName = roomName,
            venueName = venueName,
            reservationDateTime = reservationDateTime,
            createdAtMillis = createdAtMillis,
        ) ?: return null
        preferences.edit {
            putString(KEY_ID, reminder.id)
            putString(KEY_ROOM_NAME, reminder.roomName)
            putString(KEY_VENUE_NAME, reminder.venueName)
            putString(KEY_RESERVATION_DATE_TIME, reminder.reservationDateTime)
            putLong(KEY_EVENT_START, reminder.eventStartAtMillis)
            putLong(KEY_EVENT_END, reminder.eventEndAtMillis)
            putLong(KEY_DEADLINE, reminder.deadlineAtMillis)
        }
        _pendingReminder.value = reminder
        return reminder
    }

    @Synchronized
    fun dismiss(id: String) {
        if (_pendingReminder.value?.id != id) {
            return
        }
        preferences.edit { clear() }
        _pendingReminder.value = null
    }

    private fun read(): ReservationCalendarReminder? {
        val id = preferences.getString(KEY_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val eventStartAtMillis = preferences.getLong(KEY_EVENT_START, 0L)
        val eventEndAtMillis = preferences.getLong(KEY_EVENT_END, 0L)
        val deadlineAtMillis = preferences.getLong(KEY_DEADLINE, 0L)
        if (
            eventStartAtMillis <= 0L ||
            eventEndAtMillis <= eventStartAtMillis ||
            deadlineAtMillis < eventEndAtMillis ||
            deadlineAtMillis <= System.currentTimeMillis()
        ) {
            preferences.edit { clear() }
            return null
        }
        return ReservationCalendarReminder(
            id = id,
            roomName = preferences.getString(KEY_ROOM_NAME, null).orEmpty(),
            venueName = preferences.getString(KEY_VENUE_NAME, null).orEmpty(),
            reservationDateTime = preferences.getString(
                KEY_RESERVATION_DATE_TIME,
                null,
            ).orEmpty(),
            eventStartAtMillis = eventStartAtMillis,
            eventEndAtMillis = eventEndAtMillis,
            deadlineAtMillis = deadlineAtMillis,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "calendar_reminder"
        const val KEY_ID = "id"
        const val KEY_ROOM_NAME = "room_name"
        const val KEY_VENUE_NAME = "venue_name"
        const val KEY_RESERVATION_DATE_TIME = "reservation_date_time"
        const val KEY_EVENT_START = "event_start"
        const val KEY_EVENT_END = "event_end"
        const val KEY_DEADLINE = "deadline"
    }
}
