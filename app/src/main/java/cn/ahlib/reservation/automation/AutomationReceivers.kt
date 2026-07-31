package cn.ahlib.reservation.automation

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.ahlib.reservation.ReservationApplication

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
        if (task == AutomationTask.AUTO_BOOK) {
            application.automationManager.scheduler.scheduleFollowingAutoBookingCheck()
        }
        application.automationManager.scheduler.enqueue(task)
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
        application.automationManager.sync()
        AutomationLog.info("Automation schedules restored after a system event.")
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
        application.automationManager.sync()
        AutomationLog.info(
            "Automation schedules restored after the exact alarm permission changed.",
        )
    }
}
