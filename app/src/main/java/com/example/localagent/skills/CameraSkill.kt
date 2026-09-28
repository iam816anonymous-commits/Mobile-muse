package com.example.localagent.skills

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
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
                            // OEM Coordinate Fallback Strategy B
                            val metrics = service.resources.displayMetrics
                            val topRightX = metrics.widthPixels * 0.85f
                            val topRightY = metrics.heightPixels * 0.08f
                            service.gestureExecutor.tap(topRightX, topRightY)
                            service.broadcastTelemetryLog("ACT", "Camera switch OEM coordinate fallback tapped")
                        }
                    }

                    // Viewfinder reload delay
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
                                    // Attempt 2: Fixed bottom center coordinate tap
                                    val metrics = service.resources.displayMetrics
                                    val centerX = metrics.widthPixels * 0.5f
                                    val bottomY = metrics.heightPixels * 0.88f
                                    service.gestureExecutor.tap(centerX, bottomY)
                                    service.broadcastTelemetryLog("ACT", "Camera shutter bottom-center coordinate tap dispatched")

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
                    }, 800L)

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
        if (desc.contains("switch") || desc.contains("flip") || desc.contains("front") || desc.contains("rear") || desc.contains("facing") ||
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
