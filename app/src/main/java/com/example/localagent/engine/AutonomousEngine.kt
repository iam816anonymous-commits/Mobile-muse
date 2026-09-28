package com.example.localagent.engine

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.NodeData
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import com.example.localagent.memory.ScreenHasher
import com.example.localagent.serializer.ScreenSerializer
import org.json.JSONObject

object AutonomousEngine {

    private const val TAG = "AutonomousEngine"

    fun processCurrentScreen(service: LocalAgentService, goalText: String) {
        val rootNode = service.rootInActiveWindow ?: return
        try {
            val extractedNodes = mutableListOf<NodeData>()
            service.traverseAndExtractNode(rootNode, extractedNodes)

            val packageName = rootNode.packageName?.toString()
            val screenFingerprint = ScreenHasher.computeFingerprint(packageName, extractedNodes)

            // 1. Check local rule graph
            val cachedRule = service.ruleLedger.getActionRule(screenFingerprint, goalText)
            if (cachedRule != null) {
                Log.d(TAG, "Offline rule graph hit for $screenFingerprint. Executing cached action: ${cachedRule.type}")
                executeActionRule(service, cachedRule, rootNode)
                service.memoryLedger.recordStep(
                    stepIndex = service.stateManager.getCurrentState().currentStepIndex,
                    action = "OFFLINE_RULE_ACTION: ${cachedRule.type}",
                    success = true
                )
                return
            }

            // 2. Query AI Bridge if no local rule exists
            val serializedScreen = ScreenSerializer.serializeScreen(extractedNodes)
            service.aiBridgeClient.sendPayloadAsync(serializedScreen, goalText) { result ->
                result.onSuccess { aiJsonResponse ->
                    Log.d(TAG, "AI Response: $aiJsonResponse")
                    val parsedRule = parseAiActionResponse(aiJsonResponse)
                    if (parsedRule != null) {
                        service.ruleLedger.addTransition(screenFingerprint, goalText, parsedRule)
                        executeActionRule(service, parsedRule, service.rootInActiveWindow)
                        service.memoryLedger.recordStep(
                            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
                            action = "AI_RULE_ACTION: ${parsedRule.type}",
                            success = true
                        )
                        if (parsedRule.type == ActionType.TERMINATE) {
                            service.stateManager.completeTask()
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "AI Bridge query failed", error)
                    service.memoryLedger.recordStep(
                        stepIndex = service.stateManager.getCurrentState().currentStepIndex,
                        action = "AI_QUERY_FAILED",
                        success = false,
                        failureCode = error.message
                    )
                }
            }

        } finally {
            rootNode.recycle()
        }
    }

    fun parseAiActionResponse(jsonStr: String): ActionRule? {
        return try {
            val obj = JSONObject(jsonStr)
            val actionName = obj.optString("action", "CLICK").uppercase()
            val targetText = if (obj.has("target_text") && !obj.isNull("target_text")) obj.getString("target_text") else null
            val inputPayload = if (obj.has("input_payload") && !obj.isNull("input_payload")) obj.getString("input_payload") else null

            val actionType = when (actionName) {
                "INPUT_TEXT" -> ActionType.INPUT
                "SWIPE" -> ActionType.SWIPE
                "TERMINATE" -> ActionType.TERMINATE
                else -> ActionType.CLICK
            }

            ActionRule(
                type = actionType,
                textPayload = inputPayload ?: targetText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI action response JSON", e)
            null
        }
    }

    fun executeActionRule(service: LocalAgentService, rule: ActionRule, rootNode: AccessibilityNodeInfo?) {
        when (rule.type) {
            ActionType.INPUT -> {
                val payload = rule.textPayload ?: ""
                val targetNode = findEditableNode(rootNode)
                if (targetNode != null) {
                    try {
                        val arguments = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, payload)
                        }
                        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        if (!success) {
                            service.performClickWithFallback(targetNode)
                        }
                    } finally {
                        targetNode.recycle()
                    }
                }
            }
            ActionType.CLICK -> {
                val targetText = rule.textPayload
                if (targetText != null && targetText.trim().isNotEmpty() && rootNode != null) {
                    val matchingNode = findNodeByText(rootNode, targetText)
                    if (matchingNode != null) {
                        try {
                            service.performClickWithFallback(matchingNode)
                        } finally {
                            matchingNode.recycle()
                        }
                    }
                }
            }
            ActionType.SWIPE -> {
                rule.targetBounds?.let { bounds ->
                    service.gestureExecutor.swipe(
                        bounds.centerX().toFloat(),
                        bounds.bottom.toFloat(),
                        bounds.centerX().toFloat(),
                        bounds.top.toFloat()
                    )
                }
            }
            ActionType.TERMINATE -> {
                service.stateManager.completeTask()
            }
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findEditableNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, queryText: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.contains(queryText, ignoreCase = true) || desc.contains(queryText, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeByText(child, queryText)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
