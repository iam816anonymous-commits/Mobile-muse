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

        Handler(Looper.getMainLooper()).postDelayed({
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
                        }
                    }

                    val shutterNode = findShutterButtonNode(root)
                    if (shutterNode != null) {
                        try {
                            service.performClickWithFallback(shutterNode)
                            service.broadcastTelemetryLog("ACT", "Camera shutter triggered")
                        } finally {
                            shutterNode.recycle()
                        }
                    } else {
                        // Fallback coordinate tap at screen bottom center
                        val displayMetrics = service.resources.displayMetrics
                        val centerX = displayMetrics.widthPixels / 2f
                        val bottomY = displayMetrics.heightPixels * 0.88f
                        service.gestureExecutor.tap(centerX, bottomY)
                        service.broadcastTelemetryLog("ACT", "Camera shutter fallback coordinate tap dispatched")
                    }
                } finally {
                    root.recycle()
                }
            }
        }, 1000L)
    }

    private fun findToggleCameraNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("front") || desc.contains("switch camera") || desc.contains("flip") || desc.contains("rear")) {
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
        if (desc.contains("shutter") || desc.contains("take photo") || desc.contains("capture") || text.contains("shutter")) {
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
