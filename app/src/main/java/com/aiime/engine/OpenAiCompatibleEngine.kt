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
 * OpenAI 兼容引擎
 *
 * 覆盖所有使用 Chat Completions 格式的 provider：
 *   • OpenAI (api.openai.com)
 *   • Google Gemini (generativelanguage.googleapis.com/v1beta/openai)
 *   • DeepSeek (api.deepseek.com/v1)
 *   • 任何自定义 OpenAI-compatible 接口
 *
 * 格式：
 *   Header: Authorization: Bearer <apiKey>
 *   Body:   { model, stream, messages: [{role: "system"|"user"|"assistant", content}] }
 *   Endpoint: POST <baseUrl>/chat/completions
 */
class OpenAiCompatibleEngine : AiEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "OpenAiEngine"
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
            emit(AiEngine.GenerationEvent.Error("请配置 ${config.displayName} API Key"))
            return@flow
        }
        emit(AiEngine.GenerationEvent.Start)

        val systemPrompt = PromptBuilder.buildSystemPrompt(sceneContext, screenContext, knowledgeEntries)
        val messages = buildMessages(userIntent, sceneContext, systemPrompt, conversationHistory)

        val body = gson.toJson(OpenAiRequest(
            model = config.modelId,
            stream = true,
            messages = messages,
            // 部分 provider（如 o1 系列）不支持 max_tokens，用 max_completion_tokens
            // 这里用通用的 max_tokens，兼容大多数情况
            maxTokens = config.maxTokens
        ))

        // baseUrl 末尾可能有/v1，统一拼 /chat/completions
        val baseUrl = config.baseUrl.trimEnd('/')
        val url = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions"
                  else "$baseUrl/v1/chat/completions"

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                emit(AiEngine.GenerationEvent.Error("${config.displayName} API ${response.code}: ${parseError(err)}"))
                return@flow
            }

            val fullText = StringBuilder()
            BufferedReader(InputStreamReader(response.body!!.byteStream())).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val raw = line!!.trim()
                    if (!raw.startsWith("data: ")) continue
                    val data = raw.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        val delta = chunk.choices?.firstOrNull()?.delta
                        val finishReason = chunk.choices?.firstOrNull()?.finishReason

                        if (!delta?.content.isNullOrEmpty()) {
                            val txt = delta!!.content!!
                            fullText.append(txt)
                            emit(AiEngine.GenerationEvent.Delta(txt))
                        }
                        if (finishReason == "stop") break
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
        systemPrompt: String,
        history: List<ConversationMessage>
    ): List<OaiMessage> {
        val result = mutableListOf<OaiMessage>()
        // OpenAI 格式：system 作为第一条消息
        result.add(OaiMessage("system", systemPrompt))
        // 历史对话
        history.forEach { result.add(OaiMessage(it.role, it.content)) }
        // 本次意图
        result.add(OaiMessage("user",
            PromptBuilder.buildUserMessageContent(userIntent, sceneContext.existingText, history.isEmpty())
        ))
        return result
    }

    private fun parseError(body: String): String = try {
        gson.fromJson(body, OaiErrorWrapper::class.java).error?.message ?: body.take(200)
    } catch (_: Exception) { body.take(200) }

    // ---- 数据类 ----

    private data class OpenAiRequest(
        val model: String,
        val stream: Boolean,
        val messages: List<OaiMessage>,
        @SerializedName("max_tokens") val maxTokens: Int
    )

    private data class OaiMessage(val role: String, val content: String)

    private data class StreamChunk(
        val choices: List<Choice>?
    )

    private data class Choice(
        val delta: DeltaContent?,
        @SerializedName("finish_reason") val finishReason: String?
    )

    private data class DeltaContent(val content: String?)

    private data class OaiErrorWrapper(val error: OaiError?)
    private data class OaiError(val message: String?)
}
