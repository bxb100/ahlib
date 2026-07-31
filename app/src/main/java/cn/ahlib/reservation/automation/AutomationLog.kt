package cn.ahlib.reservation.automation

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AutomationLog {
    private val nextId = AtomicLong(0L)
    private val _entries = MutableStateFlow<List<AutomationLogEntry>>(emptyList())

    val entries: StateFlow<List<AutomationLogEntry>> = _entries.asStateFlow()

    fun info(message: String) = append(AutomationLogLevel.INFO, message)

    fun success(message: String) = append(AutomationLogLevel.SUCCESS, message)

    fun warning(message: String) = append(AutomationLogLevel.WARNING, message)

    fun error(message: String) = append(AutomationLogLevel.ERROR, message)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(level: AutomationLogLevel, message: String) {
        val entry = AutomationLogEntry(
            id = nextId.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            level = level,
            message = message.take(AUTOMATION_LOG_MAX_MESSAGE_LENGTH),
        )
        _entries.update { current ->
            (listOf(entry) + current).take(AUTOMATION_LOG_MAX_ENTRIES)
        }
    }
}

internal const val AUTOMATION_LOG_MAX_ENTRIES = 100
internal const val AUTOMATION_LOG_MAX_MESSAGE_LENGTH = 1_000
