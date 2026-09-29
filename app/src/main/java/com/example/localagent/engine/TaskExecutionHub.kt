package com.example.localagent.engine

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.intents.HeadlessActionEngine
import com.example.localagent.skills.CalculatorSkill
import com.example.localagent.skills.CameraSkill
import com.example.localagent.skills.HeadlessMathEngine
import com.example.localagent.skills.HeadlessMediaEngine

object TaskExecutionHub {

    private const val TAG = "TaskExecutionHub"

    /**
     * Hybrid Fallback Hierarchy:
     * 1. Headless Engine (Math in memory, AudioRecorder, Direct Intent / Deep Link)
     * 2. Accessibility Node Crawling (Only when app lacks an intent contract)
     * 3. Physical Spatial Matrix (Final fallback for unlabelled interfaces)
     */
    fun executeGoal(service: LocalAgentService, rawGoal: String): Boolean {
        val lowerGoal = rawGoal.lowercase().trim()
        Log.i(TAG, "TaskExecutionHub routing goal in strict Hybrid Fallback Hierarchy: '$rawGoal'")
        service.broadcastTelemetryLog("HUB", "Routing goal via Hybrid Fallback Hierarchy: '$rawGoal'")

        // --- LAYER 1: HEADLESS ZERO-UI ENGINE ---

        // Layer 1A: Headless Math Engine (In-Memory Evaluation)
        if (HeadlessMathEngine.isMathQuery(rawGoal)) {
            service.broadcastTelemetryLog("HUB", "Layer 1 [Headless]: Routing to HeadlessMathEngine")
            if (HeadlessMathEngine.processMathGoal(service, rawGoal, service.voiceSynthesizer)) {
                return true
            }
        }

        // Layer 1B: Headless Media Engine (Background Audio Recording)
        if (lowerGoal.contains("record audio") || lowerGoal.contains("start recording") || lowerGoal.contains("voice recorder")) {
            service.broadcastTelemetryLog("HUB", "Layer 1 [Headless]: Routing to HeadlessMediaEngine")
            if (HeadlessMediaEngine.processRecordAudioGoal(service)) {
                return true
            }
        }

        // Layer 1C: Direct Deep Link & Intent Contract Router
        if (HeadlessActionEngine.tryRouteHeadlessAction(service, rawGoal)) {
            service.broadcastTelemetryLog("HUB", "Layer 1 [Headless]: Handled via HeadlessActionEngine intent contract")
            return true
        }

        // --- LAYER 2: ACCESSIBILITY NODE CRAWLING & SPECIALIZED SKILLS ---

        // Specialized Skill 1: Camera Hardware Capture (Front & Rear)
        if (lowerGoal.contains("take selfie") || lowerGoal.contains("click front photo") || lowerGoal.contains("take front picture")) {
            service.broadcastTelemetryLog("HUB", "Layer 2 [Skill]: Routing to CameraSkill (Front Camera)")
            CameraSkill.capturePhoto(service, isFront = true)
            return true
        } else if (lowerGoal.contains("take photo") || lowerGoal.contains("click photo") || lowerGoal.contains("take picture") || lowerGoal.contains("capture photo")) {
            service.broadcastTelemetryLog("HUB", "Layer 2 [Skill]: Routing to CameraSkill (Rear Camera)")
            val isFront = lowerGoal.contains("front") || lowerGoal.contains("selfie")
            CameraSkill.capturePhoto(service, isFront = isFront)
            return true
        }

        // Specialized Skill 2: Photo Viewer Automation
        if (lowerGoal.contains("my picture") || lowerGoal.contains("gallery") || lowerGoal.contains("photo") || lowerGoal.contains("picture") || lowerGoal.contains("show pictures")) {
            service.broadcastTelemetryLog("HUB", "Layer 2 [Skill]: Routing to PhotoViewerAutomation")
            PhotoViewerAutomation.processPhotoGoal(service, rawGoal)
            return true
        }

        // Specialized Skill 3: Notes Automation Fallback
        if (lowerGoal.startsWith("note ") || lowerGoal.contains("take note") || lowerGoal.contains("note down")) {
            service.broadcastTelemetryLog("HUB", "Layer 2 [Skill]: Routing to NotesAutomation fallback")
            NotesAutomation.createNote(service, rawGoal)
            return true
        }

        // Specialized Skill 4: Calculator Skill Fallback (if in-memory math failed)
        if (lowerGoal.contains("calculate") || lowerGoal.contains("compute")) {
            service.broadcastTelemetryLog("HUB", "Layer 2 [Skill]: Routing to CalculatorSkill fallback")
            CalculatorSkill.evaluateMath(service, rawGoal)
            return true
        }

        // --- LAYER 3 & LAYER 4 & LAYER 5: 5-LAYER UNIVERSAL APP OPERATOR & SPATIAL MATRIX ---
        service.broadcastTelemetryLog("HUB", "Layer 3-5 [Fallback]: Routing to UniversalAppOperator pipeline & Spatial Grid")
        return UniversalAppOperator.executeTaskPipeline(service, rawGoal)
    }
}
