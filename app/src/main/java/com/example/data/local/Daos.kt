package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs ORDER BY isLocal ASC, displayName ASC")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs")
    suspend fun getAll(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY isDefault DESC, isFavorite DESC, lastUsedAt DESC, displayName ASC")
    fun observeAll(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE providerConfigId = :providerId")
    suspend fun getByProvider(providerId: String): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): AiModelEntity?

    @Query("SELECT * FROM ai_models WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultModel(): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AiModelEntity>)

    @Query("UPDATE ai_models SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE ai_models SET isDefault = 1 WHERE `key` = :key")
    suspend fun setDefault(key: String)

    @Query("UPDATE ai_models SET isFavorite = :favorite WHERE `key` = :key")
    suspend fun setFavorite(key: String, favorite: Boolean)

    @Query("UPDATE ai_models SET lastUsedAt = :timestamp WHERE `key` = :key")
    suspend fun markUsed(key: String, timestamp: Long)

    @Query("DELETE FROM ai_models WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM ai_models WHERE providerConfigId = :providerId AND isManual = 0")
    suspend fun deleteFetchedForProvider(providerId: String)

    @Query("DELETE FROM ai_models WHERE providerConfigId = :providerId")
    suspend fun deleteAllForProvider(providerId: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProjectFileDao {
    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY path ASC")
    fun observeForProject(projectId: String): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY path ASC")
    suspend fun getForProject(projectId: String): List<ProjectFileEntity>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId AND path = :path LIMIT 1")
    suspend fun getByPath(projectId: String, path: String): ProjectFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM project_files WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :afterTimestamp")
    suspend fun deleteAfter(conversationId: String, afterTimestamp: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromTimestamp")
    suspend fun deleteFrom(conversationId: String, fromTimestamp: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY isEnabled DESC, isBuiltIn ASC, name ASC")
    fun observeAll(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY name ASC")
    suspend fun getAll(): List<SkillEntity>

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SkillEntity>)

    @Query("UPDATE skills SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE skills SET isTrusted = :trusted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrusted(id: String, trusted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SystemPromptPresetDao {
    @Query("SELECT * FROM system_prompt_presets ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<SystemPromptPresetEntity>>

    @Query("SELECT * FROM system_prompt_presets")
    suspend fun getAll(): List<SystemPromptPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SystemPromptPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SystemPromptPresetEntity>)

    @Query("DELETE FROM system_prompt_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}
