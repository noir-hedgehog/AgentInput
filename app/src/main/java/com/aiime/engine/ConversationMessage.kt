package com.aiime.engine

/**
 * 多轮对话中的单条消息
 *
 * role = "user"  → 用户的意图/追问（不是最终发送的文字，是给 AI 的指令）
 * role = "assistant" → AI 生成的结果
 *
 * 这个历史列表直接映射到 Claude Messages API 的 messages 数组。
 */
data class ConversationMessage(
    val role: String,   // "user" | "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
}
