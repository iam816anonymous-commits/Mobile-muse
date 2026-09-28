package com.example.localagent.memory

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ActionType {
    CLICK,
    INPUT,
    SWIPE,
    SCROLL,
    EXTRACT_RESULT,
    TERMINATE
}

data class ActionRule(
    val type: ActionType,
    val targetBounds: Rect? = null,
    val textPayload: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("type", type.name)
            if (targetBounds != null) {
                val boundsObj = JSONObject().apply {
                    put("l", targetBounds.left)
                    put("t", targetBounds.top)
                    put("r", targetBounds.right)
                    put("b", targetBounds.bottom)
                }
                put("targetBounds", boundsObj)
            } else {
                put("targetBounds", JSONObject.NULL)
            }
            put("textPayload", textPayload ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ActionRule {
            val type = ActionType.valueOf(json.getString("type"))
            val targetBounds = if (!json.isNull("targetBounds")) {
                val bObj = json.getJSONObject("targetBounds")
                Rect(bObj.getInt("l"), bObj.getInt("t"), bObj.getInt("r"), bObj.getInt("b"))
            } else null
            val textPayload = if (json.isNull("textPayload")) null else json.optString("textPayload")
            return ActionRule(type, targetBounds, textPayload)
        }
    }
}

data class RuleTransition(
    val screenFingerprint: String,
    val userGoal: String,
    val actionRule: ActionRule
)

class RuleLedger(private val storageFile: File? = null) {

    private val transitions = mutableMapOf<String, ActionRule>()

    init {
        loadFromFile()
    }

    private fun makeKey(fingerprint: String, goal: String): String {
        return "$fingerprint:${goal.trim().lowercase()}"
    }

    @Synchronized
    fun addTransition(screenFingerprint: String, userGoal: String, actionRule: ActionRule) {
        val key = makeKey(screenFingerprint, userGoal)
        transitions[key] = actionRule
        saveToFile()
    }

    @Synchronized
    fun getActionRule(screenFingerprint: String, userGoal: String): ActionRule? {
        val key = makeKey(screenFingerprint, userGoal)
        return transitions[key]
    }

    @Synchronized
    fun clear() {
        transitions.clear()
        saveToFile()
    }

    private fun saveToFile() {
        val file = storageFile ?: return
        try {
            val jsonArray = JSONArray()
            transitions.forEach { (key, rule) ->
                val parts = key.split(":", limit = 2)
                val obj = JSONObject().apply {
                    put("fingerprint", parts.getOrElse(0) { "" })
                    put("goal", parts.getOrElse(1) { "" })
                    put("rule", rule.toJsonObject())
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromFile() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            if (content.isBlank()) return
            val jsonArray = JSONArray(content)
            transitions.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val fingerprint = obj.getString("fingerprint")
                val goal = obj.getString("goal")
                val ruleObj = obj.getJSONObject("rule")
                val rule = ActionRule.fromJsonObject(ruleObj)
                transitions[makeKey(fingerprint, goal)] = rule
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
