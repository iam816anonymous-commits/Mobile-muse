package com.example.localagent.engine

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService
import java.net.URLEncoder

object BrowserAutomation {

    private const val TAG = "BrowserAutomation"

    fun executeSearch(service: LocalAgentService, rawGoal: String) {
        service.isProcessingGoal = true
        val cleanQuery = QueryPayloadSanitizer.extractSearchQuery(rawGoal)
        Log.i(TAG, "Executing Dual-Tier Web Search for clean query: '$cleanQuery' (raw: '$rawGoal')")
        service.broadcastTelemetryLog("SEARCH", "Query: '$cleanQuery'")

        try {
            // Tier 1: Primary Native Search Intent targeting Chrome
            try {
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, cleanQuery)
                    `package` = "com.android.chrome"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                service.sendBroadcast(Intent("com.localagent.MINIMIZE_UI"))
                service.startActivity(searchIntent)
                service.broadcastTelemetryLog("SEARCH", "Tier 1 Native ACTION_WEB_SEARCH dispatched to Chrome")
            } catch (e: Exception) {
                Log.w(TAG, "Tier 1 ACTION_WEB_SEARCH failed, falling back to Tier 2 encoded URL", e)

                // Tier 2: Direct Encoded Search URL
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "https://www.google.com/search?q=$encodedQuery"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    `package` = "com.android.chrome"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                service.sendBroadcast(Intent("com.localagent.MINIMIZE_UI"))
                service.startActivity(browserIntent)
                service.broadcastTelemetryLog("SEARCH", "Tier 2 Direct Encoded Search URL dispatched to Chrome")
            }

            // Step C: Harvest headlines after page load stabilization (3500ms)
            Handler(Looper.getMainLooper()).postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    try {
                        val leafTexts = GenericUIOperator.harvestLeafText(root) { text -> text.length in 12..120 }
                        val headlines = leafTexts.take(3).joinToString(" | ")
                        val resultMsg = if (headlines.isNotEmpty()) {
                            "Search results for $cleanQuery: $headlines"
                        } else {
                            "Here are the search results for $cleanQuery."
                        }
                        service.broadcastGoalCompleted(cleanQuery, "SUCCESS", resultMsg)
                        service.voiceSynthesizer?.speak("Here are the search results for $cleanQuery.")
                    } finally {
                        root.recycle()
                    }
                } else {
                    service.voiceSynthesizer?.speak("Here are the search results for $cleanQuery.")
                }
            }, 3500L)

        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 4000L)
        }
    }
}
