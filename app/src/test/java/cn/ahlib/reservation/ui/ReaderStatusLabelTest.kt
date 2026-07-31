package cn.ahlib.reservation.ui

import cn.ahlib.reservation.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderStatusLabelTest {
    @Test
    fun knownReaderStatusesMapToLabels() {
        assertEquals(R.string.reader_status_valid, readerStatusLabelResource("1"))
        assertEquals(R.string.reader_status_verifying, readerStatusLabelResource("2"))
        assertEquals(R.string.reader_status_lost, readerStatusLabelResource("3"))
        assertEquals(R.string.reader_status_suspended, readerStatusLabelResource("4"))
        assertEquals(R.string.reader_status_cancelled, readerStatusLabelResource("5"))
    }

    @Test
    fun readerStatusMappingIgnoresSurroundingWhitespace() {
        assertEquals(R.string.reader_status_valid, readerStatusLabelResource(" 1 "))
    }

    @Test
    fun unknownReaderStatusesDoNotMapToAResource() {
        assertNull(readerStatusLabelResource(null))
        assertNull(readerStatusLabelResource(""))
        assertNull(readerStatusLabelResource("9"))
    }
}
