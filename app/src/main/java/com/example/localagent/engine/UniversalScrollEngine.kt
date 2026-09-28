package com.example.localagent.engine

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object UniversalScrollEngine {

    private const val TAG = "UniversalScrollEngine"

    fun scrollAndReveal(service: LocalAgentService, rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        val scrollableNode = findScrollableNode(rootNode)
        if (scrollableNode != null) {
            try {
                val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                if (scrolled) {
                    Log.i(TAG, "Programmatic ACTION_SCROLL_FORWARD succeeded")
                    service.broadcastTelemetryLog("SCROLL", "Programmatic ACTION_SCROLL_FORWARD succeeded")
                    try { Thread.sleep(400L) } catch (e: Exception) {}
                    return true
                }
            } finally {
                scrollableNode.recycle()
            }
        }

        // Coordinate Swipe Fallback
        val metrics = service.resources.displayMetrics
        val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
        val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.75f
        val endY = height * 0.25f

        val swiped = service.gestureExecutor.swipe(startX, startY, startX, endY, 350L)
        Log.i(TAG, "Physical Coordinate Swipe Fallback ($startX, $startY) to ($startX, $endY) -> $swiped")
        service.broadcastTelemetryLog("SCROLL", "Physical coordinate swipe fallback -> Dispatched: $swiped")
        try { Thread.sleep(400L) } catch (e: Exception) {}
        return swiped
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
}
