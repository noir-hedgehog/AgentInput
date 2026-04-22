package com.yuyan.imemodule.scene

enum class SceneType {
    CHAT_REPLY,
    GENERIC_TEXT
}

data class SceneProfile(
    val sceneType: SceneType,
    val allowScreenshot: Boolean,
    val maxCandidates: Int
)

object ScenePolicyEngine {
    private val chatPackages = setOf(
        "com.tencent.mm",
        "com.tencent.mobileqq",
        "com.xingin.xhs",
        "com.twitter.android",
    )

    fun resolve(packageName: String?): SceneProfile {
        return if (!packageName.isNullOrBlank() && chatPackages.contains(packageName)) {
            SceneProfile(
                sceneType = SceneType.CHAT_REPLY,
                allowScreenshot = true,
                maxCandidates = 5,
            )
        } else {
            SceneProfile(
                sceneType = SceneType.GENERIC_TEXT,
                allowScreenshot = false,
                maxCandidates = 5,
            )
        }
    }
}
