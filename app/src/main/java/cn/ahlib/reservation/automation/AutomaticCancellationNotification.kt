package cn.ahlib.reservation.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import cn.ahlib.reservation.MainActivity
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.AppointmentRecord
import kotlinx.coroutines.delay

internal enum class AutomaticCancellationDecision {
    PROCEED,
    USER_CANCELLED,
    NOTIFICATION_UNAVAILABLE,
}

internal interface AutomaticCancellationPrompt {
    fun retainActiveReservationIds(reservationIds: Set<String>)

    suspend fun awaitDecision(
        record: AppointmentRecord,
    ): AutomaticCancellationDecision
}

internal class AutomaticCancellationNotificationPrompt(
    context: Context,
) : AutomaticCancellationPrompt {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val notificationManagerCompat = NotificationManagerCompat.from(appContext)
    private val skipStore = AutomaticCancellationSkipStore(appContext)

    init {
        createNotificationChannel()
    }

    override fun retainActiveReservationIds(reservationIds: Set<String>) {
        skipStore.retainReservationIds(reservationIds)
    }

    override suspend fun awaitDecision(
        record: AppointmentRecord,
    ): AutomaticCancellationDecision {
        if (skipStore.isSkipped(record.id)) {
            return AutomaticCancellationDecision.USER_CANCELLED
        }
        if (!canShowNotifications()) {
            return AutomaticCancellationDecision.NOTIFICATION_UNAVAILABLE
        }

        val deadlineMillis = System.currentTimeMillis() + COUNTDOWN_MILLIS
        try {
            notificationManagerCompat.notify(
                AUTOMATIC_CANCELLATION_NOTIFICATION_ID,
                buildNotification(record, deadlineMillis),
            )
        } catch (_: SecurityException) {
            return AutomaticCancellationDecision.NOTIFICATION_UNAVAILABLE
        }
        return try {
            delay(
                (deadlineMillis - System.currentTimeMillis())
                    .coerceAtLeast(0L),
            )
            if (skipStore.isSkipped(record.id)) {
                AutomaticCancellationDecision.USER_CANCELLED
            } else {
                AutomaticCancellationDecision.PROCEED
            }
        } finally {
            notificationManagerCompat.cancel(
                AUTOMATIC_CANCELLATION_NOTIFICATION_ID,
            )
        }
    }

    fun canShowNotifications(): Boolean {
        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            return false
        }
        return notificationManager
            .getNotificationChannel(AUTOMATIC_CANCELLATION_CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AUTOMATIC_CANCELLATION_CHANNEL_ID,
            appContext.getString(
                R.string.automatic_cancellation_notification_channel,
            ),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(
                R.string.automatic_cancellation_notification_channel_description,
            )
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        record: AppointmentRecord,
        deadlineMillis: Long,
    ): android.app.Notification {
        val roomName = record.roomName
            ?.takeIf(String::isNotBlank)
            ?: appContext.getString(R.string.unknown_value)
        val schedule = buildList {
            record.bookDate?.takeIf(String::isNotBlank)?.let(::add)
            val timeRange = listOfNotNull(
                record.startTime?.takeIf(String::isNotBlank),
                record.endTime?.takeIf(String::isNotBlank),
            ).joinToString("\u2013")
            timeRange.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" ").ifBlank {
            appContext.getString(R.string.not_provided)
        }
        val warningText = redText(
            appContext.getString(
                R.string.automatic_cancellation_notification_body,
                roomName,
                schedule,
            ),
        )
        val cancelAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_warning,
            redText(
                appContext.getString(
                    R.string.automatic_cancellation_notification_cancel,
                ),
            ),
            cancellationPendingIntent(record.id),
        ).build()

        return NotificationCompat.Builder(
            appContext,
            AUTOMATIC_CANCELLATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle(
                appContext.getString(
                    R.string.automatic_cancellation_notification_title,
                ),
            )
            .setContentText(warningText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(warningText))
            .setContentIntent(contentPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(Color.RED)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setWhen(deadlineMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(COUNTDOWN_MILLIS)
            .addAction(cancelAction)
            .build()
    }

    private fun cancellationPendingIntent(reservationId: String): PendingIntent {
        val intent = Intent(
            appContext,
            AutomaticCancellationActionReceiver::class.java,
        )
            .setAction(ACTION_CANCEL_AUTOMATIC_CANCELLATION)
            .putExtra(EXTRA_RESERVATION_ID, reservationId)
        return PendingIntent.getBroadcast(
            appContext,
            CANCELLATION_ACTION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun redText(value: String): SpannableString =
        SpannableString(value).apply {
            setSpan(
                ForegroundColorSpan(Color.RED),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

    companion object {
        const val AUTOMATIC_CANCELLATION_CHANNEL_ID =
            "automatic_cancellation_countdown"
        const val AUTOMATIC_CANCELLATION_NOTIFICATION_ID = 27_401
        const val ACTION_CANCEL_AUTOMATIC_CANCELLATION =
            "cn.ahlib.reservation.action.CANCEL_AUTOMATIC_CANCELLATION"
        const val EXTRA_RESERVATION_ID = "reservation_id"

        private const val COUNTDOWN_MILLIS = 30_000L
        private const val CANCELLATION_ACTION_REQUEST_CODE = 27_402
        private const val CONTENT_REQUEST_CODE = 27_403
    }
}

class AutomaticCancellationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !=
            AutomaticCancellationNotificationPrompt
                .ACTION_CANCEL_AUTOMATIC_CANCELLATION
        ) {
            return
        }
        val reservationId = intent.getStringExtra(
            AutomaticCancellationNotificationPrompt.EXTRA_RESERVATION_ID,
        )?.takeIf(String::isNotBlank) ?: return
        AutomaticCancellationSkipStore(context).skipReservation(reservationId)
        NotificationManagerCompat.from(context).cancel(
            AutomaticCancellationNotificationPrompt
                .AUTOMATIC_CANCELLATION_NOTIFICATION_ID,
        )
    }
}

internal class AutomaticCancellationSkipStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isSkipped(reservationId: String): Boolean =
        preferences.getBoolean(key(reservationId), false)

    fun skipReservation(reservationId: String) {
        preferences.edit(commit = true) {
            putBoolean(key(reservationId), true)
        }
    }

    fun retainReservationIds(reservationIds: Set<String>) {
        val obsoleteKeys = preferences.all.keys.filter { key ->
            key.startsWith(KEY_PREFIX) && key.removePrefix(KEY_PREFIX) !in reservationIds
        }
        if (obsoleteKeys.isEmpty()) {
            return
        }
        preferences.edit {
            obsoleteKeys.forEach(::remove)
        }
    }

    private fun key(reservationId: String): String = "$KEY_PREFIX$reservationId"

    private companion object {
        const val PREFERENCES_NAME = "automatic_cancellation_skips"
        const val KEY_PREFIX = "reservation_"
    }
}
