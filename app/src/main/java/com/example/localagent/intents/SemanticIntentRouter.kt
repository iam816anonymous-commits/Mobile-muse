package com.example.localagent.intents

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.KnowledgeLedger

object SemanticIntentRouter {

    private const val TAG = "SemanticIntentRouter"

    fun routeAndDispatch(service: LocalAgentService, goalText: String): Boolean {
        val lowerGoal = goalText.lowercase().trim()
        val resolver = AppCapabilityResolver(service)
        val capabilities = resolver.scanAndMapCapabilities()

        return when {
            lowerGoal.contains("note") || lowerGoal.contains("write down") || lowerGoal.contains("remember") -> {
                val notesCap = capabilities[CapabilityDomain.DOMAIN_NOTES]
                val targetPkg = notesCap?.packageName ?: "com.google.android.keep"
                val launchIntent = service.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(launchIntent)
                    service.broadcastTelemetryLog("ROUTER", "Dispatched Notes domain to $targetPkg")
                    recordDomainMemory(service, "NOTES", targetPkg, "Note action initialized: '$goalText'")
                    true
                } else false
            }
            lowerGoal.contains("alarm") || lowerGoal.contains("wake me up") || lowerGoal.contains("timer") -> {
                val (hour, minute) = parseTime(lowerGoal)
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    service.startActivity(intent)
                    service.broadcastTelemetryLog("ROUTER", "Dispatched AlarmClock intent for $hour:$minute")
                    val clockPkg = capabilities[CapabilityDomain.DOMAIN_CLOCK]?.packageName ?: "com.android.deskclock"
                    recordDomainMemory(service, "CLOCK", clockPkg, "Set alarm for $hour:$minute")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start AlarmClock intent", e)
                    false
                }
            }
            lowerGoal.contains("record voice") || lowerGoal.contains("sound recorder") || lowerGoal.contains("record audio") -> {
                val recorderCap = capabilities[CapabilityDomain.DOMAIN_VOICE_RECORDER]
                val targetPkg = recorderCap?.packageName ?: "com.android.soundrecorder"
                val launchIntent = service.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    service.startActivity(launchIntent)
                    service.broadcastTelemetryLog("ROUTER", "Dispatched Voice Recorder to $targetPkg")
                    recordDomainMemory(service, "VOICE_RECORDER", targetPkg, "Voice recording started")
                    true
                } else false
            }
            lowerGoal.contains("local video") || lowerGoal.contains("offline video") || lowerGoal.contains("downloaded video") -> {
                val localCap = capabilities[CapabilityDomain.DOMAIN_LOCAL_VIDEO]
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://media/external/video/media"), "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    service.startActivity(intent)
                    service.broadcastTelemetryLog("ROUTER", "Dispatched Local Video player")
                    recordDomainMemory(service, "LOCAL_VIDEO", localCap?.packageName ?: "Gallery", "Local video playback")
                    true
                } catch (e: Exception) {
                    false
                }
            }
            lowerGoal.contains("online video") || lowerGoal.contains("youtube") || lowerGoal.contains("watch video") -> {
                val youtubeCap = capabilities[CapabilityDomain.DOMAIN_ONLINE_VIDEO]
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
                service.broadcastTelemetryLog("ROUTER", "Dispatched Online Video player")
                recordDomainMemory(service, "ONLINE_VIDEO", youtubeCap?.packageName ?: "YouTube", "Online video playback")
                true
            }
            else -> false
        }
    }

    private fun parseTime(goal: String): Pair<Int, Int> {
        var hour = 7
        var minute = 30
        try {
            val parts = goal.split(" ")
            for (p in parts) {
                if (p.contains(":")) {
                    val sub = p.split(":")
                    hour = sub[0].toIntOrNull() ?: 7
                    minute = sub[1].replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
                    if (goal.contains("pm") && hour < 12) hour += 12
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(hour, minute)
    }

    private fun recordDomainMemory(service: LocalAgentService, domain: String, appPkg: String, action: String) {
        try {
            service.knowledgeLedger.addKnowledge(
                id = java.util.UUID.randomUUID().toString(),
                query = "Domain: $domain",
                targetApp = appPkg,
                answer = action
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
