package com.example.localagent.gestures

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ActionExecutor {

    private const val TAG = "ActionExecutor"

    fun performClickWithFallback(node: AccessibilityNodeInfo, gestureExecutor: GestureExecutor): Boolean {
        // Attempt standard node performAction CLICK
        val actionSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (actionSuccess) {
            Log.d(TAG, "Standard performAction(ACTION_CLICK) succeeded")
            return true
        }

        Log.w(TAG, "performAction(ACTION_CLICK) returned false. Falling back to gesture tap on bounding center.")
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()

        return gestureExecutor.tap(centerX, centerY)
    }
}
