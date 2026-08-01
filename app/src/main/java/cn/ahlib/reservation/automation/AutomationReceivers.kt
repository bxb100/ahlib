package cn.ahlib.reservation.automation

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.ahlib.reservation.ReservationApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun BroadcastReceiver.launchBackground(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationScheduler.ACTION_AUTOMATION_ALARM) {
            return
        }
        val task = intent.getStringExtra(AutomationScheduler.EXTRA_TASK)
            ?.let { value ->
                runCatching { AutomationTask.valueOf(value) }.getOrNull()
            }
            ?: return
        val application = context.applicationContext as ReservationApplication
        launchBackground {
            when (task) {
                AutomationTask.AUTO_BOOK ->
                    application.automationManager.scheduler
                        .scheduleFollowingAutoBookingCheck()

                AutomationTask.AUTO_SIGN_OUT ->
                    application.automationManager.scheduler
                        .scheduleNextAutomaticSignOut()

                AutomationTask.CANCELLATION_CHECK -> Unit
            }
            application.automationManager.scheduler.enqueue(task)
        }
    }
}

class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val application = context.applicationContext as ReservationApplication
        launchBackground {
            application.automationManager.sync()
            AutomationLog.info("Automation schedules restored after a system event.")
        }
    }
}

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !=
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }
        val application = context.applicationContext as ReservationApplication
        launchBackground {
            application.automationManager.sync()
            AutomationLog.info(
                "Automation schedules restored after the exact alarm permission changed.",
            )
        }
    }
}
