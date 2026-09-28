package com.example.localagent.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService

object GalleryAutomation {

    private const val TAG = "GalleryAutomation"

    fun processGalleryAction(service: LocalAgentService, rawGoal: String) {
        service.isProcessingGoal = true
        val lowerGoal = rawGoal.lowercase()
        Log.i(TAG, "Processing Gallery Action: '$rawGoal'")

        try {
            val galleryPkg = AppIndexer.resolveAppByQuery(service, "gallery") ?: AppIndexer.resolveAppByQuery(service, "photo") ?: "com.android.gallery3d"
            AppLauncher.launchApp(service, galleryPkg)

            Handler(Looper.getMainLooper()).postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    try {
                        if (lowerGoal.contains("next") || lowerGoal.contains("swipe left")) {
                            MotorActuator.scroll(service, ScrollDirection.SWIPE_LEFT)
                            service.broadcastTelemetryLog("GALLERY", "Swiped left for next photo")
                        } else if (lowerGoal.contains("previous") || lowerGoal.contains("back") || lowerGoal.contains("swipe right")) {
                            MotorActuator.scroll(service, ScrollDirection.SWIPE_RIGHT)
                            service.broadcastTelemetryLog("GALLERY", "Swiped right for previous photo")
                        } else if (lowerGoal.contains("delete") || lowerGoal.contains("trash") || lowerGoal.contains("remove")) {
                            val deleteClicked = GenericUIOperator.findAndClickByKeywords(
                                root,
                                listOf("delete", "trash", "remove"),
                                service
                            )
                            if (deleteClicked) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val confirmRoot = service.getActiveWindowRoot()
                                    if (confirmRoot != null) {
                                        try {
                                            GenericUIOperator.findAndClickByKeywords(
                                                confirmRoot,
                                                listOf("ok", "delete", "confirm", "yes"),
                                                service
                                            )
                                            service.broadcastTelemetryLog("GALLERY", "Photo deletion confirmed")
                                            service.voiceSynthesizer?.speak("Photo deleted.")
                                        } finally {
                                            confirmRoot.recycle()
                                        }
                                    }
                                }, 500L)
                            }
                        }
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
