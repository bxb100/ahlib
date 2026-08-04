package cn.ahlib.reservation.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import cn.ahlib.reservation.R
import cn.ahlib.reservation.data.AvailabilityDay
import cn.ahlib.reservation.data.AvailabilitySlot
import cn.ahlib.reservation.data.RoomDetail
import cn.ahlib.reservation.ui.theme.ReservationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RoomAdvancedSettingsVisibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun autoBookingHintIsHiddenWhileAdvancedSettingsAreLocked() {
        setRoomDetailContent(isAdvancedSettingsEnabled = false)

        composeRule
            .onAllNodesWithText(context.getString(R.string.auto_booking_swipe_hint))
            .assertCountEquals(0)
    }

    @Test
    fun autoBookingHintIsVisibleAfterAdvancedSettingsAreEnabled() {
        setRoomDetailContent(isAdvancedSettingsEnabled = true)

        composeRule
            .onNodeWithText(context.getString(R.string.auto_booking_swipe_hint))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun setRoomDetailContent(isAdvancedSettingsEnabled: Boolean) {
        composeRule.setContent {
            ReservationTheme {
                RoomDetailScreen(
                    roomId = ROOM_ID,
                    detail = RoomDetail(
                        id = ROOM_ID,
                        roomName = "Reading Room",
                    ),
                    availability = listOf(
                        AvailabilityDay(
                            date = TEST_DATE,
                            list = listOf(
                                AvailabilitySlot(
                                    id = SLOT_ID,
                                    startTime = "08:30",
                                    endTime = "09:30",
                                    leftNum = 1,
                                    isOpen = 1,
                                    bookFlag = 1,
                                ),
                            ),
                        ),
                    ),
                    selectedDate = TEST_DATE,
                    selectedSlotId = null,
                    autoBookingTarget = null,
                    isAdvancedSettingsEnabled = isAdvancedSettingsEnabled,
                    isLoading = false,
                    isAvailabilityRefreshing = false,
                    isBooking = false,
                    detailErrorText = null,
                    showBookingDialog = false,
                    bookingName = "",
                    bookingMobile = "",
                    requireBookingName = false,
                    requireBookingMobile = false,
                    bookingErrorText = null,
                    onBack = {},
                    onRetry = {},
                    onRefreshAvailability = {},
                    onSelectDate = {},
                    onSelectSlot = {},
                    onOpenBookingDialog = {},
                    onDismissBookingDialog = {},
                    onBookingNameChange = {},
                    onBookingMobileChange = {},
                    onConfirmBooking = {},
                    onConfigureAutoBooking = {},
                    onClearAutoBookingTarget = {},
                )
            }
        }
    }

    private companion object {
        const val ROOM_ID = "room-id"
        const val SLOT_ID = "slot-id"
        const val TEST_DATE = "2026-08-05"
    }
}
