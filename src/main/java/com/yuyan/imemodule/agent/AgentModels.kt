package com.yuyan.imemodule.agent

import com.yuyan.imemodule.context.ContextSnapshot
import com.yuyan.imemodule.scene.SceneType

data class CandidateRequest(
    val sceneType: SceneType,
    val userDraft: String,
    val context: ContextSnapshot,
    val maxCandidates: Int,
    val minCandidateChars: Int,
    val maxCandidateChars: Int,
    val screenshotUnderstanding: ScreenshotUnderstandingResult?,
)

data class CandidateSuggestion(
    val text: String,
    val confidence: Float = 0f,
    val reason: String? = null,
)

data class CandidateResponse(
    val suggestions: List<CandidateSuggestion>,
)

data class ScreenshotUnderstandingResult(
    val screenSummary: String,
    val uiIntentHints: List<String>,
    val riskTags: List<String>,
)
