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

class OllamaAdapter : AiProviderAdapter {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            fetchModels(config, apiKey).fold(
                onSuccess = { models ->
                    if (models.isEmpty()) {
                        ConnectionResult(
                            success = true,
                            message = "Connected to Ollama at ${config.baseUrl}, but 0 local models are installed. Pull a model in Ollama first.",
                            discoveredModelCount = 0
                        )
                    } else {
                        ConnectionResult(
                            success = true,
                            message = "Connected to Ollama (${models.size} installed models found).",
                            discoveredModelCount = models.size
                        )
                    }
                },
                onFailure = { err ->
                    ConnectionResult(false, err.message ?: "Cannot reach Ollama endpoint.")
                }
            )
        }

    override suspend fun fetchModels(config: ProviderConfig, apiKey: String): Result<List<AiModel>> =
        withContext(Dispatchers.IO) {
            val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
            val reqBuilder = Request.Builder().url("$baseUrl/api/tags").get()
            if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
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
                        val lower = name.lowercase()
                        val supportsVision = lower.contains("llava") || lower.contains("vision") ||
                            lower.contains("vl") || lower.contains("bakllava") || lower.contains("moondream")
                        list.add(
                            AiModel(
                                key = "${config.id}::$name",
                                modelId = name,
                                displayName = name,
                                providerConfigId = config.id,
                                providerType = config.providerType,
                                contextWindow = null,
                                capabilities = ModelCapabilities(
                                    supportsVision = supportsVision,
                                    supportsStreaming = true,
                                    supportsSystemPrompt = true,
                                    supportsTemperature = true,
                                    supportsTopP = true,
                                    supportsFrequencyPenalty = false,
                                    supportsPresencePenalty = false,
                                    supportsStopSequences = true
                                )
                            )
                        )
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(IOException("Could not reach Ollama at $baseUrl: ${e.localizedMessage}"))
            }
        }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk> = callbackFlow {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val payload = buildOllamaPayload(request)
        val reqBuilder = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(payload.toString().toRequestBody(jsonMedia))
        if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
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
                    trySend(StreamChunk.Error("Empty response from Ollama."))
                    close()
                    return@callbackFlow
                }

                if (!request.settings.streamingEnabled) {
                    val raw = withContext(Dispatchers.IO) { body.string() }
                    val json = JSONObject(raw)
                    val content = json.optJSONObject("message")?.optString("content").orEmpty()
                    if (content.isNotEmpty()) trySend(StreamChunk.Delta(content))
                    trySend(StreamChunk.Done)
                } else {
                    val source = body.source()
                    withContext(Dispatchers.IO) {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            runCatching {
                                val json = JSONObject(line)
                                val err = json.optString("error")
                                if (err.isNotEmpty()) {
                                    trySend(StreamChunk.Error("Ollama error: $err"))
                                    return@runCatching
                                }
                                val content = json.optJSONObject("message")?.optString("content").orEmpty()
                                if (content.isNotEmpty()) trySend(StreamChunk.Delta(content))
                                if (json.optBoolean("done", false)) {
                                    val evalCount = json.optInt("eval_count", 0)
                                    val promptEval = json.optInt("prompt_eval_count", 0)
                                    if (evalCount > 0) {
                                        trySend(StreamChunk.Usage("$promptEval in / $evalCount out tok"))
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
                trySend(StreamChunk.Error("Ollama connection error: ${e.localizedMessage}"))
            }
        }
        close()
        awaitClose { call.cancel() }
    }

    private fun buildOllamaPayload(request: ChatCompletionRequest): JSONObject {
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
            val textBody = buildString {
                append(msg.content)
                textDocs.forEach { att ->
                    append("\n\n[File: ${att.fileName}]\n${att.extractedText}")
                }
            }.trim()
            val msgObj = JSONObject().apply {
                put("role", role)
                put("content", textBody.ifBlank { " " })
            }
            val imgs = msg.attachments.filter { it.isImage && it.base64Data.isNotBlank() }
            if (msg.role == MessageRole.USER && imgs.isNotEmpty() && request.model.capabilities.supportsVision) {
                msgObj.put("images", JSONArray(imgs.map { it.base64Data }))
            }
            messagesArr.put(msgObj)
        }

        val options = JSONObject().apply {
            put("temperature", request.settings.temperature.toDouble())
            put("top_p", request.settings.topP.toDouble())
            if (request.settings.maxTokens > 0) put("num_predict", request.settings.maxTokens)
            if (request.settings.stopSequences.isNotEmpty()) {
                put("stop", JSONArray(request.settings.stopSequences))
            }
        }

        return JSONObject().apply {
            put("model", request.model.modelId)
            put("messages", messagesArr)
            put("stream", request.settings.streamingEnabled)
            put("options", options)
        }
    }
}
