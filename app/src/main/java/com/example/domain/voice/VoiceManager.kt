package com.example.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class VoiceOption(
    val name: String,
    val localeLabel: String,
    val isLocal: Boolean
)

class VoiceManager(private val context: Context) {

    private val appContext = context.applicationContext

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var rawVoices: List<Voice> = emptyList()

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            textToSpeech = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    textToSpeech?.language = Locale.getDefault()
                    val voices = textToSpeech?.voices?.toList().orEmpty()
                        .sortedWith(compareBy({ !it.locale.language.equals(Locale.getDefault().language, true) }, { it.name }))
                    rawVoices = voices
                    _availableVoices.value = voices.map { v ->
                        VoiceOption(
                            name = v.name,
                            localeLabel = "${v.locale.displayLanguage} (${v.locale.country})",
                            isLocal = !v.isNetworkConnectionRequired
                        )
                    }
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }
                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _speakingMessageId.value = null
                        }
                    })
                }
            }
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        _voiceError.value = null
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _voiceError.value = "On-device Speech Recognition service is not available on this device."
            return
        }
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _partialTranscript.value = ""
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _isListening.value = false
            }
            override fun onError(error: Int) {
                _isListening.value = false
                if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                    _voiceError.value = "Speech recognition error (code $error)."
                }
            }
            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isNotEmpty()) onFinalResult(best)
                _partialTranscript.value = ""
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                _partialTranscript.value = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        _isListening.value = false
    }

    fun speak(text: String, messageId: String, preferredVoiceName: String, speechRate: Float) {
        if (!ttsReady) {
            _voiceError.value = "Android Text-to-Speech engine is initializing or unavailable."
            return
        }
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("[#*_`>]"), "")
            .trim()
        if (cleanText.isEmpty()) return

        if (preferredVoiceName.isNotBlank()) {
            rawVoices.firstOrNull { it.name == preferredVoiceName }?.let { voice ->
                textToSpeech?.voice = voice
            }
        }
        textToSpeech?.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        _speakingMessageId.value = messageId
        _isSpeaking.value = true
        textToSpeech?.speak(
            cleanText.take(3900),
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    fun stopSpeaking() {
        runCatching { textToSpeech?.stop() }
        _isSpeaking.value = false
        _speakingMessageId.value = null
    }

    fun clearError() {
        _voiceError.value = null
    }

    fun release() {
        stopListening()
        runCatching { textToSpeech?.shutdown() }
    }
}
