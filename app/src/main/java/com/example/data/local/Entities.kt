package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val displayName: String,
    val baseUrl: String,
    val customHeadersJson: String = "{}",
    val isEnabled: Boolean = true,
    val isLocal: Boolean = false,
    val lastStatus: String = "UNTESTED",
    val lastStatusMessage: String = "",
    val lastTestedAt: Long = 0L
)

@Entity(
    tableName = "ai_models",
    indices = [Index("providerConfigId")]
)
data class AiModelEntity(
    @PrimaryKey val key: String,
    val modelId: String,
    val displayName: String,
    val providerConfigId: String,
    val providerType: String,
    val contextWindow: Int? = null,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsSystemPrompt: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsFrequencyPenalty: Boolean = false,
    val supportsPresencePenalty: Boolean = false,
    val supportsStopSequences: Boolean = true,
    val supportsPdfNative: Boolean = false,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = 0L,
    val isManual: Boolean = false
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val defaultModelKey: String? = null,
    val selectedSkillIdsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectFileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val path: String,
    val content: String,
    val mimeType: String = "text/plain",
    val sizeBytes: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [Index("projectId")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val projectId: String? = null,
    val modelKey: String? = null,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val streamingEnabled: Boolean = true,
    val stopSequencesJson: String = "[]",
    val webSearchEnabled: Boolean = false,
    val activeSkillIdsJson: String = "[]",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val attachmentsJson: String = "[]",
    val citationsJson: String = "[]",
    val skillsUsedJson: String = "[]",
    val modelUsed: String = "",
    val tokenInfo: String = "",
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val skillMdContent: String,
    val instructionsBody: String,
    val supportingFilesJson: String = "[]",
    val requiredPermissionsJson: String = "[]",
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val isTrusted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_prompt_presets")
data class SystemPromptPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val content: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
