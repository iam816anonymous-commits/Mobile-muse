package com.example.localagent.engine

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService
import java.net.URLEncoder

object MediaAutomation {

    private const val TAG = "MediaAutomation"

    fun playYouTubeVideo(service: LocalAgentService, rawGoal: String) {
        service.isProcessingGoal = true
        val cleanQuery = rawGoal.lowercase()
            .replace("play ", "")
            .replace("on youtube", "")
            .replace("youtube", "")
            .trim()

        Log.i(TAG, "Playing YouTube video query: '$cleanQuery'")
        service.broadcastTelemetryLog("MEDIA", "YouTube Query: '$cleanQuery'")

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val uriStr = "https://www.youtube.com/results?search_query=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                `package` = "com.google.android.youtube"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            service.sendBroadcast(Intent("com.localagent.MINIMIZE_UI"))
            try {
                service.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "YouTube package launch failed, opening via browser fallback", e)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                service.startActivity(browserIntent)
            }

            // Wait 2500ms for layout load, scan for first video thumbnail/title node, and click
            Handler(Looper.getMainLooper()).postDelayed({
                val root = service.getActiveWindowRoot()
                if (root != null) {
                    try {
                        val videoClicked = GenericUIOperator.findAndClickByKeywords(
                            root,
                            listOf(cleanQuery, "video", "play", "thumbnail", "views"),
                            service
                        )
                        service.broadcastTelemetryLog("MEDIA", "YouTube first video click result: $videoClicked")
                        service.voiceSynthesizer?.speak("Playing $cleanQuery on YouTube.")
                    } finally {
                        root.recycle()
                    }
                }
            }, 2500L)

        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 3000L)
        }
    }
}
