package com.aiime.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    @Query("SELECT * FROM knowledge_entries ORDER BY useCount DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<KnowledgeEntry>>

    /**
     * 按场景 + 关键词检索知识条目
     * 支持在 title、content、tags 中模糊匹配
     */
    @Query("""
        SELECT * FROM knowledge_entries
        WHERE (scene = :scene OR scene = '' OR :scene = '')
        AND (
            title LIKE '%' || :keyword || '%'
            OR content LIKE '%' || :keyword || '%'
            OR tags LIKE '%' || :keyword || '%'
        )
        ORDER BY useCount DESC
        LIMIT :limit
    """)
    suspend fun searchEntries(scene: String, keyword: String, limit: Int = 5): List<KnowledgeEntry>

    /**
     * 按场景获取最常用的条目
     */
    @Query("""
        SELECT * FROM knowledge_entries
        WHERE scene = :scene OR scene = ''
        ORDER BY useCount DESC
        LIMIT :limit
    """)
    suspend fun getEntriesByScene(scene: String, limit: Int = 3): List<KnowledgeEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: KnowledgeEntry): Long

    @Update
    suspend fun update(entry: KnowledgeEntry)

    @Delete
    suspend fun delete(entry: KnowledgeEntry)

    @Query("UPDATE knowledge_entries SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Long)
}
