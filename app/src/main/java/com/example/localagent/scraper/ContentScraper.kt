package com.example.localagent.scraper

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.NodeData

object ContentScraper {

    private const val TAG = "LocalAgentResult"
    const val STABILIZATION_TIMEOUT_MS = 1500L

    fun monitorAndExtractResult(
        service: LocalAgentService,
        callback: (String) -> Unit
    ) {
        var lastExtractedText = ""
        var lastChangeTime = System.currentTimeMillis()

        service.waitForNodeOrTimeout(
            predicate = { node ->
                val currentText = collectScreenText(node)
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
                collectScreenText(matchedNode)
            } else {
                lastExtractedText
            }

            Log.i(TAG, resultText)
            callback(resultText)
        }
    }

    fun collectScreenText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectTextRecursive(node, sb)
        return sb.toString().trim()
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (!text.isNull_or_blank()) {
            sb.append(text).append("\n")
        } else if (!desc.isNull_or_blank()) {
            sb.append(desc).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectTextRecursive(child, sb)
            } finally {
                child.recycle()
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
