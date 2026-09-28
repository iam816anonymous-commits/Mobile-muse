package com.example.localagent.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object UniversalTextInjector {

    private const val TAG = "UniversalTextInjector"

    fun executeTextInject(service: LocalAgentService, rootNode: AccessibilityNodeInfo?, textToType: String): Boolean {
        if (rootNode == null) return false

        val editNode = findFirstEditableNode(rootNode) ?: return false

        try {
            // Level 1: Standard ACTION_SET_TEXT
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val setSuccess = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Level 1 ACTION_SET_TEXT result: $setSuccess")

            try { Thread.sleep(200L) } catch (e: Exception) {}

            val currentText = editNode.text?.toString() ?: ""
            if (setSuccess && currentText.contains(textToType)) {
                service.broadcastTelemetryLog("INJECT", "Level 1: ACTION_SET_TEXT succeeded")
                return true
            }

            // Level 2: Clipboard Manager ACTION_PASTE
            Log.w(TAG, "Level 1 failed or text empty after 200ms. Triggering Level 2 Clipboard ACTION_PASTE...")
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("LocalAgent_Inject", textToType)
                clipboard.setPrimaryClip(clip)

                editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasteSuccess = editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.d(TAG, "Level 2 ACTION_PASTE result: $pasteSuccess")

                if (pasteSuccess) {
                    service.broadcastTelemetryLog("INJECT", "Level 2: Clipboard ACTION_PASTE succeeded")
                    return true
                }
            }

            // Level 3: 600ms Long-Press Gesture + System 'Paste' Menu Tap
            Log.w(TAG, "Level 2 failed. Triggering Level 3 Long-Press Gesture & Context Menu 'Paste'...")
            val bounds = Rect()
            editNode.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()

                // Dispatch long press
                service.gestureExecutor.swipe(centerX, centerY, centerX, centerY, 600L)
                try { Thread.sleep(400L) } catch (e: Exception) {}

                val activeRoot = service.getActiveWindowRoot()
                if (activeRoot != null) {
                    try {
                        val pasteMenuNode = findPasteNodeInContextMenu(activeRoot)
                        if (pasteMenuNode != null) {
                            try {
                                val clicked = pasteMenuNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                service.broadcastTelemetryLog("INJECT", "Level 3: Long-press system 'Paste' menu item clicked ($clicked)")
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
        } finally {
            editNode.recycle()
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        if (node.isEditable || className.contains("EditText") || hint.contains("search") || text.contains("search") || desc.contains("search")) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findFirstEditableNode(child)
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
