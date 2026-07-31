package cn.ahlib.reservation.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class AutomationScheduler(
    context: Context,
    private val preferences: AutomationPreferences,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val workManager = WorkManager.getInstance(appContext)

    fun sync() {
        clearLegacyReservationReminder()
        val settings = preferences.settings.value
        if (settings.autoBookingEnabled) {
            scheduleNextAutoBooking()
        } else {
            cancelAlarm(AutomationTask.AUTO_BOOK)
            workManager.cancelUniqueWork(UNIQUE_AUTO_BOOKING_WORK)
        }
        if (settings.cancellationEnabled) {
            requestCancellationRefresh()
        } else {
            cancelCancellationCheck()
            workManager.cancelUniqueWork(UNIQUE_CANCELLATION_WORK)
        }
    }

    fun scheduleNextAutoBooking(nowMillis: Long = System.currentTimeMillis()) {
        if (!preferences.settings.value.autoBookingEnabled) {
            cancelAlarm(AutomationTask.AUTO_BOOK)
            return
        }
        val triggerAtMillis = calculateNextAutoBookingCheckAt(
            nowMillis = nowMillis,
            allowImmediateDenseCheck = true,
        )
        scheduleAlarm(AutomationTask.AUTO_BOOK, triggerAtMillis)
    }

    fun scheduleFollowingAutoBookingCheck(
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!preferences.settings.value.autoBookingEnabled) {
            cancelAlarm(AutomationTask.AUTO_BOOK)
            return
        }
        val triggerAtMillis = calculateNextAutoBookingCheckAt(
            nowMillis = nowMillis,
            allowImmediateDenseCheck = false,
        )
        scheduleAlarm(AutomationTask.AUTO_BOOK, triggerAtMillis)
    }

    fun scheduleNextAutoBookingDay(
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!preferences.settings.value.autoBookingEnabled) {
            cancelAlarm(AutomationTask.AUTO_BOOK)
            return
        }
        val currentWindow = calculateAutoBookingWindow(nowMillis)
        val triggerAtMillis = calculateNextAutoBookingCheckAt(
            nowMillis = currentWindow.finishAtMillis,
            allowImmediateDenseCheck = false,
        )
        scheduleAlarm(AutomationTask.AUTO_BOOK, triggerAtMillis)
    }

    fun scheduleCancellationCheck(triggerAtMillis: Long) {
        if (!preferences.settings.value.cancellationEnabled) {
            cancelCancellationCheck()
            return
        }
        scheduleAlarm(
            AutomationTask.CANCELLATION_CHECK,
            maxOf(triggerAtMillis, System.currentTimeMillis() + MINIMUM_ALARM_DELAY_MILLIS),
        )
    }

    fun requestCancellationRefresh() {
        if (!preferences.settings.value.cancellationEnabled) {
            return
        }
        enqueue(AutomationTask.CANCELLATION_CHECK)
    }

    fun cancelCancellationCheck() {
        cancelAlarm(AutomationTask.CANCELLATION_CHECK)
    }

    fun enqueue(task: AutomationTask) {
        val input = Data.Builder()
            .putString(AutomationWorker.KEY_TASK, task.name)
            .build()
        val requestBuilder = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(input)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
        if (task == AutomationTask.AUTO_BOOK) {
            requestBuilder.setExpedited(
                OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST,
            )
        }
        val request = requestBuilder.build()
        workManager.enqueueUniqueWork(
            task.uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun canScheduleExactAlarms(): Boolean =
        alarmManager.canScheduleExactAlarms()

    private fun scheduleAlarm(task: AutomationTask, triggerAtMillis: Long) {
        val operation = checkNotNull(
            pendingIntent(task, PendingIntent.FLAG_UPDATE_CURRENT),
        )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation,
            )
            AutomationLog.warning(
                "Exact alarm access is unavailable; the task may run late.",
            )
        }
    }

    private fun cancelAlarm(task: AutomationTask) {
        val operation = pendingIntent(
            task,
            PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun clearLegacyReservationReminder() {
        val intent = Intent(appContext, AutomationAlarmReceiver::class.java)
            .setAction(ACTION_AUTOMATION_ALARM)
            .putExtra(EXTRA_TASK, LEGACY_RESERVATION_REMINDER_TASK)
        PendingIntent.getBroadcast(
            appContext,
            LEGACY_RESERVATION_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
        workManager.cancelUniqueWork(LEGACY_RESERVATION_REMINDER_WORK)
    }

    private fun pendingIntent(
        task: AutomationTask,
        lookupFlag: Int,
    ): PendingIntent? {
        val intent = Intent(appContext, AutomationAlarmReceiver::class.java)
            .setAction(ACTION_AUTOMATION_ALARM)
            .putExtra(EXTRA_TASK, task.name)
        return PendingIntent.getBroadcast(
            appContext,
            task.requestCode,
            intent,
            lookupFlag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal companion object {
        const val ACTION_AUTOMATION_ALARM =
            "cn.ahlib.reservation.action.AUTOMATION_ALARM"
        const val EXTRA_TASK = "automation_task"
        const val MINIMUM_ALARM_DELAY_MILLIS = 1_000L
        const val UNIQUE_AUTO_BOOKING_WORK = "automatic_booking"
        const val UNIQUE_CANCELLATION_WORK = "automatic_cancellation"
        private const val LEGACY_RESERVATION_REMINDER_TASK =
            "RESERVATION_REMINDER"
        private const val LEGACY_RESERVATION_REMINDER_WORK =
            "reservation_reminder"
        private const val LEGACY_RESERVATION_REMINDER_REQUEST_CODE = 7102
    }
}

private val AutomationTask.requestCode: Int
    get() = when (this) {
        AutomationTask.AUTO_BOOK -> 7100
        AutomationTask.CANCELLATION_CHECK -> 7101
    }

private val AutomationTask.uniqueWorkName: String
    get() = when (this) {
        AutomationTask.AUTO_BOOK -> AutomationScheduler.UNIQUE_AUTO_BOOKING_WORK
        AutomationTask.CANCELLATION_CHECK ->
            AutomationScheduler.UNIQUE_CANCELLATION_WORK
    }
