package com.example.localagent.skills

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.util.Log
import com.example.localagent.LocalAgentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CameraSkill {
    private const val TAG = "CameraSkill"

    fun capturePhoto(service: AccessibilityService, isFront: Boolean = false) {
        val agentService = service as? LocalAgentService
        agentService?.isProcessingGoal = true

        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                if (isFront) {
                    putExtra("android.intent.extras.CAMERA_FACING", 0) // LENS_FACING_FRONT
                    putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                    putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                } else {
                    putExtra("android.intent.extras.CAMERA_FACING", 1) // LENS_FACING_BACK
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Wait 1500ms for camera preview stabilization
                    delay(1500)

                    // If front camera requested, tap lens flip fallback anchor (Tecno 18:9 anchor: width * 0.82, height * 0.88 -> 590, 1260)
                    if (isFront) {
                        agentService?.gestureExecutor?.tap(590f, 1260f)
                        delay(600)
                    }

                    // Trigger hardware volume click via system service
                    val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_SYSTEM,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_PLAY_SOUND
                    )

                    // Coordinate center-bottom shutter tap fallback (720x1440 display: width * 0.50, height * 0.88 -> 360, 1260)
                    agentService?.gestureExecutor?.tap(360f, 1260f)
                    agentService?.broadcastTelemetryLog("CAMERA", "Camera shutter triggered (isFront=$isFront)")
                    agentService?.broadcastGoalCompleted("capture photo", "SUCCESS", "Photo captured (isFront=$isFront)")
                    delay(1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing camera shutter", e)
                } finally {
                    agentService?.isProcessingGoal = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            agentService?.isProcessingGoal = false
        }
    }
}
