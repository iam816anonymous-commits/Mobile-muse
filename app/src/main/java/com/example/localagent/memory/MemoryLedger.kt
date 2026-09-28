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
    fun clear() {
        entries.clear()
        saveToFile()
    }

    private fun saveToFile() {
        val file = storageFile ?: return
        try {
            val jsonArray = JSONArray()
            entries.forEach { jsonArray.put(it.toJsonObject()) }
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
            entries.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                entries.add(LedgerEntry.fromJsonObject(jsonObject))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
