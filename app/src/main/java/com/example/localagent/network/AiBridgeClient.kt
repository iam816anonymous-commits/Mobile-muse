package com.example.localagent.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class AgentAction(
    val action: String,
    val targetIndex: Int? = null,
    val inputText: String? = null
)

class AiBridgeClient(
    private val endpointUrl: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
    private val apiKey: String? = null
) {

    companion object {
        const val SYSTEM_PROMPT = "You are an Android OS and mobile web automation brain. Given the USER_GOAL and current SCREEN_ELEMENTS, decide the single next step. Support native apps and mobile web apps (Chrome). Output valid JSON only without markdown fences: {\"action\": \"CLICK\"|\"INPUT\"|\"SCROLL\"|\"COMPLETE\", \"target_index\": Int, \"input_text\": String?}"
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun sendPayloadAsync(
        serializedScreen: String,
        goalDescription: String,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val response = sendPayloadSync(serializedScreen, goalDescription)
                callback(Result.success(response))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun sendPayloadSync(
        serializedScreen: String,
        goalDescription: String
    ): String {
        if (apiKey.isNull_or_blank() || apiKey.equals("YOUR_API_KEY", ignoreCase = true)) {
            throw IllegalArgumentException("API_KEY_UNSET: Cloud reasoning unavailable. Proceeding with local heuristics.")
        }

        val fullUrlStr = "$endpointUrl?key=$apiKey"

        val url = URL(fullUrlStr)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.doOutput = true

            val requestBody = buildGeminiRequestBody(serializedScreen, goalDescription)

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    return reader.readText()
                }
            } else {
                val errorText = try {
                    BufferedReader(InputStreamReader(connection.errorStream, "UTF-8")).use { it.readText() }
                } catch (e: Exception) {
                    "HTTP $responseCode"
                }
                throw RuntimeException("AI API request failed [$responseCode]: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun buildGeminiRequestBody(serializedScreen: String, goalDescription: String): String {
        val prompt = "$SYSTEM_PROMPT\n\nUSER_GOAL: $goalDescription\n\nSCREEN_ELEMENTS:\n$serializedScreen"
        val partObj = JSONObject().apply {
            put("text", prompt)
        }
        val partsArray = JSONArray().apply {
            put(partObj)
        }
        val contentObj = JSONObject().apply {
            put("parts", partsArray)
        }
        val contentsArray = JSONArray().apply {
            put(contentObj)
        }
        return JSONObject().apply {
            put("contents", contentsArray)
        }.toString()
    }

    fun parseAgentAction(jsonStr: String): AgentAction? {
        return try {
            val cleanJson = jsonStr.replace("```json", "").replace("```", "").trim()
            val obj = JSONObject(cleanJson)
            val action = obj.optString("action", "CLICK").uppercase()
            val targetIndex = if (obj.has("target_index") && !obj.isNull("target_index")) obj.getInt("target_index") else null
            val inputText = if (obj.has("input_text") && !obj.isNull("input_text")) obj.getString("input_text") else null
            AgentAction(action = action, targetIndex = targetIndex, inputText = inputText)
        } catch (e: Exception) {
            null
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
