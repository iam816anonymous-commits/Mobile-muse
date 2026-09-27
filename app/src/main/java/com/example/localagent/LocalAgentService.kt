package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class LocalAgentService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events
    }

    override fun onInterrupt() {
        // Handle service interruption
    }
}
