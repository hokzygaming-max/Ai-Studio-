package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.repository.AiStudioRepository
import com.example.domain.model.*
import com.example.domain.provider.ConnectionResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ProvidersModelsViewModel(private val repo: AiStudioRepository) : ViewModel() {

    val providers: StateFlow<List<ProviderConfig>> = repo.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<AiModel>> = repo.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: StateFlow<List<SystemPromptPreset>> = repo.promptPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = repo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _busyProviderId = MutableStateFlow<String?>(null)
    val busyProviderId: StateFlow<String?> = _busyProviderId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun hasApiKey(providerId: String): Boolean = repo.hasApiKey(providerId)
    fun maskedApiKey(providerId: String): String = repo.maskedApiKey(providerId)
    fun maskedWebSearchKey(): String = repo.maskedWebSearchKey()

    fun saveProvider(
        existingId: String?,
        providerType: ProviderType,
        displayName: String,
        baseUrl: String,
        apiKeyInput: String?,
        customHeadersText: String,
        testAfterSave: Boolean = true
    ) {
        viewModelScope.launch {
            val id = existingId ?: "prov_${UUID.randomUUID()}"
            val headers = parseHeaders(customHeadersText)
            val config = ProviderConfig(
                id = id,
                providerType = providerType,
                displayName = displayName.trim().ifBlank { providerType.displayName },
                baseUrl = baseUrl.trim().ifBlank { providerType.defaultBaseUrl },
                customHeaders = headers,
                isEnabled = true,
                isLocal = providerType.isLocalDefault
            )
            val keyToStore = if (apiKeyInput != null && apiKeyInput.isNotBlank()) apiKeyInput else null
            repo.saveProviderConfig(config, keyToStore)
            _statusMessage.value = "Saved ${config.displayName}."
            if (testAfterSave) {
                testProvider(id)
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repo.deleteProviderConfig(id)
            _statusMessage.value = "Provider deleted."
        }
    }

    fun testProvider(providerId: String) {
        viewModelScope.launch {
            _busyProviderId.value = providerId
            val res: ConnectionResult = repo.testProviderConnection(providerId)
            _busyProviderId.value = null
            _statusMessage.value = res.message
        }
    }

    fun refreshModels(providerId: String) {
        viewModelScope.launch {
            _busyProviderId.value = providerId
            val res = repo.fetchAndSyncModels(providerId)
            _busyProviderId.value = null
            res.fold(
                onSuccess = { list ->
                    _statusMessage.value = "Synced ${list.size} model(s)."
                },
                onFailure = { err ->
                    _statusMessage.value = err.message ?: "Model refresh failed."
                }
            )
        }
    }

    fun addOrUpdateManualModel(
        existingKey: String?,
        provider: ProviderConfig,
        modelId: String,
        displayName: String,
        contextWindow: Int?,
        capabilities: ModelCapabilities
    ) {
        viewModelScope.launch {
            val cleanId = modelId.trim()
            if (cleanId.isEmpty()) return@launch
            val key = existingKey ?: "${provider.id}::$cleanId"
            val model = AiModel(
                key = key,
                modelId = cleanId,
                displayName = displayName.trim().ifBlank { cleanId },
                providerConfigId = provider.id,
                providerType = provider.providerType,
                contextWindow = contextWindow,
                capabilities = capabilities,
                isManual = true
            )
            repo.saveModel(model)
            _statusMessage.value = "Saved model ${model.displayName}."
        }
    }

    fun deleteModel(key: String) {
        viewModelScope.launch { repo.deleteModel(key) }
    }

    fun setDefaultModel(key: String) {
        viewModelScope.launch { repo.setDefaultModel(key) }
    }

    fun toggleFavoriteModel(model: AiModel) {
        viewModelScope.launch { repo.toggleFavoriteModel(model.key, !model.isFavorite) }
    }

    // --- Settings & Presets ---

    fun updateGlobalSystemPrompt(prompt: String) {
        viewModelScope.launch {
            repo.updateGlobalSystemPrompt(prompt)
            _statusMessage.value = "Global instructions saved."
        }
    }

    fun updateDefaults(temp: Float, maxTokens: Int, streaming: Boolean) {
        viewModelScope.launch { repo.updateDefaults(temp, maxTokens, streaming) }
    }

    fun updateAutoDiscoverSkills(enabled: Boolean) {
        viewModelScope.launch { repo.updateAutoDiscoverSkills(enabled) }
    }

    fun updateDarkTheme(dark: Boolean) {
        viewModelScope.launch { repo.updateDarkTheme(dark) }
    }

    fun updateVoiceSettings(autoRead: Boolean, voiceName: String, rate: Float) {
        viewModelScope.launch { repo.updateVoiceSettings(autoRead, voiceName, rate) }
    }

    fun updateWebSearch(type: WebSearchProviderType, endpoint: String, apiKey: String?) {
        viewModelScope.launch {
            repo.updateWebSearch(type, endpoint.trim(), apiKey?.takeIf { it.isNotBlank() })
            _statusMessage.value = "Web search configuration updated."
        }
    }

    fun savePromptPreset(name: String, content: String, existingId: String? = null) {
        viewModelScope.launch {
            if (name.isBlank() || content.isBlank()) return@launch
            repo.upsertPromptPreset(
                SystemPromptPreset(
                    id = existingId ?: "preset_${UUID.randomUUID()}",
                    name = name.trim(),
                    content = content.trim()
                )
            )
        }
    }

    fun deletePromptPreset(id: String) {
        viewModelScope.launch { repo.deletePromptPreset(id) }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            repo.deleteAllConversations()
            _statusMessage.value = "All local chat history deleted."
        }
    }

    fun clearCachedFiles() {
        viewModelScope.launch {
            repo.fileProcessor.clearCachedFiles()
            _statusMessage.value = "Temporary uploaded file caches cleared."
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.lines().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
            }
        }
        return map
    }

    class Factory(private val repo: AiStudioRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProvidersModelsViewModel(repo) as T
    }
}
