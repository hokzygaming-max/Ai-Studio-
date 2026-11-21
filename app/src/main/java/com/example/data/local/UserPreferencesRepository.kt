package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.WebSearchProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ai_studio_preferences")

data class UserPreferences(
    val globalSystemPrompt: String = "You are AI Studio, a helpful, accurate, and privacy-respecting AI engineering and writing assistant. Always be honest about capabilities and never fabricate facts or citations.",
    val defaultTemperature: Float = 0.7f,
    val defaultMaxTokens: Int = 4096,
    val defaultStreaming: Boolean = true,
    val autoDiscoverSkills: Boolean = true,
    val darkTheme: Boolean = true,
    val ttsAutoRead: Boolean = false,
    val selectedTtsVoice: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val webSearchProvider: WebSearchProviderType = WebSearchProviderType.NONE,
    val webSearchCustomEndpoint: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val GLOBAL_SYSTEM_PROMPT = stringPreferencesKey("global_system_prompt")
        val DEFAULT_TEMPERATURE = floatPreferencesKey("default_temperature")
        val DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
        val DEFAULT_STREAMING = booleanPreferencesKey("default_streaming")
        val AUTO_DISCOVER_SKILLS = booleanPreferencesKey("auto_discover_skills")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val TTS_AUTO_READ = booleanPreferencesKey("tts_auto_read")
        val SELECTED_TTS_VOICE = stringPreferencesKey("selected_tts_voice")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val WEB_SEARCH_ENDPOINT = stringPreferencesKey("web_search_endpoint")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val default = UserPreferences()
        UserPreferences(
            globalSystemPrompt = prefs[Keys.GLOBAL_SYSTEM_PROMPT] ?: default.globalSystemPrompt,
            defaultTemperature = prefs[Keys.DEFAULT_TEMPERATURE] ?: default.defaultTemperature,
            defaultMaxTokens = prefs[Keys.DEFAULT_MAX_TOKENS] ?: default.defaultMaxTokens,
            defaultStreaming = prefs[Keys.DEFAULT_STREAMING] ?: default.defaultStreaming,
            autoDiscoverSkills = prefs[Keys.AUTO_DISCOVER_SKILLS] ?: default.autoDiscoverSkills,
            darkTheme = prefs[Keys.DARK_THEME] ?: default.darkTheme,
            ttsAutoRead = prefs[Keys.TTS_AUTO_READ] ?: default.ttsAutoRead,
            selectedTtsVoice = prefs[Keys.SELECTED_TTS_VOICE] ?: default.selectedTtsVoice,
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: default.ttsSpeechRate,
            webSearchProvider = runCatching {
                WebSearchProviderType.valueOf(prefs[Keys.WEB_SEARCH_PROVIDER] ?: "NONE")
            }.getOrDefault(WebSearchProviderType.NONE),
            webSearchCustomEndpoint = prefs[Keys.WEB_SEARCH_ENDPOINT] ?: ""
        )
    }

    suspend fun updateGlobalSystemPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.GLOBAL_SYSTEM_PROMPT] = prompt }
    }

    suspend fun updateDefaults(temperature: Float, maxTokens: Int, streaming: Boolean) {
        context.dataStore.edit {
            it[Keys.DEFAULT_TEMPERATURE] = temperature
            it[Keys.DEFAULT_MAX_TOKENS] = maxTokens
            it[Keys.DEFAULT_STREAMING] = streaming
        }
    }

    suspend fun updateAutoDiscoverSkills(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DISCOVER_SKILLS] = enabled }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun updateVoiceSettings(autoRead: Boolean, voiceName: String, speechRate: Float) {
        context.dataStore.edit {
            it[Keys.TTS_AUTO_READ] = autoRead
            it[Keys.SELECTED_TTS_VOICE] = voiceName
            it[Keys.TTS_SPEECH_RATE] = speechRate
        }
    }

    suspend fun updateWebSearchConfig(provider: WebSearchProviderType, customEndpoint: String) {
        context.dataStore.edit {
            it[Keys.WEB_SEARCH_PROVIDER] = provider.name
            it[Keys.WEB_SEARCH_ENDPOINT] = customEndpoint
        }
    }
}
