package com.example.localagent.engine

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object UniversalTapEngine {

    private const val TAG = "UniversalTapEngine"

    fun execute3LevelTap(service: LocalAgentService, node: AccessibilityNodeInfo): Boolean {
        // Level 1: Direct Node Click
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.i(TAG, "Level 1 Direct Node Click succeeded")
                service.broadcastTelemetryLog("TAP", "Level 1: Direct ACTION_CLICK succeeded")
                return true
            }
        }

        // Level 2: Parent Climbing (up to 4 levels)
        var currentParent: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (currentParent != null && depth < 4) {
            if (currentParent.isClickable) {
                val parentClicked = currentParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (parentClicked) {
                    Log.i(TAG, "Level 2 Parent Climbing Click succeeded at depth ${depth + 1}")
                    service.broadcastTelemetryLog("TAP", "Level 2: Parent climbing click succeeded at depth ${depth + 1}")
                    currentParent.recycle()
                    return true
                }
            }
            val nextParent = currentParent.parent
            currentParent.recycle()
            currentParent = nextParent
            depth++
        }

        // Level 3: Physical Coordinate Tap via Rect bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()
            val dispatched = service.gestureExecutor.tap(centerX, centerY)
            Log.i(TAG, "Level 3 Physical Touch Bounds Tap ($centerX, $centerY) -> $dispatched")
            service.broadcastTelemetryLog("TAP", "Level 3: Physical touch tap at ($centerX, $centerY) -> Dispatched: $dispatched")
            return dispatched
        }

        return false
    }
}
