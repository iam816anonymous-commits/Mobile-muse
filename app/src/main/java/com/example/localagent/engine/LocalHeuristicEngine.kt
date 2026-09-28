package com.example.localagent.engine

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType

object LocalHeuristicEngine {

    private const val TAG = "LocalHeuristicEngine"

    fun processLocalHeuristics(service: LocalAgentService, goalText: String, rootNode: AccessibilityNodeInfo): Boolean {
        val lowerGoal = goalText.lowercase().trim()

        // 1. Math Expression Heuristics
        if (lowerGoal.contains(Regex("(?i)(calculate|compute|\\d+\\s*[*+\\-/x]\\s*\\d+)"))) {
            return processMathHeuristics(service, goalText, rootNode)
        }

        // 2. Search Goals Heuristics
        if (lowerGoal.startsWith("search ") || lowerGoal.contains("search for ")) {
            val query = lowerGoal.removePrefix("search for ").removePrefix("search ").trim()
            return processSearchHeuristics(service, query, rootNode)
        }

        // 3. General Keyword Heuristics
        return processKeywordHeuristics(service, lowerGoal, rootNode)
    }

    private fun processMathHeuristics(service: LocalAgentService, goalText: String, rootNode: AccessibilityNodeInfo): Boolean {
        val sanitized = goalText.replace(Regex("(?i)(calculate|compute|localagent)"), "").trim()
        val tokens = sanitized.toCharArray().map { it.toString() }

        var matchedAny = false
        for (token in tokens) {
            val tokenNode = findNodeMatchingToken(rootNode, token)
            if (tokenNode != null) {
                try {
                    service.performClickWithFallback(tokenNode)
                    matchedAny = true
                    service.broadcastTelemetryLog("HEURISTIC", "Math token clicked: '$token'")
                } finally {
                    tokenNode.recycle()
                }
            }
        }

        val equalsNode = findNodeMatchingToken(rootNode, "=") ?: findNodeMatchingToken(rootNode, "equals")
        if (equalsNode != null) {
            try {
                service.performClickWithFallback(equalsNode)
                matchedAny = true
                service.broadcastTelemetryLog("HEURISTIC", "Clicked math equals button")
            } finally {
                equalsNode.recycle()
            }
        }

        return matchedAny
    }

    private fun processSearchHeuristics(service: LocalAgentService, query: String, rootNode: AccessibilityNodeInfo): Boolean {
        val editableNode = AutonomousEngine.findEditableNode(rootNode)
        if (editableNode != null) {
            try {
                editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
                }
                val setSuccess = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                service.broadcastTelemetryLog("HEURISTIC", "Search query setText: '$query' -> Success: $setSuccess")

                val sendNode = AutonomousEngine.findSendOrSubmitButton(rootNode)
                if (sendNode != null) {
                    try {
                        service.performClickWithFallback(sendNode)
                        service.broadcastTelemetryLog("HEURISTIC", "Clicked search submit button")
                    } finally {
                        sendNode.recycle()
                    }
                }
                return true
            } finally {
                editableNode.recycle()
            }
        }
        return false
    }

    private fun processKeywordHeuristics(service: LocalAgentService, goalText: String, rootNode: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("new note", "record", "add", "plus", "create", "start", "settings", "search")
        for (keyword in keywords) {
            if (goalText.contains(keyword)) {
                val matchNode = findNodeByLabel(rootNode, keyword)
                if (matchNode != null) {
                    try {
                        service.performClickWithFallback(matchNode)
                        service.broadcastTelemetryLog("HEURISTIC", "Keyword match clicked: '$keyword'")
                        return true
                    } finally {
                        matchNode.recycle()
                    }
                }
            }
        }
        return false
    }

    private fun findNodeMatchingToken(node: AccessibilityNodeInfo?, token: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""

        if ((text == token || desc.contains(token, ignoreCase = true)) && (node.isClickable || node.childCount == 0)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeMatchingToken(child, token)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findNodeByLabel(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if ((text.contains(label) || desc.contains(label)) && (node.isClickable || node.childCount == 0)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeByLabel(child, label)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
