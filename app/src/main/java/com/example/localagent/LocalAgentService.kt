package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class NodeData(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val boundsInScreen: Rect
)

class LocalAgentService : AccessibilityService() {

    companion object {
        private const val TAG = "LocalAgentService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "LocalAgentService connected")
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return
        try {
            val extractedNodes = mutableListOf<NodeData>()
            traverseAndExtractNode(rootNode, extractedNodes)
            Log.d(TAG, "Extracted ${extractedNodes.size} nodes from active window")
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "LocalAgentService interrupted")
    }

    fun traverseAndExtractNode(node: AccessibilityNodeInfo?, result: MutableList<NodeData>) {
        if (node == null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        result.add(
            NodeData(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                boundsInScreen = bounds
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseAndExtractNode(child, result)
            } finally {
                child.recycle()
            }
        }
    }
}
