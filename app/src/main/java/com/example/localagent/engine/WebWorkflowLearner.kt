package com.example.localagent.engine

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import org.json.JSONArray
import org.json.JSONObject

object WebWorkflowLearner {

    private const val TAG = "WebWorkflowLearner"

    fun learnAndExecute(service: LocalAgentService, goalText: String, appName: String) {
        val prompt = "Provide the minimal 3-step Android UI sequence to accomplish $goalText in $appName. Format as JSON array: [{\"step\": 1, \"target_label\": \"...\", \"action\": \"CLICK\"}]"

        service.broadcastTelemetryLog("SYS", "Querying AI for web workflow sequence...")
        service.aiBridgeClient.sendPayloadAsync(prompt, goalText) { result ->
            result.onSuccess { jsonStr ->
                val steps = parseWorkflowSteps(jsonStr)
                if (steps.isNotEmpty()) {
                    service.broadcastTelemetryLog("LEARN", "Web workflow acquired & cached for $appName")
                    val root = service.getActiveWindowRoot()
                    val screenFingerprint = root?.packageName?.toString() ?: appName
                    root?.recycle()

                    steps.forEach { stepRule ->
                        service.ruleLedger.addTransition(screenFingerprint, goalText, stepRule)
                    }
                    AutonomousEngine.processCurrentScreen(service, goalText)
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to learn web workflow", err)
            }
        }
    }

    fun parseWorkflowSteps(jsonStr: String): List<ActionRule> {
        val rules = mutableListOf<ActionRule>()
        try {
            val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()
            val array = if (cleanJson.startsWith("[")) JSONArray(cleanJson) else JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val label = obj.optString("target_label", "")
                val action = obj.optString("action", "CLICK")
                val actionType = if (action.equals("INPUT", ignoreCase = true)) ActionType.INPUT else ActionType.CLICK
                rules.add(ActionRule(type = actionType, textPayload = label))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing workflow steps", e)
        }
        return rules
    }
}
