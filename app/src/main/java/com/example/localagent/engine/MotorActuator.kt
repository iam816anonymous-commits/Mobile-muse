package com.example.localagent.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

enum class ScrollDirection {
    SCROLL_DOWN,
    SCROLL_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT
}

object MotorActuator {

    private const val TAG = "MotorActuator"

    fun click(service: LocalAgentService, node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Level 1: Direct Node Click
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "Level 1: Direct ACTION_CLICK succeeded")
                service.broadcastTelemetryLog("MOTOR", "Level 1: Direct ACTION_CLICK succeeded")
                return true
            }
        }

        // Level 2: Parent Climbing (up to 4 levels)
        var parent: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable) {
                val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (parentClicked) {
                    Log.d(TAG, "Level 2: Parent climbing click succeeded at depth ${depth + 1}")
                    service.broadcastTelemetryLog("MOTOR", "Level 2: Parent climbing click succeeded at depth ${depth + 1}")
                    parent.recycle()
                    return true
                }
            }
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
            depth++
        }

        // Level 3: Screen Bounds Physical Touch Tap
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()
            val dispatched = service.gestureExecutor.tap(centerX, centerY)
            Log.d(TAG, "Level 3: Physical touch tap ($centerX, $centerY) -> $dispatched")
            service.broadcastTelemetryLog("MOTOR", "Level 3: Physical bounds touch ($centerX, $centerY) -> Dispatched: $dispatched")
            return dispatched
        }

        return false
    }

    fun type(service: LocalAgentService, node: AccessibilityNodeInfo?, textToType: String): Boolean {
        if (node == null) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val targetX = if (rect.width() > 0) rect.centerX().toFloat() else 360f
        val targetY = if (rect.height() > 0) rect.centerY().toFloat() else 720f

        com.example.localagent.hud.PointerIndicatorManager.showKeystroke(textToType, targetX, targetY)

        try {
            // Level 1: Standard ACTION_SET_TEXT
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val setSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Level 1 ACTION_SET_TEXT result: $setSuccess")

            try { Thread.sleep(150L) } catch (e: Exception) {}

            val currentText = node.text?.toString() ?: ""
            if (setSuccess && currentText.contains(textToType)) {
                service.broadcastTelemetryLog("MOTOR", "Level 1: ACTION_SET_TEXT succeeded")
                return true
            }

            // Level 2: Clipboard Manager ACTION_PASTE
            Log.w(TAG, "Level 1 failed or text empty after 150ms. Triggering Level 2 Clipboard ACTION_PASTE...")
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("LocalAgent_Motor_Inject", textToType)
                clipboard.setPrimaryClip(clip)

                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteSuccess = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.d(TAG, "Level 2 ACTION_PASTE result: $pasteSuccess")

                if (pasteSuccess) {
                    service.broadcastTelemetryLog("MOTOR", "Level 2: Clipboard ACTION_PASTE succeeded")
                    return true
                }
            }

            // Level 3: Long-Press Gesture + System 'Paste' Menu Tap
            Log.w(TAG, "Level 2 failed. Triggering Level 3 Long-Press Gesture & Context Menu 'Paste'...")
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()

                longPress(service, centerX, centerY, 600L)
                try { Thread.sleep(300L) } catch (e: Exception) {}

                val activeRoot = service.getActiveWindowRoot()
                if (activeRoot != null) {
                    try {
                        val pasteMenuNode = findPasteNodeInContextMenu(activeRoot)
                        if (pasteMenuNode != null) {
                            try {
                                val clicked = pasteMenuNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                service.broadcastTelemetryLog("MOTOR", "Level 3: Long-press system 'Paste' menu item clicked ($clicked)")
                                return clicked
                            } finally {
                                pasteMenuNode.recycle()
                            }
                        }
                    } finally {
                        activeRoot.recycle()
                    }
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Motor type failed", e)
            return false
        }
    }

    fun scroll(service: LocalAgentService, direction: ScrollDirection): Boolean {
        val rootNode = service.getActiveWindowRoot()
        if (rootNode != null) {
            try {
                val scrollableNode = findScrollableNode(rootNode)
                if (scrollableNode != null) {
                    try {
                        val action = if (direction == ScrollDirection.SCROLL_UP) {
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        } else {
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        }
                        val scrolled = scrollableNode.performAction(action)
                        if (scrolled) {
                            Log.d(TAG, "Programmatic scroll succeeded for $direction")
                            service.broadcastTelemetryLog("MOTOR", "Programmatic scroll succeeded for $direction")
                            try { Thread.sleep(350L) } catch (e: Exception) {}
                            return true
                        }
                    } finally {
                        scrollableNode.recycle()
                    }
                }
            } finally {
                rootNode.recycle()
            }
        }

        // Coordinate Stroke Fallback
        val metrics = service.resources.displayMetrics
        val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
        val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (direction) {
            ScrollDirection.SCROLL_DOWN -> arrayOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
            ScrollDirection.SCROLL_UP -> arrayOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            ScrollDirection.SWIPE_LEFT -> arrayOf(width * 0.85f, height * 0.50f, width * 0.15f, height * 0.50f)
            ScrollDirection.SWIPE_RIGHT -> arrayOf(width * 0.15f, height * 0.50f, width * 0.85f, height * 0.50f)
        }

        val dispatched = service.gestureExecutor.swipe(startX, startY, endX, endY, 350L)
        Log.d(TAG, "Physical gesture stroke $direction ($startX, $startY) -> ($endX, $endY) -> $dispatched")
        service.broadcastTelemetryLog("MOTOR", "Physical gesture stroke $direction -> Dispatched: $dispatched")
        try { Thread.sleep(350L) } catch (e: Exception) {}
        return dispatched
    }

    fun longPress(service: LocalAgentService, x: Float, y: Float, durationMs: Long = 650L): Boolean {
        return service.gestureExecutor.swipe(x, y, x, y, durationMs)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findScrollableNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findPasteNodeInContextMenu(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text == "paste" || desc == "paste" || text.contains("paste") || desc.contains("paste")) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findPasteNodeInContextMenu(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
