package cn.ahlib.reservation.automation

data class AutoBookingTarget(
    val roomId: String,
    val roomName: String,
    val venueName: String,
    val startTime: String,
    val endTime: String,
)

data class AutomaticSignOutQrCode(
    val roomId: String,
    val imageUri: String,
)

data class AutomationSettings(
    val autoBookingEnabled: Boolean = false,
    val cancellationEnabled: Boolean = false,
    val cancellationLeadMinutes: Int = DEFAULT_CANCELLATION_LEAD_MINUTES,
    val mockLocationEnabled: Boolean = false,
    val target: AutoBookingTarget? = null,
    val automaticSignOutQrCode: AutomaticSignOutQrCode? = null,
)

enum class AutomationTask {
    AUTO_BOOK,
    CANCELLATION_CHECK,
    AUTO_SIGN_OUT,
}

enum class AutomationLogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class AutomationLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: AutomationLogLevel,
    val message: String,
)

const val DEFAULT_CANCELLATION_LEAD_MINUTES = 5
const val MIN_CANCELLATION_LEAD_MINUTES = 1
const val MAX_CANCELLATION_LEAD_MINUTES = 60
