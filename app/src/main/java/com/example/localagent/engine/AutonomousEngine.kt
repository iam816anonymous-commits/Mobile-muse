package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.NodeData
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import com.example.localagent.memory.MemoryGuard
import com.example.localagent.memory.ScreenHasher
import com.example.localagent.serializer.ScreenSerializer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

object AutonomousEngine {

    private const val TAG = "AutonomousEngine"
    private val fingerprintRingBuffer = ArrayDeque<String>(3)

    fun processCurrentScreen(service: LocalAgentService, goalText: String) {
        // Enforce 25s hard timeout
        runBlocking {
            withTimeoutOrNull(25_000L) {
                processCurrentScreenInternal(service, goalText)
            } ?: run {
                Log.w(TAG, "Task execution timed out (25s limit). Returning to IDLE.")
                service.broadcastTelemetryLog("WARN", "Task execution timed out (25s limit). Returning to IDLE.")
                service.voiceSynthesizer?.speak("Task execution timed out. Returning to standing by.")
                service.stateManager.reset()
            }
        }
    }

    private fun processCurrentScreenInternal(service: LocalAgentService, goalText: String) {
        // Enforce strict step count limit <= 10
        val currentStep = service.stateManager.getCurrentState().currentStepIndex
        if (currentStep >= 10) {
            Log.w(TAG, "Strict step limit reached ($currentStep >= 10). Aborting to prevent infinite loop.")
            service.broadcastTelemetryLog("WARN", "Task step limit reached ($currentStep >= 10). Aborting to prevent infinite loop.")
            service.voiceSynthesizer?.speak("Task step limit reached. Aborting to prevent infinite loop.")
            service.stateManager.reset()
            return
        }

        // LMK Protection Check
        if (MemoryGuard.isLowMemoryCondition(service)) {
            service.broadcastTelemetryLog("SYS", "Low RAM <250MB threshold reached. Resetting task state to IDLE.")
            service.haltAndResetAgent("Low RAM Memory Protection Triggered")
            return
        }

        // Check for Web Goal routing first
        handleWebRoutingIfNeeded(service, goalText)

        val rootNode = service.getActiveWindowRoot() ?: return
        try {
            // Check for obstacle/popup blockers first
            val obstacleHandled = ObstacleDetector.checkForAndDismissObstacle(service, rootNode)
            if (obstacleHandled) {
                return
            }

            val extractedNodes = mutableListOf<NodeData>()
            service.traverseAndExtractNode(rootNode, extractedNodes)

            val packageName = rootNode.packageName?.toString() ?: "unknown"
            val screenFingerprint = ScreenHasher.computeFingerprint(packageName, extractedNodes)
            service.broadcastTelemetryLog("SYS", "Screen Fingerprint computed: #$screenFingerprint")

            // Loop Detection via Ring Buffer
            if (fingerprintRingBuffer.size >= 3) {
                fingerprintRingBuffer.removeFirst()
            }
            fingerprintRingBuffer.addLast(screenFingerprint)

            if (fingerprintRingBuffer.size == 3 && fingerprintRingBuffer.all { it == screenFingerprint }) {
                Log.w(TAG, "Loop detected! Fingerprint $screenFingerprint repeated 3 times. Stepping back and purging cached rule.")
                service.broadcastTelemetryLog("RECOVERY", "Loop detected on #$screenFingerprint. Stepping back & purging rule.")
                service.ruleLedger.removeTransition(screenFingerprint, goalText)
                fingerprintRingBuffer.clear()
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return
            }

            // 1. Check local rule graph
            val cachedRule = service.ruleLedger.getActionRule(screenFingerprint, goalText)
            if (cachedRule != null) {
                Log.d(TAG, "Offline rule graph hit for $screenFingerprint. Executing cached action: ${cachedRule.type}")
                service.broadcastTelemetryLog("CACHE", "Offline match found -> Replaying rule ${cachedRule.type}")
                executeActionRule(service, cachedRule, rootNode)
                StallDetector.reset()
                service.memoryLedger.recordStep(
                    stepIndex = service.stateManager.getCurrentState().currentStepIndex,
                    action = "OFFLINE_RULE_ACTION: ${cachedRule.type}",
                    success = true
                )
                return
            }

            // 2. Query AI Bridge if no local rule exists
            val serializedScreen = ScreenSerializer.serializeScreen(extractedNodes)
            service.broadcastTelemetryLog("SYS", "Querying AI Bridge for unknown screen state...")
            service.aiBridgeClient.sendPayloadAsync(serializedScreen, goalText) { result ->
                result.onSuccess { aiJsonResponse ->
                    Log.d(TAG, "AI Response: $aiJsonResponse")
                    service.broadcastTelemetryLog("EXTRACT", "AI Bridge response received")
                    val parsedRule = parseAiActionResponse(aiJsonResponse)
                    if (parsedRule != null) {
                        StallDetector.reset()
                        service.ruleLedger.addTransition(screenFingerprint, goalText, parsedRule)
                        executeActionRule(service, parsedRule, service.getActiveWindowRoot())
                        service.memoryLedger.recordStep(
                            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
                            action = "AI_RULE_ACTION: ${parsedRule.type}",
                            success = true
                        )
                        if (parsedRule.type == ActionType.TERMINATE) {
                            service.stateManager.completeTask()
                        }
                    } else {
                        StallDetector.recordFailure()
                        val visibleLabels = extractedNodes.mapNotNull { it.text ?: it.contentDescription }
                        if (StallDetector.isStalled(hasTargetIndex = false)) {
                            val stallCtx = StallDetector.buildContext(packageName, goalText, visibleLabels)
                            SelfHealingResolver.resolveAndHeal(service, stallCtx) { _, healedRule ->
                                service.ruleLedger.addTransition(screenFingerprint, goalText, healedRule)
                                executeActionRule(service, healedRule, service.getActiveWindowRoot())
                            }
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "AI Bridge query failed", error)
                    StallDetector.recordFailure()
                    service.broadcastTelemetryLog("SYS", "AI Bridge query failed: ${error.message}")
                    val visibleLabels = extractedNodes.mapNotNull { it.text ?: it.contentDescription }
                    if (StallDetector.isStalled(hasTargetIndex = false)) {
                        val stallCtx = StallDetector.buildContext(packageName, goalText, visibleLabels)
                        SelfHealingResolver.resolveAndHeal(service, stallCtx) { _, healedRule ->
                            service.ruleLedger.addTransition(screenFingerprint, goalText, healedRule)
                            executeActionRule(service, healedRule, service.getActiveWindowRoot())
                        }
                    }
                }
            }

        } finally {
            rootNode.recycle()
        }
    }

    private fun handleWebRoutingIfNeeded(service: LocalAgentService, goalText: String) {
        val lowerGoal = goalText.lowercase()
        if (lowerGoal.contains("open gemini") || lowerGoal.contains("ask gemini")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            service.broadcastTelemetryLog("NAV", "Navigated Chrome to https://gemini.google.com")
        } else if (lowerGoal.contains("open chatgpt") || lowerGoal.contains("ask chatgpt")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            service.broadcastTelemetryLog("NAV", "Navigated Chrome to https://chatgpt.com")
        }
    }

    fun parseAiActionResponse(jsonStr: String): ActionRule? {
        return try {
            val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()
            val obj = JSONObject(cleanJson)
            val actionName = obj.optString("action", "CLICK").uppercase()
            val targetText = if (obj.has("target_text") && !obj.isNull("target_text")) {
                obj.getString("target_text")
            } else if (obj.has("input_text") && !obj.isNull("input_text")) {
                obj.getString("input_text")
            } else null

            val actionType = when (actionName) {
                "INPUT", "INPUT_TEXT" -> ActionType.INPUT
                "SWIPE" -> ActionType.SWIPE
                "SCROLL" -> ActionType.SCROLL
                "EXTRACT_RESULT" -> ActionType.EXTRACT_RESULT
                "COMPLETE", "TERMINATE" -> ActionType.TERMINATE
                else -> ActionType.CLICK
            }

            ActionRule(
                type = actionType,
                textPayload = targetText
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
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val arguments = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, payload)
                        }
                        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        if (success) {
                            service.broadcastTelemetryLog("ACT", "Injected text via ACTION_SET_TEXT: '$payload'")
                        } else {
                            service.performClickWithFallback(targetNode)
                        }

                        // Locate and click submit/send button
                        if (rootNode != null) {
                            val sendNode = findSendOrSubmitButton(rootNode)
                            if (sendNode != null) {
                                try {
                                    service.performClickWithFallback(sendNode)
                                    service.broadcastTelemetryLog("ACT", "Triggered send/submit button action")
                                } finally {
                                    sendNode.recycle()
                                }
                            }
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
                            service.broadcastTelemetryLog("ACT", "Dispatched Click/Fallback for target '$targetText'")
                        } finally {
                            matchingNode.recycle()
                        }
                    }
                }
            }
            ActionType.SWIPE, ActionType.SCROLL -> {
                rule.targetBounds?.let { bounds ->
                    service.gestureExecutor.swipe(
                        bounds.centerX().toFloat(),
                        bounds.bottom.toFloat(),
                        bounds.centerX().toFloat(),
                        bounds.top.toFloat()
                    )
                    service.broadcastTelemetryLog("ACT", "Dispatched Swipe gesture at bounds ${bounds.toShortString()}")
                }
            }
            ActionType.EXTRACT_RESULT -> {
                Log.d(TAG, "Result extracted: ${rule.textPayload}")
                service.broadcastTelemetryLog("EXTRACT", "Result captured: '${rule.textPayload}'")
            }
            ActionType.TERMINATE -> {
                service.stateManager.completeTask()
                service.broadcastTelemetryLog("SYS", "Goal termination executed")
            }
        }
    }

    fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val isInputHint = hint.contains("ask") || hint.contains("search") || hint.contains("message") ||
                text.contains("ask") || text.contains("search") || text.contains("message") ||
                desc.contains("ask") || desc.contains("search") || desc.contains("message")

        if (node.isEditable || isInputHint) {
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

    fun findSendOrSubmitButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val isSendOrSubmit = text.contains("send") || text.contains("submit") || text.contains("search") ||
                desc.contains("send") || desc.contains("submit") || desc.contains("search")

        val hasActions = try { node.actionList?.isNotEmpty() == true } catch (e: Exception) { false }

        if ((node.isClickable || hasActions) && isSendOrSubmit) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findSendOrSubmitButton(child)
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
