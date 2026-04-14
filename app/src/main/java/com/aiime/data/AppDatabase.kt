package com.aiime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [KnowledgeEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aiime_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // 预填充一些示例知识条目
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    populateSampleData(database.knowledgeDao())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateSampleData(dao: KnowledgeDao) {
            dao.insert(KnowledgeEntry(
                title = "工作邮件开头",
                content = "您好，希望这封邮件找到您一切安好。",
                tags = "邮件,问候,正式",
                scene = "email"
            ))
            dao.insert(KnowledgeEntry(
                title = "会议邀请模板",
                content = "我想邀请您参加关于[主题]的会议，时间为[时间]，地点为[地点]，请确认您是否方便出席。",
                tags = "会议,邀请,日程",
                scene = "work"
            ))
            dao.insert(KnowledgeEntry(
                title = "感谢回复",
                content = "非常感谢您的帮助！这对我们的工作很有价值。",
                tags = "感谢,回复,礼貌",
                scene = "work"
            ))
            dao.insert(KnowledgeEntry(
                title = "朋友问候",
                content = "最近怎么样？好久不见，有空一起出来聚聚！",
                tags = "聊天,朋友,问候",
                scene = "chat"
            ))
            dao.insert(KnowledgeEntry(
                title = "技术文档说明",
                content = "该功能的实现采用了[技术]方案，主要考虑到了性能和可维护性的平衡。",
                tags = "技术,文档,说明",
                scene = "work"
            ))
        }
    }
}
