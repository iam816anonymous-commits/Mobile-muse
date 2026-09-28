package com.example.localagent.engine

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import org.json.JSONObject

object SelfHealingResolver {

    private const val TAG = "SelfHealingResolver"

    fun resolveAndHeal(
        service: LocalAgentService,
        context: StallContext,
        onResolved: (String, ActionRule) -> Unit
    ) {
        val query = QueryFormulator.formulateQuery(context)
        val prompt = "The agent is stuck on screen with elements: ${context.visibleTexts.joinToString(", ")}. Goal: ${context.userGoal}. What specific UI element should it tap next to proceed? Respond with JSON: {\"target_keyword\": \"${context.visibleTexts.firstOrNull() ?: "Button"}\", \"action\": \"CLICK\"}"

        service.broadcastTelemetryLog("SYS", "Self-healing triggered with query: '$query'")
        service.aiBridgeClient.sendPayloadAsync(prompt, context.userGoal) { result ->
            result.onSuccess { jsonStr ->
                val (keyword, rule) = parseHealingResponse(jsonStr)
                if (keyword.isNotBlank()) {
                    service.broadcastTelemetryLog("SELF-HEAL", "Unstuck using crafted query: '$query' -> Target found: '$keyword'")
                    service.voiceSynthesizer?.speak("Resolved unexpected layout. Proceeding.")
                    onResolved(keyword, rule)
                }
            }.onFailure { err ->
                Log.e(TAG, "Self-healing query failed", err)
            }
        }
    }

    fun parseHealingResponse(jsonStr: String): Pair<String, ActionRule> {
        return try {
            val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()
            val obj = JSONObject(cleanJson)
            val keyword = obj.optString("target_keyword", "")
            val actionName = obj.optString("action", "CLICK").uppercase()
            val actionType = if (actionName == "SWIPE") ActionType.SWIPE else ActionType.CLICK
            Pair(keyword, ActionRule(type = actionType, textPayload = keyword))
        } catch (e: Exception) {
            Pair("", ActionRule(type = ActionType.CLICK))
        }
    }
}
