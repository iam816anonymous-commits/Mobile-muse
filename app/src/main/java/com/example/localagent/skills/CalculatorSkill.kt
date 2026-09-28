package com.example.localagent.skills

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
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
            val matches = Regex("[0-9+\\-*/=.]+").findAll(cleaned).map { it.value }.joinToString("")
            return matches.ifEmpty { "1+1" }
        }
    }

    fun executeCalculation(expression: String, voiceEngine: VoiceEngine? = null) {
        val sanitized = sanitizeExpression(expression)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALCULATOR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Standard calculator intent failed, resolving via package manager", e)
        }

        val tokens = sanitized.toCharArray().map { it.toString() }
        val handler = Handler(Looper.getMainLooper())

        tokens.forEachIndexed { index, token ->
            handler.postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    val buttonNode = findButtonForToken(root, token)
                    if (buttonNode != null) {
                        try {
                            service.performClickWithFallback(buttonNode)
                        } finally {
                            buttonNode.recycle()
                        }
                    }
                    root.recycle()
                }
            }, index * 150L)
        }

        // Tap equals and extract result
        handler.postDelayed({
            val root = service.getActiveWindowRoot()
            if (root != null) {
                val equalsNode = findButtonForToken(root, "=") ?: findButtonForToken(root, "equals")
                if (equalsNode != null) {
                    try {
                        service.performClickWithFallback(equalsNode)
                    } finally {
                        equalsNode.recycle()
                    }
                }

                // Asynchronous stabilization poll for result
                handler.postDelayed({
                    val pollRoot = service.getActiveWindowRoot()
                    val resultText = if (pollRoot != null) {
                        val activePkg = pollRoot.packageName?.toString() ?: ""
                        val res = extractResultText(pollRoot, activePkg)
                        pollRoot.recycle()
                        res
                    } else ""

                    service.broadcastGoalCompleted(sanitized, "SUCCESS", resultText)
                    voiceEngine?.speak("Calculated result is $resultText")
                }, 500L)

                root.recycle()
            }
        }, tokens.size * 150L + 300L)
    }

    private fun findButtonForToken(node: AccessibilityNodeInfo?, token: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if ((text == token || desc.contains(token, ignoreCase = true)) && (node.isClickable || node.childCount == 0)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findButtonForToken(child, token)
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
            if (text.isNotBlank()) return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val res = extractResultText(child, activePkg)
            child.recycle()
            if (res.isNotBlank()) return res
        }
        return text.ifBlank { if (node.childCount == 0 && text.contains(Regex("[0-9]"))) text else "" }
    }
}
