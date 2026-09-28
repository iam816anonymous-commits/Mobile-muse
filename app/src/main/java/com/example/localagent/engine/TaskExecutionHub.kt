package com.example.localagent.engine

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.skills.CalculatorSkill
import com.example.localagent.skills.CameraSkill

object TaskExecutionHub {

    private const val TAG = "TaskExecutionHub"

    fun executeGoal(service: LocalAgentService, rawGoal: String): Boolean {
        val lowerGoal = rawGoal.lowercase().trim()
        Log.i(TAG, "TaskExecutionHub routing goal: '$rawGoal'")
        service.broadcastTelemetryLog("HUB", "Routing goal: '$rawGoal'")

        // 1. Math / Calculation
        if (lowerGoal.contains(Regex("(?i)(calculate|compute|\\d+\\s*[*+\\-/x^%]\\s*\\d+)"))) {
            service.broadcastTelemetryLog("HUB", "Routed to CalculatorSkill")
            val calcSkill = CalculatorSkill(service)
            calcSkill.executeCalculation(rawGoal, service.voiceSynthesizer)
            return true
        }

        // 2. YouTube / Video Playback
        if (lowerGoal.contains("play ") && (lowerGoal.contains("youtube") || lowerGoal.contains("video"))) {
            service.broadcastTelemetryLog("HUB", "Routed to MediaAutomation (YouTube)")
            MediaAutomation.playYouTubeVideo(service, rawGoal)
            return true
        }

        // 3. Web Search & AI Web Chats
        if (lowerGoal.contains("chrome") || lowerGoal.contains("google search") ||
            lowerGoal.startsWith("search ") || lowerGoal.contains("search for ") || lowerGoal.startsWith("look up ")
        ) {
            service.broadcastTelemetryLog("HUB", "Routed to BrowserAutomation (Web Search)")
            BrowserAutomation.executeSearch(service, rawGoal)
            return true
        }

        // 4. Notes / Notebook
        if (lowerGoal.startsWith("note ") || lowerGoal.contains("take note") || lowerGoal.contains("note down")) {
            service.broadcastTelemetryLog("HUB", "Routed to NotesAutomation")
            NotesAutomation.createNote(service, rawGoal)
            return true
        }

        // 5. Voice Recorder
        if (lowerGoal.contains("record audio") || lowerGoal.contains("start recording") || lowerGoal.contains("voice recorder")) {
            service.broadcastTelemetryLog("HUB", "Routed to RecorderAutomation")
            RecorderAutomation.recordAudio(service)
            return true
        }

        // 6. Camera Photo Capture
        if (lowerGoal.contains("take photo") || lowerGoal.contains("take picture") || lowerGoal.contains("capture photo") || lowerGoal.contains("take selfie")) {
            service.broadcastTelemetryLog("HUB", "Routed to CameraSkill")
            val cameraSkill = CameraSkill(service)
            val isFront = lowerGoal.contains("selfie") || lowerGoal.contains("front")
            cameraSkill.capturePhoto(useFrontCamera = isFront)
            return true
        }

        // 7. Gallery Browsing & Deletion
        if (lowerGoal.contains("gallery") || lowerGoal.contains("photo") || lowerGoal.contains("picture") && (lowerGoal.contains("next") || lowerGoal.contains("delete") || lowerGoal.contains("swipe"))) {
            service.broadcastTelemetryLog("HUB", "Routed to GalleryAutomation")
            GalleryAutomation.processGalleryAction(service, rawGoal)
            return true
        }

        // Fallback: 5-Layer Universal App Operator
        service.broadcastTelemetryLog("HUB", "Routing to UniversalAppOperator pipeline")
        return UniversalAppOperator.executeTaskPipeline(service, rawGoal)
    }
}
