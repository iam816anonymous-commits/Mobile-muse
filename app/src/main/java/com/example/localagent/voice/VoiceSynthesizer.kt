package com.example.localagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceSynthesizer(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceSynthesizer"
    }

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            Log.d(TAG, "TTS initialized: isInitialized=$isInitialized")
        } else {
            Log.e(TAG, "TTS initialization failed code: $status")
        }
    }

    fun speak(text: String) {
        if (isInitialized && tts != null && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LocalAgentTTS")
            Log.d(TAG, "Verbal feedback spoken: '$text'")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tts = null
        isInitialized = false
    }
}
