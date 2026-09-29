package com.example.localagent.memory

import android.graphics.PointF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object AdaptiveRuleGraph {

    private const val TAG = "AdaptiveRuleGraph"
    private val rulesMap = HashMap<String, MutableList<ActionNodeRule>>()
    private var actionCounter = 0

    init {
        loadRulesFromDisk()
    }

    @Synchronized
    fun findRule(fingerprint: String, goalIntent: String): ActionNodeRule? {
        val list = rulesMap[fingerprint] ?: return null
        return list.find { it.goalIntent.equals(goalIntent, ignoreCase = true) && it.failureCount < 3 }
    }

    @Synchronized
    fun recordSuccess(rule: ActionNodeRule) {
        rule.successCount++
        rule.failureCount = 0
        rule.lastUpdatedMs = System.currentTimeMillis()
        saveRulesToDisk()
        checkActionCounterAndPrune()
    }

    @Synchronized
    fun recordFailure(rule: ActionNodeRule) {
        rule.failureCount++
        rule.lastUpdatedMs = System.currentTimeMillis()
        if (rule.failureCount >= 3) {
            Log.w(TAG, "Evicting stale rule for fingerprint ${rule.fingerprint} (failureCount >= 3)")
        }
        saveRulesToDisk()
        checkActionCounterAndPrune()
    }

    @Synchronized
    fun upsertRule(
        fingerprint: String,
        goalIntent: String,
        actionType: String,
        targetText: String? = null,
        targetResourceId: String? = null,
        targetCoord: PointF? = null
    ) {
        val list = rulesMap.getOrPut(fingerprint) { ArrayList() }
        val existing = list.find { it.goalIntent.equals(goalIntent, ignoreCase = true) }

        if (existing != null) {
            Log.i(TAG, "Overwriting stale rule for ($fingerprint, $goalIntent) with new target '$targetText'")
            list.remove(existing)
        }

        val newRule = ActionNodeRule(
            fingerprint = fingerprint,
            goalIntent = goalIntent,
            targetResourceId = targetResourceId,
            targetText = targetText,
            targetRelativeCoord = targetCoord,
            actionType = actionType,
            successCount = 1,
            failureCount = 0,
            lastUpdatedMs = System.currentTimeMillis()
        )
        list.add(newRule)
        saveRulesToDisk()
        checkActionCounterAndPrune()
    }

    @Synchronized
    fun pruneStaleRules() {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        var evicted = 0

        val iterator = rulesMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val list = entry.value
            val initialSize = list.size
            list.removeAll { rule ->
                rule.failureCount >= 3 || (now - rule.lastUpdatedMs) > thirtyDaysMs
            }
            evicted += (initialSize - list.size)
            if (list.isEmpty()) {
                iterator.remove()
            }
        }

        if (evicted > 0) {
            Log.i(TAG, "Pruned $evicted stale rules from AdaptiveRuleGraph")
            saveRulesToDisk()
        }
    }

    private fun checkActionCounterAndPrune() {
        actionCounter++
        if (actionCounter >= 25) {
            actionCounter = 0
            pruneStaleRules()
        }
    }

    private fun loadRulesFromDisk() {
        try {
            val storageDir = StorageManager.getStorageDirectory()
            val file = File(storageDir, "local_rules.json")
            if (!file.exists()) return

            val jsonArray = JSONArray(file.readText())
            rulesMap.clear()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val fingerprint = obj.getString("fingerprint")
                val goalIntent = obj.getString("goal_intent")
                val actionType = obj.optString("action_type", "CLICK")
                val targetText = if (obj.has("target_text") && !obj.isNull("target_text")) obj.getString("target_text") else null
                val targetRes = if (obj.has("target_resource_id") && !obj.isNull("target_resource_id")) obj.getString("target_resource_id") else null
                val successCount = obj.optInt("success_count", 1)
                val failureCount = obj.optInt("failure_count", 0)
                val lastUpdated = obj.optLong("last_updated_ms", System.currentTimeMillis())

                val rule = ActionNodeRule(
                    fingerprint = fingerprint,
                    goalIntent = goalIntent,
                    targetResourceId = targetRes,
                    targetText = targetText,
                    actionType = actionType,
                    successCount = successCount,
                    failureCount = failureCount,
                    lastUpdatedMs = lastUpdated
                )

                rulesMap.getOrPut(fingerprint) { ArrayList() }.add(rule)
            }
            Log.d(TAG, "Loaded ${rulesMap.size} screen fingerprints from /Download/LocalAgent/local_rules.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local_rules.json", e)
        }
    }

    private fun saveRulesToDisk() {
        try {
            val storageDir = StorageManager.getStorageDirectory()
            val file = File(storageDir, "local_rules.json")
            val jsonArray = JSONArray()

            rulesMap.values.flatten().forEach { rule ->
                val obj = JSONObject().apply {
                    put("fingerprint", rule.fingerprint)
                    put("goal_intent", rule.goalIntent)
                    put("action_type", rule.actionType)
                    put("target_text", rule.targetText)
                    put("target_resource_id", rule.targetResourceId)
                    put("success_count", rule.successCount)
                    put("failure_count", rule.failureCount)
                    put("last_updated_ms", rule.lastUpdatedMs)
                }
                jsonArray.put(obj)
            }

            FileWriter(file, false).use { writer ->
                writer.write(jsonArray.toString(2))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save local_rules.json to disk", e)
        }
    }
}
