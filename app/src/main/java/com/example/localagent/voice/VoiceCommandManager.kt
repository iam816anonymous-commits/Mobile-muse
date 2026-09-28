package com.example.localagent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceCommandManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VoiceCommandManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    var isProcessingGoal: Boolean = false

    fun startListening() {
        if (isProcessingGoal) {
            Log.w(TAG, "Speech recognition ignored: Current task is processing")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable on device")
            return
        }

        destroyRecognizer()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error code: $error")
                    onError("Speech error code: $error")
                    isProcessingGoal = false
                    destroyRecognizer()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Transcribed speech onResults: '$text'")
                        isProcessingGoal = true
                        onResult(text)
                    } else {
                        onError("No speech recognized")
                        isProcessingGoal = false
                    }
                    destroyRecognizer()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onPartialResult?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech listener", e)
            onError(e.message ?: "Failed to start speech listener")
            isProcessingGoal = false
            destroyRecognizer()
        }
    }

    fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    fun resetProcessingFlag() {
        isProcessingGoal = false
    }
}
