package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.ChatExportManager
import com.example.domain.coding.CodingAgentEngine
import com.example.domain.coding.CodingScaffoldType
import com.example.domain.coding.ProposedCodeFile
import com.example.domain.model.*
import com.example.domain.provider.ChatCompletionRequest
import com.example.domain.provider.ProviderAdapterFactory
import com.example.domain.provider.StreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ProjectsSkillsViewModel(private val repo: AiStudioRepository) : ViewModel() {

    val projects: StateFlow<List<ProjectWorkspace>> = repo.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<Skill>> = repo.skillsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<AiModel>> = repo.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedProjectFiles: StateFlow<List<ProjectFile>> = _selectedProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeProjectFiles(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedProjectConversations: StateFlow<List<Conversation>> = _selectedProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeProjectConversations(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _codingOutput = MutableStateFlow("")
    val codingOutput: StateFlow<String> = _codingOutput.asStateFlow()

    private val _isCodingBusy = MutableStateFlow(false)
    val isCodingBusy: StateFlow<Boolean> = _isCodingBusy.asStateFlow()

    private val _proposedFiles = MutableStateFlow<List<ProposedCodeFile>>(emptyList())
    val proposedFiles: StateFlow<List<ProposedCodeFile>> = _proposedFiles.asStateFlow()

    private val _diagnosticsReport = MutableStateFlow<String?>(null)
    val diagnosticsReport: StateFlow<String?> = _diagnosticsReport.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var codingJob: Job? = null

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun selectProject(id: String?) {
        _selectedProjectId.value = id
        _diagnosticsReport.value = null
    }

    fun createOrUpdateProject(
        existing: ProjectWorkspace?,
        name: String,
        description: String,
        instructions: String,
        defaultModelKey: String?,
        skillIds: List<String>
    ) {
        viewModelScope.launch {
            val proj = ProjectWorkspace(
                id = existing?.id ?: "proj_${UUID.randomUUID()}",
                name = name.trim().ifBlank { "Untitled Project" },
                description = description.trim(),
                instructions = instructions.trim(),
                defaultModelKey = defaultModelKey,
                selectedSkillIds = skillIds,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repo.upsertProject(proj)
            _selectedProjectId.value = proj.id
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repo.deleteProject(id)
            if (_selectedProjectId.value == id) _selectedProjectId.value = null
        }
    }

    fun saveProjectFile(projectId: String, path: String, content: String) {
        viewModelScope.launch {
            val cleanPath = path.trim().replace("..", "").trimStart('/')
            if (cleanPath.isBlank()) return@launch
            repo.upsertProjectFile(
                ProjectFile(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    path = cleanPath,
                    content = content
                )
            )
            _bannerMessage.value = "Saved $cleanPath"
        }
    }

    fun deleteProjectFile(fileId: String) {
        viewModelScope.launch { repo.deleteProjectFile(fileId) }
    }

    fun uploadFilesToProject(projectId: String, uris: List<Uri>) {
        viewModelScope.launch {
            var count = 0
            uris.forEach { uri ->
                val item = repo.fileProcessor.processUri(uri)
                if (item.errorMessage == null && item.extractedText.isNotBlank()) {
                    repo.upsertProjectFile(
                        ProjectFile(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            path = item.fileName,
                            content = item.extractedText,
                            mimeType = item.mimeType
                        )
                    )
                    count++
                }
            }
            _bannerMessage.value = "Added $count file(s) to project workspace."
        }
    }

    fun generateScaffold(projectId: String, type: CodingScaffoldType, projectName: String) {
        viewModelScope.launch {
            val files = CodingAgentEngine.buildScaffoldFiles(projectId, type, projectName)
            files.forEach { repo.upsertProjectFile(it) }
            _bannerMessage.value = "Generated ${type.label} scaffold (${files.size} files)."
        }
    }

    fun runCodingAgentTask(
        taskPrompt: String,
        actionLabel: String,
        modelKey: String?,
        projectId: String?
    ) {
        codingJob?.cancel()
        codingJob = viewModelScope.launch {
            _isCodingBusy.value = true
            _codingOutput.value = ""
            _proposedFiles.value = emptyList()

            val resolved = repo.getProviderAndModel(modelKey)
            if (resolved == null) {
                _codingOutput.value = "Error: No AI model configured. Add a provider and model in the Models tab first."
                _isCodingBusy.value = false
                return@launch
            }
            val (provider, model) = resolved
            val apiKey = repo.getApiKey(provider.id)
            if (provider.providerType.requiresApiKey && apiKey.isBlank()) {
                _codingOutput.value = "Error: API key missing for ${provider.displayName}."
                _isCodingBusy.value = false
                return@launch
            }

            val project = projectId?.let { repo.getProject(it) }
            val files = if (projectId != null) repo.getProjectFiles(projectId) else emptyList()

            val systemPrompt = buildString {
                appendLine("You are the AI Studio Coding Agent, a principal software engineer.")
                appendLine("When creating or updating files, always format each file as a fenced code block with its path attribute, like:")
                appendLine("```kotlin path=src/main/Main.kt")
                appendLine("// code")
                appendLine("```")
                appendLine("Never claim to have compiled an APK or executed shell tests unless verified.")
                if (project != null && project.instructions.isNotBlank()) {
                    appendLine("\nProject Instructions:\n${project.instructions}")
                }
            }

            val fullUserPrompt = buildString {
                appendLine("Task ($actionLabel): $taskPrompt")
                if (files.isNotEmpty()) {
                    appendLine("\nCurrent Project Files:")
                    files.take(12).forEach { f ->
                        appendLine("\n--- FILE: ${f.path} ---")
                        appendLine(f.content.take(4000))
                    }
                }
            }

            val adapter = ProviderAdapterFactory.forProvider(provider.providerType)
            val sb = StringBuilder()
            adapter.streamChat(
                request = ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage(
                            id = "coding_req",
                            conversationId = "coding_agent",
                            role = MessageRole.USER,
                            content = fullUserPrompt
                        )
                    ),
                    systemPrompt = systemPrompt,
                    settings = ChatGenerationSettings(temperature = 0.25f, maxTokens = 4096, streamingEnabled = true)
                ),
                config = provider,
                apiKey = apiKey
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        sb.append(chunk.text)
                        _codingOutput.value = sb.toString()
                    }
                    is StreamChunk.Error -> {
                        sb.append("\n\n[Error: ${chunk.message}]")
                        _codingOutput.value = sb.toString()
                    }
                    else -> Unit
                }
            }
            _proposedFiles.value = CodingAgentEngine.extractProposedFiles(_codingOutput.value)
            _isCodingBusy.value = false
        }
    }

    fun stopCodingAgent() {
        codingJob?.cancel()
        _isCodingBusy.value = false
    }

    fun applyProposedFile(projectId: String, proposed: ProposedCodeFile) {
        saveProjectFile(projectId, proposed.path, proposed.content)
    }

    fun applyAllProposedFiles(projectId: String) {
        viewModelScope.launch {
            val list = _proposedFiles.value
            list.forEach { p ->
                repo.upsertProjectFile(
                    ProjectFile(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        path = p.path,
                        content = p.content
                    )
                )
            }
            _bannerMessage.value = "Applied ${list.size} file(s) to project workspace."
        }
    }

    fun runSafeDiagnostics(files: List<ProjectFile>) {
        _diagnosticsReport.value = CodingAgentEngine.runSafeWorkspaceDiagnostics(files)
    }

    suspend fun exportProjectJson(project: ProjectWorkspace): String {
        val files = repo.getProjectFiles(project.id)
        return ChatExportManager.exportProjectJson(project, files, repo.knownSecretsList())
    }

    fun exportProjectZipBytes(files: List<ProjectFile>): ByteArray =
        CodingAgentEngine.exportProjectZipBytes(files)

    fun importProjectJson(json: String) {
        viewModelScope.launch {
            runCatching { repo.importProjectJson(json) }
                .onSuccess { p ->
                    _selectedProjectId.value = p.id
                    _bannerMessage.value = "Imported project '${p.name}'."
                }
                .onFailure { err -> _bannerMessage.value = "Project import failed: ${err.localizedMessage}" }
        }
    }

    // --- Skills Management ---

    fun importSkillsFromUris(items: List<Pair<Uri, String>>) {
        viewModelScope.launch {
            var imported = 0
            items.forEach { (uri, name) ->
                repo.skillManager.importFromUri(uri, name)
                    .onSuccess { list ->
                        list.forEach { repo.upsertSkill(it) }
                        imported += list.size
                    }
                    .onFailure { err ->
                        _bannerMessage.value = "Skill import error ($name): ${err.localizedMessage}"
                    }
            }
            if (imported > 0) {
                _bannerMessage.value = "Imported $imported skill(s). Review permissions before enabling untrusted scripts."
            }
        }
    }

    fun saveCustomSkill(
        existing: Skill?,
        name: String,
        category: String,
        description: String,
        instructionsBody: String
    ) {
        viewModelScope.launch {
            val cleanName = name.trim().ifBlank { "Custom Skill" }
            val cleanCat = category.trim().ifBlank { "Custom" }
            val cleanDesc = description.trim().ifBlank { "User-defined skill instructions." }
            val skillMd = buildString {
                appendLine("---")
                appendLine("name: \"$cleanName\"")
                appendLine("category: \"$cleanCat\"")
                appendLine("description: \"$cleanDesc\"")
                appendLine("---")
                appendLine()
                appendLine("# $cleanName")
                appendLine()
                append(instructionsBody.trim())
            }
            val skill = (existing ?: Skill(
                id = "skill_${UUID.randomUUID()}",
                name = cleanName,
                description = cleanDesc,
                category = cleanCat,
                skillMdContent = skillMd,
                instructionsBody = instructionsBody.trim(),
                isTrusted = true
            )).copy(
                name = cleanName,
                category = cleanCat,
                description = cleanDesc,
                skillMdContent = skillMd,
                instructionsBody = instructionsBody.trim()
            )
            repo.upsertSkill(skill)
        }
    }

    fun toggleSkillEnabled(skill: Skill) {
        viewModelScope.launch { repo.setSkillEnabled(skill.id, !skill.isEnabled) }
    }

    fun approveSkillTrust(skillId: String) {
        viewModelScope.launch { repo.setSkillTrusted(skillId, true) }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch { repo.deleteSkill(id) }
    }

    fun exportSkillZipBytes(skill: Skill): ByteArray = repo.skillManager.exportSkillToZipBytes(skill)

    class Factory(private val repo: AiStudioRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectsSkillsViewModel(repo) as T
    }
}
