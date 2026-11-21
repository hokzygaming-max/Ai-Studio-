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

class AnthropicAdapter : AiProviderAdapter {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val anthropicVersion = "2023-06-01"

    override suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext ConnectionResult(false, "Anthropic API key is required.")
            }
            fetchModels(config, apiKey).fold(
                onSuccess = { models ->
                    ConnectionResult(
                        success = true,
                        message = "Connected to Anthropic (${models.size} models discovered).",
                        discoveredModelCount = models.size
                    )
                },
                onFailure = { err ->
                    ConnectionResult(false, err.message ?: "Connection failed")
                }
            )
        }

    override suspend fun fetchModels(config: ProviderConfig, apiKey: String): Result<List<AiModel>> =
        withContext(Dispatchers.IO) {
            val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
            val reqBuilder = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .header("x-api-key", apiKey.trim())
                .header("anthropic-version", anthropicVersion)
            config.customHeaders.forEach { (k, v) -> if (k.isNotBlank()) reqBuilder.header(k, v) }

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
                        val id = obj.optString("id").trim()
                        if (id.isEmpty()) continue
                        val displayName = obj.optString("display_name").ifBlank { id }
                        models.add(
                            AiModel(
                                key = "${config.id}::$id",
                                modelId = id,
                                displayName = displayName,
                                providerConfigId = config.id,
                                providerType = ProviderType.ANTHROPIC,
                                contextWindow = 200_000,
                                capabilities = ModelCapabilities(
                                    supportsVision = true,
                                    supportsStreaming = true,
                                    supportsSystemPrompt = true,
                                    supportsTemperature = true,
                                    supportsTopP = true,
                                    supportsFrequencyPenalty = false,
                                    supportsPresencePenalty = false,
                                    supportsStopSequences = true,
                                    supportsPdfNative = true
                                )
                            )
                        )
                    }
                    Result.success(models)
                }
            } catch (e: Exception) {
                Result.failure(IOException("Failed to list Anthropic models: ${e.localizedMessage}"))
            }
        }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk> = callbackFlow {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val payload = buildAnthropicPayload(request)
        val reqBuilder = Request.Builder()
            .url("$baseUrl/messages")
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("x-api-key", apiKey.trim())
            .header("anthropic-version", anthropicVersion)
            .header("Accept", "text/event-stream")
        config.customHeaders.forEach { (k, v) -> if (k.isNotBlank()) reqBuilder.header(k, v) }

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
                val body = resp.body ?: run {
                    trySend(StreamChunk.Error("Empty response from Anthropic."))
                    close()
                    return@callbackFlow
                }

                if (!request.settings.streamingEnabled) {
                    val raw = withContext(Dispatchers.IO) { body.string() }
                    val json = JSONObject(raw)
                    val content = json.optJSONArray("content")
                    if (content != null) {
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i)
                            if (block?.optString("type") == "text") {
                                trySend(StreamChunk.Delta(block.optString("text")))
                            }
                        }
                    }
                    trySend(StreamChunk.Done)
                } else {
                    val source = body.source()
                    withContext(Dispatchers.IO) {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val dataStr = line.removePrefix("data:").trim()
                            runCatching {
                                val json = JSONObject(dataStr)
                                when (json.optString("type")) {
                                    "content_block_delta" -> {
                                        val delta = json.optJSONObject("delta")
                                        if (delta?.optString("type") == "text_delta") {
                                            val text = delta.optString("text")
                                            if (text.isNotEmpty()) trySend(StreamChunk.Delta(text))
                                        }
                                    }
                                    "message_delta" -> {
                                        val usage = json.optJSONObject("usage")
                                        val outTokens = usage?.optInt("output_tokens", 0) ?: 0
                                        if (outTokens > 0) trySend(StreamChunk.Usage("$outTokens out tok"))
                                    }
                                    "error" -> {
                                        val err = json.optJSONObject("error")?.optString("message") ?: "Anthropic stream error"
                                        trySend(StreamChunk.Error(err))
                                    }
                                }
                            }
                        }
                    }
                    trySend(StreamChunk.Done)
                }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) {
                trySend(StreamChunk.Error("Network error (Anthropic): ${e.localizedMessage}"))
            }
        }
        close()
        awaitClose { call.cancel() }
    }

    private fun buildAnthropicPayload(request: ChatCompletionRequest): JSONObject {
        val messagesArr = JSONArray()
        request.messages.filter { !it.isError && it.role != MessageRole.SYSTEM }.forEach { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            val blocks = JSONArray()

            if (msg.role == MessageRole.USER) {
                msg.attachments.filter { it.isImage && it.base64Data.isNotBlank() }.forEach { img ->
                    blocks.put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", img.mimeType.ifBlank { "image/jpeg" })
                            put("data", img.base64Data)
                        })
                    })
                }
                msg.attachments.filter { it.isPdf && it.base64Data.isNotBlank() && request.model.capabilities.supportsPdfNative }
                    .forEach { pdf ->
                        blocks.put(JSONObject().apply {
                            put("type", "document")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "application/pdf")
                                put("data", pdf.base64Data)
                            })
                        })
                    }
            }

            val textDocs = msg.attachments.filter { !it.isImage && it.extractedText.isNotBlank() }
            val fullText = buildString {
                append(msg.content)
                textDocs.forEach { att ->
                    append("\n\n[File: ${att.fileName}]\n${att.extractedText}")
                }
            }.trim()

            blocks.put(JSONObject().apply {
                put("type", "text")
                put("text", fullText.ifBlank { " " })
            })

            messagesArr.put(JSONObject().apply {
                put("role", role)
                put("content", blocks)
            })
        }

        return JSONObject().apply {
            put("model", request.model.modelId)
            put("messages", messagesArr)
            put("max_tokens", request.settings.maxTokens.coerceAtLeast(1))
            put("stream", request.settings.streamingEnabled)
            if (request.systemPrompt.isNotBlank()) {
                put("system", request.systemPrompt)
            }
            put("temperature", request.settings.temperature.toDouble().coerceIn(0.0, 1.0))
            if (request.settings.topP < 1.0f) {
                put("top_p", request.settings.topP.toDouble())
            }
            if (request.settings.stopSequences.isNotEmpty()) {
                put("stop_sequences", JSONArray(request.settings.stopSequences))
            }
        }
    }
}
