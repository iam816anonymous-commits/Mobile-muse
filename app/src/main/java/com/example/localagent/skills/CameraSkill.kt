package com.example.localagent.skills

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.util.Log
import com.example.localagent.LocalAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraSkill(private val service: LocalAgentService) {

    companion object {
        private const val TAG = "CameraSkill"
    }

    fun capturePhoto(useFrontCamera: Boolean = false) {
        capturePhotoDirect(service)
    }

    fun capturePhotoDirect(service: AccessibilityService) {
        val agentService = service as LocalAgentService
        agentService.isProcessingGoal = true

        // 1. Launch Camera
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        agentService.startActivity(intent)

        // 2. Wait 1500ms for hardware lens stabilization inside a coroutine
        agentService.serviceScope.launch(Dispatchers.Default) {
            try {
                delay(1500)
                // 3. Fire shutter via hardware volume key (universal across all Android cameras)
                val audioManager = agentService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)

                // Fallback: Dispatch exact physical center-bottom shutter tap for 720x1440
                agentService.gestureExecutor.tap(360f, 1260f)

                delay(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error during capturePhotoDirect execution", e)
            } finally {
                // Always reset lock
                agentService.isProcessingGoal = false
            }
        }
    }
}
