package com.example

import android.app.Application
import com.example.core.security.SecureKeyStore
import com.example.data.local.AiStudioDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.AiStudioRepository
import com.example.domain.files.FileProcessor
import com.example.domain.search.WebSearchService
import com.example.domain.skills.SkillManager
import com.example.domain.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiStudioApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AiStudioDatabase
        private set
    lateinit var keyStore: SecureKeyStore
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var voiceManager: VoiceManager
        private set
    lateinit var repository: AiStudioRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AiStudioDatabase.getInstance(this)
        keyStore = SecureKeyStore(this)
        preferencesRepository = UserPreferencesRepository(this)
        voiceManager = VoiceManager(this)
        repository = AiStudioRepository(
            db = database,
            keyStore = keyStore,
            prefsRepo = preferencesRepository,
            fileProcessor = FileProcessor(this),
            skillManager = SkillManager(this),
            webSearchService = WebSearchService()
        )
        appScope.launch {
            repository.ensureSeeded()
        }
    }
}
