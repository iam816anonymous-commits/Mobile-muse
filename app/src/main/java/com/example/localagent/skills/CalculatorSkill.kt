package com.example.localagent.skills

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppIndexer
import com.example.localagent.engine.AppLauncher
import com.example.localagent.engine.GenericUIOperator
import com.example.localagent.voice.VoiceEngine

class CalculatorSkill(private val service: LocalAgentService) {

    companion object {
        private const val TAG = "CalculatorSkill"

        fun sanitizeExpression(input: String): String {
            val cleaned = input.lowercase()
                .replace("calculator", "")
                .replace("localagent", "")
                .replace("calculate", "")
                .replace("compute", "")
                .replace("sum", "")
                .trim()
            val matches = Regex("[0-9+\\-*/=.^%]+").findAll(cleaned).map { it.value }.joinToString("")
            return matches.ifEmpty { "1+1" }
        }
    }

    fun executeCalculation(expression: String, voiceSynthesizer: com.example.localagent.voice.VoiceSynthesizer? = null) {
        val sanitized = sanitizeExpression(expression)

        // Step A: Launch installed Calculator app
        val calcPkg = AppIndexer.resolveAppByQuery(service, "calc") ?: "com.android.calculator2"
        service.broadcastTelemetryLog("CALC", "Launching real calculator app ($calcPkg)...")
        AppLauncher.launchApp(service, calcPkg)

        val handler = Handler(Looper.getMainLooper())

        // Step B: Poll until rootInActiveWindow matches calculator package (max 3000ms)
        var elapsed = 0L
        val pollInterval = 200L
        handler.post(object : Runnable {
            override fun run() {
                val root = service.getActiveWindowRoot()
                val currentPkg = root?.packageName?.toString() ?: ""
                root?.recycle()

                if (currentPkg.lowercase().contains("calc") || elapsed >= 3000L) {
                    performCalculatorSequence(sanitized, voiceSynthesizer)
                } else {
                    elapsed += pollInterval
                    handler.postDelayed(this, pollInterval)
                }
            }
        })
    }

    private fun performCalculatorSequence(expression: String, voiceSynthesizer: com.example.localagent.voice.VoiceSynthesizer?) {
        val root = service.getActiveWindowRoot() ?: return
        try {
            // Step C: Check if expression contains scientific operators (^ or %)
            if (expression.contains("^") || expression.contains("%")) {
                val toggleScientificNode = findScientificToggleNode(root)
                if (toggleScientificNode != null) {
                    try {
                        service.performClickWithFallback(toggleScientificNode)
                        service.broadcastTelemetryLog("CALC", "Toggled scientific keyboard expansion")
                        try { Thread.sleep(300L) } catch (e: Exception) {}
                    } finally {
                        toggleScientificNode.recycle()
                    }
                }
            }

            val tokens = expression.toCharArray().map { it.toString() }
            GenericUIOperator.sequenceTap(root, tokens, service, 150L)
            GenericUIOperator.confirmAction(root, listOf("=", "equals"), service)

            // Step D: Extract finalized numeric display matching ^[0-9,.]+$
            Handler(Looper.getMainLooper()).postDelayed({
                val pollRoot = service.getActiveWindowRoot()
                if (pollRoot != null) {
                    try {
                        val leafNumbers = GenericUIOperator.harvestLeafText(pollRoot) { text -> text.matches(Regex("^[0-9,.]+$")) }
                        val resultText = leafNumbers.lastOrNull() ?: extractResultText(pollRoot, pollRoot.packageName?.toString() ?: "")
                        service.broadcastGoalCompleted(expression, "SUCCESS", resultText)
                        voiceSynthesizer?.speak("The calculated result is $resultText")
                    } finally {
                        pollRoot.recycle()
                    }
                }
            }, 500L)
        } finally {
            root.recycle()
        }
    }

    private fun findScientificToggleNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""

        if (text == "inv" || text == "adv" || desc.contains("scientific") || desc.contains("advanced") || id.contains("pad_advanced")) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findScientificToggleNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun extractResultText(node: AccessibilityNodeInfo?, activePkg: String): String {
        if (node == null) return ""
        val nodePkg = node.packageName?.toString() ?: ""
        if (activePkg.isNotEmpty() && nodePkg.isNotEmpty() && !nodePkg.equals(activePkg, ignoreCase = true)) {
            return ""
        }

        val text = node.text?.toString() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("result_final") || id.contains("result_preview") || id.contains("result") || id.contains("formula")) {
            if (text.isNotBlank() && text.matches(Regex("^[0-9,.]+$"))) return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = extractResultText(child, activePkg)
            child.recycle()
            if (res.isNotBlank()) return res
        }
        return if (node.childCount == 0 && text.matches(Regex("^[0-9,.]+$"))) text else ""
    }
}
