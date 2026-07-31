package cn.ahlib.reservation.automation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationLogTest {
    @After
    fun tearDown() {
        AutomationLog.clear()
    }

    @Test
    fun retainsOnlyTheNewestEntries() {
        val overflow = 20
        repeat(AUTOMATION_LOG_MAX_ENTRIES + overflow) { index ->
            AutomationLog.info("entry-$index")
        }

        val entries = AutomationLog.entries.value

        assertEquals(AUTOMATION_LOG_MAX_ENTRIES, entries.size)
        assertEquals(
            "entry-${AUTOMATION_LOG_MAX_ENTRIES + overflow - 1}",
            entries.first().message,
        )
        assertEquals("entry-$overflow", entries.last().message)
    }

    @Test
    fun truncatesLongMessages() {
        AutomationLog.warning(
            "x".repeat(AUTOMATION_LOG_MAX_MESSAGE_LENGTH + 100),
        )

        assertEquals(
            AUTOMATION_LOG_MAX_MESSAGE_LENGTH,
            AutomationLog.entries.value.single().message.length,
        )
    }
}
