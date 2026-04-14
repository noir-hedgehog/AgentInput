package com.aiime.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AI 辅助无障碍服务
 *
 * 职责：监听屏幕变化，从 UI 树中提取有价值的文字信息，
 * 写入 AccessibilityContextBridge 供 IME 服务读取。
 *
 * 设计原则：
 * - 只抓取文本，不做任何操控
 * - 过滤无意义的控件（按钮标签、底部导航等）
 * - 识别 IM 对话列表，提取对话上下文
 */
class AiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AiAccessService"

        // 过滤掉这些类型控件的文本（导航栏、按钮等无意义信息）
        private val SKIP_CLASS_NAMES = setOf(
            "android.widget.ImageButton",
            "android.widget.ImageView",
            "android.widget.ProgressBar",
            "android.widget.SeekBar",
            "android.widget.CheckBox",
            "android.widget.RadioButton",
            "android.widget.Switch",
        )

        // IM 类应用的包名前缀，对这些应用做对话历史抓取
        private val IM_PACKAGES = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.slack",
            "com.alibaba.android.rimet",
            "com.larksuite.lark",
            "com.lark.android",
        )

        // 单次抓取节点数上限，防止遍历过深
        private const val MAX_NODES = 200

        // 文本最短有效长度
        private const val MIN_TEXT_LENGTH = 2
    }

    private var lastPackage = ""
    private var lastWindowTitle = ""
    private var lastContentHash = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 节流：相同包名+窗口标题+内容哈希不重复抓取
                val pkg = event.packageName?.toString() ?: return
                val title = event.text?.joinToString(" ") ?: ""

                if (pkg == lastPackage && title == lastWindowTitle) return
                lastPackage = pkg
                lastWindowTitle = title

                captureScreenContext(pkg, title)
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    // ---- 屏幕上下文抓取 ----

    private fun captureScreenContext(packageName: String, windowTitle: String) {
        try {
            val rootNode = rootInActiveWindow ?: return
            val isImApp = IM_PACKAGES.any { packageName.startsWith(it) }

            val texts = mutableListOf<String>()
            val conversations = mutableListOf<String>()

            traverseNode(rootNode, texts, conversations, isImApp, depth = 0)
            rootNode.recycle()

            // 去重 + 过滤
            val filtered = texts.filter { it.length >= MIN_TEXT_LENGTH }.distinct()
            val hash = filtered.hashCode()
            if (hash == lastContentHash) return
            lastContentHash = hash

            val context = ScreenContext(
                windowTitle = windowTitle.trim(),
                visibleTexts = filtered,
                conversationHistory = conversations,
                packageName = packageName,
                capturedAt = System.currentTimeMillis()
            )

            AccessibilityContextBridge.update(context)
            Log.d(TAG, "Context updated: pkg=$packageName texts=${filtered.size} convs=${conversations.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screen context", e)
        }
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        conversations: MutableList<String>,
        isImApp: Boolean,
        depth: Int
    ) {
        if (node == null || depth > 15 || texts.size + conversations.size > MAX_NODES) return

        val className = node.className?.toString() ?: ""

        // 跳过纯图形控件
        if (className in SKIP_CLASS_NAMES) {
            node.recycle()
            return
        }

        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()

        // IM 对话气泡：RecyclerView / ListView 的直接子文本视图
        if (isImApp && isConversationBubble(node, className, depth)) {
            val bubbleText = text ?: contentDesc
            if (!bubbleText.isNullOrEmpty() && bubbleText.length >= MIN_TEXT_LENGTH) {
                conversations.add(bubbleText)
            }
        } else {
            // 普通文本节点
            if (!text.isNullOrEmpty() && text.length >= MIN_TEXT_LENGTH) {
                texts.add(text)
            }
        }

        // 递归子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, texts, conversations, isImApp, depth + 1)
            child.recycle()
        }
    }

    /**
     * 判断一个节点是否是对话气泡（IM 消息文本）
     * 粗略判断：在列表容器中，深度适中，包含实质性文字
     */
    private fun isConversationBubble(
        node: AccessibilityNodeInfo,
        className: String,
        depth: Int
    ): Boolean {
        if (depth < 3 || depth > 12) return false
        // TextView 类型，有父级 RecyclerView/ListView
        if (!className.contains("TextView")) return false
        val parent = node.parent ?: return false
        val parentClass = parent.className?.toString() ?: ""
        parent.recycle()
        return parentClass.contains("RecyclerView") ||
                parentClass.contains("ListView") ||
                parentClass.contains("ViewGroup")
    }
}
