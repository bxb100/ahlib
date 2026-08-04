package cn.ahlib.reservation.ui

internal const val DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS = 7

internal data class AdvancedSettingsUnlockState(
    val clickCount: Int = 0,
    val isEnabled: Boolean = false,
) {
    init {
        require(clickCount >= 0) { "Click count must not be negative" }
    }

    fun onVersionClick(
        requiredClicks: Int = DEFAULT_ADVANCED_SETTINGS_UNLOCK_CLICKS,
    ): AdvancedSettingsUnlockResult {
        require(requiredClicks > 0) { "Required clicks must be positive" }

        if (isEnabled || clickCount >= requiredClicks) {
            return AdvancedSettingsUnlockResult(
                state = copy(isEnabled = true),
                feedback = AdvancedSettingsUnlockFeedback.Enabled,
                didEnable = false,
            )
        }

        val nextClickCount = clickCount + 1
        if (nextClickCount == requiredClicks) {
            return AdvancedSettingsUnlockResult(
                state = copy(
                    clickCount = nextClickCount,
                    isEnabled = true,
                ),
                feedback = AdvancedSettingsUnlockFeedback.Enabled,
                didEnable = true,
            )
        }

        val remainingClicks = requiredClicks - nextClickCount
        return AdvancedSettingsUnlockResult(
            state = copy(clickCount = nextClickCount),
            feedback = if (remainingClicks in 1..FEEDBACK_CLICK_COUNT) {
                AdvancedSettingsUnlockFeedback.Remaining(remainingClicks)
            } else {
                null
            },
            didEnable = false,
        )
    }
}

internal data class AdvancedSettingsUnlockResult(
    val state: AdvancedSettingsUnlockState,
    val feedback: AdvancedSettingsUnlockFeedback?,
    val didEnable: Boolean,
)

internal sealed interface AdvancedSettingsUnlockFeedback {
    data class Remaining(val clicks: Int) : AdvancedSettingsUnlockFeedback

    data object Enabled : AdvancedSettingsUnlockFeedback
}

private const val FEEDBACK_CLICK_COUNT = 3
