package com.example.localagent.memory

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LedgerEntry(
    val stepIndex: Int,
    val action: String,
    val success: Boolean,
    val failureCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("stepIndex", stepIndex)
            put("action", action)
            put("success", success)
            put("failureCode", failureCode ?: JSONObject.NULL)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): LedgerEntry {
            return LedgerEntry(
                stepIndex = json.getInt("stepIndex"),
                action = json.getString("action"),
                success = json.getBoolean("success"),
                failureCode = if (json.isNull("failureCode")) null else json.optString("failureCode"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

class MemoryLedger(private val storageFile: File? = null) {

    private val entries = mutableListOf<LedgerEntry>()
    private val ruleCache = mutableMapOf<String, String>()

    init {
        loadFromFile()
    }

    @Synchronized
    fun recordStep(stepIndex: Int, action: String, success: Boolean, failureCode: String? = null) {
        val entry = LedgerEntry(
            stepIndex = stepIndex,
            action = action,
            success = success,
            failureCode = failureCode
        )
        entries.add(entry)
        saveToFile()
    }

    @Synchronized
    fun getEntries(): List<LedgerEntry> = entries.toList()

    @Synchronized
    fun hasFailedRecently(action: String, failureCode: String?): Boolean {
        return entries.any { !it.success && it.action == action && (failureCode == null || it.failureCode == failureCode) }
    }

    @Synchronized
    fun cacheRule(screenSignature: String, actionRule: String) {
        ruleCache[screenSignature] = actionRule
        saveToFile()
    }

    @Synchronized
    fun getCachedRule(screenSignature: String): String? {
        return ruleCache[screenSignature]
    }

    @Synchronized
    fun clearRuleCache() {
        ruleCache.clear()
        saveToFile()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        ruleCache.clear()
        saveToFile()
    }

    private fun saveToFile() {
        val file = storageFile ?: return
        try {
            val rootObj = JSONObject()

            val jsonArray = JSONArray()
            entries.forEach { jsonArray.put(it.toJsonObject()) }
            rootObj.put("entries", jsonArray)

            val rulesObj = JSONObject()
            ruleCache.forEach { (k, v) -> rulesObj.put(k, v) }
            rootObj.put("ruleCache", rulesObj)

            file.writeText(rootObj.toString(2))
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
            val rootObj = JSONObject(content)

            if (rootObj.has("entries")) {
                val jsonArray = rootObj.getJSONArray("entries")
                entries.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    entries.add(LedgerEntry.fromJsonObject(jsonObject))
                }
            }

            if (rootObj.has("ruleCache")) {
                val rulesObj = rootObj.getJSONObject("ruleCache")
                ruleCache.clear()
                val keys = rulesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    ruleCache[key] = rulesObj.getString(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
