package com.example.domain.model

enum class ApiProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
    COHERE,
    HUGGINGFACE,
    OLLAMA
}

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val protocol: ApiProtocol,
    val requiresApiKey: Boolean,
    val supportsModelListing: Boolean,
    val isLocalDefault: Boolean,
    val pricingAndTierNote: String,
    val defaultModelsEndpointHint: String
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Paid API usage billed by OpenAI per token.",
        defaultModelsEndpointHint = "/models"
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        protocol = ApiProtocol.ANTHROPIC,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Paid API usage billed by Anthropic per token.",
        defaultModelsEndpointHint = "/models"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Usage billed by DeepSeek; check DeepSeek console for balance.",
        defaultModelsEndpointHint = "/models"
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        protocol = ApiProtocol.GEMINI,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Free tier available with rate limits; paid tiers billed by Google.",
        defaultModelsEndpointHint = "/models"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Some free-tier models available with rate limits; paid models billed by OpenRouter.",
        defaultModelsEndpointHint = "/models"
    ),
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Free tier available with strict rate limits; paid tiers billed by Groq.",
        defaultModelsEndpointHint = "/models"
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Free experiment tier / paid API usage determined by Mistral AI.",
        defaultModelsEndpointHint = "/models"
    ),
    TOGETHER(
        displayName = "Together AI",
        defaultBaseUrl = "https://api.together.xyz/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Usage billed by Together AI per token.",
        defaultModelsEndpointHint = "/models"
    ),
    FIREWORKS(
        displayName = "Fireworks AI",
        defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Usage billed by Fireworks AI per token.",
        defaultModelsEndpointHint = "/models"
    ),
    PERPLEXITY(
        displayName = "Perplexity API",
        defaultBaseUrl = "https://api.perplexity.ai",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = false,
        isLocalDefault = false,
        pricingAndTierNote = "Paid API usage billed by Perplexity; add sonar models manually if needed.",
        defaultModelsEndpointHint = "Manual model ID (e.g. sonar, sonar-pro)"
    ),
    XAI(
        displayName = "xAI",
        defaultBaseUrl = "https://api.x.ai/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Paid API usage billed by xAI.",
        defaultModelsEndpointHint = "/models"
    ),
    CEREBRAS(
        displayName = "Cerebras",
        defaultBaseUrl = "https://api.cerebras.ai/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Free tier with rate limits and paid tiers governed by Cerebras.",
        defaultModelsEndpointHint = "/models"
    ),
    COHERE(
        displayName = "Cohere",
        defaultBaseUrl = "https://api.cohere.com/v2",
        protocol = ApiProtocol.COHERE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Trial key rate-limited; production usage billed by Cohere.",
        defaultModelsEndpointHint = "/v1/models"
    ),
    HUGGINGFACE(
        displayName = "Hugging Face",
        defaultBaseUrl = "https://router.huggingface.co/v1",
        protocol = ApiProtocol.HUGGINGFACE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Free inference tier with monthly/rate limits; PRO/paid billed by Hugging Face.",
        defaultModelsEndpointHint = "/models"
    ),
    OLLAMA(
        displayName = "Ollama (Local / LAN)",
        defaultBaseUrl = "http://127.0.0.1:11434",
        protocol = ApiProtocol.OLLAMA,
        requiresApiKey = false,
        supportsModelListing = true,
        isLocalDefault = true,
        pricingAndTierNote = "Runs locally or on your LAN host; performance depends on device/host RAM & hardware.",
        defaultModelsEndpointHint = "/api/tags"
    ),
    CUSTOM_LOCAL_OPENAI(
        displayName = "Custom Local OpenAI-Compatible",
        defaultBaseUrl = "http://127.0.0.1:8080/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = false,
        supportsModelListing = true,
        isLocalDefault = true,
        pricingAndTierNote = "Local endpoint (llama.cpp, LM Studio, vLLM, LocalAI); speed depends on device/host hardware.",
        defaultModelsEndpointHint = "/models"
    ),
    CUSTOM_OPENAI(
        displayName = "Custom OpenAI-Compatible API",
        defaultBaseUrl = "https://api.example.com/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        requiresApiKey = true,
        supportsModelListing = true,
        isLocalDefault = false,
        pricingAndTierNote = "Third-party OpenAI-compatible gateway; terms and billing set by the endpoint owner.",
        defaultModelsEndpointHint = "/models"
    )
}
