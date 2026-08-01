package cn.ahlib.reservation.automation

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import cn.ahlib.reservation.calendar.CalendarReminderStore
import cn.ahlib.reservation.calendar.ReservationCalendarReminder
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.RoomDetail
import cn.ahlib.reservation.scanner.QrImageScanError
import cn.ahlib.reservation.scanner.QrImageScanResult
import cn.ahlib.reservation.scanner.scanQrCodeFromImage
import kotlinx.coroutines.flow.StateFlow

class AutomationManager(
    context: Context,
) {
    internal val preferences = AutomationPreferences(context)
    internal val scheduler = AutomationScheduler(context, preferences)
    internal val automaticCancellationPrompt =
        AutomaticCancellationNotificationPrompt(context)
    private val calendarReminderStore = CalendarReminderStore(context)
    private val automaticSignOutQrStore = AutomaticSignOutQrStore(context)
    private val appContext = context.applicationContext

    val settings: StateFlow<AutomationSettings> = preferences.settings
    val logs: StateFlow<List<AutomationLogEntry>> = AutomationLog.entries
    val pendingCalendarReminder:
        StateFlow<ReservationCalendarReminder?> =
        calendarReminderStore.pendingReminder

    init {
        clearLegacyNotificationArtifacts(context)
    }

    fun sync() {
        scheduler.sync()
    }

    fun configureAutoBooking(
        roomId: String,
        detail: RoomDetail,
        slot: AvailabilitySlot,
    ) {
        val target = AutoBookingTarget(
            roomId = roomId,
            roomName = detail.roomName,
            venueName = detail.venueName.orEmpty(),
            startTime = slot.startTime,
            endTime = slot.endTime,
        )
        preferences.setTarget(target)
        scheduler.sync()
        AutomationLog.info(
            "Automatic booking target updated to ${target.roomName} at " +
                "${target.startTime}-${target.endTime}.",
        )
    }

    fun clearAutoBookingTarget() {
        preferences.clearTarget()
        scheduler.sync()
        AutomationLog.info("Automatic booking target cleared.")
    }

    fun setAutoBookingEnabled(enabled: Boolean) {
        preferences.setAutoBookingEnabled(enabled)
        scheduler.sync()
        AutomationLog.info(
            if (enabled) {
                "Automatic booking enabled."
            } else {
                "Automatic booking disabled."
            },
        )
    }

    fun setCancellationEnabled(enabled: Boolean) {
        preferences.setCancellationEnabled(enabled)
        scheduler.sync()
        AutomationLog.info(
            if (enabled) {
                "Automatic cancellation enabled."
            } else {
                "Automatic cancellation disabled."
            },
        )
    }

    fun setCancellationLeadMinutes(minutes: Int) {
        preferences.setCancellationLeadMinutes(minutes)
        scheduler.sync()
        val savedMinutes = preferences.settings.value.cancellationLeadMinutes
        AutomationLog.info(
            "Cancellation lead time changed to $savedMinutes minutes.",
        )
    }

    suspend fun configureAutomaticSignOutQrImage(
        imageUri: Uri,
    ): QrImageScanResult {
        val scanResult = scanQrCodeFromImage(appContext, imageUri)
        if (scanResult is QrImageScanResult.Failure) {
            return scanResult
        }
        val code = (scanResult as QrImageScanResult.Success).code
        val storedImageUri = try {
            automaticSignOutQrStore.replaceImage(imageUri)
        } catch (exception: Exception) {
            return QrImageScanResult.Failure(
                QrImageScanError.ImageFailure(exception),
            )
        }
        preferences.setAutomaticSignOutQrCode(
            AutomaticSignOutQrCode(
                roomId = code.roomId,
                imageUri = storedImageUri.toString(),
            ),
        )
        scheduler.sync()
        AutomationLog.info(
            "Automatic sign-out QR image configured for room ${code.roomId}.",
        )
        return scanResult
    }

    fun clearAutomaticSignOutQrImage() {
        preferences.clearAutomaticSignOutQrCode()
        scheduler.sync()
        automaticSignOutQrStore.clear()
        AutomationLog.info("Automatic sign-out QR image cleared.")
    }

    fun setMockLocationEnabled(enabled: Boolean) {
        preferences.setMockLocationEnabled(enabled)
        AutomationLog.info(
            if (enabled) {
                "Room coordinate simulation enabled."
            } else {
                "Room coordinate simulation disabled."
            },
        )
    }

    fun shouldUseMockLocation(): Boolean =
        settings.value.mockLocationEnabled

    fun refreshCancellationSchedule() {
        scheduler.requestCancellationRefresh()
    }

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    fun canShowCancellationNotifications(): Boolean =
        automaticCancellationPrompt.canShowNotifications()

    internal fun queueCalendarReminder(
        roomName: String,
        venueName: String,
        reservationDateTime: String,
        createdAtMillis: Long,
    ) {
        if (
            calendarReminderStore.queue(
                roomName = roomName,
                venueName = venueName,
                reservationDateTime = reservationDateTime,
                createdAtMillis = createdAtMillis,
            ) == null
        ) {
            AutomationLog.warning(
                "Unable to prepare a calendar reminder for the reservation.",
            )
        }
    }

    fun dismissCalendarReminder(id: String) {
        calendarReminderStore.dismiss(id)
    }

    fun clearLogs() {
        AutomationLog.clear()
    }

    private fun clearLegacyNotificationArtifacts(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .apply {
                deleteNotificationChannel(LEGACY_BOOKING_NOTIFICATION_CHANNEL)
                deleteNotificationChannel(LEGACY_REMINDER_NOTIFICATION_CHANNEL)
            }
    }

    private companion object {
        const val LEGACY_BOOKING_NOTIFICATION_CHANNEL =
            "automatic_booking_success"
        const val LEGACY_REMINDER_NOTIFICATION_CHANNEL =
            "reservation_reminder"
    }
}
