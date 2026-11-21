package com.example.data.repository

import com.example.core.security.SecureKeyStore
import com.example.data.local.AiStudioDatabase
import com.example.data.local.EntityMappers.toDomain
import com.example.data.local.EntityMappers.toEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.domain.files.FileProcessor
import com.example.domain.model.*
import com.example.domain.provider.*
import com.example.domain.search.WebSearchService
import com.example.domain.skills.BuiltInSkillsSeed
import com.example.domain.skills.SkillManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class AiStudioRepository(
    private val db: AiStudioDatabase,
    private val keyStore: SecureKeyStore,
    private val prefsRepo: UserPreferencesRepository,
    val fileProcessor: FileProcessor,
    val skillManager: SkillManager,
    val webSearchService: WebSearchService
) {
    val providersFlow: Flow<List<ProviderConfig>> =
        db.providerConfigDao().observeAll().map { list -> list.map { it.toDomain() } }

    val modelsFlow: Flow<List<AiModel>> =
        db.aiModelDao().observeAll().map { list -> list.map { it.toDomain() } }

    val projectsFlow: Flow<List<ProjectWorkspace>> =
        db.projectDao().observeAll().map { list -> list.map { it.toDomain() } }

    val conversationsFlow: Flow<List<Conversation>> =
        db.conversationDao().observeAll().map { list -> list.map { it.toDomain() } }

    val skillsFlow: Flow<List<Skill>> =
        db.skillDao().observeAll().map { list -> list.map { it.toDomain() } }

    val promptPresetsFlow: Flow<List<SystemPromptPreset>> =
        db.systemPromptPresetDao().observeAll().map { list -> list.map { it.toDomain() } }

    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (db.skillDao().getAll().isEmpty()) {
            db.skillDao().upsertAll(BuiltInSkillsSeed.defaultSkills().map { it.toEntity() })
        }
        if (db.systemPromptPresetDao().getAll().isEmpty()) {
            db.systemPromptPresetDao().upsertAll(BuiltInSkillsSeed.defaultPresets().map { it.toEntity() })
        }
    }

    // --- Providers & API Keys ---

    fun hasApiKey(providerId: String): Boolean = keyStore.hasSecret("provider_$providerId")
    fun getApiKey(providerId: String): String = keyStore.getSecret("provider_$providerId")
    fun maskedApiKey(providerId: String): String = keyStore.maskedPreview("provider_$providerId")

    fun getWebSearchApiKey(): String = keyStore.getSecret("web_search_api_key")
    fun setWebSearchApiKey(key: String) = keyStore.putSecret("web_search_api_key", key)
    fun maskedWebSearchKey(): String = keyStore.maskedPreview("web_search_api_key")

    suspend fun saveProviderConfig(config: ProviderConfig, newApiKey: String?) {
        db.providerConfigDao().upsert(config.toEntity())
        if (newApiKey != null) {
            keyStore.putSecret("provider_${config.id}", newApiKey)
        }
    }

    suspend fun deleteProviderConfig(providerId: String) {
        keyStore.deleteSecret("provider_$providerId")
        db.aiModelDao().deleteAllForProvider(providerId)
        db.providerConfigDao().deleteById(providerId)
    }

    suspend fun testProviderConnection(providerId: String): ConnectionResult {
        val config = db.providerConfigDao().getById(providerId)?.toDomain()
            ?: return ConnectionResult(false, "Provider configuration not found.")
        val apiKey = getApiKey(providerId)
        val adapter = ProviderAdapterFactory.forProvider(config.providerType)
        val result = adapter.testConnection(config, apiKey)

        val updated = config.copy(
            lastStatus = if (result.success) ConnectionStatus.CONNECTED else ConnectionStatus.ERROR,
            lastStatusMessage = result.message,
            lastTestedAt = System.currentTimeMillis()
        )
        db.providerConfigDao().upsert(updated.toEntity())

        if (result.success && config.providerType.supportsModelListing) {
            fetchAndSyncModels(providerId)
        }
        return result
    }

    suspend fun fetchAndSyncModels(providerId: String): Result<List<AiModel>> {
        val config = db.providerConfigDao().getById(providerId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Provider not found"))
        val apiKey = getApiKey(providerId)
        val adapter = ProviderAdapterFactory.forProvider(config.providerType)
        val res = adapter.fetchModels(config, apiKey)
        res.onSuccess { fetched ->
            val existingByKey = db.aiModelDao().getByProvider(providerId).associateBy { it.key }
            val merged = fetched.map { m ->
                val old = existingByKey[m.key]
                if (old != null) {
                    m.copy(
                        displayName = if (old.displayName != old.modelId) old.displayName else m.displayName,
                        isFavorite = old.isFavorite,
                        isDefault = old.isDefault,
                        lastUsedAt = old.lastUsedAt
                    )
                } else m
            }
            db.aiModelDao().deleteFetchedForProvider(providerId)
            db.aiModelDao().upsertAll(merged.map { it.toEntity() })
            if (db.aiModelDao().getDefaultModel() == null && merged.isNotEmpty()) {
                db.aiModelDao().setDefault(merged.first().key)
            }
        }
        return res
    }

    // --- Models ---

    suspend fun saveModel(model: AiModel) {
        db.aiModelDao().upsert(model.toEntity())
        if (db.aiModelDao().getDefaultModel() == null) {
            db.aiModelDao().setDefault(model.key)
        }
    }

    suspend fun deleteModel(key: String) = db.aiModelDao().deleteByKey(key)

    suspend fun setDefaultModel(key: String) {
        db.aiModelDao().clearDefault()
        db.aiModelDao().setDefault(key)
    }

    suspend fun toggleFavoriteModel(key: String, favorite: Boolean) {
        db.aiModelDao().setFavorite(key, favorite)
    }

    suspend fun markModelUsed(key: String) {
        db.aiModelDao().markUsed(key, System.currentTimeMillis())
    }

    // --- Conversations & Messages ---

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        db.messageDao().observeForConversation(conversationId).map { list -> list.map { it.toDomain() } }

    suspend fun getConversation(id: String): Conversation? = db.conversationDao().getById(id)?.toDomain()

    suspend fun getMessages(conversationId: String): List<ChatMessage> =
        db.messageDao().getForConversation(conversationId).map { it.toDomain() }

    suspend fun createConversation(
        title: String = "New Conversation",
        projectId: String? = null,
        modelKey: String? = null,
        systemPrompt: String? = null
    ): Conversation {
        val prefs = prefsRepo.preferencesFlow.first()
        val defaultModel = modelKey ?: db.aiModelDao().getDefaultModel()?.key
        val project = projectId?.let { db.projectDao().getById(it)?.toDomain() }
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            projectId = projectId,
            modelKey = project?.defaultModelKey ?: defaultModel,
            systemPrompt = systemPrompt ?: prefs.globalSystemPrompt,
            settings = ChatGenerationSettings(
                temperature = prefs.defaultTemperature,
                maxTokens = prefs.defaultMaxTokens,
                streamingEnabled = prefs.defaultStreaming
            ),
            activeSkillIds = project?.selectedSkillIds ?: emptyList()
        )
        db.conversationDao().upsert(conv.toEntity())
        return conv
    }

    suspend fun updateConversation(conversation: Conversation) {
        db.conversationDao().upsert(conversation.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteConversation(id: String) = db.conversationDao().deleteById(id)

    suspend fun deleteAllConversations() = db.conversationDao().deleteAll()

    suspend fun upsertMessage(message: ChatMessage) {
        db.messageDao().upsert(message.toEntity())
    }

    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long) {
        db.messageDao().deleteFrom(conversationId, fromTimestamp)
    }

    suspend fun deleteMessagesAfter(conversationId: String, afterTimestamp: Long) {
        db.messageDao().deleteAfter(conversationId, afterTimestamp)
    }

    suspend fun getProviderAndModel(modelKey: String?): Pair<ProviderConfig, AiModel>? {
        val modelEntity = if (modelKey != null) db.aiModelDao().getByKey(modelKey) else db.aiModelDao().getDefaultModel()
        val model = modelEntity?.toDomain() ?: return null
        val provider = db.providerConfigDao().getById(model.providerConfigId)?.toDomain() ?: return null
        return provider to model
    }

    // --- Projects & Files ---

    fun observeProjectFiles(projectId: String): Flow<List<ProjectFile>> =
        db.projectFileDao().observeForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeProjectConversations(projectId: String): Flow<List<Conversation>> =
        db.conversationDao().observeForProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getProject(id: String): ProjectWorkspace? = db.projectDao().getById(id)?.toDomain()
    suspend fun getProjectFiles(projectId: String): List<ProjectFile> =
        db.projectFileDao().getForProject(projectId).map { it.toDomain() }

    suspend fun upsertProject(project: ProjectWorkspace) =
        db.projectDao().upsert(project.copy(updatedAt = System.currentTimeMillis()).toEntity())

    suspend fun deleteProject(id: String) = db.projectDao().deleteById(id)

    suspend fun upsertProjectFile(file: ProjectFile) {
        val existing = db.projectFileDao().getByPath(file.projectId, file.path)
        val toSave = if (existing != null) {
            file.copy(
                id = existing.id,
                sizeBytes = file.content.toByteArray().size.toLong(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            file.copy(
                sizeBytes = file.content.toByteArray().size.toLong(),
                updatedAt = System.currentTimeMillis()
            )
        }
        db.projectFileDao().upsert(toSave.toEntity())
    }

    suspend fun deleteProjectFile(fileId: String) = db.projectFileDao().deleteById(fileId)

    // --- Skills & Presets ---

    suspend fun upsertSkill(skill: Skill) =
        db.skillDao().upsert(skill.copy(updatedAt = System.currentTimeMillis()).toEntity())

    suspend fun setSkillEnabled(id: String, enabled: Boolean) = db.skillDao().setEnabled(id, enabled)
    suspend fun setSkillTrusted(id: String, trusted: Boolean) = db.skillDao().setTrusted(id, trusted)
    suspend fun deleteSkill(id: String) = db.skillDao().deleteById(id)

    suspend fun upsertPromptPreset(preset: SystemPromptPreset) = db.systemPromptPresetDao().upsert(preset.toEntity())
    suspend fun deletePromptPreset(id: String) = db.systemPromptPresetDao().deleteById(id)

    // --- Preferences & Exports ---

    suspend fun updateGlobalSystemPrompt(prompt: String) = prefsRepo.updateGlobalSystemPrompt(prompt)
    suspend fun updateDefaults(temp: Float, maxTokens: Int, streaming: Boolean) =
        prefsRepo.updateDefaults(temp, maxTokens, streaming)
    suspend fun updateAutoDiscoverSkills(enabled: Boolean) = prefsRepo.updateAutoDiscoverSkills(enabled)
    suspend fun updateDarkTheme(dark: Boolean) = prefsRepo.updateDarkTheme(dark)
    suspend fun updateVoiceSettings(autoRead: Boolean, voiceName: String, rate: Float) =
        prefsRepo.updateVoiceSettings(autoRead, voiceName, rate)
    suspend fun updateWebSearch(type: WebSearchProviderType, endpoint: String, apiKey: String?) {
        prefsRepo.updateWebSearchConfig(type, endpoint)
        if (apiKey != null) setWebSearchApiKey(apiKey)
    }

    suspend fun knownSecretsList(): List<String> {
        val providers = db.providerConfigDao().getAll()
        val list = providers.mapNotNull { p -> getApiKey(p.id).takeIf { it.isNotBlank() } }.toMutableList()
        val searchKey = getWebSearchApiKey()
        if (searchKey.isNotBlank()) list.add(searchKey)
        return list
    }

    suspend fun importConversationsJson(json: String): Int {
        val imported = ChatExportManager.parseImportedConversationsJson(json)
        imported.forEach { (conv, msgs) ->
            db.conversationDao().upsert(conv.toEntity())
            msgs.forEach { db.messageDao().upsert(it.toEntity()) }
        }
        return imported.size
    }

    suspend fun importProjectJson(json: String): ProjectWorkspace {
        val (project, files) = ChatExportManager.parseImportedProjectJson(json)
        db.projectDao().upsert(project.toEntity())
        files.forEach { db.projectFileDao().upsert(it.toEntity()) }
        return project
    }
}
