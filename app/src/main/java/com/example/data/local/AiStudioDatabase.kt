package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderConfigEntity::class,
        AiModelEntity::class,
        ProjectEntity::class,
        ProjectFileEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        SkillEntity::class,
        SystemPromptPresetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AiStudioDatabase : RoomDatabase() {
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectFileDao(): ProjectFileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun skillDao(): SkillDao
    abstract fun systemPromptPresetDao(): SystemPromptPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AiStudioDatabase? = null

        fun getInstance(context: Context): AiStudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiStudioDatabase::class.java,
                    "ai_studio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
