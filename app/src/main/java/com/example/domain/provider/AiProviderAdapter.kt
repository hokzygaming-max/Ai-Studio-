package com.example.domain.provider

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class ChatCompletionRequest(
    val model: AiModel,
    val messages: List<ChatMessage>,
    val systemPrompt: String,
    val settings: ChatGenerationSettings
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val discoveredModelCount: Int = 0
)

sealed interface StreamChunk {
    data class Delta(val text: String) : StreamChunk
    data class Citations(val citations: List<SearchCitation>) : StreamChunk
    data class Usage(val info: String) : StreamChunk
    data object Done : StreamChunk
    data class Error(val message: String) : StreamChunk
}

interface AiProviderAdapter {
    suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionResult
    suspend fun fetchModels(config: ProviderConfig, apiKey: String): Result<List<AiModel>>
    fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamChunk>
}

object ProviderHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

    fun formatHttpError(code: Int, body: String, providerName: String): String {
        val snippet = extractErrorMessage(body).take(260)
        return when (code) {
            401 -> "$providerName authentication failed (401). Check that your API key is valid."
            403 -> "$providerName access denied (403). Your key may lack permissions or billing access."
            404 -> "$providerName endpoint or model not found (404). Verify base URL and model ID.${if (snippet.isNotBlank()) " ($snippet)" else ""}"
            413 -> "Request payload too large (413). Try fewer attachments or shorter context."
            429 -> "$providerName rate limit or quota exceeded (429). Wait and retry or check provider limits.${if (snippet.isNotBlank()) " ($snippet)" else ""}"
            in 400..499 -> "$providerName rejected the request ($code): ${snippet.ifBlank { "Invalid parameters or token limit exceeded." }}"
            in 500..599 -> "$providerName server error ($code). The provider may be experiencing an outage."
            else -> "$providerName HTTP error $code: $snippet"
        }
    }

    private fun extractErrorMessage(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val json = org.json.JSONObject(body)
            when {
                json.has("error") -> {
                    val err = json.get("error")
                    if (err is org.json.JSONObject) err.optString("message", err.toString())
                    else err.toString()
                }
                json.has("message") -> json.optString("message")
                json.has("detail") -> json.optString("detail")
                else -> body
            }
        }.getOrDefault(body)
    }
}
