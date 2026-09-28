package com.example.localagent.engine

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.voice.VoiceEngine

object UniversalNavigator {

    private const val TAG = "UniversalNavigator"

    fun navigateAndExecuteGoal(
        service: LocalAgentService,
        goalText: String,
        voiceEngine: VoiceEngine? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        val lowerGoal = goalText.lowercase()
        if (lowerGoal.contains("chrome")) {
            IntentLauncher.launchChrome(service)
        } else if (lowerGoal.contains("youtube")) {
            IntentLauncher.launchYouTube(service)
        } else if (lowerGoal.contains("camera")) {
            IntentLauncher.launchCamera(service)
        }

        AutonomousEngine.processCurrentScreen(service, goalText)
    }

    fun scanActionableNodes(rootNode: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (rootNode == null) return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        scanRecursive(rootNode, results)
        return results
    }

    private fun scanRecursive(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return

        if (node.isEditable || node.isClickable || node.isScrollable) {
            results.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanRecursive(child, results)
            } finally {
                child.recycle()
            }
        }
    }

    fun calculateBoundingCenter(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return Pair(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }
}
