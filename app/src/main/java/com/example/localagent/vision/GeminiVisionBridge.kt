package com.example.localagent.vision

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GeminiVisionBridge(
    private val endpointUrl: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
    private val apiKey: String? = null
) {

    private val executor = Executors.newSingleThreadExecutor()

    fun analyzeVisualAsync(
        base64Image: String,
        queryPrompt: String,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            try {
                val result = analyzeVisualSync(base64Image, queryPrompt)
                callback(Result.success(result))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun analyzeVisualSync(base64Image: String, queryPrompt: String): String {
        val fullUrlStr = if (!apiKey.isNullOrEmpty()) "$endpointUrl?key=$apiKey" else endpointUrl
        val url = URL(fullUrlStr)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            val requestBody = buildVisionRequestBody(base64Image, queryPrompt)

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                return parseCandidateText(responseText)
            } else {
                val errorText = try {
                    BufferedReader(InputStreamReader(connection.errorStream, "UTF-8")).use { it.readText() }
                } catch (e: Exception) { "HTTP $responseCode" }
                throw RuntimeException("Gemini Vision API request failed [$responseCode]: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun buildVisionRequestBody(base64Image: String, queryPrompt: String): String {
        val promptText = "You are the visual sensory cortex of an Android assistant. Answer concisely in 1-2 sentences: $queryPrompt"

        val textPart = JSONObject().apply {
            put("text", promptText)
        }
        val imagePart = JSONObject().apply {
            val inlineData = JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", base64Image)
            }
            put("inline_data", inlineData)
        }

        val partsArray = JSONArray().apply {
            put(textPart)
            put(imagePart)
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

    private fun parseCandidateText(jsonResponse: String): String {
        return try {
            val obj = JSONObject(jsonResponse)
            val candidates = obj.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text").trim()
                }
            }
            "Visual analysis complete."
        } catch (e: Exception) {
            "Unable to parse visual response."
        }
    }
}
