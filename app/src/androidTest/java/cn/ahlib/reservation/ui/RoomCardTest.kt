package cn.ahlib.reservation.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.ahlib.reservation.data.RoomSummary
import cn.ahlib.reservation.ui.theme.ReservationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoomCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomCardDisplaysSummaryAndHandlesClick() {
        var clickCount = 0
        val room = RoomSummary(
            id = "room-1",
            roomName = "East Reading Room",
            venueName = "Main Library",
            address = "74 Wuhu Road",
            totalNum = 226,
            ableNum = 12,
        )
        composeRule.setContent {
            ReservationTheme {
                RoomCard(
                    room = room,
                    onClick = { clickCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText(room.roomName).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(checkNotNull(room.venueName)).assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }
}
