package com.example.localagent.intents

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.example.localagent.LocalAgentService

object IntentContractBridge {

    private const val TAG = "IntentContractBridge"

    fun tryIntentFastPath(context: Context, goalText: String): Boolean {
        val lowerGoal = goalText.lowercase().trim()

        // 1. Timer Intent Contract
        if (lowerGoal.contains("timer") || lowerGoal.contains("set timer")) {
            val seconds = extractNumber(lowerGoal, 60)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "LocalAgent Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Visually opens Clock app!
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                (context as? LocalAgentService)?.broadcastTelemetryLog("TIER1", "Dispatched AlarmClock.ACTION_SET_TIMER ($seconds sec)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path SET_TIMER intent failed", e)
            }
        }

        // 2. Alarm Intent Contract
        if (lowerGoal.contains("alarm") || lowerGoal.contains("set alarm")) {
            val hour = extractHour(lowerGoal, 7)
            val minute = extractMinute(lowerGoal, 0)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "LocalAgent Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Visually opens Clock app!
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                (context as? LocalAgentService)?.broadcastTelemetryLog("TIER1", "Dispatched AlarmClock.ACTION_SET_ALARM ($hour:$minute)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path SET_ALARM intent failed", e)
            }
        }

        // 3. Quick Note / Share Intent Contract
        if (lowerGoal.contains("share") || lowerGoal.startsWith("note ")) {
            val noteBody = lowerGoal.removePrefix("note ").removePrefix("share ").trim()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noteBody)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Share via LocalAgent").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                (context as? LocalAgentService)?.broadcastTelemetryLog("TIER1", "Dispatched Intent.ACTION_SEND Note Fast-Path")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Fast-path ACTION_SEND intent failed", e)
            }
        }

        return false
    }

    private fun extractNumber(input: String, defaultVal: Int): Int {
        val match = Regex("\\d+").find(input)
        return match?.value?.toIntOrNull() ?: defaultVal
    }

    private fun extractHour(input: String, defaultHour: Int): Int {
        val match = Regex("(\\d{1,2})(:\\d{2})?\\s*(am|pm)?").find(input)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: defaultHour
            val amPm = match.groupValues[3].lowercase()
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            return hour
        }
        return defaultHour
    }

    private fun extractMinute(input: String, defaultMinute: Int): Int {
        val match = Regex(":\\d{2}").find(input)
        if (match != null) {
            return match.value.removePrefix(":").toIntOrNull() ?: defaultMinute
        }
        return defaultMinute
    }
}
