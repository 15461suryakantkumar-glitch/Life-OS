package com.example.suru

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SuruVoiceManager(private val appContext: Context) {

    private val _voiceState = MutableStateFlow(SuruVoiceState.READY)
    val voiceState: StateFlow<SuruVoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentSpeechRate: Float = 1.0f
    private var isMuted: Boolean = false
    private var currentLanguage: SuruVoiceLanguage = SuruVoiceLanguage.HINGLISH

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyTtsLanguage(currentLanguage)
                    tts?.setSpeechRate(currentSpeechRate)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _voiceState.value = SuruVoiceState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            if (_voiceState.value == SuruVoiceState.SPEAKING) {
                                _voiceState.value = SuruVoiceState.COMPLETED
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _voiceState.value = SuruVoiceState.READY
                        }
                    })
                    _isTtsReady.value = true
                }
            }
        } catch (_: Throwable) {
            _isTtsReady.value = false
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (_: Throwable) {
            false
        }
    }

    fun updateSettings(muted: Boolean, speechRate: Float, language: SuruVoiceLanguage) {
        isMuted = muted
        currentSpeechRate = speechRate.coerceIn(0.75f, 1.5f)
        currentLanguage = language
        try {
            tts?.setSpeechRate(currentSpeechRate)
            applyTtsLanguage(language)
            if (muted) {
                stopSpeaking()
            }
        } catch (_: Throwable) {
        }
    }

    private fun applyTtsLanguage(language: SuruVoiceLanguage) {
        val locale = Locale.forLanguageTag(language.ttsLanguageTag)
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.ENGLISH)
        }
    }

    fun startListening(
        language: SuruVoiceLanguage = currentLanguage,
        onFinalTranscript: (String) -> Unit
    ) {
        _voiceError.value = null
        _partialTranscript.value = ""
        stopSpeaking()

        if (!hasRecordAudioPermission()) {
            _voiceState.value = SuruVoiceState.FAILED
            _voiceError.value = "Microphone permission is needed for voice commands. You can also type below anytime."
            return
        }

        if (!isSpeechRecognitionAvailable()) {
            _voiceState.value = SuruVoiceState.FAILED
            _voiceError.value = "Android speech recognition service is unavailable on this device. Please type your message."
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = SuruVoiceState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    _voiceState.value = SuruVoiceState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _voiceState.value = SuruVoiceState.PROCESSING
                }

                override fun onError(error: Int) {
                    _voiceState.value = SuruVoiceState.FAILED
                    _voiceError.value = mapRecognitionError(error)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        _partialTranscript.value = text
                        _voiceState.value = SuruVoiceState.COMPLETED
                        onFinalTranscript(text)
                    } else {
                        _voiceState.value = SuruVoiceState.FAILED
                        _voiceError.value = "Didn't catch that clearly — try speaking again or type below."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.sttLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.sttLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            _voiceState.value = SuruVoiceState.LISTENING
            recognizer.startListening(intent)
        } catch (_: Throwable) {
            _voiceState.value = SuruVoiceState.FAILED
            _voiceError.value = "Could not start voice recognition. Please type your request."
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Throwable) {
        }
        if (_voiceState.value == SuruVoiceState.LISTENING) {
            _voiceState.value = SuruVoiceState.READY
        }
    }

    fun speak(text: String) {
        if (isMuted || !_isTtsReady.value || text.isBlank()) return
        try {
            val cleanForSpeech = text
                .replace("•", "")
                .replace("\n", ". ")
                .take(800)
            tts?.setSpeechRate(currentSpeechRate)
            tts?.speak(cleanForSpeech, TextToSpeech.QUEUE_FLUSH, null, "suru_${System.currentTimeMillis()}")
            _voiceState.value = SuruVoiceState.SPEAKING
        } catch (_: Throwable) {
            _voiceState.value = SuruVoiceState.READY
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
        if (_voiceState.value == SuruVoiceState.SPEAKING) {
            _voiceState.value = SuruVoiceState.READY
        }
    }

    fun resetStateToReady() {
        _voiceError.value = null
        _partialTranscript.value = ""
        _voiceState.value = SuruVoiceState.READY
    }

    fun clearError() {
        _voiceError.value = null
        if (_voiceState.value == SuruVoiceState.FAILED) {
            _voiceState.value = SuruVoiceState.READY
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Throwable) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun mapRecognitionError(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Check your microphone and try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech service needs connectivity (unless an offline voice pack is installed on your device). You can type anytime."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched. Try speaking clearly or type your command."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected in time. Tap the mic to try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy — please try again in a moment."
            else -> "Voice input encountered an issue. Manual text input is ready below."
        }
    }
}
