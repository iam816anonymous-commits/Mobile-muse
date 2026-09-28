package com.example.localagent.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService

object RecorderAutomation {

    private const val TAG = "RecorderAutomation"

    fun recordAudio(service: LocalAgentService) {
        service.isProcessingGoal = true
        Log.i(TAG, "Executing Sound Recorder automation...")
        service.broadcastTelemetryLog("RECORDER", "Launching Sound Recorder...")

        try {
            val recorderPkg = AppIndexer.resolveAppByQuery(service, "record") ?: "com.transsion.soundrecorder"
            val launched = AppLauncher.launchApp(service, recorderPkg)

            Handler(Looper.getMainLooper()).postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    try {
                        val recordClicked = GenericUIOperator.findAndClickByKeywords(
                            root,
                            listOf("record", "start", "mic", "rec"),
                            service
                        )

                        if (!recordClicked) {
                            // Center bottom record trigger coordinate tap fallback (0.50f, 0.82f)
                            val metrics = service.resources.displayMetrics
                            val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
                            val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

                            val tapX = width * 0.50f
                            val tapY = height * 0.82f
                            service.gestureExecutor.tap(tapX, tapY)
                            service.broadcastTelemetryLog("RECORDER", "Center bottom record trigger coordinate tap ($tapX, $tapY) dispatched")
                        }

                        service.voiceSynthesizer?.speak("Voice recording started.")
                    } finally {
                        root.recycle()
                    }
                }
            }, 1000L)

        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 3000L)
        }
    }
}
