package com.aiime.engine

import com.aiime.accessibility.ScreenContext
import com.aiime.data.KnowledgeEntry
import kotlinx.coroutines.flow.Flow

/**
 * AI 引擎接口
 *
 * 所有 provider 实现都必须支持流式生成和多轮对话。
 * 通过 AiEngineFactory.create() 获取当前激活的实例。
 */
interface AiEngine {

    fun generateStream(
        userIntent: String,
        sceneContext: SceneContext,
        screenContext: ScreenContext?,
        knowledgeEntries: List<KnowledgeEntry>,
        conversationHistory: List<ConversationMessage>,
        config: AiProviderConfig
    ): Flow<GenerationEvent>

    sealed class GenerationEvent {
        object Start : GenerationEvent()
        data class Delta(val text: String) : GenerationEvent()
        data class Complete(val fullText: String) : GenerationEvent()
        data class Error(val message: String) : GenerationEvent()
    }
}
