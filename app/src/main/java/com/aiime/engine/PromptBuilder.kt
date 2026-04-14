package com.aiime.engine

import com.aiime.accessibility.ScreenContext
import com.aiime.data.KnowledgeEntry

/**
 * 公共 Prompt 构建逻辑，两套引擎实现共用
 */
object PromptBuilder {

    fun buildSystemPrompt(
        sceneContext: SceneContext,
        screenContext: ScreenContext?,
        knowledgeEntries: List<KnowledgeEntry>
    ): String = buildString {
        appendLine("你是一个智能输入助手，帮助用户在手机上快速生成合适的文字内容。")
        appendLine("在多轮对话中，根据用户追问持续修改优化上一次的结果。")
        appendLine()

        appendLine("## 当前输入场景")
        appendLine(sceneContext.humanReadableDescription)
        appendLine()

        val screenSummary = screenContext?.toPromptSummary()
        if (!screenSummary.isNullOrEmpty()) {
            appendLine("## 屏幕上下文（用户正在查看的内容）")
            appendLine(screenSummary)
            appendLine()
        }

        if (knowledgeEntries.isNotEmpty()) {
            appendLine("## 用户知识库（参考风格，不要直接复制）")
            knowledgeEntries.forEach { appendLine("【${it.title}】${it.content}") }
            appendLine()
        }

        appendLine("## 输出要求")
        appendLine("- 直接输出最终文字，不加解释、前缀或 Markdown 标签")
        appendLine("- 工作场景用正式语气，聊天场景用自然口语")
        appendLine("- 如追问「更正式」「缩短」「换个说法」，给出修改后的完整版本")
        appendLine("- 默认中文，除非用户要求其他语言")
    }

    fun buildUserMessageContent(
        userIntent: String,
        existingText: String,
        isFirstTurn: Boolean
    ): String {
        return if (existingText.isNotEmpty() && isFirstTurn) {
            "输入框当前已有内容：「$existingText」\n\n我的需求：$userIntent"
        } else {
            userIntent
        }
    }
}
