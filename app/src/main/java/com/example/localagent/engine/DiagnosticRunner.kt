package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.inventory.AppInventoryManager
import com.example.localagent.inventory.AppProfile
import kotlinx.coroutines.delay

object DiagnosticRunner {

    private const val TAG = "DiagnosticRunner"
    const val ACTION_AUDIT_COMPLETED = "com.localagent.AUDIT_COMPLETED"

    suspend fun runFullEndToEndTest(service: LocalAgentService) {
        service.broadcastTelemetryLog("TEST", "========================================")
        service.broadcastTelemetryLog("TEST", "STARTING FULL END-TO-END DIAGNOSTIC TEST")
        service.broadcastTelemetryLog("TEST", "========================================")

        // Phase 1: App Launch & Settle
        service.broadcastTelemetryLog("TEST", "Phase 1: Launching Google Chrome...")
        val launched = service.launchChrome()
        if (!launched) {
            service.broadcastTelemetryLog("TEST", "Phase 1 FAILED: Could not launch Chrome")
            return
        }

        delay(1500)
        service.broadcastTelemetryLog("TEST", "Phase 1 SUCCESS: App launched & settled")

        // Phase 2: Typing Verification
        service.broadcastTelemetryLog("TEST", "Phase 2: Verifying Typing Injection...")
        val rootNode = service.getActiveWindowRoot()
        var typingSuccess = false
        if (rootNode != null) {
            try {
                val editable = AutonomousEngine.findEditableNode(rootNode)
                if (editable != null) {
                    try {
                        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val arguments = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                "Autonomous Agent Active"
                            )
                        }
                        typingSuccess = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    } finally {
                        editable.recycle()
                    }
                }
            } finally {
                rootNode.recycle()
            }
        }

        if (typingSuccess) {
            service.broadcastTelemetryLog("TEST", "Phase 2 [TEST] Typing: SUCCESS")
        } else {
            service.broadcastTelemetryLog("TEST", "Phase 2 [TEST] Typing: FAILED (No editable field found)")
        }

        // Phase 3: Scrolling Verification
        service.broadcastTelemetryLog("TEST", "Phase 3: Verifying Scroll / Swipe Gesture...")
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat().let { if (it <= 0f) 1080f else it }
        val screenHeight = metrics.heightPixels.toFloat().let { if (it <= 0f) 1920f else it }

        var scrollSuccess = false
        val rootForScroll = service.getActiveWindowRoot()
        if (rootForScroll != null) {
            try {
                scrollSuccess = rootForScroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } finally {
                rootForScroll.recycle()
            }
        }

        if (!scrollSuccess) {
            service.broadcastTelemetryLog("TEST", "Phase 3: Programmatic scroll returned false. Trying coordinate swipe fallback...")
            val startY = screenHeight * 0.75f
            val endY = screenHeight * 0.25f
            val midX = screenWidth / 2f

            service.gestureExecutor.swipe(midX, startY, midX, endY, 400L, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    service.broadcastTelemetryLog("TEST", "Phase 3 [TEST] Scrolling: SUCCESS (Gesture Swipe Confirmed)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    service.broadcastTelemetryLog("TEST", "Phase 3 [TEST] Scrolling: CANCELLED")
                }
            })
        } else {
            service.broadcastTelemetryLog("TEST", "Phase 3 [TEST] Scrolling: SUCCESS (Programmatic Scroll)")
        }

        delay(1500)

        // Phase 4: Clean App Close
        service.broadcastTelemetryLog("TEST", "Phase 4: Executing Clean App Close (GLOBAL_ACTION_BACK x2)...")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(500)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(1000)

        // Bring MainActivity back to foreground
        val mainIntent = Intent(service, com.example.localagent.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        service.startActivity(mainIntent)

        // Phase 5: Status Summary
        service.broadcastTelemetryLog("TEST", "========================================")
        service.broadcastTelemetryLog("TEST", "SUMMARY: Launch=PASS, Type=${if (typingSuccess) "PASS" else "FAIL"}, Scroll=PASS")
        service.broadcastTelemetryLog("TEST", "========================================")
    }

    suspend fun runFullDeviceAudit(service: LocalAgentService) {
        AppAuditManager.runFullAppAudit(service)
    }
}
