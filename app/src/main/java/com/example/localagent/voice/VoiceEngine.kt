package com.example.localagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceEngine(
    private val context: Context,
    private val onUtteranceComplete: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceEngine"
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var voiceCommandManager: VoiceCommandManager? = null
    private var isTtsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale.US is missing or not supported, trying default locale")
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsReady = true
            Log.d(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) {
            Log.w(TAG, "TTS not ready or blank text provided")
            return
        }
        Log.d(TAG, "Speaking: '$text'")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LocalAgentTTS")
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        voiceCommandManager = VoiceCommandManager(context, onResult, onError)
        voiceCommandManager?.startListening()
    }

    fun stopListening() {
        voiceCommandManager?.destroyRecognizer()
        voiceCommandManager = null
    }

    fun shutdown() {
        stopListening()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isTtsReady = false
    }
}
