package com.yuyan.imemodule.agent

import com.yuyan.imemodule.context.ContextSnapshot
import com.yuyan.imemodule.scene.SceneProfile
import com.yuyan.imemodule.settings.FeatureFlags

class AgentOrchestrator(
    private val agentClient: AgentClient,
    private val screenshotClient: ScreenshotUnderstandingClient,
) {
    suspend fun suggest(
        contextSnapshot: ContextSnapshot,
        sceneProfile: SceneProfile,
        flags: FeatureFlags,
        screenshotBytes: ByteArray?,
        minCandidateChars: Int,
        maxCandidateChars: Int,
    ): CandidateResponse {
        val screenshotUnderstanding = if (
            flags.enableScreenshotUnderstanding &&
            sceneProfile.allowScreenshot &&
            !contextSnapshot.packageName.isNullOrBlank() &&
            screenshotBytes != null
        ) {
            screenshotClient.understand(
                packageName = contextSnapshot.packageName,
                screenshotBytes = screenshotBytes,
                promptHint = contextSnapshot.inputText.beforeCursor,
            )
        } else {
            null
        }
        val screenshotSummary = screenshotUnderstanding?.let { result ->
            buildString {
                append(result.screenSummary)
                if (result.uiIntentHints.isNotEmpty()) {
                    append(" | intents=").append(result.uiIntentHints.joinToString(","))
                }
                if (result.riskTags.isNotEmpty()) {
                    append(" | risks=").append(result.riskTags.joinToString(","))
                }
            }
        }
        val request = CandidateRequest(
            sceneType = sceneProfile.sceneType,
            userDraft = contextSnapshot.inputText.beforeCursor.takeLast(120),
            context = contextSnapshot.copy(screenshotSummary = screenshotSummary),
            maxCandidates = sceneProfile.maxCandidates,
            minCandidateChars = minCandidateChars,
            maxCandidateChars = maxCandidateChars,
            screenshotUnderstanding = screenshotUnderstanding,
        )
        return agentClient.suggestCandidates(request)
    }
}
