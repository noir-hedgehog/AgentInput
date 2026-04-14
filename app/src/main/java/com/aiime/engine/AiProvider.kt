package com.aiime.engine

/**
 * AI Provider 类型
 *
 * ANTHROPIC  — 使用 Anthropic 专属 API 格式
 * 其余全部  — 使用 OpenAI-compatible Chat Completions 格式
 */
enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val presetModels: List<ModelOption>,
    val apiKeyPlaceholder: String,
    val docUrl: String
) {
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com",
        presetModels = listOf(
            ModelOption("claude-opus-4-6",          "Claude Opus 4.6  · 最强推理"),
            ModelOption("claude-sonnet-4-6",         "Claude Sonnet 4.6 · 均衡"),
            ModelOption("claude-haiku-4-5-20251001", "Claude Haiku 4.5  · 极速"),
        ),
        apiKeyPlaceholder = "sk-ant-api03-…",
        docUrl = "https://console.anthropic.com/settings/keys"
    ),

    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com",
        presetModels = listOf(
            ModelOption("gpt-4o",      "GPT-4o  · 多模态旗舰"),
            ModelOption("gpt-4o-mini", "GPT-4o mini  · 快速经济"),
            ModelOption("o1-mini",     "o1-mini  · 推理增强"),
            ModelOption("gpt-4-turbo", "GPT-4 Turbo  · 长上下文"),
        ),
        apiKeyPlaceholder = "sk-…",
        docUrl = "https://platform.openai.com/api-keys"
    ),

    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        presetModels = listOf(
            ModelOption("gemini-2.0-flash",   "Gemini 2.0 Flash  · 极速"),
            ModelOption("gemini-1.5-pro",     "Gemini 1.5 Pro  · 长上下文"),
            ModelOption("gemini-1.5-flash",   "Gemini 1.5 Flash · 均衡"),
        ),
        apiKeyPlaceholder = "AIzaSy…",
        docUrl = "https://aistudio.google.com/app/apikey"
    ),

    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        presetModels = listOf(
            ModelOption("deepseek-chat",     "DeepSeek V3  · 通用对话"),
            ModelOption("deepseek-reasoner", "DeepSeek R1  · 深度推理"),
        ),
        apiKeyPlaceholder = "sk-…",
        docUrl = "https://platform.deepseek.com/api_keys"
    ),

    MINIMAX(
        displayName = "MiniMax",
        defaultBaseUrl = "https://api.minimax.chat/v1",
        presetModels = listOf(
            ModelOption("MiniMax-Text-01", "Text-01  · 最强文本"),
            ModelOption("MiniMax-4o",      "4o  · 多模态旗舰"),
            ModelOption("MiniMax-o4",      "o4  · 推理增强"),
        ),
        apiKeyPlaceholder = "eyJ…",
        docUrl = "https://platform.minimax.chat"
    ),

    CUSTOM(
        displayName = "自定义 (OpenAI 兼容)",
        defaultBaseUrl = "",
        presetModels = emptyList(),
        apiKeyPlaceholder = "your-api-key",
        docUrl = ""
    );
}

data class ModelOption(
    val id: String,
    val label: String
)

/**
 * 单个 Provider 的完整配置
 *
 * 存储在 DataStore 中（JSON），多个 provider 共存，
 * isActive = true 的那个是当前使用的引擎。
 */
data class AiProviderConfig(
    val type: AiProviderType,
    val apiKey: String = "",
    val modelId: String = type.presetModels.firstOrNull()?.id ?: "",
    val customBaseUrl: String = type.defaultBaseUrl,
    val maxTokens: Int = 1024,
    val isActive: Boolean = false
) {
    val displayName: String get() = type.displayName

    val baseUrl: String get() = if (type == AiProviderType.CUSTOM) customBaseUrl
    else type.defaultBaseUrl

    val isConfigured: Boolean get() = apiKey.isNotBlank() && modelId.isNotBlank()
        && (type != AiProviderType.CUSTOM || customBaseUrl.isNotBlank())
}
