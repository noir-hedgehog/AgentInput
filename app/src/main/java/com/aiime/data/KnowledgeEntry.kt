package com.aiime.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 知识库条目
 *
 * 用于存储用户的常用回复模板、专业术语、个人风格等，
 * AI 在生成内容时会检索匹配的知识条目作为上下文参考。
 */
@Entity(tableName = "knowledge_entries")
data class KnowledgeEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 条目标题，方便用户管理 */
    val title: String,

    /** 条目正文内容，会被注入 AI prompt */
    val content: String,

    /** 逗号分隔的标签，用于检索匹配 */
    val tags: String = "",

    /** 适用场景标识，如 "work", "chat", "email", "social" */
    val scene: String = "",

    /** 使用次数，用于排序 */
    val useCount: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
)
