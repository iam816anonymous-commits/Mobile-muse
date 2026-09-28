package com.example.localagent.memory

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class KnowledgeEntry(
    val id: String,
    val timestamp: Long,
    val query: String,
    val targetApp: String,
    val answer: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("query", query)
            put("targetApp", targetApp)
            put("answer", answer)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): KnowledgeEntry {
            return KnowledgeEntry(
                id = json.getString("id"),
                timestamp = json.getLong("timestamp"),
                query = json.getString("query"),
                targetApp = json.getString("targetApp"),
                answer = json.getString("answer")
            )
        }
    }
}

class KnowledgeLedger(private val storageFile: File? = null) {

    companion object {
        const val MAX_KNOWLEDGE_ENTRIES = 200
    }

    private val entries = mutableListOf<KnowledgeEntry>()

    init {
        loadFromFile()
    }

    @Synchronized
    fun addKnowledge(id: String, query: String, targetApp: String, answer: String) {
        val entry = KnowledgeEntry(
            id = id,
            timestamp = System.currentTimeMillis(),
            query = query,
            targetApp = targetApp,
            answer = answer
        )

        // FIFO eviction if exceeding max entries
        entries.add(entry)
        while (entries.size > MAX_KNOWLEDGE_ENTRIES) {
            entries.removeAt(0)
        }
        saveToFile()
    }

    @Synchronized
    fun findAnswerForQuery(query: String): KnowledgeEntry? {
        val normalizedQuery = query.trim().lowercase()
        return entries.find { it.query.trim().lowercase() == normalizedQuery }
    }

    @Synchronized
    fun getEntries(): List<KnowledgeEntry> = entries.toList()

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
                val obj = jsonArray.getJSONObject(i)
                entries.add(KnowledgeEntry.fromJsonObject(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
