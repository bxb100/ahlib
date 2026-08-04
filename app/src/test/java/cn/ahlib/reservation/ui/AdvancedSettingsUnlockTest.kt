package cn.ahlib.reservation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSettingsUnlockTest {
    @Test
    fun defaultThresholdIsSilentForFirstThreeClicks() {
        var state = AdvancedSettingsUnlockState()

        repeat(3) { index ->
            val result = state.onVersionClick()

            state = result.state
            assertEquals(index + 1, state.clickCount)
            assertFalse(state.isEnabled)
            assertFalse(result.didEnable)
            assertNull(result.feedback)
        }
    }

    @Test
    fun defaultThresholdReportsLastThreeRemainingClicks() {
        var state = AdvancedSettingsUnlockState()
        repeat(3) {
            state = state.onVersionClick().state
        }

        listOf(3, 2, 1).forEach { expectedRemaining ->
            val result = state.onVersionClick()

            state = result.state
            assertEquals(
                AdvancedSettingsUnlockFeedback.Remaining(expectedRemaining),
                result.feedback,
            )
            assertFalse(result.didEnable)
            assertFalse(state.isEnabled)
        }
    }

    @Test
    fun thresholdClickEnablesAdvancedSettingsOnce() {
        var state = AdvancedSettingsUnlockState()
        repeat(DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS - 1) {
            state = state.onVersionClick().state
        }

        val result = state.onVersionClick()

        assertEquals(DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS, result.state.clickCount)
        assertTrue(result.state.isEnabled)
        assertTrue(result.didEnable)
        assertEquals(AdvancedSettingsUnlockFeedback.Enabled, result.feedback)
    }

    @Test
    fun furtherClicksReportEnabledWithoutEnablingAgain() {
        var state = AdvancedSettingsUnlockState()
        repeat(DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS) {
            state = state.onVersionClick().state
        }

        val result = state.onVersionClick()

        assertEquals(state, result.state)
        assertEquals(AdvancedSettingsUnlockFeedback.Enabled, result.feedback)
        assertFalse(result.didEnable)
    }

    @Test
    fun initiallyEnabledStateDoesNotEnableAgain() {
        val state = AdvancedSettingsUnlockState(isEnabled = true)

        val result = state.onVersionClick()

        assertEquals(state, result.state)
        assertEquals(AdvancedSettingsUnlockFeedback.Enabled, result.feedback)
        assertFalse(result.didEnable)
    }

    @Test
    fun customThresholdUsesTheSameThreeClickCountdown() {
        var state = AdvancedSettingsUnlockState()
        val requiredClicks = 5

        val firstResult = state.onVersionClick(requiredClicks)
        state = firstResult.state
        assertNull(firstResult.feedback)

        listOf(3, 2, 1).forEach { expectedRemaining ->
            val result = state.onVersionClick(requiredClicks)

            state = result.state
            assertEquals(
                AdvancedSettingsUnlockFeedback.Remaining(expectedRemaining),
                result.feedback,
            )
            assertFalse(result.didEnable)
        }

        val enabledResult = state.onVersionClick(requiredClicks)
        assertTrue(enabledResult.state.isEnabled)
        assertTrue(enabledResult.didEnable)
        assertEquals(
            AdvancedSettingsUnlockFeedback.Enabled,
            enabledResult.feedback,
        )
    }

    @Test
    fun thresholdMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            AdvancedSettingsUnlockState().onVersionClick(requiredClicks = 0)
        }
    }
}
