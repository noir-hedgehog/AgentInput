package com.yuyan.imemodule.agent

import com.yuyan.imemodule.BuildConfig
import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.io.File

interface AgentClient {
    suspend fun suggestCandidates(request: CandidateRequest): CandidateResponse
}

interface ScreenshotUnderstandingClient {
    suspend fun understand(
        packageName: String,
        screenshotBytes: ByteArray,
        promptHint: String,
    ): ScreenshotUnderstandingResult?
}

class CloudAgentClient(
) : AgentClient {
    override suspend fun suggestCandidates(request: CandidateRequest): CandidateResponse = withContext(Dispatchers.IO) {
        val endpoint = resolveCandidateApiUrl(runtimeCandidateEndpoint())
        if (endpoint.isBlank()) {
            return@withContext CandidateResponse(
                suggestions = fallbackCandidates(request.userDraft, request.minCandidateChars, request.maxCandidateChars)
            )
        }
        try {
            val payload = buildOpenAiPayload(request)
            val response = postJson(
                endpoint = endpoint,
                payload = payload,
                apiKey = runtimeApiKey(),
                isMcpToolRequest = false,
                channel = "text-candidate",
            )
            parseCandidateResponse(response.body, request)
        } catch (t: Throwable) {
            LogUtil.e("CloudAgentClient", "suggestCandidates", t.message ?: "unknown error")
            CandidateResponse(suggestions = fallbackCandidates(request.userDraft, request.minCandidateChars, request.maxCandidateChars))
        }
    }

    private fun buildOpenAiPayload(request: CandidateRequest): JSONObject {
        val systemPrompt = """
            你是中文输入法候选生成助手。你需要基于上下文给出自然、简洁、可直接发送的候选句。
            每条候选控制在 ${request.minCandidateChars}-${request.maxCandidateChars} 个中文字符。
            输出必须是可直接上屏的完整句子。
            仅输出候选句，每行一条，不要编号，不要解释，共 ${request.maxCandidates} 条。
        """.trimIndent()
        val screenshotContext = request.screenshotUnderstanding?.let { result ->
            buildString {
                append("screenSummary=").append(result.screenSummary)
                if (result.uiIntentHints.isNotEmpty()) {
                    append("\nuiIntentHints=").append(result.uiIntentHints.joinToString(" | "))
                }
                if (result.riskTags.isNotEmpty()) {
                    append("\nriskTags=").append(result.riskTags.joinToString(" | "))
                }
            }
        }.orEmpty()
        val userPrompt = """
            scene=${request.sceneType.name.lowercase(Locale.ROOT)}
            packageName=${request.context.packageName ?: ""}
            uiPackageName=${request.context.uiSummary?.packageName.orEmpty()}
            fieldHint=${request.context.editorInfo.fieldHint.orEmpty()}
            inputType=${request.context.editorInfo.inputType}
            imeAction=${request.context.editorInfo.imeAction}
            cursorPosition=${request.context.inputText.beforeCursor.length}
            beforeCursor=${request.context.inputText.beforeCursor}
            selectedText=${request.context.inputText.selectedText.orEmpty()}
            afterCursor=${request.context.inputText.afterCursor}
            uiSummary=${request.context.uiSummary?.visibleTextSummary.orEmpty()}
            screenshotSummary=${request.context.screenshotSummary.orEmpty()}
            screenshotUnderstanding=${screenshotContext}
        """.trimIndent()
        return JSONObject().apply {
            put("model", runtimeModelName())
            put("max_completion_tokens", 320)
            put("temperature", 0.6)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("stream", false)
        }
    }

    private fun parseCandidateResponse(raw: String, request: CandidateRequest): CandidateResponse {
        val obj = JSONObject(raw)
        // 兼容旧格式：{"items":[...]}
        val items = obj.optJSONArray("items") ?: JSONArray()
        val suggestions = ArrayList<CandidateSuggestion>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val text = normalizeCandidateText(item.optString("text"))
            if (text.isBlank()) continue
            suggestions.add(
                CandidateSuggestion(
                    text = text,
                    confidence = item.optDouble("confidence", 0.6).toFloat(),
                    reason = item.optString("reason").ifBlank { null },
                )
            )
        }
        val normalizedLegacy = normalizeByLength(suggestions, request)
        if (normalizedLegacy.isNotEmpty()) return CandidateResponse(suggestions = normalizedLegacy)

        // Minimax OpenAI 兼容格式：choices[0].message.content
        val content = obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        if (content.isBlank()) return CandidateResponse(suggestions = emptyList())
        val cleaned = content
            .replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .trim()
        val lines = cleaned
            .split('\n')
            .map { normalizeCandidateText(it) }
            .filter { it.isNotBlank() }
            .take(5)
            .map { CandidateSuggestion(text = it, confidence = 0.7f, reason = "minimax-openai") }
        val normalizedLines = normalizeByLength(lines, request)
        if (normalizedLines.isNotEmpty()) return CandidateResponse(suggestions = normalizedLines)
        return CandidateResponse(suggestions = fallbackCandidates(request.userDraft, request.minCandidateChars, request.maxCandidateChars))
    }

    private fun fallbackCandidates(draft: String, minChars: Int, maxChars: Int): List<CandidateSuggestion> {
        if (draft.isBlank()) return emptyList()
        return listOf(
            CandidateSuggestion(expandToLength("我看到了你刚才输入的内容：$draft。结合当前页面信息，我建议先确认关键信息，再按步骤继续推进。", minChars, maxChars), 0.6f, "默认兜底"),
            CandidateSuggestion(expandToLength("基于你现在的输入“$draft”，我先给一个稳妥版本：先说明结论，再补充原因，最后给出下一步动作。", minChars, maxChars), 0.55f, "默认兜底"),
            CandidateSuggestion(expandToLength("结合当前上下文，我建议把“$draft”整理成更完整表达：先交代背景，再明确诉求，最后附上可执行的安排。", minChars, maxChars), 0.5f, "默认兜底"),
            CandidateSuggestion(expandToLength("针对“$draft”这个点，建议先给对方一个明确回应，再补充边界与时间预期，这样沟通会更顺畅。", minChars, maxChars), 0.45f, "默认兜底"),
            CandidateSuggestion(expandToLength("如果你想直接发送，我建议把“$draft”改成结果导向表达：先结论、后细节、再行动项，读起来更清楚。", minChars, maxChars), 0.4f, "默认兜底"),
        )
    }
}

