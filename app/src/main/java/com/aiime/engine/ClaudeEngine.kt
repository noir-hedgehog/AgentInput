package com.aiime.engine

import android.util.Log
import com.aiime.accessibility.ScreenContext
import com.aiime.data.KnowledgeEntry
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude 引擎
 *
 * 使用 Anthropic 专属 API 格式：
 * - Header: x-api-key + anthropic-version
 * - system 字段在请求体顶层（而非 messages[0]）
 * - 端点：POST /v1/messages
 */
class ClaudeEngine : AiEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "ClaudeEngine"
    }

    override fun generateStream(
        userIntent: String,
        sceneContext: SceneContext,
        screenContext: ScreenContext?,
        knowledgeEntries: List<KnowledgeEntry>,
        conversationHistory: List<ConversationMessage>,
        config: AiProviderConfig
    ): Flow<AiEngine.GenerationEvent> = flow {
        if (config.apiKey.isBlank()) {
            emit(AiEngine.GenerationEvent.Error("请配置 Anthropic API Key"))
            return@flow
        }
        emit(AiEngine.GenerationEvent.Start)

        val systemPrompt = PromptBuilder.buildSystemPrompt(sceneContext, screenContext, knowledgeEntries)
        val messages = buildMessages(userIntent, sceneContext, conversationHistory)

        val body = gson.toJson(ClaudeRequest(
            model = config.modelId,
            maxTokens = config.maxTokens,
            stream = true,
            system = systemPrompt,
            messages = messages
        ))

        val request = Request.Builder()
            .url("${config.baseUrl}/v1/messages")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                emit(AiEngine.GenerationEvent.Error("Claude API ${response.code}: ${parseError(err)}"))
                return@flow
            }

            val fullText = StringBuilder()
            BufferedReader(InputStreamReader(response.body!!.byteStream())).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val data = line!!.trim().removePrefix("data: ")
                    if (data.isEmpty() || data == "[DONE]") continue
                    try {
                        val ev = gson.fromJson(data, StreamEvent::class.java)
                        when (ev.type) {
                            "content_block_delta" -> {
                                val txt = ev.delta?.text ?: continue
                                fullText.append(txt)
                                emit(AiEngine.GenerationEvent.Delta(txt))
                            }
                            "message_stop" -> break
                            "error" -> {
                                emit(AiEngine.GenerationEvent.Error(ev.error?.message ?: "流错误"))
                                return@flow
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            emit(AiEngine.GenerationEvent.Complete(fullText.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            emit(AiEngine.GenerationEvent.Error("网络错误：${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildMessages(
        userIntent: String,
        sceneContext: SceneContext,
        history: List<ConversationMessage>
    ): List<ApiMessage> {
        val result = history.map { ApiMessage(it.role, it.content) }.toMutableList()
        result.add(ApiMessage("user",
            PromptBuilder.buildUserMessageContent(userIntent, sceneContext.existingText, history.isEmpty())
        ))
        return result
    }

    private fun parseError(body: String): String = try {
        gson.fromJson(body, ErrorWrapper::class.java).error?.message ?: body.take(200)
    } catch (_: Exception) { body.take(200) }

    // ---- 数据类 ----

    private data class ClaudeRequest(
        val model: String,
        @SerializedName("max_tokens") val maxTokens: Int,
        val stream: Boolean,
        val system: String,
        val messages: List<ApiMessage>
    )

    private data class ApiMessage(val role: String, val content: String)

    private data class StreamEvent(
        val type: String?,
        val delta: Delta?,
        val error: ErrorDetail?
    )

    private data class Delta(val type: String?, val text: String?)
    private data class ErrorDetail(val type: String?, val message: String?)
    private data class ErrorWrapper(val error: ErrorDetail?)
}
