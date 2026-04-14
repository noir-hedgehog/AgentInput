package com.aiime.engine

import android.content.Context
import android.content.pm.PackageManager
import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * 场景识别引擎
 *
 * 通过分析 EditorInfo（输入法服务提供的当前输入框元信息）
 * 以及包名，推断用户当前所在的应用场景。
 *
 * 不需要 AccessibilityService 权限，仅用 IME 自带的上下文信息。
 */
class ContextAnalyzer(private val context: Context) {

    // 包名 → 场景类型映射
    private val packageToScene = mapOf(
        "com.tencent.mm" to SceneType.WECHAT,
        "com.tencent.mobileqq" to SceneType.WECHAT,
        "org.telegram.messenger" to SceneType.TELEGRAM,
        "com.whatsapp" to SceneType.WHATSAPP,
        "com.facebook.katana" to SceneType.SOCIAL_MEDIA,
        "com.instagram.android" to SceneType.SOCIAL_MEDIA,
        "com.twitter.android" to SceneType.SOCIAL_MEDIA,
        "com.sina.weibo" to SceneType.SOCIAL_MEDIA,
        "com.zhihu.android" to SceneType.SOCIAL_MEDIA,
        "com.google.android.gm" to SceneType.EMAIL,
        "com.netease.mail" to SceneType.EMAIL,
        "com.tencent.androidqqmail" to SceneType.EMAIL,
        "com.microsoft.office.outlook" to SceneType.EMAIL,
        "com.slack" to SceneType.SLACK,
        "com.alibaba.android.rimet" to SceneType.DINGTALK,
        "com.larksuite.lark" to SceneType.FEISHU,
        "com.lark.android" to SceneType.FEISHU,
        "com.android.chrome" to SceneType.BROWSER,
        "org.mozilla.firefox" to SceneType.BROWSER,
        "com.taobao.taobao" to SceneType.SHOPPING,
        "com.jingdong.app.mall" to SceneType.SHOPPING,
    )

    fun analyze(editorInfo: EditorInfo, currentPackage: String): SceneContext {
        val sceneType = detectSceneType(editorInfo, currentPackage)
        val appName = getAppName(currentPackage)
        val inputTypeName = describeInputType(editorInfo.inputType)
        val inputHint = editorInfo.hintText?.toString() ?: ""
        val inputLabel = editorInfo.label?.toString() ?: ""

        val description = buildDescription(
            appName = appName,
            sceneType = sceneType,
            inputHint = inputHint,
            inputLabel = inputLabel,
            inputTypeName = inputTypeName
        )

        return SceneContext(
            packageName = currentPackage,
            appName = appName,
            sceneType = sceneType,
            inputHint = inputHint,
            inputLabel = inputLabel,
            inputTypeName = inputTypeName,
            humanReadableDescription = description
        )
    }

    private fun detectSceneType(editorInfo: EditorInfo, packageName: String): SceneType {
        // 1. 优先按包名精确匹配
        packageToScene[packageName]?.let { return it }

        // 2. 按包名前缀模糊匹配
        if (packageName.contains("mail") || packageName.contains("email")) {
            return SceneType.EMAIL
        }
        if (packageName.contains("chat") || packageName.contains("message") || packageName.contains("im.")) {
            return SceneType.WORK_GENERAL
        }

        // 3. 按 EditorInfo 的 hint/label 推断
        val hint = editorInfo.hintText?.toString()?.lowercase() ?: ""
        val label = editorInfo.label?.toString()?.lowercase() ?: ""
        val combined = "$hint $label"

        if (combined.contains("search") || combined.contains("搜索") || combined.contains("查找")) {
            return SceneType.SEARCH
        }
        if (combined.contains("message") || combined.contains("消息") || combined.contains("发送")) {
            return SceneType.WECHAT // 通用消息场景
        }
        if (combined.contains("email") || combined.contains("邮件") || combined.contains("subject") || combined.contains("主题")) {
            return SceneType.EMAIL
        }

        // 4. 按输入类型推断
        val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                return SceneType.EMAIL
            }
        }

        return SceneType.GENERAL
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun describeInputType(inputType: Int): String {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        return when (cls) {
            InputType.TYPE_CLASS_TEXT -> {
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "邮件地址"
                    InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> "邮件主题"
                    InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> "短消息"
                    InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> "长消息"
                    InputType.TYPE_TEXT_VARIATION_URI -> "网址"
                    InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> "地址"
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> "人名"
                    else -> "普通文本"
                }
            }
            InputType.TYPE_CLASS_NUMBER -> "数字"
            InputType.TYPE_CLASS_PHONE -> "电话号码"
            InputType.TYPE_CLASS_DATETIME -> "日期时间"
            else -> "文本"
        }
    }

    private fun buildDescription(
        appName: String,
        sceneType: SceneType,
        inputHint: String,
        inputLabel: String,
        inputTypeName: String
    ): String {
        val parts = mutableListOf<String>()
        if (appName.isNotEmpty()) parts.add("应用：$appName")
        parts.add("场景：${sceneType.label}")
        if (inputHint.isNotEmpty()) parts.add("输入框提示：$inputHint")
        if (inputLabel.isNotEmpty() && inputLabel != inputHint) parts.add("字段名：$inputLabel")
        if (inputTypeName.isNotEmpty()) parts.add("输入类型：$inputTypeName")
        return parts.joinToString("，")
    }
}
