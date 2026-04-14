package com.aiime.engine

/**
 * 根据 provider 配置返回对应的引擎实例
 *
 * 两个实现都是无状态的，可以全局复用单例。
 */
object AiEngineFactory {

    private val claudeEngine = ClaudeEngine()
    private val openAiEngine = OpenAiCompatibleEngine()

    fun create(config: AiProviderConfig): AiEngine = when (config.type) {
        AiProviderType.ANTHROPIC -> claudeEngine
        else                     -> openAiEngine   // OpenAI / Gemini / DeepSeek / Custom
    }
}
