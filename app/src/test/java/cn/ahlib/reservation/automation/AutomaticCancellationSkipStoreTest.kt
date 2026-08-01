package cn.ahlib.reservation.automation

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AutomaticCancellationSkipStoreTest {
    private val store by lazy {
        AutomaticCancellationSkipStore(RuntimeEnvironment.getApplication())
    }

    @Before
    fun resetStore() {
        store.retainReservationIds(emptySet())
    }

    @Test
    fun skippedReservationRemainsSkipped() {
        store.skipReservation("reservation-one")

        assertTrue(store.isSkipped("reservation-one"))
        assertFalse(store.isSkipped("reservation-two"))
    }

    @Test
    fun obsoleteReservationSkipsAreRemoved() {
        store.skipReservation("reservation-one")
        store.skipReservation("reservation-two")

        store.retainReservationIds(setOf("reservation-two"))

        assertFalse(store.isSkipped("reservation-one"))
        assertTrue(store.isSkipped("reservation-two"))
    }
}
