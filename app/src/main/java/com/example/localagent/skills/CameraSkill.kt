package com.example.localagent.skills

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppLauncher

class CameraSkill(private val service: LocalAgentService) {

    companion object {
        private const val TAG = "CameraSkill"
    }

    fun capturePhoto(useFrontCamera: Boolean = false) {
        service.isProcessingGoal = true
        val handler = Handler(Looper.getMainLooper())

        // 4-Second Timeout Guard to prevent zombie states
        val timeoutRunnable = Runnable {
            Log.w(TAG, "Camera capture photo 4s timeout reached. Unconditionally resetting isProcessingGoal.")
            service.isProcessingGoal = false
        }
        handler.postDelayed(timeoutRunnable, 4000L)

        try {
            val cameraPkg = "com.android.camera"
            val launched = AppLauncher.launchApp(service, cameraPkg)
            if (!launched) {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
            }

            handler.postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    try {
                        if (useFrontCamera) {
                            val toggleNode = findToggleCameraNode(root)
                            if (toggleNode != null) {
                                try {
                                    service.performClickWithFallback(toggleNode)
                                } finally {
                                    toggleNode.recycle()
                                }
                            } else {
                                // Tecno Camon i Click 720x1440 Display Lens Flip Fallbacks
                                val metrics = service.resources.displayMetrics
                                val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
                                val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

                                val flipX = if (width == 720f) 590f else width * 0.82f
                                val flipY = if (height == 1440f) 1280f else height * 0.88f
                                service.gestureExecutor.tap(flipX, flipY)
                                service.broadcastTelemetryLog("ACT", "Camera switch coordinate tap ($flipX, $flipY) dispatched")
                            }
                        }

                        // Shutter Trigger (360, 1280 or 0.50f, 0.88f)
                        handler.postDelayed({
                            val captureRoot = service.getActiveWindowRoot()
                            if (captureRoot != null) {
                                try {
                                    val shutterNode = findShutterButtonNode(captureRoot)
                                    if (shutterNode != null) {
                                        try {
                                            service.performClickWithFallback(shutterNode)
                                            service.broadcastTelemetryLog("ACT", "Camera shutter node triggered")
                                        } finally {
                                            shutterNode.recycle()
                                        }
                                    } else {
                                        val metrics = service.resources.displayMetrics
                                        val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
                                        val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()
                                        val shutterX = if (width == 720f) 360f else width * 0.50f
                                        val shutterY = if (height == 1440f) 1280f else height * 0.88f
                                        service.gestureExecutor.tap(shutterX, shutterY)
                                        service.broadcastTelemetryLog("ACT", "Tecno camera shutter coordinate tap ($shutterX, $shutterY) dispatched")

                                        handler.postDelayed({
                                            service.sendBroadcast(Intent(Intent.ACTION_CAMERA_BUTTON))
                                        }, 150L)
                                    }
                                } finally {
                                    captureRoot.recycle()
                                }
                            }
                        }, 800L)

                    } finally {
                        root.recycle()
                    }
                }
            }, 800L)

        } finally {
            handler.postDelayed({
                handler.removeCallbacks(timeoutRunnable)
                service.isProcessingGoal = false
            }, 3500L)
        }
    }

    private fun findToggleCameraNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (desc.contains("switch") || desc.contains("flip") || desc.contains("front") || desc.contains("rear") || desc.contains("facing") || desc.contains("camera toggle") ||
            id.contains("switch") || id.contains("flip") || id.contains("front")
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findToggleCameraNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findShutterButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (desc.contains("shutter") || desc.contains("take photo") || desc.contains("capture") || text.contains("shutter") || id.contains("shutter")) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findShutterButtonNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
