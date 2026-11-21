package com.example.domain.provider

import com.example.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenAiCompatibleAdapter : AiProviderAdapter {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            if (config.providerType.requiresApiKey && apiKey.isBlank()) {
                return@withContext ConnectionResult(false, "API key is required for ${config.displayName}.")
            }
            if (!config.providerType.supportsModelListing) {
                return@withContext testMinimalCompletion(config, apiKey)
            }
            val result = fetchModels(config, apiKey)
            result.fold(
                onSuccess = { models ->
                    ConnectionResult(
                        success = true,
                        message = "Connected to ${config.displayName} (${models.size} models available).",
                        discoveredModelCount = models.size
                    )
                },
                onFailure = { err ->
                    ConnectionResult(false, err.message ?: "Connection failed")
                }
            )
        }

    private fun testMinimalCompletion(config: ProviderConfig, apiKey: String): ConnectionResult {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val payload = JSONObject().apply {
            put("model", if (config.providerType == ProviderType.PERPLEXITY) "sonar" else "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "ping")
            }))
            put("max_tokens", 1)
        }
        val reqBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(payload.toString().toRequestBody(jsonMedia))
        applyAuthAndHeaders(reqBuilder, config, apiKey)
        return try {
            ProviderHttpClient.client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    ConnectionResult(true, "Connected to ${config.displayName}.")
                } else {
                    ConnectionResult(false, ProviderHttpClient.formatHttpError(resp.code, body, config.displayName))
                }
            }
        } catch (e: IOException) {
            ConnectionResult(false, "Network error connecting to ${config.displayName}: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchModels(config: ProviderConfig, apiKey: String): Result<List<AiModel>> =
        withContext(Dispatchers.IO) {
            if (!config.providerType.supportsModelListing) {
                return@withContext Result.success(emptyList())
            }
            val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
            val reqBuilder = Request.Builder().url("$baseUrl/models").get()
            applyAuthAndHeaders(reqBuilder, config, apiKey)

            try {
                ProviderHttpClient.client.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(ProviderHttpClient.formatHttpError(resp.code, body, config.displayName))
                        )
                    }
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    val models = mutableListOf<AiModel>()
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val modelId = obj.optString("id").trim()
                        if (modelId.isEmpty()) continue
                        val displayName = obj.optString("name").ifBlank { modelId }
                        val contextWindow = when {
                            obj.has("context_length") -> obj.optInt("context_length").takeIf { it > 0 }
                            obj.has("context_window") -> obj.optInt("context_window").takeIf { it > 0 }
                            obj.has("max_context_length") -> obj.optInt("max_context_length").takeIf { it > 0 }
                            else -> null
                        }
                        models.add(
                            AiModel(
                                key = "${config.id}::$modelId",
                                modelId = modelId,
                                displayName = displayName,
                                providerConfigId = config.id,
                                providerType = config.providerType,
                                contextWindow = contextWindow,
                                capabilities = inferOpenAiCompatibleCapabilities(modelId, obj, config.providerType)
                            )
                        )
                    }
                    Result.success(models.sortedBy { it.displayName.lowercase() })
                }
            } catch (e: Exception) {
                Result.failure(IOException("Failed to fetch models from ${config.displayName}: ${e.localizedMessage}"))
            }
        }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk> = callbackFlow {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val payload = buildChatPayload(request, config)
        val reqBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("Accept", if (request.settings.streamingEnabled) "text/event-stream" else "application/json")
        applyAuthAndHeaders(reqBuilder, config, apiKey)

        val call = ProviderHttpClient.client.newCall(reqBuilder.build())
        try {
            val response = withContext(Dispatchers.IO) { call.execute() }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    trySend(StreamChunk.Error(ProviderHttpClient.formatHttpError(resp.code, errBody, config.displayName)))
                    close()
                    return@callbackFlow
                }

                val body = resp.body
                if (body == null) {
                    trySend(StreamChunk.Error("Empty response body from ${config.displayName}."))
                    close()
                    return@callbackFlow
                }

                if (!request.settings.streamingEnabled) {
                    val raw = withContext(Dispatchers.IO) { body.string() }
                    val json = JSONObject(raw)
                    val choices = json.optJSONArray("choices")
                    val text = choices?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                    if (text.isNotEmpty()) trySend(StreamChunk.Delta(text))
                    emitCitationsIfPresent(json) { trySend(it) }
                    emitUsageIfPresent(json) { trySend(it) }
                    trySend(StreamChunk.Done)
                } else {
                    val source = body.source()
                    withContext(Dispatchers.IO) {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank() || line.startsWith(":")) continue
                            if (line.startsWith("data:")) {
                                val dataStr = line.removePrefix("data:").trim()
                                if (dataStr == "[DONE]") {
                                    break
                                }
                                runCatching {
                                    val json = JSONObject(dataStr)
                                    val choices = json.optJSONArray("choices")
                                    val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                                    val content = delta?.optString("content").orEmpty()
                                    val reasoning = delta?.optString("reasoning_content").orEmpty()
                                    if (reasoning.isNotEmpty()) {
                                        trySend(StreamChunk.Delta(reasoning))
                                    }
                                    if (content.isNotEmpty()) {
                                        trySend(StreamChunk.Delta(content))
                                    }
                                    emitCitationsIfPresent(json) { trySend(it) }
                                    emitUsageIfPresent(json) { trySend(it) }
                                }
                            }
                        }
                    }
                    trySend(StreamChunk.Done)
                }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) {
                trySend(StreamChunk.Error("Network error (${config.displayName}): ${e.localizedMessage ?: "Connection interrupted"}"))
            }
        } catch (e: Exception) {
            trySend(StreamChunk.Error("Unexpected error (${config.displayName}): ${e.localizedMessage}"))
        }
        close()
        awaitClose { call.cancel() }
    }

    private fun applyAuthAndHeaders(builder: Request.Builder, config: ProviderConfig, apiKey: String) {
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        if (config.providerType == ProviderType.OPENROUTER) {
            builder.header("HTTP-Referer", "https://aistudio.local")
            builder.header("X-Title", "AI Studio Android")
        }
        config.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                builder.header(k.trim(), v.trim())
            }
        }
    }

    private fun buildChatPayload(request: ChatCompletionRequest, config: ProviderConfig): JSONObject {
        val caps = request.model.capabilities
        val messagesArr = JSONArray()

        if (request.systemPrompt.isNotBlank() && caps.supportsSystemPrompt) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }

        request.messages.filter { !it.isError }.forEach { msg ->
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            val imageAttachments = msg.attachments.filter { it.isImage && it.base64Data.isNotBlank() }
            val textAttachments = msg.attachments.filter { !it.isImage && it.extractedText.isNotBlank() }
            val enrichedText = buildString {
                append(msg.content)
                textAttachments.forEach { att ->
                    append("\n\n[Attached File: ${att.fileName}]\n```\n${att.extractedText}\n```")
                }
            }.trim()

            if (msg.role == MessageRole.USER && imageAttachments.isNotEmpty() && caps.supportsVision) {
                val parts = JSONArray()
                if (enrichedText.isNotEmpty()) {
                    parts.put(JSONObject().apply {
                        put("type", "text")
                        put("text", enrichedText)
                    })
                }
                imageAttachments.forEach { img ->
                    val mime = img.mimeType.ifBlank { "image/jpeg" }
                    parts.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:$mime;base64,${img.base64Data}")
                        })
                    })
                }
                messagesArr.put(JSONObject().apply {
                    put("role", roleStr)
                    put("content", parts)
                })
            } else {
                messagesArr.put(JSONObject().apply {
                    put("role", roleStr)
                    put("content", enrichedText.ifBlank { " " })
                })
            }
        }

        return JSONObject().apply {
            put("model", request.model.modelId)
            put("messages", messagesArr)
            put("stream", request.settings.streamingEnabled && caps.supportsStreaming)
            if (caps.supportsTemperature) {
                put("temperature", request.settings.temperature.toDouble())
            }
            if (request.settings.maxTokens > 0) {
                put("max_tokens", request.settings.maxTokens)
            }
            if (caps.supportsTopP) {
                put("top_p", request.settings.topP.toDouble())
            }
            if (caps.supportsFrequencyPenalty && request.settings.frequencyPenalty != 0f) {
                put("frequency_penalty", request.settings.frequencyPenalty.toDouble())
            }
            if (caps.supportsPresencePenalty && request.settings.presencePenalty != 0f) {
                put("presence_penalty", request.settings.presencePenalty.toDouble())
            }
            if (caps.supportsStopSequences && request.settings.stopSequences.isNotEmpty()) {
                put("stop", JSONArray(request.settings.stopSequences))
            }
            if (config.providerType == ProviderType.OPENROUTER && request.settings.streamingEnabled) {
                put("stream_options", JSONObject().apply { put("include_usage", true) })
            }
        }
    }

    private fun emitCitationsIfPresent(json: JSONObject, emit: (StreamChunk) -> Unit) {
        val citationsArr = json.optJSONArray("citations") ?: return
        if (citationsArr.length() == 0) return
        val list = List(citationsArr.length()) { idx ->
            val url = citationsArr.optString(idx)
            SearchCitation(index = idx + 1, title = url, url = url, snippet = "")
        }.filter { it.url.isNotBlank() }
        if (list.isNotEmpty()) emit(StreamChunk.Citations(list))
    }

    private fun emitUsageIfPresent(json: JSONObject, emit: (StreamChunk) -> Unit) {
        val usage = json.optJSONObject("usage") ?: return
        val prompt = usage.optInt("prompt_tokens", 0)
        val completion = usage.optInt("completion_tokens", 0)
        val total = usage.optInt("total_tokens", prompt + completion)
        if (total > 0) {
            emit(StreamChunk.Usage("${prompt} in / ${completion} out ($total tok)"))
        }
    }

    private fun inferOpenAiCompatibleCapabilities(
        modelId: String,
        raw: JSONObject,
        providerType: ProviderType
    ): ModelCapabilities {
        val lower = modelId.lowercase()
        val architecture = raw.optJSONObject("architecture")
        val modality = architecture?.optString("modality").orEmpty().lowercase()
        val supportsVision = modality.contains("image") ||
            lower.contains("gpt-4o") ||
            lower.contains("gpt-4.1") ||
            lower.contains("gpt-4.5") ||
            lower.contains("gpt-5") ||
            lower.contains("vision") ||
            lower.contains("-vl") ||
            lower.contains("pixtral") ||
            lower.contains("llama-3.2-11b-vision") ||
            lower.contains("llama-3.2-90b-vision") ||
            lower.contains("gemini") ||
            lower.contains("claude-3") ||
            lower.contains("claude-4") ||
            lower.contains("grok-2-vision")

        val isReasoningModel = lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4")
        val supportsPenalties = providerType != ProviderType.PERPLEXITY && !isReasoningModel
        return ModelCapabilities(
            supportsVision = supportsVision,
            supportsStreaming = true,
            supportsSystemPrompt = !lower.startsWith("o1-mini") && !lower.startsWith("o1-preview"),
            supportsTemperature = !isReasoningModel,
            supportsTopP = !isReasoningModel,
            supportsFrequencyPenalty = supportsPenalties,
            supportsPresencePenalty = supportsPenalties,
            supportsStopSequences = !isReasoningModel
        )
    }
}
