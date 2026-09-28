package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.NodeData

object ObstacleDetector {

    private const val TAG = "ObstacleDetector"

    val BLOCKER_PATTERNS = listOf(
        "allow", "while using the app", "grant",
        "accept", "agree", "got it", "dismiss", "i agree",
        "not now", "later", "close", "cancel"
    )

    fun isObstacleNode(text: String?, description: String?): Boolean {
        val label = (text ?: description ?: "").lowercase().trim()
        if (label.isEmpty()) return false
        return BLOCKER_PATTERNS.any { pattern -> label == pattern || label.contains(pattern) }
    }

    fun checkForAndDismissObstacle(service: LocalAgentService, rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        val obstacleNode = findObstacleNode(rootNode)
        if (obstacleNode != null) {
            try {
                val bounds = Rect()
                obstacleNode.getBoundsInScreen(bounds)
                Log.w(TAG, "Obstacle node detected: '${obstacleNode.text ?: obstacleNode.contentDescription}'. Tapping coordinate fallback.")
                service.broadcastTelemetryLog("OBSTACLE", "Dismissing popup blocker: '${obstacleNode.text ?: obstacleNode.contentDescription}'")

                service.gestureExecutor.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                return true
            } finally {
                obstacleNode.recycle()
            }
        }
        return false
    }

    private fun findObstacleNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (isObstacleNode(text, desc) && (node.isClickable || node.actionList.isNotEmpty())) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findObstacleNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
