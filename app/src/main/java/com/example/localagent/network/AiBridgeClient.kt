package com.example.localagent.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiBridgeClient(
    private val endpointUrl: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
    private val apiKey: String? = null
) {

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
        val fullUrlStr = if (!apiKey.isNull_or_blank()) {
            "$endpointUrl?key=$apiKey"
        } else {
            endpointUrl
        }

        val url = URL(fullUrlStr)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
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
        val prompt = "Goal: $goalDescription\nCurrent Screen Nodes JSON:\n$serializedScreen"
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

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
