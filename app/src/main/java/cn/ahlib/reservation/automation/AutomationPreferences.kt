package cn.ahlib.reservation.automation

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    init {
        initializeAdvancedSettingsPreference()
    }

    private val _settings = MutableStateFlow(readSettings())

    val settings: StateFlow<AutomationSettings> = _settings.asStateFlow()

    @Synchronized
    fun setAutoBookingEnabled(enabled: Boolean) {
        updateSettings {
            putBoolean(KEY_AUTO_BOOKING_ENABLED, enabled)
        }
    }

    @Synchronized
    fun setCancellationEnabled(enabled: Boolean) {
        updateSettings {
            putBoolean(KEY_CANCELLATION_ENABLED, enabled)
        }
    }

    @Synchronized
    fun setCancellationLeadMinutes(minutes: Int) {
        updateSettings {
            putInt(
                KEY_CANCELLATION_LEAD_MINUTES,
                minutes.coerceIn(
                    MIN_CANCELLATION_LEAD_MINUTES,
                    MAX_CANCELLATION_LEAD_MINUTES,
                ),
            )
        }
    }

    @Synchronized
    fun setMockLocationEnabled(enabled: Boolean) {
        updateSettings {
            putBoolean(KEY_MOCK_LOCATION_ENABLED, enabled)
        }
    }

    @Synchronized
    fun enableAdvancedSettings() {
        updateSettings {
            putBoolean(KEY_ADVANCED_SETTINGS_ENABLED, true)
        }
    }

    @Synchronized
    fun setAutomaticSignOutQrCode(qrCode: AutomaticSignOutQrCode) {
        updateSettings {
            putString(KEY_AUTO_SIGN_OUT_ROOM_ID, qrCode.roomId)
            putString(KEY_AUTO_SIGN_OUT_IMAGE_URI, qrCode.imageUri)
        }
    }

    @Synchronized
    fun clearAutomaticSignOutQrCode() {
        updateSettings {
            remove(KEY_AUTO_SIGN_OUT_ROOM_ID)
            remove(KEY_AUTO_SIGN_OUT_IMAGE_URI)
        }
    }

    @Synchronized
    fun setTarget(target: AutoBookingTarget) {
        updateSettings {
            putString(KEY_TARGET_ROOM_ID, target.roomId)
            putString(KEY_TARGET_ROOM_NAME, target.roomName)
            putString(KEY_TARGET_VENUE_NAME, target.venueName)
            putString(KEY_TARGET_START_TIME, target.startTime)
            putString(KEY_TARGET_END_TIME, target.endTime)
        }
    }

    @Synchronized
    fun clearTarget() {
        updateSettings {
            remove(KEY_TARGET_ROOM_ID)
            remove(KEY_TARGET_ROOM_NAME)
            remove(KEY_TARGET_VENUE_NAME)
            remove(KEY_TARGET_START_TIME)
            remove(KEY_TARGET_END_TIME)
        }
    }

    private inline fun updateSettings(update: SharedPreferences.Editor.() -> Unit) {
        preferences.edit(action = update)
        publish()
    }

    private fun publish() {
        _settings.value = readSettings()
    }

    private fun initializeAdvancedSettingsPreference() {
        if (preferences.contains(KEY_ADVANCED_SETTINGS_ENABLED)) {
            return
        }
        val hasLegacySettings = preferences.all.isNotEmpty()
        preferences.edit {
            putBoolean(KEY_ADVANCED_SETTINGS_ENABLED, hasLegacySettings)
        }
    }

    private fun readSettings(): AutomationSettings {
        val target = readTarget()
        return AutomationSettings(
            autoBookingEnabled = preferences.getBoolean(
                KEY_AUTO_BOOKING_ENABLED,
                false,
            ),
            cancellationEnabled = preferences.getBoolean(
                KEY_CANCELLATION_ENABLED,
                false,
            ),
            cancellationLeadMinutes = preferences.getInt(
                KEY_CANCELLATION_LEAD_MINUTES,
                DEFAULT_CANCELLATION_LEAD_MINUTES,
            ).coerceIn(
                MIN_CANCELLATION_LEAD_MINUTES,
                MAX_CANCELLATION_LEAD_MINUTES,
            ),
            mockLocationEnabled = preferences.getBoolean(
                KEY_MOCK_LOCATION_ENABLED,
                false,
            ),
            isAdvancedSettingsEnabled = preferences.getBoolean(
                KEY_ADVANCED_SETTINGS_ENABLED,
                false,
            ),
            target = target,
            automaticSignOutQrCode = readAutomaticSignOutQrCode(),
        )
    }

    private fun readAutomaticSignOutQrCode(): AutomaticSignOutQrCode? {
        val roomId = preferences.getString(KEY_AUTO_SIGN_OUT_ROOM_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val imageUri = preferences.getString(KEY_AUTO_SIGN_OUT_IMAGE_URI, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return AutomaticSignOutQrCode(
            roomId = roomId,
            imageUri = imageUri,
        )
    }

    private fun readTarget(): AutoBookingTarget? {
        val roomId = preferences.getString(KEY_TARGET_ROOM_ID, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val roomName = preferences.getString(KEY_TARGET_ROOM_NAME, null).orEmpty()
        val venueName = preferences.getString(KEY_TARGET_VENUE_NAME, null).orEmpty()
        val startTime = preferences.getString(KEY_TARGET_START_TIME, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val endTime = preferences.getString(KEY_TARGET_END_TIME, null)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return AutoBookingTarget(
            roomId = roomId,
            roomName = roomName,
            venueName = venueName,
            startTime = startTime,
            endTime = endTime,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "automation"
        const val KEY_AUTO_BOOKING_ENABLED = "auto_booking_enabled"
        const val KEY_CANCELLATION_ENABLED = "cancellation_enabled"
        const val KEY_CANCELLATION_LEAD_MINUTES = "cancellation_lead_minutes"
        const val KEY_MOCK_LOCATION_ENABLED = "mock_location_enabled"
        const val KEY_ADVANCED_SETTINGS_ENABLED = "advanced_settings_enabled"
        const val KEY_AUTO_SIGN_OUT_ROOM_ID = "auto_sign_out_room_id"
        const val KEY_AUTO_SIGN_OUT_IMAGE_URI = "auto_sign_out_image_uri"
        const val KEY_TARGET_ROOM_ID = "target_room_id"
        const val KEY_TARGET_ROOM_NAME = "target_room_name"
        const val KEY_TARGET_VENUE_NAME = "target_venue_name"
        const val KEY_TARGET_START_TIME = "target_start_time"
        const val KEY_TARGET_END_TIME = "target_end_time"
    }
}
