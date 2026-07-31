package cn.ahlib.reservation.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.ahlib.reservation.ReservationApplication
import kotlinx.coroutines.CancellationException

class AutomationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as ReservationApplication
        val task = inputData.getString(KEY_TASK)
            ?.let { value ->
                runCatching { AutomationTask.valueOf(value) }.getOrNull()
            }
            ?: return Result.failure()
        val engine = AutomationEngine(
            repository = application.appContainer.repository,
            preferences = application.automationManager.preferences,
            scheduler = application.automationManager.scheduler,
            queueCalendarReminder =
                application.automationManager::queueCalendarReminder,
        )
        val result = try {
            when (task) {
                AutomationTask.AUTO_BOOK -> engine.runAutoBooking()
                AutomationTask.CANCELLATION_CHECK -> engine.runCancellationCheck()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            AutomationLog.error(
                "Automation task failed unexpectedly: " +
                    "${exception.message ?: exception.javaClass.simpleName}.",
            )
            AutomationRunResult.Retry
        }
        if (task == AutomationTask.AUTO_BOOK) {
            if (result == AutomationRunResult.AutoBookingFinishedForToday) {
                application.automationManager.scheduler.scheduleNextAutoBookingDay()
            } else {
                application.automationManager.scheduler.scheduleNextAutoBooking()
            }
        }
        return when (result) {
            AutomationRunResult.Success -> Result.success()
            AutomationRunResult.Retry -> {
                if (task == AutomationTask.AUTO_BOOK) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }

            AutomationRunResult.AutoBookingFinishedForToday -> Result.success()
        }
    }

    companion object {
        const val KEY_TASK = "task"
    }
}
