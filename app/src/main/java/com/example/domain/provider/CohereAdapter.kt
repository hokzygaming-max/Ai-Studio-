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

class CohereAdapter : AiProviderAdapter {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext ConnectionResult(false, "Cohere API key is required.")
            }
            fetchModels(config, apiKey).fold(
                onSuccess = { models ->
                    ConnectionResult(
                        success = true,
                        message = "Connected to Cohere (${models.size} chat models found).",
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
            val modelsUrl = baseUrl.replace("/v2", "/v1") + "/models?endpoint=chat"
            val reqBuilder = Request.Builder()
                .url(modelsUrl)
                .get()
                .header("Authorization", "Bearer ${apiKey.trim()}")
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
                    val arr = root.optJSONArray("models") ?: JSONArray()
                    val list = mutableListOf<AiModel>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        if (name.isEmpty()) continue
                        val ctx = obj.optInt("context_length", 0).takeIf { it > 0 }
                        val supportsVision = name.contains("vision", ignoreCase = true)
                        list.add(
                            AiModel(
                                key = "${config.id}::$name",
                                modelId = name,
                                displayName = name,
                                providerConfigId = config.id,
                                providerType = ProviderType.COHERE,
                                contextWindow = ctx,
                                capabilities = ModelCapabilities(
                                    supportsVision = supportsVision,
                                    supportsStreaming = true,
                                    supportsSystemPrompt = true,
                                    supportsTemperature = true,
                                    supportsTopP = true,
                                    supportsFrequencyPenalty = true,
                                    supportsPresencePenalty = true,
                                    supportsStopSequences = true
                                )
                            )
                        )
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(IOException("Failed to fetch Cohere models: ${e.localizedMessage}"))
            }
        }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk> = callbackFlow {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val messagesArr = JSONArray()
        if (request.systemPrompt.isNotBlank()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }
        request.messages.filter { !it.isError }.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            val textDocs = msg.attachments.filter { !it.isImage && it.extractedText.isNotBlank() }
            val text = buildString {
                append(msg.content)
                textDocs.forEach { att -> append("\n\n[File: ${att.fileName}]\n${att.extractedText}") }
            }.trim()
            messagesArr.put(JSONObject().apply {
                put("role", role)
                put("content", text.ifBlank { " " })
            })
        }

        val payload = JSONObject().apply {
            put("model", request.model.modelId)
            put("messages", messagesArr)
            put("stream", request.settings.streamingEnabled)
            put("temperature", request.settings.temperature.toDouble())
            if (request.settings.maxTokens > 0) put("max_tokens", request.settings.maxTokens)
            if (request.settings.topP < 1.0f) put("p", request.settings.topP.toDouble())
            if (request.settings.stopSequences.isNotEmpty()) {
                put("stop_sequences", JSONArray(request.settings.stopSequences))
            }
        }

        val reqBuilder = Request.Builder()
            .url("$baseUrl/chat")
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer ${apiKey.trim()}")
        config.customHeaders.forEach { (k, v) -> if (k.isNotBlank()) reqBuilder.header(k, v) }

        val call = ProviderHttpClient.client.newCall(reqBuilder.build())
        try {
            val response = withContext(Dispatchers.IO) { call.execute() }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    trySend(StreamChunk.Error(ProviderHttpClient.formatHttpError(resp.code, err, config.displayName)))
                    close()
                    return@callbackFlow
                }
                val body = resp.body ?: run {
                    trySend(StreamChunk.Error("Empty response from Cohere."))
                    close()
                    return@callbackFlow
                }
                if (!request.settings.streamingEnabled) {
                    val raw = withContext(Dispatchers.IO) { body.string() }
                    val json = JSONObject(raw)
                    val contentArr = json.optJSONObject("message")?.optJSONArray("content")
                    if (contentArr != null) {
                        for (i in 0 until contentArr.length()) {
                            val text = contentArr.optJSONObject(i)?.optString("text").orEmpty()
                            if (text.isNotEmpty()) trySend(StreamChunk.Delta(text))
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
                            if (dataStr == "[DONE]") break
                            runCatching {
                                val json = JSONObject(dataStr)
                                if (json.optString("type") == "content-delta") {
                                    val text = json.optJSONObject("delta")
                                        ?.optJSONObject("message")
                                        ?.optJSONObject("content")
                                        ?.optString("text")
                                        .orEmpty()
                                    if (text.isNotEmpty()) trySend(StreamChunk.Delta(text))
                                }
                            }
                        }
                    }
                    trySend(StreamChunk.Done)
                }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) {
                trySend(StreamChunk.Error("Network error (Cohere): ${e.localizedMessage}"))
            }
        }
        close()
        awaitClose { call.cancel() }
    }
}
