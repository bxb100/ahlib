package cn.ahlib.reservation.automation

import android.app.Application
import android.content.Context
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
class AutomationPreferencesTest {
    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun advancedSettingsAreDisabledByDefault() {
        val preferences = AutomationPreferences(application)

        assertFalse(preferences.settings.value.isAdvancedSettingsEnabled)
        assertTrue(
            application.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).contains(KEY_ADVANCED_SETTINGS_ENABLED),
        )
    }

    @Test
    fun enablingAdvancedSettingsPersistsAcrossInstances() {
        val preferences = AutomationPreferences(application)

        preferences.enableAdvancedSettings()

        assertTrue(preferences.settings.value.isAdvancedSettingsEnabled)
        assertTrue(
            AutomationPreferences(application)
                .settings
                .value
                .isAdvancedSettingsEnabled,
        )
    }

    @Test
    fun legacySettingsEnableAdvancedSettingsDuringMigration() {
        application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_BOOKING_ENABLED, false)
            .commit()

        val preferences = AutomationPreferences(application)

        assertTrue(preferences.settings.value.isAdvancedSettingsEnabled)
    }

    @Test
    fun newInstallationStaysDisabledAfterTargetIsWritten() {
        val preferences = AutomationPreferences(application)

        preferences.setTarget(
            AutoBookingTarget(
                roomId = "room-id",
                roomName = "Room",
                venueName = "Venue",
                startTime = "08:30",
                endTime = "22:00",
            ),
        )

        assertFalse(preferences.settings.value.isAdvancedSettingsEnabled)
        assertFalse(
            AutomationPreferences(application)
                .settings
                .value
                .isAdvancedSettingsEnabled,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "automation"
        const val KEY_ADVANCED_SETTINGS_ENABLED = "advanced_settings_enabled"
        const val KEY_AUTO_BOOKING_ENABLED = "auto_booking_enabled"
    }
}
