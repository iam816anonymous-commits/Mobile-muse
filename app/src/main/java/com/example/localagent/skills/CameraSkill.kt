package com.example.localagent.skills

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
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

        // 1. Launch Camera app without crawling nodes
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        agentService.startActivity(intent)

        // 2. Hardware Shutter Shortcut: Wait 1500ms for lens stabilization, then trigger volume key shutter shortcut
        agentService.serviceScope.launch(Dispatchers.Default) {
            try {
                delay(1500)
                val audioManager = agentService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)

                // Dispatch camera shutter key event fallback
                agentService.sendBroadcast(Intent(Intent.ACTION_CAMERA_BUTTON))

                // Physical gesture tap fallback at bottom center shutter position (360, 1260)
                agentService.gestureExecutor.tap(360f, 1260f)

                agentService.broadcastTelemetryLog("CAMERA", "Hardware shutter shortcut triggered")
                agentService.broadcastGoalCompleted("capture photo", "SUCCESS", "Photo captured via hardware shutter shortcut")
                delay(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error during hardware shutter photo capture", e)
                agentService.broadcastTelemetryLog("CAMERA", "Hardware shutter error: ${e.message}")
            } finally {
                agentService.isProcessingGoal = false
            }
        }
    }
}
