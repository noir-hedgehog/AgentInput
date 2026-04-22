package com.yuyan.imemodule.settings

import com.yuyan.imemodule.prefs.AppPrefs

data class FeatureFlags(
    val enableAiCandidates: Boolean,
    val enableVoiceInput: Boolean,
    val enableScreenshotUnderstanding: Boolean,
    val enableA11yContext: Boolean,
    val enableGuiActionExecution: Boolean,
    val autoExecuteLowRisk: Boolean
)

object FeatureFlagsProvider {
    fun current(): FeatureFlags {
        val voice = AppPrefs.getInstance().voice
        return FeatureFlags(
            enableAiCandidates = voice.enableAiCandidates.getValue(),
            enableVoiceInput = voice.enableVoiceInput.getValue(),
            enableScreenshotUnderstanding = voice.enableScreenshotUnderstanding.getValue(),
            enableA11yContext = voice.enableA11yContext.getValue(),
            enableGuiActionExecution = voice.enableGuiActionExecution.getValue(),
            autoExecuteLowRisk = voice.autoExecuteLowRisk.getValue(),
        )
    }
}
