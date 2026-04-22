package com.yuyan.imemodule.agent

import androidx.preference.PreferenceManager
import com.yuyan.imemodule.application.Launcher

data class AgentDebugSnapshot(
    val channel: String,
    val url: String,
    val statusCode: Int,
    val elapsedMs: Long,
    val error: String,
    val responsePreview: String,
    val requestPrompt: String,
    val screenshotFilePath: String,
    val screenshotUnderstandingResult: String,
    val updatedAtMs: Long,
)

object AgentDebugLogStore {
    private const val KEY_CHANNEL = "ai_debug_last_channel"
    private const val KEY_URL = "ai_debug_last_url"
    private const val KEY_STATUS = "ai_debug_last_status"
    private const val KEY_ELAPSED = "ai_debug_last_elapsed"
    private const val KEY_ERROR = "ai_debug_last_error"
    private const val KEY_RESPONSE = "ai_debug_last_response"
    private const val KEY_PROMPT = "ai_debug_last_prompt"
    private const val KEY_SCREENSHOT_PATH = "ai_debug_last_screenshot_path"
    private const val KEY_SCREENSHOT_RESULT = "ai_debug_last_screenshot_result"
    private const val KEY_UPDATED_AT = "ai_debug_last_updated_at"

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(Launcher.instance.context)

    fun save(
        channel: String? = null,
        url: String? = null,
        statusCode: Int? = null,
        elapsedMs: Long? = null,
        error: String? = null,
        responsePreview: String? = null,
        requestPrompt: String? = null,
        screenshotFilePath: String? = null,
        screenshotUnderstandingResult: String? = null,
    ) {
        val editor = prefs().edit()
        if (channel != null) editor.putString(KEY_CHANNEL, channel)
        if (url != null) editor.putString(KEY_URL, url)
        if (statusCode != null) editor.putInt(KEY_STATUS, statusCode)
        if (elapsedMs != null) editor.putLong(KEY_ELAPSED, elapsedMs)
        if (requestPrompt != null) editor.putString(KEY_PROMPT, requestPrompt)
        if (screenshotFilePath != null) editor.putString(KEY_SCREENSHOT_PATH, screenshotFilePath)
        if (screenshotUnderstandingResult != null) editor.putString(KEY_SCREENSHOT_RESULT, screenshotUnderstandingResult)
        if (error != null) editor.putString(KEY_ERROR, error)
        if (responsePreview != null) editor.putString(KEY_RESPONSE, responsePreview)
        editor.putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
    }

    fun read(): AgentDebugSnapshot {
        val p = prefs()
        return AgentDebugSnapshot(
            channel = p.getString(KEY_CHANNEL, "").orEmpty(),
            url = p.getString(KEY_URL, "").orEmpty(),
            statusCode = p.getInt(KEY_STATUS, 0),
            elapsedMs = p.getLong(KEY_ELAPSED, 0L),
            error = p.getString(KEY_ERROR, "").orEmpty(),
            responsePreview = p.getString(KEY_RESPONSE, "").orEmpty(),
            requestPrompt = p.getString(KEY_PROMPT, "").orEmpty(),
            screenshotFilePath = p.getString(KEY_SCREENSHOT_PATH, "").orEmpty(),
            screenshotUnderstandingResult = p.getString(KEY_SCREENSHOT_RESULT, "").orEmpty(),
            updatedAtMs = p.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun clear() {
        prefs().edit()
            .remove(KEY_CHANNEL)
            .remove(KEY_URL)
            .remove(KEY_STATUS)
            .remove(KEY_ELAPSED)
            .remove(KEY_ERROR)
            .remove(KEY_RESPONSE)
            .remove(KEY_PROMPT)
            .remove(KEY_SCREENSHOT_PATH)
            .remove(KEY_SCREENSHOT_RESULT)
            .remove(KEY_UPDATED_AT)
            .apply()
    }
}
