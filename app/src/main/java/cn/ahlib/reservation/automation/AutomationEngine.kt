package cn.ahlib.reservation.automation

import cn.ahlib.reservation.data.ApiErrorKind
import cn.ahlib.reservation.data.ApiResult
import cn.ahlib.reservation.data.AppointmentRecord
import cn.ahlib.reservation.data.CreateReservationRequest
import cn.ahlib.reservation.data.ReservationRepository
import cn.ahlib.reservation.data.isCancellationEligible
import cn.ahlib.reservation.data.isPendingCheckIn
import kotlinx.coroutines.delay

internal class AutomationEngine(
    private val repository: ReservationRepository,
    private val preferences: AutomationPreferences,
    private val scheduler: AutomationScheduler,
    private val queueCalendarReminder: (
        roomName: String,
        venueName: String,
        reservationDateTime: String,
        createdAtMillis: Long,
    ) -> Unit,
) {
    suspend fun runAutoBooking(): AutomationRunResult {
        val settings = preferences.settings.value
        val target = settings.target
        if (!settings.autoBookingEnabled) {
            AutomationLog.info("Automatic booking is disabled.")
            return AutomationRunResult.Success
        }
        if (target == null) {
            AutomationLog.warning(
                "Automatic booking is enabled, but no target is configured.",
            )
            return AutomationRunResult.AutoBookingFinishedForToday
        }
        val window = calculateAutoBookingWindow(System.currentTimeMillis())
        val startedAt = System.currentTimeMillis()
        if (
            startedAt < window.denseStartAtMillis ||
            startedAt >= window.finishAtMillis
        ) {
            AutomationLog.warning(
                "Automatic booking started outside the daily checking window.",
            )
            return AutomationRunResult.Success
        }
        val reservations = when (val result = loadAllReservations()) {
            is ReservationLoadResult.Success -> result.records
            is ReservationLoadResult.Failure -> return result.result
        }
        if (
            hasMatchingActiveReservation(
                records = reservations,
                target = target,
                targetDate = window.targetDate,
            )
        ) {
            AutomationLog.info(
                "A matching reservation already exists for " +
                    "${window.targetDate}; automatic booking skipped.",
            )
            scheduler.requestCancellationRefresh()
            return AutomationRunResult.AutoBookingFinishedForToday
        }
        val denseRun = startedAt < window.denseEndAtMillis
        AutomationLog.info(
            "Checking ${target.roomName} for ${window.targetDate} at " +
                "${target.startTime}-${target.endTime}.",
        )

        var attempts = 0
        var lastFailureMessage: String? = null
        while (true) {
            val currentSettings = preferences.settings.value
            if (
                !currentSettings.autoBookingEnabled ||
                currentSettings.target != target
            ) {
                AutomationLog.info("Automatic booking polling stopped.")
                return AutomationRunResult.Success
            }
            attempts += 1
            when (val result = repository.getRoomAvailability(target.roomId)) {
                is ApiResult.Success -> {
                    val selection = selectMatchingSlotForDate(
                        availability = result.data,
                        target = target,
                        targetDate = window.targetDate,
                    )
                    if (selection != null) {
                        val request = CreateReservationRequest(
                            venueName = target.venueName,
                            dateTime = "${window.targetDate} " +
                                "${selection.slot.startTime}~${selection.slot.endTime}",
                            bookingId = selection.slot.id,
                        )
                        return when (
                            val bookingResult = repository.createReservation(request)
                        ) {
                            is ApiResult.Success -> {
                                AutomationLog.success(
                                    "Booked ${target.roomName} on " +
                                        "${window.targetDate} at " +
                                        "${selection.slot.startTime}-" +
                                        "${selection.slot.endTime}.",
                                )
                                queueCalendarReminder(
                                    target.roomName,
                                    target.venueName,
                                    request.dateTime,
                                    System.currentTimeMillis(),
                                )
                                scheduler.requestCancellationRefresh()
                                AutomationRunResult.AutoBookingFinishedForToday
                            }

                            is ApiResult.Failure ->
                                bookingResult.exception.toAutomationFailure(
                                    "Automatic booking failed",
                                )
                        }
                    }
                }

                is ApiResult.Failure -> {
                    lastFailureMessage = result.exception.message
                    if (
                        result.exception.kind != ApiErrorKind.NETWORK &&
                        result.exception.kind != ApiErrorKind.HTTP &&
                        result.exception.kind != ApiErrorKind.UNKNOWN
                    ) {
                        return result.exception.toAutomationFailure(
                            "Unable to load room availability",
                        )
                    }
                }
            }
            if (!denseRun) {
                break
            }
            val remainingMillis =
                window.denseEndAtMillis - System.currentTimeMillis()
            if (remainingMillis <= 0) {
                break
            }
            delay(minOf(AUTO_BOOKING_DENSE_POLL_INTERVAL_MILLIS, remainingMillis))
        }
        val failureSuffix = lastFailureMessage
            ?.takeIf(String::isNotBlank)
            ?.let { message -> " Last error: $message." }
            .orEmpty()
        if (denseRun) {
            AutomationLog.warning(
                "No matching slot became available for ${window.targetDate} " +
                    "after $attempts checks.$failureSuffix",
            )
        } else {
            AutomationLog.info(
                "The selected slot is not available for ${window.targetDate}." +
                    failureSuffix,
            )
        }
        return AutomationRunResult.Success
    }

    suspend fun runCancellationCheck(): AutomationRunResult {
        val settings = preferences.settings.value
        if (!settings.cancellationEnabled) {
            AutomationLog.info("Automatic cancellation is disabled.")
            scheduler.cancelCancellationCheck()
            return AutomationRunResult.Success
        }

        AutomationLog.info("Checking pending reservations for sign-in status.")
        val reservations = when (val result = loadAllReservations()) {
            is ReservationLoadResult.Success -> result.records
            is ReservationLoadResult.Failure -> return result.result
        }
        val now = System.currentTimeMillis()
        var nextCheckAt: Long? = null
        var retryRequired = false
        reservations
            .asSequence()
            .filter(AppointmentRecord::isPendingCheckIn)
            .forEach { record ->
                val checkAt = calculateCancellationCheckAt(
                    record = record,
                    leadMinutes = settings.cancellationLeadMinutes,
                )
                if (checkAt == null) {
                    AutomationLog.warning(
                        "Skipped reservation ${record.id}: its deadline could not be read.",
                    )
                    return@forEach
                }
                if (checkAt > now) {
                    nextCheckAt = minOf(nextCheckAt ?: checkAt, checkAt)
                    return@forEach
                }
                if (!record.isCancellationEligible()) {
                    return@forEach
                }
                when (val result = repository.cancelReservation(record.id)) {
                    is ApiResult.Success -> AutomationLog.success(
                        "Cancelled unsigned reservation ${record.id} for " +
                            "${record.roomName.orEmpty()} on ${record.bookDate.orEmpty()}.",
                    )

                    is ApiResult.Failure -> {
                        val failure = result.exception.toAutomationFailure(
                            "Unable to cancel reservation ${record.id}",
                        )
                        retryRequired = retryRequired ||
                            failure == AutomationRunResult.Retry
                    }
                }
            }

        if (nextCheckAt == null) {
            scheduler.cancelCancellationCheck()
            AutomationLog.info("No future sign-in checks are currently required.")
        } else {
            scheduler.scheduleCancellationCheck(nextCheckAt)
            AutomationLog.info("Scheduled the next sign-in check.")
        }
        return if (retryRequired) {
            AutomationRunResult.Retry
        } else {
            AutomationRunResult.Success
        }
    }

    private suspend fun loadAllReservations(): ReservationLoadResult {
        val first = when (
            val result = repository.getMyReservations(
                pageNum = FIRST_PAGE,
                pageSize = RESERVATION_PAGE_SIZE,
                total = 0,
            )
        ) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return ReservationLoadResult.Failure(
                result.exception.toAutomationFailure(
                    "Unable to load reservations",
                ),
            )
        }
        val records = first.result.toMutableList()
        for (page in (FIRST_PAGE + 1)..first.totalPages) {
            when (
                val result = repository.getMyReservations(
                    pageNum = page,
                    pageSize = RESERVATION_PAGE_SIZE,
                    total = first.total,
                )
            ) {
                is ApiResult.Success -> records += result.data.result
                is ApiResult.Failure -> return ReservationLoadResult.Failure(
                    result.exception.toAutomationFailure(
                        "Unable to load reservation page $page",
                    ),
                )
            }
        }
        return ReservationLoadResult.Success(
            records.distinctBy(AppointmentRecord::id),
        )
    }

    private fun cn.ahlib.reservation.data.ApiException.toAutomationFailure(
        prefix: String,
    ): AutomationRunResult {
        AutomationLog.error("$prefix: $message.")
        return if (
            kind == ApiErrorKind.NETWORK ||
            kind == ApiErrorKind.HTTP ||
            kind == ApiErrorKind.UNKNOWN
        ) {
            AutomationRunResult.Retry
        } else {
            AutomationRunResult.Success
        }
    }

    private sealed interface ReservationLoadResult {
        data class Success(
            val records: List<AppointmentRecord>,
        ) : ReservationLoadResult

        data class Failure(
            val result: AutomationRunResult,
        ) : ReservationLoadResult
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val RESERVATION_PAGE_SIZE = 10
        const val AUTO_BOOKING_DENSE_POLL_INTERVAL_MILLIS = 500L
    }
}

internal enum class AutomationRunResult {
    Success,
    Retry,
    AutoBookingFinishedForToday,
}
