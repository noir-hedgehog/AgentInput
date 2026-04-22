package com.yuyan.imemodule.agent

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.yuyan.imemodule.context.ContextCollector
import com.yuyan.imemodule.context.ImeContextCollector
import com.yuyan.imemodule.context.A11yScreenshotCaptureGateway
import com.yuyan.imemodule.context.ScreenshotCaptureGateway
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.scene.ScenePolicyEngine
import com.yuyan.imemodule.settings.FeatureFlags
import com.yuyan.imemodule.settings.FeatureFlagsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ImeAiCoordinator(
    private val inputConnectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?,
    private val onSuggestionsReady: (List<String>, Boolean) -> Unit,
    private val contextCollector: ContextCollector = ImeContextCollector(),
    private val screenshotGateway: ScreenshotCaptureGateway = A11yScreenshotCaptureGateway,
    private val orchestrator: AgentOrchestrator = AgentOrchestrator(
        agentClient = CloudAgentClient(),
        screenshotClient = MinimaxImageUnderstandMcpClient(),
    ),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingJob: Job? = null

    fun onSelectionOrTextMayChanged() {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(250)
            collectAndSuggest(forceWhenEmpty = false, manualTrigger = false)
        }
    }

    fun requestNow() {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            collectAndSuggest(forceWhenEmpty = true, manualTrigger = true)
        }
    }

    private suspend fun collectAndSuggest(forceWhenEmpty: Boolean, manualTrigger: Boolean) {
            val flags = FeatureFlagsProvider.current()
            if (!flags.enableAiCandidates) return
            val snapshot = contextCollector.collect(
                inputConnection = inputConnectionProvider(),
                editorInfo = editorInfoProvider(),
            )
            if (!forceWhenEmpty && snapshot.inputText.beforeCursor.isBlank()) return
            val normalizedSnapshot = if (flags.enableA11yContext) snapshot else snapshot.copy(uiSummary = null)
            val profile = ScenePolicyEngine.resolve(snapshot.packageName)
            val screenshotBytes = captureScreenshotIfNeeded(flags, profile.allowScreenshot)
            val (minChars, maxChars) = resolveCandidateLengthRange()
            val response = orchestrator.suggest(
                contextSnapshot = normalizedSnapshot,
                sceneProfile = profile,
                flags = flags,
                screenshotBytes = screenshotBytes,
                minCandidateChars = minChars,
                maxCandidateChars = maxChars,
            )
            val suggestions = response.suggestions.map { it.text }.filter { it.isNotBlank() }
            if (suggestions.isNotEmpty()) {
                onSuggestionsReady(suggestions, manualTrigger)
            }
    }

    fun destroy() {
        scope.cancel()
    }

    private suspend fun captureScreenshotIfNeeded(flags: FeatureFlags, allowScreenshot: Boolean): ByteArray? {
        if (!flags.enableScreenshotUnderstanding || !allowScreenshot) return null
        return screenshotGateway.captureCurrentScreen()
    }

    private fun resolveCandidateLengthRange(): Pair<Int, Int> {
        val raw = AppPrefs.getInstance().voice.aiCandidateLengthRange.getValue().trim()
        val match = Regex("""(\d+)\D+(\d+)""").find(raw)
        val min = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 50
        val max = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 100
        val normalizedMin = min.coerceIn(10, 200)
        val normalizedMax = max.coerceIn(normalizedMin, 260)
        return normalizedMin to normalizedMax
    }
}
