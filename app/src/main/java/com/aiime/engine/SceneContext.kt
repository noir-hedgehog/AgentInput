package com.aiime.engine

/**
 * 当前输入场景的上下文快照
 *
 * 由 ContextAnalyzer 从 EditorInfo + 系统信息中提取，
 * 传给 AI 做场景识别和内容生成。
 */
data class SceneContext(
    /** 当前应用包名，如 "com.tencent.mm" */
    val packageName: String = "",

    /** 推断出的应用名称 */
    val appName: String = "",

    /** 推断出的场景类型 */
    val sceneType: SceneType = SceneType.UNKNOWN,

    /** 输入框的 hint 文字（如"搜索"、"发送消息"） */
    val inputHint: String = "",

    /** 输入框的 label */
    val inputLabel: String = "",

    /** 当前输入框已有的文字 */
    val existingText: String = "",

    /** 输入类型（文本、数字、邮件等） */
    val inputTypeName: String = "",

    /** 人类可读的场景描述，注入到 AI prompt */
    val humanReadableDescription: String = ""
)

enum class SceneType(val label: String, val sceneKey: String) {
    // 社交通讯
    WECHAT("微信", "chat"),
    WHATSAPP("WhatsApp", "chat"),
    TELEGRAM("Telegram", "chat"),
    SOCIAL_MEDIA("社交媒体", "social"),

    // 工作效率
    EMAIL("邮件", "email"),
    SLACK("Slack", "work"),
    DINGTALK("钉钉", "work"),
    FEISHU("飞书", "work"),
    WORK_GENERAL("工作应用", "work"),

    // 搜索与浏览
    SEARCH("搜索", "search"),
    BROWSER("浏览器", "browse"),

    // 购物
    SHOPPING("购物", "shopping"),

    // 其他
    GENERAL("通用", "general"),
    UNKNOWN("未知", "general")
}
