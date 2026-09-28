package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

object GenericUIOperator {

    private const val TAG = "GenericUIOperator"

    fun findAndClickByKeywords(root: AccessibilityNodeInfo?, keywords: List<String>, service: LocalAgentService? = null): Boolean {
        if (root == null) return false
        for (keyword in keywords) {
            val node = findMatchingNodeByLabel(root, keyword.lowercase().trim())
            if (node != null) {
                try {
                    val clickableNode = findClickableAncestor(node) ?: node
                    val clicked = if (service != null) {
                        service.performClickWithFallback(clickableNode)
                    } else {
                        clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    if (clicked) {
                        Log.i(TAG, "Successfully clicked node matching keyword '$keyword'")
                        return true
                    }
                } finally {
                    node.recycle()
                }
            }
        }
        return false
    }

    fun findInputAndType(root: AccessibilityNodeInfo?, textToType: String): Boolean {
        if (root == null) return false
        val editNode = findFirstEditableNode(root) ?: return false
        try {
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val setSuccess = editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.i(TAG, "findInputAndType set text '$textToType' -> $setSuccess")
            return setSuccess
        } finally {
            editNode.recycle()
        }
    }

    fun sequenceTap(root: AccessibilityNodeInfo?, tokens: List<String>, service: LocalAgentService? = null, delayMs: Long = 150L): Boolean {
        if (root == null) return false
        var tappedAny = false
        for (token in tokens) {
            val node = findMatchingNodeByLabel(root, token.trim())
            if (node != null) {
                try {
                    val clickableNode = findClickableAncestor(node) ?: node
                    if (service != null) {
                        service.performClickWithFallback(clickableNode)
                    } else {
                        clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    tappedAny = true
                    try { Thread.sleep(delayMs) } catch (e: Exception) {}
                } finally {
                    node.recycle()
                }
            }
        }
        return tappedAny
    }

    fun confirmAction(root: AccessibilityNodeInfo?, actionHints: List<String>, service: LocalAgentService? = null): Boolean {
        if (root == null) return false
        return findAndClickByKeywords(root, actionHints, service)
    }

    fun harvestLeafText(root: AccessibilityNodeInfo?, filter: (String) -> Boolean): List<String> {
        val result = mutableListOf<String>()
        if (root == null) return result
        collectLeafNodes(root, result, filter)
        return result
    }

    private fun collectLeafNodes(node: AccessibilityNodeInfo, list: MutableList<String>, filter: (String) -> Boolean) {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val valStr = if (text.isNotEmpty()) text else desc

        if (node.childCount == 0 && valStr.isNotEmpty() && filter(valStr)) {
            list.add(valStr)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectLeafNodes(child, list, filter)
            } finally {
                child.recycle()
            }
        }
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

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        if (node.isEditable || className.contains("EditText") || hint.contains("search") || text.contains("search") || desc.contains("search")) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findFirstEditableNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
