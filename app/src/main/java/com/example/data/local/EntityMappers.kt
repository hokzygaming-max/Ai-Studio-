package com.example.data.local

import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject

object EntityMappers {

    fun ProviderConfigEntity.toDomain(): ProviderConfig {
        val type = runCatching { ProviderType.valueOf(providerType) }.getOrDefault(ProviderType.CUSTOM_OPENAI)
        val status = runCatching { ConnectionStatus.valueOf(lastStatus) }.getOrDefault(ConnectionStatus.UNTESTED)
        return ProviderConfig(
            id = id,
            providerType = type,
            displayName = displayName,
            baseUrl = baseUrl,
            customHeaders = jsonToStringMap(customHeadersJson),
            isEnabled = isEnabled,
            isLocal = isLocal,
            lastStatus = status,
            lastStatusMessage = lastStatusMessage,
            lastTestedAt = lastTestedAt
        )
    }

    fun ProviderConfig.toEntity(): ProviderConfigEntity = ProviderConfigEntity(
        id = id,
        providerType = providerType.name,
        displayName = displayName,
        baseUrl = baseUrl,
        customHeadersJson = stringMapToJson(customHeaders),
        isEnabled = isEnabled,
        isLocal = isLocal,
        lastStatus = lastStatus.name,
        lastStatusMessage = lastStatusMessage,
        lastTestedAt = lastTestedAt
    )

    fun AiModelEntity.toDomain(): AiModel {
        val type = runCatching { ProviderType.valueOf(providerType) }.getOrDefault(ProviderType.CUSTOM_OPENAI)
        return AiModel(
            key = key,
            modelId = modelId,
            displayName = displayName,
            providerConfigId = providerConfigId,
            providerType = type,
            contextWindow = contextWindow,
            capabilities = ModelCapabilities(
                supportsVision = supportsVision,
                supportsStreaming = supportsStreaming,
                supportsSystemPrompt = supportsSystemPrompt,
                supportsTemperature = supportsTemperature,
                supportsTopP = supportsTopP,
                supportsFrequencyPenalty = supportsFrequencyPenalty,
                supportsPresencePenalty = supportsPresencePenalty,
                supportsStopSequences = supportsStopSequences,
                supportsPdfNative = supportsPdfNative
            ),
            isFavorite = isFavorite,
            isDefault = isDefault,
            lastUsedAt = lastUsedAt,
            isManual = isManual
        )
    }

    fun AiModel.toEntity(): AiModelEntity = AiModelEntity(
        key = key,
        modelId = modelId,
        displayName = displayName,
        providerConfigId = providerConfigId,
        providerType = providerType.name,
        contextWindow = contextWindow,
        supportsVision = capabilities.supportsVision,
        supportsStreaming = capabilities.supportsStreaming,
        supportsSystemPrompt = capabilities.supportsSystemPrompt,
        supportsTemperature = capabilities.supportsTemperature,
        supportsTopP = capabilities.supportsTopP,
        supportsFrequencyPenalty = capabilities.supportsFrequencyPenalty,
        supportsPresencePenalty = capabilities.supportsPresencePenalty,
        supportsStopSequences = capabilities.supportsStopSequences,
        supportsPdfNative = capabilities.supportsPdfNative,
        isFavorite = isFavorite,
        isDefault = isDefault,
        lastUsedAt = lastUsedAt,
        isManual = isManual
    )

    fun ProjectEntity.toDomain(): ProjectWorkspace = ProjectWorkspace(
        id = id,
        name = name,
        description = description,
        instructions = instructions,
        defaultModelKey = defaultModelKey,
        selectedSkillIds = jsonToStringList(selectedSkillIdsJson),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ProjectWorkspace.toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        description = description,
        instructions = instructions,
        defaultModelKey = defaultModelKey,
        selectedSkillIdsJson = stringListToJson(selectedSkillIds),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ProjectFileEntity.toDomain(): ProjectFile = ProjectFile(
        id = id,
        projectId = projectId,
        path = path,
        content = content,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        updatedAt = updatedAt
    )

    fun ProjectFile.toEntity(): ProjectFileEntity = ProjectFileEntity(
        id = id,
        projectId = projectId,
        path = path,
        content = content,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        updatedAt = updatedAt
    )

    fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        projectId = projectId,
        modelKey = modelKey,
        systemPrompt = systemPrompt,
        settings = ChatGenerationSettings(
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            streamingEnabled = streamingEnabled,
            stopSequences = jsonToStringList(stopSequencesJson),
            webSearchEnabled = webSearchEnabled
        ),
        activeSkillIds = jsonToStringList(activeSkillIdsJson),
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
        id = id,
        title = title,
        projectId = projectId,
        modelKey = modelKey,
        systemPrompt = systemPrompt,
        temperature = settings.temperature,
        maxTokens = settings.maxTokens,
        topP = settings.topP,
        frequencyPenalty = settings.frequencyPenalty,
        presencePenalty = settings.presencePenalty,
        streamingEnabled = settings.streamingEnabled,
        stopSequencesJson = stringListToJson(settings.stopSequences),
        webSearchEnabled = settings.webSearchEnabled,
        activeSkillIdsJson = stringListToJson(activeSkillIds),
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
        content = content,
        attachments = jsonToAttachments(attachmentsJson),
        citations = jsonToCitations(citationsJson),
        skillsUsed = jsonToStringList(skillsUsedJson),
        modelUsed = modelUsed,
        tokenInfo = tokenInfo,
        isError = isError,
        isStreaming = false,
        createdAt = createdAt
    )

    fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        content = content,
        attachmentsJson = attachmentsToJson(attachments),
        citationsJson = citationsToJson(citations),
        skillsUsedJson = stringListToJson(skillsUsed),
        modelUsed = modelUsed,
        tokenInfo = tokenInfo,
        isError = isError,
        createdAt = createdAt
    )

    fun SkillEntity.toDomain(): Skill = Skill(
        id = id,
        name = name,
        description = description,
        category = category,
        skillMdContent = skillMdContent,
        instructionsBody = instructionsBody,
        supportingFiles = jsonToSupportingFiles(supportingFilesJson),
        requiredPermissions = jsonToStringList(requiredPermissionsJson)
            .mapNotNull { runCatching { SkillPermission.valueOf(it) }.getOrNull() },
        isEnabled = isEnabled,
        isBuiltIn = isBuiltIn,
        isTrusted = isTrusted,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Skill.toEntity(): SkillEntity = SkillEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        skillMdContent = skillMdContent,
        instructionsBody = instructionsBody,
        supportingFilesJson = supportingFilesToJson(supportingFiles),
        requiredPermissionsJson = stringListToJson(requiredPermissions.map { it.name }),
        isEnabled = isEnabled,
        isBuiltIn = isBuiltIn,
        isTrusted = isTrusted,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun SystemPromptPresetEntity.toDomain(): SystemPromptPreset = SystemPromptPreset(
        id = id,
        name = name,
        content = content,
        isDefault = isDefault,
        createdAt = createdAt
    )

    fun SystemPromptPreset.toEntity(): SystemPromptPresetEntity = SystemPromptPresetEntity(
        id = id,
        name = name,
        content = content,
        isDefault = isDefault,
        createdAt = createdAt
    )

    fun stringListToJson(items: List<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    fun jsonToStringList(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { arr.optString(it) }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    fun stringMapToJson(map: Map<String, String>): String =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()

    fun jsonToStringMap(json: String): Map<String, String> = runCatching {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.optString(k)
        }
        result
    }.getOrDefault(emptyMap())

    private fun attachmentsToJson(items: List<AttachmentItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("mimeType", item.mimeType)
                put("sizeBytes", item.sizeBytes)
                put("isImage", item.isImage)
                put("isPdf", item.isPdf)
                put("extractedText", item.extractedText.take(12000))
                put("localCachePath", item.localCachePath)
                put("pageCount", item.pageCount)
                if (item.errorMessage != null) put("errorMessage", item.errorMessage)
            })
        }
        return arr.toString()
    }

    private fun jsonToAttachments(json: String): List<AttachmentItem> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            AttachmentItem(
                id = obj.optString("id"),
                fileName = obj.optString("fileName"),
                mimeType = obj.optString("mimeType"),
                sizeBytes = obj.optLong("sizeBytes"),
                isImage = obj.optBoolean("isImage"),
                isPdf = obj.optBoolean("isPdf"),
                extractedText = obj.optString("extractedText"),
                localCachePath = obj.optString("localCachePath"),
                pageCount = obj.optInt("pageCount"),
                errorMessage = if (obj.has("errorMessage")) obj.optString("errorMessage") else null
            )
        }
    }.getOrDefault(emptyList())

    private fun citationsToJson(items: List<SearchCitation>): String {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("index", c.index)
                put("title", c.title)
                put("url", c.url)
                put("snippet", c.snippet)
            })
        }
        return arr.toString()
    }

    private fun jsonToCitations(json: String): List<SearchCitation> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            SearchCitation(
                index = obj.optInt("index"),
                title = obj.optString("title"),
                url = obj.optString("url"),
                snippet = obj.optString("snippet")
            )
        }
    }.getOrDefault(emptyList())

    private fun supportingFilesToJson(items: List<SkillSupportingFile>): String {
        val arr = JSONArray()
        items.forEach { f ->
            arr.put(JSONObject().apply {
                put("relativePath", f.relativePath)
                put("content", f.content)
                put("isScript", f.isScript)
            })
        }
        return arr.toString()
    }

    private fun jsonToSupportingFiles(json: String): List<SkillSupportingFile> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            SkillSupportingFile(
                relativePath = obj.optString("relativePath"),
                content = obj.optString("content"),
                isScript = obj.optBoolean("isScript")
            )
        }
    }.getOrDefault(emptyList())
}
