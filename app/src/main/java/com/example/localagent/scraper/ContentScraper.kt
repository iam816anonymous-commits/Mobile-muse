package com.example.localagent.scraper

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object ContentScraper {

    private const val TAG = "LocalAgentResult"
    const val STABILIZATION_TIMEOUT_MS = 1500L
    const val POLL_INTERVAL_MS = 400L

    fun monitorAndExtractResult(
        service: LocalAgentService,
        callback: (String) -> Unit
    ) {
        var lastExtractedText = ""
        var lastChangeTime = System.currentTimeMillis()

        service.waitForNodeOrTimeout(
            predicate = { node ->
                val currentText = collectLeafText(node)
                if (currentText != lastExtractedText && currentText.isNotBlank()) {
                    lastExtractedText = currentText
                    lastChangeTime = System.currentTimeMillis()
                    false
                } else {
                    currentText.isNotBlank() && (System.currentTimeMillis() - lastChangeTime >= STABILIZATION_TIMEOUT_MS)
                }
            },
            timeoutMs = 10000L
        ) { matchedNode ->
            val resultText = if (matchedNode != null) {
                collectLeafText(matchedNode)
            } else {
                lastExtractedText
            }

            Log.i(TAG, resultText)
            callback(resultText)
        }
    }

    fun collectLeafText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectLeafTextRecursive(node, sb)
        return sb.toString().trim()
    }

    private fun collectLeafTextRecursive(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return

        // Extract text solely from leaf nodes (childCount == 0) holding non-empty text
        if (node.childCount == 0) {
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val label = text ?: desc
            if (label != null && label.trim().isNotEmpty()) {
                sb.append(label.trim()).append("\n")
            }
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectLeafTextRecursive(child, sb)
            } finally {
                child.recycle()
            }
        }
    }
}