class MinimaxImageUnderstandMcpClient(
) : ScreenshotUnderstandingClient {
    override suspend fun understand(
        packageName: String,
        screenshotBytes: ByteArray,
        promptHint: String,
    ): ScreenshotUnderstandingResult? = withContext(Dispatchers.IO) {
        val endpoint = resolveScreenshotApiUrl(runtimeScreenshotEndpoint())
        if (endpoint.isBlank() || screenshotBytes.isEmpty()) return@withContext null
        try {
            val screenshotPath = persistDebugScreenshot(screenshotBytes).orEmpty()
            val imageDataUrl = "data:image/jpeg;base64," +
                    android.util.Base64.encodeToString(screenshotBytes, android.util.Base64.NO_WRAP)
            val imagePrompt = "请用中文简要理解当前APP界面，输出页面用途、可操作意图和风险提示。app=$packageName hint=$promptHint"
            val payload = JSONObject().apply {
                put("prompt", imagePrompt)
                put("image_url", imageDataUrl)
            }
            AgentDebugLogStore.save(
                screenshotFilePath = screenshotPath,
                requestPrompt = imagePrompt
            )
            val raw = postJson(
                endpoint = endpoint,
                payload = payload,
                apiKey = runtimeApiKey(),
                isMcpToolRequest = true,
                channel = "image-understand",
            )
            val obj = JSONObject(raw.body)
            val result = ScreenshotUnderstandingResult(
                screenSummary = obj.optString("content")
                    .ifBlank { obj.optString("screenSummary").ifBlank { "未返回有效描述" } },
                uiIntentHints = obj.optJSONArray("uiIntentHints").toStringList(),
                riskTags = obj.optJSONArray("riskTags").toStringList(),
            )
            val debugResult = buildString {
                append("summary=").append(result.screenSummary)
                if (result.uiIntentHints.isNotEmpty()) {
                    append("\nuiIntentHints=").append(result.uiIntentHints.joinToString(" | "))
                }
                if (result.riskTags.isNotEmpty()) {
                    append("\nriskTags=").append(result.riskTags.joinToString(" | "))
                }
            }
            AgentDebugLogStore.save(
                screenshotUnderstandingResult = debugResult.take(1200)
            )
            result
        } catch (t: Throwable) {
            LogUtil.e("MinimaxImageClient", "understand", t.message ?: "unknown error")
            AgentDebugLogStore.save(
                screenshotUnderstandingResult = "请求失败：${t::class.java.simpleName}: ${t.message ?: "unknown error"}"
            )
            null
        }
    }
}

private data class HttpCallResult(
    val body: String,
    val statusCode: Int,
    val elapsedMs: Long
)

private fun postJson(
    endpoint: String,
    payload: JSONObject,
    apiKey: String,
    isMcpToolRequest: Boolean,
    channel: String
): HttpCallResult {
    val start = System.currentTimeMillis()
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15000
        readTimeout = 15000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (isMcpToolRequest) {
            setRequestProperty("MM-API-Source", "Minimax-MCP")
        }
        if (apiKey.isNotBlank()) {
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("X-API-Key", apiKey)
        }
    }
    return try {
        conn.outputStream.use { os -> os.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val statusCode = conn.responseCode
        val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        val elapsedMs = System.currentTimeMillis() - start
        AgentDebugLogStore.save(
            channel = channel,
            url = endpoint,
            statusCode = statusCode,
            elapsedMs = elapsedMs,
            error = "",
            responsePreview = body.take(600),
            requestPrompt = extractPromptPreview(payload).take(1800),
        )
        if (statusCode !in 200..299) {
            throw IllegalStateException("HTTP $statusCode: ${body.take(240)}")
        }
        HttpCallResult(body = body, statusCode = statusCode, elapsedMs = elapsedMs)
    } catch (t: Throwable) {
        val elapsedMs = System.currentTimeMillis() - start
        AgentDebugLogStore.save(
            channel = channel,
            url = endpoint,
            statusCode = -1,
            elapsedMs = elapsedMs,
            error = "${t::class.java.simpleName}: ${t.message ?: "unknown error"}",
            responsePreview = "",
            requestPrompt = extractPromptPreview(payload).take(1800),
        )
        throw t
    } finally {
        conn.disconnect()
    }
}

