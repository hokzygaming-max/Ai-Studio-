package com.example.domain.model

data class ProviderConfig(
    val id: String,
    val providerType: ProviderType,
    val displayName: String,
    val baseUrl: String,
    val customHeaders: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
    val isLocal: Boolean = providerType.isLocalDefault,
    val lastStatus: ConnectionStatus = ConnectionStatus.UNTESTED,
    val lastStatusMessage: String = "",
    val lastTestedAt: Long = 0L
)

enum class ConnectionStatus {
    UNTESTED,
    CONNECTED,
    ERROR
}

data class ModelCapabilities(
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsSystemPrompt: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsFrequencyPenalty: Boolean = false,
    val supportsPresencePenalty: Boolean = false,
    val supportsStopSequences: Boolean = true,
    val supportsPdfNative: Boolean = false
)

data class AiModel(
    val key: String, // "$providerConfigId::$modelId"
    val modelId: String,
    val displayName: String,
    val providerConfigId: String,
    val providerType: ProviderType,
    val contextWindow: Int? = null,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = 0L,
    val isManual: Boolean = false
)

data class AttachmentItem(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val isPdf: Boolean = false,
    val extractedText: String = "",
    val base64Data: String = "",
    val localCachePath: String = "",
    val pageCount: Int = 0,
    val errorMessage: String? = null
)

data class SearchCitation(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String
)

data class ChatGenerationSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val streamingEnabled: Boolean = true,
    val stopSequences: List<String> = emptyList(),
    val webSearchEnabled: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val attachments: List<AttachmentItem> = emptyList(),
    val citations: List<SearchCitation> = emptyList(),
    val skillsUsed: List<String> = emptyList(),
    val modelUsed: String = "",
    val tokenInfo: String = "",
    val isError: Boolean = false,
    val isStreaming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String,
    val title: String,
    val projectId: String? = null,
    val modelKey: String? = null,
    val systemPrompt: String = "",
    val settings: ChatGenerationSettings = ChatGenerationSettings(),
    val activeSkillIds: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProjectWorkspace(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val defaultModelKey: String? = null,
    val selectedSkillIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProjectFile(
    val id: String,
    val projectId: String,
    val path: String,
    val content: String,
    val mimeType: String = "text/plain",
    val sizeBytes: Long = content.toByteArray().size.toLong(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SkillPermission(val label: String, val riskNote: String) {
    READ_ATTACHMENTS("Read Uploaded Files", "Allows skill instructions to analyze uploaded documents."),
    WRITE_PROJECT_FILES("Modify Project Files", "Allows proposing/writing files in Project Workspace."),
    NETWORK_SEARCH("Use Web Search", "Allows triggering user-configured web search."),
    EXECUTE_SANDBOX("Run Code / Script Inspection", "Requires explicit user approval before running or evaluating any script.")
}

data class SkillSupportingFile(
    val relativePath: String,
    val content: String,
    val isScript: Boolean = false
)

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val skillMdContent: String,
    val instructionsBody: String,
    val supportingFiles: List<SkillSupportingFile> = emptyList(),
    val requiredPermissions: List<SkillPermission> = emptyList(),
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val isTrusted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SystemPromptPreset(
    val id: String,
    val name: String,
    val content: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class WebSearchProviderType(val displayName: String, val requiresKey: Boolean, val placeholderEndpoint: String) {
    NONE("Disabled / Not Configured", false, ""),
    TAVILY("Tavily Search API", true, "https://api.tavily.com/search"),
    BRAVE("Brave Search API", true, "https://api.search.brave.com/res/v1/web/search"),
    SERPER("Serper (Google Search API)", true, "https://google.serper.dev/search"),
    SEARXNG("SearXNG Instance (Self-Hosted)", false, "https://your-searxng-instance/search")
}

data class WebSearchConfig(
    val providerType: WebSearchProviderType = WebSearchProviderType.NONE,
    val customEndpoint: String = "",
    val hasApiKey: Boolean = false
)
