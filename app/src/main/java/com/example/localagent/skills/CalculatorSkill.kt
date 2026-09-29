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
import com.example.localagent.engine.MotorActuator
import com.example.localagent.voice.VoiceSynthesizer

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

    fun executeCalculation(expression: String, voiceSynthesizer: VoiceSynthesizer? = null) {
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

    private fun performCalculatorSequence(expression: String, voiceSynthesizer: VoiceSynthesizer?) {
        val tokens = expression.toCharArray().map { it.toString() }

        // Process keys with explicit 120ms delay and 5-iteration limit per key
        for (token in tokens) {
            var found = false
            for (attempt in 1..5) {
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    val node = findMatchingNodeByLabel(root, token.trim())
                    if (node != null) {
                        try {
                            val clickableNode = findClickableAncestor(node) ?: node
                            val clicked = MotorActuator.click(service, clickableNode)
                            if (clickableNode != node) clickableNode.recycle()
                            if (clicked) {
                                found = true
                                root.recycle()
                                node.recycle()
                                break
                            }
                        } finally {
                            if (!node.isRecycled) node.recycle()
                        }
                    }
                    root.recycle()
                }
                try { Thread.sleep(50L) } catch (e: Exception) {}
            }

            if (!found) {
                val msg = "Failed to locate calculator key '$token' after 5 attempts."
                service.broadcastTelemetryLog("CALC", msg)
                voiceSynthesizer?.speak("Calculator key $token not found.")
                break
            }

            // Explicit 120ms delay between consecutive key presses for layout buffer registration
            try { Thread.sleep(120L) } catch (e: Exception) {}
        }

        // Confirm equals
        var equalsConfirmed = false
        for (attempt in 1..5) {
            val root = service.getActiveWindowRoot()
            if (root != null) {
                try {
                    if (GenericUIOperator.confirmAction(root, listOf("=", "equals"), service)) {
                        equalsConfirmed = true
                        root.recycle()
                        break
                    }
                } finally {
                    if (!root.isRecycled) root.recycle()
                }
            }
            try { Thread.sleep(50L) } catch (e: Exception) {}
        }

        // Extract finalized numeric display matching ^[0-9,.]+$
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
    }

    private fun findMatchingNodeByLabel(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text == label || desc == label || text.contains(label) || desc.contains(label)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findMatchingNodeByLabel(child, label)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            val parent = current.parent ?: break
            if (current != node) {
                current.recycle()
            }
            current = parent
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
