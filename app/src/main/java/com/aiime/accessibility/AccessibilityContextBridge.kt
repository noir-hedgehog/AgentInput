package com.aiime.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * IME ↔ AccessibilityService 共享数据桥
 *
 * 两个服务运行在同一进程中，通过这个单例安全地交换屏幕上下文。
 * IME 只读，AccessibilityService 只写。
 */
object AccessibilityContextBridge {

    private val _screenContext = MutableStateFlow(ScreenContext())
    val screenContext: StateFlow<ScreenContext> = _screenContext

    fun update(context: ScreenContext) {
        _screenContext.value = context
    }

    fun current(): ScreenContext = _screenContext.value
}

/**
 * 当前屏幕上下文快照
 *
 * @param windowTitle    当前窗口/Activity 标题（如"群聊：项目讨论"）
 * @param focusedText    焦点控件上方可见的文字（聊天记录最后几条、邮件正文等）
 * @param visibleTexts   屏幕上所有可见文字列表（去噪后），用于 AI 理解上下文
 * @param conversationHistory  检测到的对话历史（适用于 IM 类 App），格式 "发言人: 内容"
 * @param packageName    来源包名
 * @param capturedAt     抓取时间戳
 */
data class ScreenContext(
    val windowTitle: String = "",
    val focusedText: String = "",
    val visibleTexts: List<String> = emptyList(),
    val conversationHistory: List<String> = emptyList(),
    val packageName: String = "",
    val capturedAt: Long = 0L
) {
    val isEmpty: Boolean get() = windowTitle.isEmpty() && visibleTexts.isEmpty()

    /**
     * 生成供 AI 使用的上下文摘要，控制在合理长度
     */
    fun toPromptSummary(maxChars: Int = 800): String {
        if (isEmpty) return ""
        val sb = StringBuilder()
        if (windowTitle.isNotEmpty()) {
            sb.appendLine("当前页面：$windowTitle")
        }
        if (conversationHistory.isNotEmpty()) {
            sb.appendLine("最近对话记录：")
            // 取最近8条
            conversationHistory.takeLast(8).forEach { sb.appendLine("  $it") }
        } else if (visibleTexts.isNotEmpty()) {
            sb.appendLine("屏幕可见内容（节选）：")
            // 取前10条有意义的文本
            visibleTexts.take(10).forEach { sb.appendLine("  • $it") }
        }
        return sb.toString().take(maxChars)
    }
}
