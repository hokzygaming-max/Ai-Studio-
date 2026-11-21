package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.ChatExportManager
import com.example.domain.model.*
import com.example.domain.provider.ChatCompletionRequest
import com.example.domain.provider.ProviderAdapterFactory
import com.example.domain.provider.StreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val repo: AiStudioRepository) : ViewModel() {

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<AttachmentItem>>(emptyList())
    val pendingAttachments: StateFlow<List<AttachmentItem>> = _pendingAttachments.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    private val _streamingMessageId = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _uiBanner = MutableStateFlow<String?>(null)
    val uiBanner: StateFlow<String?> = _uiBanner.asStateFlow()

    private var generationJob: Job? = null

    val conversations: StateFlow<List<Conversation>> = repo.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<AiModel>> = repo.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers: StateFlow<List<ProviderConfig>> = repo.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<Skill>> = repo.skillsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promptPresets: StateFlow<List<SystemPromptPreset>> = repo.promptPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<UserPreferences> = repo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val activeConversation: StateFlow<Conversation?> = combine(
        _activeConversationId,
        conversations
    ) { activeId, list ->
        if (activeId == null) null else list.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repo.observeMessages(id)
        }
        .combine(_streamingMessageId) { msgs, streamId -> msgs to streamId }
        .combine(_streamingContent) { (msgs, streamId), streamText ->
            if (streamId == null) msgs
            else msgs.map { m ->
                if (m.id == streamId) m.copy(content = streamText, isStreaming = true) else m
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setShowArchived(show: Boolean) {
        _showArchived.value = show
    }

    fun dismissBanner() {
        _uiBanner.value = null
    }

    fun selectConversation(id: String?) {
        if (_isGenerating.value) stopGeneration()
        _activeConversationId.value = id
    }

    fun startNewConversation(projectId: String? = null) {
        viewModelScope.launch {
            val conv = repo.createConversation(projectId = projectId)
            _activeConversationId.value = conv.id
            _pendingAttachments.value = emptyList()
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch {
            val existing = repo.getConversation(id) ?: return@launch
            repo.updateConversation(existing.copy(title = title.trim().ifBlank { existing.title }))
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repo.deleteConversation(id)
            if (_activeConversationId.value == id) _activeConversationId.value = null
        }
    }

    fun togglePin(conv: Conversation) {
        viewModelScope.launch {
            repo.updateConversation(conv.copy(isPinned = !conv.isPinned))
        }
    }

    fun toggleArchive(conv: Conversation) {
        viewModelScope.launch {
            repo.updateConversation(conv.copy(isArchived = !conv.isArchived))
        }
    }

    fun updateConversationModel(modelKey: String) {
        viewModelScope.launch {
            val conv = activeConversation.value ?: repo.createConversation(modelKey = modelKey).also {
                _activeConversationId.value = it.id
            }
            repo.updateConversation(conv.copy(modelKey = modelKey))
        }
    }

    fun updateConversationSettings(
        systemPrompt: String,
        settings: ChatGenerationSettings,
        activeSkillIds: List<String>
    ) {
        viewModelScope.launch {
            val conv = activeConversation.value ?: repo.createConversation().also {
                _activeConversationId.value = it.id
            }
            repo.updateConversation(
                conv.copy(
                    systemPrompt = systemPrompt,
                    settings = settings,
                    activeSkillIds = activeSkillIds
                )
            )
        }
    }

    fun toggleConversationSkill(skillId: String) {
        viewModelScope.launch {
            val conv = activeConversation.value ?: repo.createConversation().also {
                _activeConversationId.value = it.id
            }
            val next = if (skillId in conv.activeSkillIds) {
                conv.activeSkillIds - skillId
            } else {
                conv.activeSkillIds + skillId
            }
            repo.updateConversation(conv.copy(activeSkillIds = next))
        }
    }

    fun attachFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val processed = uris.map { repo.fileProcessor.processUri(it) }
            val errors = processed.mapNotNull { it.errorMessage }
            if (errors.isNotEmpty()) {
                _uiBanner.value = errors.first()
            }
            val valid = processed.filter { it.errorMessage == null }
            _pendingAttachments.value = (_pendingAttachments.value + valid).take(10)
        }
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
    }

    fun sendMessage(rawInput: String) {
        val trimmed = rawInput.trim()
        val attachments = _pendingAttachments.value
        if (trimmed.isEmpty() && attachments.isEmpty()) return

        viewModelScope.launch {
            val conv = activeConversation.value ?: repo.createConversation().also {
                _activeConversationId.value = it.id
            }
            val updatedConv = if (conv.title == "New Conversation" && trimmed.isNotBlank()) {
                conv.copy(title = trimmed.take(42)).also { repo.updateConversation(it) }
            } else conv

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = updatedConv.id,
                role = MessageRole.USER,
                content = trimmed.ifBlank { "Analyze the attached file(s)." },
                attachments = attachments
            )
            repo.upsertMessage(userMsg)
            _pendingAttachments.value = emptyList()

            executeCompletion(updatedConv, isContinue = false)
        }
    }

    fun regenerateLastResponse() {
        val conv = activeConversation.value ?: return
        val currentMsgs = messages.value
        val lastAssistant = currentMsgs.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return
        viewModelScope.launch {
            repo.deleteMessagesFrom(conv.id, lastAssistant.createdAt)
            executeCompletion(conv, isContinue = false)
        }
    }

    fun continueGeneration() {
        val conv = activeConversation.value ?: return
        val lastAssistant = messages.value.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return
        viewModelScope.launch {
            executeCompletion(conv, isContinue = true, continueTarget = lastAssistant)
        }
    }

    fun editUserMessageAndResend(messageId: String, newContent: String) {
        val conv = activeConversation.value ?: return
        val target = messages.value.firstOrNull { it.id == messageId } ?: return
        if (newContent.isBlank()) return
        viewModelScope.launch {
            repo.deleteMessagesAfter(conv.id, target.createdAt)
            repo.upsertMessage(target.copy(content = newContent.trim()))
            executeCompletion(conv, isContinue = false)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _streamingMessageId.value = null
    }

    private fun executeCompletion(
        conv: Conversation,
        isContinue: Boolean,
        continueTarget: ChatMessage? = null
    ) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _uiBanner.value = null

            val resolved = repo.getProviderAndModel(conv.modelKey)
            if (resolved == null) {
                val errMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    role = MessageRole.ASSISTANT,
                    content = "No AI provider/model configured yet. Go to Models -> Add Provider, enter your API key (or local Ollama URL), and select a model.",
                    isError = true
                )
                repo.upsertMessage(errMsg)
                _isGenerating.value = false
                return@launch
            }

            val (provider, model) = resolved
            val apiKey = repo.getApiKey(provider.id)
            if (provider.providerType.requiresApiKey && apiKey.isBlank()) {
                repo.upsertMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        conversationId = conv.id,
                        role = MessageRole.ASSISTANT,
                        content = "Missing API key for ${provider.displayName}. Add your API key in Models -> Providers.",
                        isError = true
                    )
                )
                _isGenerating.value = false
                return@launch
            }

            val history = repo.getMessages(conv.id)
            val lastUser = history.lastOrNull { it.role == MessageRole.USER }

            if (lastUser?.attachments?.any { it.isImage } == true && !model.capabilities.supportsVision) {
                _uiBanner.value = "Warning: '${model.displayName}' is text-only and does not support image understanding. Switch to a vision-capable model to analyze images."
            }

            val prefs = preferences.value
            val activeSkills = repo.skillManager.selectActiveSkills(
                userPrompt = lastUser?.content.orEmpty(),
                allSkills = skills.value,
                manualSkillIds = conv.activeSkillIds,
                autoDiscover = prefs.autoDiscoverSkills
            )

            val projectInstructions = conv.projectId?.let { pid ->
                val proj = repo.getProject(pid)
                val files = repo.getProjectFiles(pid)
                buildString {
                    if (!proj?.instructions.isNullOrBlank()) appendLine(proj?.instructions)
                    if (files.isNotEmpty()) {
                        appendLine("\nProject Workspace Files:")
                        files.take(8).forEach { f ->
                            appendLine("--- ${f.path} ---\n${f.content.take(3000)}")
                        }
                    }
                }
            }.orEmpty()

            var searchCitations: List<SearchCitation> = emptyList()
            var searchBlock = ""
            if (conv.settings.webSearchEnabled && lastUser != null) {
                val searchConfig = WebSearchConfig(
                    providerType = prefs.webSearchProvider,
                    customEndpoint = prefs.webSearchCustomEndpoint,
                    hasApiKey = repo.getWebSearchApiKey().isNotBlank()
                )
                val sr = repo.webSearchService.search(
                    query = lastUser.content,
                    config = searchConfig,
                    apiKey = repo.getWebSearchApiKey()
                )
                if (sr.errorMessage != null) {
                    _uiBanner.value = sr.errorMessage
                } else {
                    searchCitations = sr.citations
                    searchBlock = sr.contextPromptBlock
                }
            }

            val combinedSystem = repo.skillManager.buildCombinedSystemPrompt(
                baseSystemPrompt = conv.systemPrompt.ifBlank { prefs.globalSystemPrompt },
                projectInstructions = projectInstructions,
                activeSkills = activeSkills,
                searchContext = searchBlock
            )

            val requestMessages = if (isContinue && continueTarget != null) {
                history + ChatMessage(
                    id = "continue_prompt",
                    conversationId = conv.id,
                    role = MessageRole.USER,
                    content = "Continue your previous response seamlessly from exactly where it left off without repeating earlier text."
                )
            } else {
                history
            }

            val targetMsgId = if (isContinue && continueTarget != null) {
                continueTarget.id
            } else {
                UUID.randomUUID().toString()
            }
            val initialText = if (isContinue && continueTarget != null) continueTarget.content else ""

            val placeholder = ChatMessage(
                id = targetMsgId,
                conversationId = conv.id,
                role = MessageRole.ASSISTANT,
                content = initialText,
                citations = searchCitations,
                skillsUsed = activeSkills.map { it.name },
                modelUsed = model.displayName,
                createdAt = continueTarget?.createdAt ?: System.currentTimeMillis()
            )
            repo.upsertMessage(placeholder)

            _streamingMessageId.value = targetMsgId
            _streamingContent.value = initialText

            val buffer = StringBuilder(initialText)
            var latestUsage = ""
            var latestCitations = searchCitations
            var errorText: String? = null

            try {
                repo.markModelUsed(model.key)
                val adapter = ProviderAdapterFactory.forProvider(provider.providerType)
                adapter.streamChat(
                    request = ChatCompletionRequest(
                        model = model,
                        messages = requestMessages,
                        systemPrompt = combinedSystem,
                        settings = conv.settings
                    ),
                    config = provider,
                    apiKey = apiKey
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Delta -> {
                            buffer.append(chunk.text)
                            _streamingContent.value = buffer.toString()
                        }
                        is StreamChunk.Citations -> {
                            latestCitations = (latestCitations + chunk.citations).distinctBy { it.url }
                        }
                        is StreamChunk.Usage -> {
                            latestUsage = chunk.info
                        }
                        is StreamChunk.Error -> {
                            errorText = chunk.message
                        }
                        is StreamChunk.Done -> Unit
                    }
                }
            } catch (ce: CancellationException) {
                // User stopped generation; persist partial output cleanly
                if (buffer.isNotBlank()) {
                    repo.upsertMessage(
                        placeholder.copy(
                            content = buffer.toString(),
                            citations = latestCitations,
                            tokenInfo = latestUsage
                        )
                    )
                }
                throw ce
            } finally {
                _streamingMessageId.value = null
                _isGenerating.value = false
            }

            val finalContent = when {
                errorText != null && buffer.isBlank() -> errorText!!
                errorText != null -> "${buffer}\n\n[Error: $errorText]"
                buffer.isBlank() -> "No output received from ${provider.displayName}."
                else -> buffer.toString()
            }

            repo.upsertMessage(
                placeholder.copy(
                    content = finalContent,
                    citations = latestCitations,
                    tokenInfo = latestUsage,
                    isError = errorText != null && buffer.isBlank()
                )
            )
            repo.updateConversation(conv)
        }
    }

    suspend fun exportConversationMarkdown(convId: String): String? {
        val conv = repo.getConversation(convId) ?: return null
        val msgs = repo.getMessages(convId)
        val secrets = repo.knownSecretsList()
        return ChatExportManager.exportConversationMarkdown(conv, msgs, secrets)
    }

    suspend fun exportAllChatsJson(): String {
        val allConvs = conversations.value
        val pairs = allConvs.map { it to repo.getMessages(it.id) }
        return ChatExportManager.exportConversationsJson(pairs, repo.knownSecretsList())
    }

    fun importChatsJson(json: String) {
        viewModelScope.launch {
            runCatching { repo.importConversationsJson(json) }
                .onSuccess { count -> _uiBanner.value = "Imported $count conversation(s)." }
                .onFailure { err -> _uiBanner.value = "Import failed: ${err.localizedMessage}" }
        }
    }

    class Factory(private val repo: AiStudioRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(repo) as T
    }
}
