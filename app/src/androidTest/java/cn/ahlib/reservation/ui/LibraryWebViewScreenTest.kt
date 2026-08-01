package cn.ahlib.reservation.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import cn.ahlib.reservation.R
import cn.ahlib.reservation.ui.theme.ReservationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryWebViewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenShowsBrowserChromeAndWebView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.library_web_view_title)
        val host = context.getString(R.string.library_web_view_host)
        composeRule.setContent {
            ReservationTheme {
                LibraryWebViewScreen(
                    sessionCookies = emptyList(),
                    onClose = {},
                    pageUrl = "about:blank",
                )
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(host).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_WEB_VIEW_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(LIBRARY_WEB_VIEW_MENU_TEST_TAG)
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag(LIBRARY_WEB_VIEW_REFRESH_TEST_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun closeActionInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val closeDescription = context.getString(R.string.library_web_view_close)
        var closeCount = 0
        composeRule.setContent {
            ReservationTheme {
                LibraryWebViewScreen(
                    sessionCookies = emptyList(),
                    onClose = { closeCount += 1 },
                    pageUrl = "about:blank",
                )
            }
        }

        composeRule.onNodeWithContentDescription(closeDescription).performClick()

        composeRule.runOnIdle {
            assertEquals(1, closeCount)
        }
    }

    @Test
    fun systemBackInvokesCallback() {
        var closeCount = 0
        composeRule.setContent {
            ReservationTheme {
                LibraryWebViewScreen(
                    sessionCookies = emptyList(),
                    onClose = { closeCount += 1 },
                    pageUrl = "about:blank",
                )
            }
        }

        pressBack()

        composeRule.runOnIdle {
            assertEquals(1, closeCount)
        }
    }
}