private fun extractPromptPreview(payload: JSONObject): String {
    val messages = payload.optJSONArray("messages")
    if (messages != null && messages.length() > 0) {
        val lines = ArrayList<String>(messages.length())
        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val role = message.optString("role").ifBlank { "unknown" }
            val content = message.optString("content").trim()
            if (content.isBlank()) continue
            lines.add("[$role] $content")
        }
        if (lines.isNotEmpty()) return lines.joinToString("\n")
    }
    val directPrompt = payload.optString("prompt").trim()
    if (directPrompt.isNotBlank()) return directPrompt
    return payload.toString()
}

private fun normalizeCandidateText(raw: String): String {
    return raw.trim()
        .trimStart('-', '1','2','3','4','5','6','7','8','9','0','.', '、', ' ')
        .replace(Regex("""^\s*候选[:：]\s*"""), "")
        .trim()
}

private fun normalizeByLength(
    suggestions: List<CandidateSuggestion>,
    request: CandidateRequest
): List<CandidateSuggestion> {
    val inRange = suggestions
        .map { it.copy(text = normalizeCandidateText(it.text)) }
        .filter { it.text.isNotBlank() }
        .filter { it.text.length in request.minCandidateChars..request.maxCandidateChars }
    if (inRange.isNotEmpty()) return inRange.take(request.maxCandidates)
    return suggestions
        .map { it.copy(text = normalizeCandidateText(it.text)) }
        .filter { it.text.isNotBlank() }
        .take(request.maxCandidates)
}

private fun expandToLength(text: String, minChars: Int, maxChars: Int): String {
    if (text.length in minChars..maxChars) return text
    var result = text
    while (result.length < minChars) {
        result += " 这样可以减少歧义，也更容易让对方快速理解并配合执行。"
    }
    return if (result.length > maxChars) result.take(maxChars) else result
}

private fun persistDebugScreenshot(screenshotBytes: ByteArray): String? {
    return try {
        val context = Launcher.instance.context
        val dir = File(context.cacheDir, "ai_debug").apply { mkdirs() }
        val file = File(dir, "last_image_understand.jpg")
        file.outputStream().use { it.write(screenshotBytes) }
        file.absolutePath
    } catch (t: Throwable) {
        LogUtil.e("MinimaxImageClient", "persistDebugScreenshot", t.message ?: "unknown error")
        null
    }
}

private fun runtimeCandidateEndpoint(): String {
    val configured = AppPrefs.getInstance().voice.candidateEndpoint.getValue().trim()
    return if (configured.isNotBlank()) configured else BuildConfig.AI_CANDIDATE_ENDPOINT
}

private fun runtimeScreenshotEndpoint(): String {
    val configured = AppPrefs.getInstance().voice.screenshotEndpoint.getValue().trim()
    return if (configured.isNotBlank()) configured else BuildConfig.AI_SCREENSHOT_ENDPOINT
}

private fun runtimeApiKey(): String {
    return AppPrefs.getInstance().voice.apiKey.getValue().trim()
}

private fun runtimeModelName(): String {
    val configured = AppPrefs.getInstance().voice.modelName.getValue().trim()
    return if (configured.isNotBlank()) configured else "MiniMax-M2.7"
}

private fun resolveCandidateApiUrl(configuredValue: String): String {
    val fallbackHost = "https://api.minimaxi.com"
    val value = configuredValue.trim()
    if (value.isBlank()) return "$fallbackHost/v1/chat/completions"
    if (!value.startsWith("http")) return "$fallbackHost/v1/chat/completions"
    if (value.contains("/v1/chat/completions")) return value
    return value.trimEnd('/') + "/v1/chat/completions"
}

private fun resolveScreenshotApiUrl(configuredValue: String): String {
    val fallbackHost = "https://api.minimaxi.com"
    val value = configuredValue.trim()
    if (value.isBlank()) return "$fallbackHost/v1/coding_plan/vlm"
    if (!value.startsWith("http")) return "$fallbackHost/v1/coding_plan/vlm"
    if (value.contains("/v1/coding_plan/vlm")) return value
    return value.trimEnd('/') + "/v1/coding_plan/vlm"
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val result = ArrayList<String>(length())
    for (i in 0 until length()) {
        optString(i).takeIf { it.isNotBlank() }?.let(result::add)
    }
    return result
}
