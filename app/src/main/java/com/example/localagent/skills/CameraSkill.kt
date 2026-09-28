package com.example.localagent.skills

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

class CameraSkill(private val service: LocalAgentService) {

    companion object {
        private const val TAG = "CameraSkill"
    }

    fun capturePhoto(useFrontCamera: Boolean = false) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)

        val handler = Handler(Looper.getMainLooper())
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

                            // Position 1: Tecno Camera UI lens flip bottom right (590, 1280)
                            val flipX1 = if (width == 720f) 590f else width * 0.82f
                            val flipY1 = if (height == 1440f) 1280f else height * 0.88f
                            service.gestureExecutor.tap(flipX1, flipY1)

                            // Position 2: Tecno Camera UI top right flip (610, 80)
                            handler.postDelayed({
                                val flipX2 = if (width == 720f) 610f else width * 0.85f
                                val flipY2 = if (height == 1440f) 80f else height * 0.08f
                                service.gestureExecutor.tap(flipX2, flipY2)
                            }, 200L)

                            service.broadcastTelemetryLog("ACT", "Camera switch Tecno 720x1440 coordinate fallbacks dispatched")
                        }
                    }

                    // 1000ms viewfinder reload delay
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
                                    // Attempt 2: Tecno Camon i Click shutter coordinate tap (360, 1280)
                                    val metrics = service.resources.displayMetrics
                                    val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
                                    val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()
                                    val shutterX = if (width == 720f) 360f else width * 0.50f
                                    val shutterY = if (height == 1440f) 1280f else height * 0.88f
                                    service.gestureExecutor.tap(shutterX, shutterY)
                                    service.broadcastTelemetryLog("ACT", "Tecno camera shutter coordinate tap ($shutterX, $shutterY) dispatched")

                                    // Attempt 3: Hardware Key Event fallback
                                    handler.postDelayed({
                                        service.sendBroadcast(Intent(Intent.ACTION_CAMERA_BUTTON))
                                        service.broadcastTelemetryLog("ACT", "Dispatched KEYCODE_CAMERA broadcast fallback")
                                    }, 200L)
                                }
                            } finally {
                                captureRoot.recycle()
                            }
                        }
                    }, 1000L)

                } finally {
                    root.recycle()
                }
            }
        }, 1000L)
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
