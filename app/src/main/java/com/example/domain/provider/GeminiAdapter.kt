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

class GeminiAdapter : AiProviderAdapter {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext ConnectionResult(false, "Google Gemini API key is required.")
            }
            fetchModels(config, apiKey).fold(
                onSuccess = { models ->
                    ConnectionResult(
                        success = true,
                        message = "Connected to Google Gemini (${models.size} generative models).",
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
                .url("$baseUrl/models?pageSize=100")
                .get()
                .header("x-goog-api-key", apiKey.trim())
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
                    val modelsArr = root.optJSONArray("models") ?: JSONArray()
                    val models = mutableListOf<AiModel>()
                    for (i in 0 until modelsArr.length()) {
                        val obj = modelsArr.optJSONObject(i) ?: continue
                        val methods = obj.optJSONArray("supportedGenerationMethods")
                        val canGenerate = methods != null && (0 until methods.length()).any {
                            methods.optString(it) == "generateContent"
                        }
                        if (!canGenerate) continue
                        val rawName = obj.optString("name")
                        val modelId = rawName.removePrefix("models/").trim()
                        if (modelId.isEmpty()) continue
                        val displayName = obj.optString("displayName").ifBlank { modelId }
                        val inputLimit = obj.optInt("inputTokenLimit", 0).takeIf { it > 0 }
                        models.add(
                            AiModel(
                                key = "${config.id}::$modelId",
                                modelId = modelId,
                                displayName = displayName,
                                providerConfigId = config.id,
                                providerType = ProviderType.GEMINI,
                                contextWindow = inputLimit,
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
                    Result.success(models.sortedBy { it.displayName })
                }
            } catch (e: Exception) {
                Result.failure(IOException("Failed to list Gemini models: ${e.localizedMessage}"))
            }
        }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk> = callbackFlow {
        val baseUrl = ProviderHttpClient.normalizeBaseUrl(config.baseUrl)
        val modelId = request.model.modelId.removePrefix("models/")
        val endpoint = if (request.settings.streamingEnabled) {
            "$baseUrl/models/$modelId:streamGenerateContent?alt=sse"
        } else {
            "$baseUrl/models/$modelId:generateContent"
        }
        val payload = buildGeminiPayload(request)
        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("x-goog-api-key", apiKey.trim())
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
                    trySend(StreamChunk.Error("Empty response from Gemini."))
                    close()
                    return@callbackFlow
                }

                if (!request.settings.streamingEnabled) {
                    val raw = withContext(Dispatchers.IO) { body.string() }
                    val json = JSONObject(raw)
                    emitGeminiJson(json) { trySend(it) }
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
                                emitGeminiJson(json) { trySend(it) }
                            }
                        }
                    }
                    trySend(StreamChunk.Done)
                }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) {
                trySend(StreamChunk.Error("Network error (Gemini): ${e.localizedMessage}"))
            }
        }
        close()
        awaitClose { call.cancel() }
    }

    private fun emitGeminiJson(json: JSONObject, emit: (StreamChunk) -> Unit) {
        val candidates = json.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text")
                if (text.isNotEmpty()) emit(StreamChunk.Delta(text))
            }
        }
        val usage = json.optJSONObject("usageMetadata")
        if (usage != null) {
            val prompt = usage.optInt("promptTokenCount", 0)
            val cand = usage.optInt("candidatesTokenCount", 0)
            val total = usage.optInt("totalTokenCount", prompt + cand)
            if (total > 0) emit(StreamChunk.Usage("$prompt in / $cand out ($total tok)"))
        }
    }

    private fun buildGeminiPayload(request: ChatCompletionRequest): JSONObject {
        val contentsArr = JSONArray()
        request.messages.filter { !it.isError && it.role != MessageRole.SYSTEM }.forEach { msg ->
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val parts = JSONArray()

            if (msg.role == MessageRole.USER) {
                msg.attachments.filter { it.isImage && it.base64Data.isNotBlank() }.forEach { img ->
                    parts.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", img.mimeType.ifBlank { "image/jpeg" })
                            put("data", img.base64Data)
                        })
                    })
                }
                msg.attachments.filter { it.isPdf && it.base64Data.isNotBlank() && request.model.capabilities.supportsPdfNative }
                    .forEach { pdf ->
                        parts.put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "application/pdf")
                                put("data", pdf.base64Data)
                            })
                        })
                    }
            }

            val docsText = msg.attachments.filter { !it.isImage && it.extractedText.isNotBlank() }
            val textBody = buildString {
                append(msg.content)
                docsText.forEach { att ->
                    append("\n\n[Attached File: ${att.fileName}]\n${att.extractedText}")
                }
            }.trim()

            parts.put(JSONObject().apply {
                put("text", textBody.ifBlank { " " })
            })

            contentsArr.put(JSONObject().apply {
                put("role", role)
                put("parts", parts)
            })
        }

        val genConfig = JSONObject().apply {
            put("temperature", request.settings.temperature.toDouble())
            if (request.settings.maxTokens > 0) {
                put("maxOutputTokens", request.settings.maxTokens)
            }
            put("topP", request.settings.topP.toDouble())
            if (request.settings.stopSequences.isNotEmpty()) {
                put("stopSequences", JSONArray(request.settings.stopSequences))
            }
        }

        return JSONObject().apply {
            put("contents", contentsArr)
            put("generationConfig", genConfig)
            if (request.systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", request.systemPrompt)
                    }))
                })
            }
        }
    }
}
