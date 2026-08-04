package cn.ahlib.reservation.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import cn.ahlib.reservation.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun versionClicksEnableAndRevealAdvancedSettings() {
        var isEnabled by mutableStateOf(false)
        var enableCount = 0
        var openCount = 0
        val versionText = context.getString(
            R.string.app_update_current_version,
            TEST_VERSION,
        )
        val advancedSettingsText = context.getString(
            R.string.automation_settings_title,
        )

        composeRule.setContent {
            MaterialTheme {
                AboutScreen(
                    appVersionName = TEST_VERSION,
                    isCheckingUpdate = false,
                    isAdvancedSettingsEnabled = isEnabled,
                    onBack = {},
                    onCheckUpdate = {},
                    onEnableAdvancedSettings = {
                        enableCount += 1
                        isEnabled = true
                    },
                    onOpenAdvancedSettings = { openCount += 1 },
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.app_name))
            .assertHasClickAction()
        composeRule.onNodeWithText(versionText).assertHasClickAction()
        composeRule.onAllNodesWithText(advancedSettingsText).assertCountEquals(0)

        repeat(3) {
            composeRule.onNodeWithText(versionText).performClick()
        }
        assertRemainingClicks(versionText, 3)
        assertRemainingClicks(versionText, 2)
        assertRemainingClicks(versionText, 1)

        composeRule.onNodeWithText(versionText).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.advanced_settings_enabled))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(advancedSettingsText)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, enableCount)
        }

        composeRule.onNodeWithText(versionText).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.advanced_settings_enabled))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, enableCount)
        }

        composeRule.onAllNodesWithText(advancedSettingsText).assertCountEquals(1)
        composeRule.onNodeWithText(advancedSettingsText).performClick()
        composeRule.runOnIdle {
            assertEquals(1, openCount)
        }
    }

    @Test
    fun enabledStateIsVisibleWithoutRepeatingEnableAction() {
        var enableCount = 0
        val versionText = context.getString(
            R.string.app_update_current_version,
            TEST_VERSION,
        )
        val advancedSettingsText = context.getString(
            R.string.automation_settings_title,
        )

        composeRule.setContent {
            MaterialTheme {
                AboutScreen(
                    appVersionName = TEST_VERSION,
                    isCheckingUpdate = false,
                    isAdvancedSettingsEnabled = true,
                    onBack = {},
                    onCheckUpdate = {},
                    onEnableAdvancedSettings = { enableCount += 1 },
                    onOpenAdvancedSettings = {},
                )
            }
        }

        composeRule
            .onNodeWithText(advancedSettingsText)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(versionText).performClick()
        composeRule
            .onNodeWithText(context.getString(R.string.advanced_settings_enabled))
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, enableCount)
        }
    }

    private fun assertRemainingClicks(versionText: String, remainingClicks: Int) {
        composeRule.onNodeWithText(versionText).performClick()
        composeRule
            .onNodeWithText(
                context.getString(
                    R.string.advanced_settings_clicks_remaining,
                    remainingClicks,
                ),
            )
            .assertIsDisplayed()
    }

    private companion object {
        const val TEST_VERSION = "1.0.0-test"
    }
}
