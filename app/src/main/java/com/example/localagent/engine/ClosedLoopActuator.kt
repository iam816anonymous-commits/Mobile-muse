package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.gestures.GestureExecutor
import kotlinx.coroutines.delay

object ClosedLoopActuator {
    private const val TAG = "ClosedLoopActuator"

    suspend fun executeVerifiedClick(
        service: AccessibilityService,
        targetNode: AccessibilityNodeInfo?,
        fallbackX: Float? = null,
        fallbackY: Float? = null
    ): Boolean {
        if (targetNode == null && (fallbackX == null || fallbackY == null)) {
            Log.w(TAG, "Cannot execute click: No target node or fallback coordinates provided.")
            return false
        }

        val agentService = service as? LocalAgentService
        val gestureExec = agentService?.gestureExecutor ?: GestureExecutor(service)

        // Step 1: Capture pre-action screen hash
        val beforeHash = ScreenDeltaVerifier.computeWindowHash(service.rootInActiveWindow)

        // Step 2: Primary execution via MotorActuator or direct AccessibilityNodeInfo click
        if (targetNode != null) {
            if (agentService != null) {
                MotorActuator.click(agentService, targetNode)
            } else {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } else if (fallbackX != null && fallbackY != null) {
            gestureExec.tap(fallbackX, fallbackY)
        }

        // Step 3: Wait for layout transition & render
        delay(350)

        // Step 4: Capture post-action screen hash
        val afterHash = ScreenDeltaVerifier.computeWindowHash(service.rootInActiveWindow)

        // Step 5: Screen changed, action succeeded
        if (beforeHash != afterHash && afterHash != 0L) {
            Log.i(TAG, "Action verified with screen delta ($beforeHash -> $afterHash)")
            return true
        }

        // Step 6: Stall detected (beforeHash == afterHash). Execute Recovery Fallbacks
        Log.w(TAG, "Touch stall detected. Initiating recovery cascade...")

        // Recovery Layer A: Click parent wrapper if node exists
        var parent = targetNode?.parent
        var depth = 0
        while (parent != null && depth < 3) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                delay(300)
                val recoveryHash = ScreenDeltaVerifier.computeWindowHash(service.rootInActiveWindow)
                if (recoveryHash != beforeHash) {
                    Log.i(TAG, "Recovery Layer A succeeded via parent click.")
                    return true
                }
            }
            parent = parent.parent
            depth++
        }

        // Recovery Layer B: Forced Physical Coordinate Tap
        val bounds = Rect()
        targetNode?.getBoundsInScreen(bounds)
        val tapX = if (bounds.width() > 0) bounds.centerX().toFloat() else (fallbackX ?: 360f)
        val tapY = if (bounds.height() > 0) bounds.centerY().toFloat() else (fallbackY ?: 720f)

        Log.i(TAG, "Recovery Layer B: Dispatching physical tap at ($tapX, $tapY)")
        gestureExec.tap(tapX, tapY)
        delay(350)

        val finalHash = ScreenDeltaVerifier.computeWindowHash(service.rootInActiveWindow)
        val recovered = finalHash != beforeHash
        if (recovered) {
            Log.i(TAG, "Recovery Layer B succeeded.")
        } else {
            Log.e(TAG, "Closed-loop click failed across all layers.")
        }
        return recovered
    }
}
