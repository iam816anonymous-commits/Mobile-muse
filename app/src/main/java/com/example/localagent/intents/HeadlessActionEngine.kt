package com.example.localagent.intents

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppIndexer
import com.example.localagent.engine.QueryPayloadSanitizer
import java.net.URLEncoder

object HeadlessActionEngine {

    private const val TAG = "HeadlessActionEngine"

    fun tryRouteHeadlessAction(service: LocalAgentService, rawGoal: String): Boolean {
        val lowerGoal = rawGoal.lowercase().trim()
        Log.i(TAG, "Evaluating goal for HeadlessActionEngine routing: '$rawGoal'")

        // 1. Web Search
        if (lowerGoal.contains("search ") || lowerGoal.contains("google ") || lowerGoal.contains("look up ")) {
            val cleanQuery: String = QueryPayloadSanitizer.extractSearchQuery(rawGoal)
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return launchIntentSafely(service, intent, "Web Search ($cleanQuery)")
        }

        // 2. YouTube Direct Search / Play
        if (lowerGoal.contains("youtube") || (lowerGoal.contains("play ") && lowerGoal.contains("video"))) {
            val cleanQuery: String = QueryPayloadSanitizer.extractSearchQuery(rawGoal)
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return launchIntentSafely(service, intent, "YouTube ($cleanQuery)")
        }

        // 3. Instant Message Staging (WhatsApp / SMS)
        if (lowerGoal.contains("whatsapp") || lowerGoal.contains("send message") || lowerGoal.contains("text ")) {
            val message = extractMessagePayload(rawGoal)
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?text=$encodedMessage"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return launchIntentSafely(service, intent, "WhatsApp/Messaging ($message)")
        }

        // 4. Timer / Alarm
        if (lowerGoal.contains("timer") || lowerGoal.contains("alarm") || lowerGoal.contains("wake me up")) {
            val seconds = extractTimeInSeconds(lowerGoal)
            if (lowerGoal.contains("timer") && seconds > 0) {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "LocalAgent Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return launchIntentSafely(service, intent, "Set Timer ($seconds sec)")
            } else if (lowerGoal.contains("alarm")) {
                val (hour, minute) = extractAlarmTime(lowerGoal)
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "LocalAgent Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return launchIntentSafely(service, intent, "Set Alarm ($hour:$minute)")
            }
        }

        // 5. Note Append / Share
        if (lowerGoal.startsWith("note ") || lowerGoal.contains("take note") || lowerGoal.contains("note down")) {
            val noteText = extractNoteText(rawGoal)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noteText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Resolve any app accepting text/plain
            val resolvedPkg = AppIndexer.resolveAppByQuery(service, "note") ?: "com.google.android.keep"
            sendIntent.setPackage(resolvedPkg)

            return if (launchIntentSafely(service, sendIntent, "Note Append to $resolvedPkg")) {
                true
            } else {
                val chooserIntent = Intent.createChooser(sendIntent, "Send Note Payload").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntentSafely(service, chooserIntent, "Note Append Chooser")
            }
        }

        return false
    }

    private fun launchIntentSafely(context: Context, intent: Intent, desc: String): Boolean {
        return try {
            context.startActivity(intent)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("HEADLESS", "Direct platform contract launched successfully: $desc")
                context.broadcastGoalCompleted("Direct Intent: $desc", "SUCCESS", desc)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch headless intent for '$desc'", e)
            if (context is LocalAgentService) {
                context.broadcastTelemetryLog("HEADLESS", "Direct platform contract failed for '$desc': ${e.message}")
            }
            false
        }
    }

    private fun extractMessagePayload(rawGoal: String): String {
        val cleaned = rawGoal.replace("whatsapp", "", ignoreCase = true)
            .replace("send message", "", ignoreCase = true)
            .replace("text", "", ignoreCase = true)
            .replace("saying", "", ignoreCase = true)
            .replace("to", "", ignoreCase = true)
            .trim()
        return cleaned.ifEmpty { "Hello from LocalAgent!" }
    }

    private fun extractNoteText(rawGoal: String): String {
        return rawGoal.replace("note down", "", ignoreCase = true)
            .replace("take note", "", ignoreCase = true)
            .replace("note that", "", ignoreCase = true)
            .replace("note", "", ignoreCase = true)
            .trim()
            .ifEmpty { rawGoal }
    }

    private fun extractTimeInSeconds(goal: String): Int {
        val minMatch = Regex("(\\d+)\\s*min").find(goal)
        if (minMatch != null) return minMatch.groupValues[1].toInt() * 60
        val secMatch = Regex("(\\d+)\\s*sec").find(goal)
        if (secMatch != null) return secMatch.groupValues[1].toInt()
        val numMatch = Regex("(\\d+)").find(goal)
        return numMatch?.groupValues?.get(1)?.toInt()?.times(60) ?: 60
    }

    private fun extractAlarmTime(goal: String): Pair<Int, Int> {
        val timeMatch = Regex("(\\d{1,2}):(\\d{2})").find(goal)
        if (timeMatch != null) {
            return Pair(timeMatch.groupValues[1].toInt(), timeMatch.groupValues[2].toInt())
        }
        val hourMatch = Regex("(\\d{1,2})\\s*(am|pm)?").find(goal)
        if (hourMatch != null) {
            var hr = hourMatch.groupValues[1].toInt()
            val ampm = hourMatch.groupValues[2].lowercase()
            if (ampm == "pm" && hr < 12) hr += 12
            if (ampm == "am" && hr == 12) hr = 0
            return Pair(hr, 0)
        }
        return Pair(8, 0)
    }
}
