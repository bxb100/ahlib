package cn.ahlib.reservation.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class QrImageHistoryStoreTest {
    @Test
    fun newestImageIsPlacedFirstAndDuplicateIsReplaced() {
        val updated = updateQrImageHistory(
            entries = listOf(
                entry("content://images/one", 100L),
                entry("content://images/two", 200L),
            ),
            newEntry = entry("content://images/one", 300L),
        )

        assertEquals(
            listOf("content://images/one", "content://images/two"),
            updated.map(QrImageHistoryEntry::uriString),
        )
        assertEquals(300L, updated.first().scannedAtMillis)
    }

    @Test
    fun historyIsLimitedToTheRequestedSize() {
        val updated = updateQrImageHistory(
            entries = listOf(
                entry("content://images/one", 100L),
                entry("content://images/two", 200L),
                entry("content://images/three", 300L),
            ),
            newEntry = entry("content://images/four", 400L),
            limit = 2,
        )

        assertEquals(
            listOf("content://images/four", "content://images/three"),
            updated.map(QrImageHistoryEntry::uriString),
        )
    }

    private fun entry(
        uriString: String,
        scannedAtMillis: Long,
    ): QrImageHistoryEntry = QrImageHistoryEntry(
        uriString = uriString,
        scannedAtMillis = scannedAtMillis,
    )
}
