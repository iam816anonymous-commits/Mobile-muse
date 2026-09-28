package com.example.localagent.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService

object PhotoViewerAutomation {

    private const val TAG = "PhotoViewerAutomation"

    fun launchPhotoViewer(service: LocalAgentService) {
        service.isProcessingGoal = true
        Log.i(TAG, "Launching Photo Viewer app...")
        service.broadcastTelemetryLog("PHOTO", "Launching Photo Viewer...")

        try {
            val photoPkg = AppIndexer.resolveAppByQuery(service, "my picture")
                ?: AppIndexer.resolveAppByQuery(service, "gallery")
                ?: "com.transsion.aigallery"
            AppLauncher.launchApp(service, photoPkg)
            service.voiceSynthesizer?.speak("Opening My Picture.")
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 2000L)
        }
    }

    fun openFirstPicture(service: LocalAgentService) {
        val root = service.getActiveWindowRoot()
        if (root != null) {
            try {
                val thumbnailClicked = GenericUIOperator.findAndClickByKeywords(
                    root,
                    listOf("photo", "picture", "image", "item", "grid", "thumbnail"),
                    service
                )
                if (!thumbnailClicked) {
                    val metrics = service.resources.displayMetrics
                    val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
                    val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

                    val tileX = width * 0.22f
                    val tileY = height * 0.25f
                    service.gestureExecutor.tap(tileX, tileY)
                    service.broadcastTelemetryLog("PHOTO", "First grid tile fallback coordinate tap ($tileX, $tileY) dispatched")
                }
            } finally {
                root.recycle()
            }
        }
    }

    fun viewNextPhoto(service: LocalAgentService) {
        MotorActuator.scroll(service, ScrollDirection.SWIPE_LEFT)
        service.broadcastTelemetryLog("PHOTO", "Swiped left to view next photo")
    }

    fun viewPreviousPhoto(service: LocalAgentService) {
        MotorActuator.scroll(service, ScrollDirection.SWIPE_RIGHT)
        service.broadcastTelemetryLog("PHOTO", "Swiped right to view previous photo")
    }

    fun deleteCurrentPhoto(service: LocalAgentService) {
        service.isProcessingGoal = true
        Log.i(TAG, "Executing delete current photo sequence...")

        try {
            val metrics = service.resources.displayMetrics
            val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
            val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

            // Step A: Tap screen center to reveal top/bottom media toolbars
            service.gestureExecutor.tap(width * 0.5f, height * 0.5f)
            try { Thread.sleep(300L) } catch (e: Exception) {}

            val root = service.getActiveWindowRoot()
            if (root != null) {
                try {
                    // Step B: Look for nodes matching delete/trash keywords
                    val deleteClicked = GenericUIOperator.findAndClickByKeywords(
                        root,
                        listOf("delete", "trash", "remove", "bin"),
                        service
                    )

                    // Step C: Fallback tap standard Tecno HiOS bottom-right trash icon (0.85f, 0.94f)
                    if (!deleteClicked) {
                        val trashX = width * 0.85f
                        val trashY = height * 0.94f
                        service.gestureExecutor.tap(trashX, trashY)
                        service.broadcastTelemetryLog("PHOTO", "Fallback Tecno trash icon coordinate tap ($trashX, $trashY) dispatched")
                    }

                    // Step D: Wait 350ms for confirmation dialog, then click confirmation node
                    Handler(Looper.getMainLooper()).postDelayed({
                        val confirmRoot = service.getActiveWindowRoot()
                        if (confirmRoot != null) {
                            try {
                                GenericUIOperator.findAndClickByKeywords(
                                    confirmRoot,
                                    listOf("delete", "ok", "confirm", "yes", "move to trash"),
                                    service
                                )
                                service.broadcastTelemetryLog("PHOTO", "Photo deletion confirmed")
                                service.voiceSynthesizer?.speak("Photo deleted.")
                            } finally {
                                confirmRoot.recycle()
                            }
                        } else {
                            service.voiceSynthesizer?.speak("Photo deleted.")
                        }
                    }, 350L)

                } finally {
                    root.recycle()
                }
            }
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 3000L)
        }
    }

    fun slideshowBrowse(service: LocalAgentService, count: Int = 5) {
        service.isProcessingGoal = true
        launchPhotoViewer(service)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            openFirstPicture(service)

            for (i in 1..count) {
                handler.postDelayed({
                    viewNextPhoto(service)
                    service.broadcastTelemetryLog("PHOTO", "Slideshow paging photo $i/$count")
                }, i * 2000L)
            }

            handler.postDelayed({
                service.isProcessingGoal = false
                service.broadcastTelemetryLog("PHOTO", "Slideshow browsing completed")
            }, (count + 1) * 2000L)

        }, 1500L)
    }

    fun processPhotoGoal(service: LocalAgentService, rawGoal: String) {
        val lowerGoal = rawGoal.lowercase().trim()

        if (lowerGoal.contains("show pictures one by one") || lowerGoal.contains("slideshow") || lowerGoal.contains("browse photos")) {
            slideshowBrowse(service, 5)
        } else if (lowerGoal.contains("delete this picture") || lowerGoal.contains("delete photo") || lowerGoal.contains("delete picture")) {
            deleteCurrentPhoto(service)
        } else if (lowerGoal.contains("next") || lowerGoal.contains("swipe left")) {
            viewNextPhoto(service)
        } else if (lowerGoal.contains("previous") || lowerGoal.contains("swipe right")) {
            viewPreviousPhoto(service)
        } else {
            launchPhotoViewer(service)
        }
    }
}
