package com.example.domain.provider

import com.example.domain.model.ApiProtocol
import com.example.domain.model.ProviderType

object ProviderAdapterFactory {
    private val openAiAdapter = OpenAiCompatibleAdapter()
    private val anthropicAdapter = AnthropicAdapter()
    private val geminiAdapter = GeminiAdapter()
    private val ollamaAdapter = OllamaAdapter()
    private val cohereAdapter = CohereAdapter()

    fun forProvider(providerType: ProviderType): AiProviderAdapter = when (providerType.protocol) {
        ApiProtocol.OPENAI_COMPATIBLE,
        ApiProtocol.HUGGINGFACE -> openAiAdapter
        ApiProtocol.ANTHROPIC -> anthropicAdapter
        ApiProtocol.GEMINI -> geminiAdapter
        ApiProtocol.OLLAMA -> ollamaAdapter
        ApiProtocol.COHERE -> cohereAdapter
    }
}
