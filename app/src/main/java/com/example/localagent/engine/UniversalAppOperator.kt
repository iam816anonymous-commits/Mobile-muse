package com.example.localagent.engine

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.intents.IntentContractBridge

object UniversalAppOperator {

    private const val TAG = "UniversalAppOperator"

    fun executeTaskPipeline(service: LocalAgentService, goalText: String): Boolean {
        Log.i(TAG, "Executing 5-layer Universal Task Pipeline for goal: '$goalText'")
        service.broadcastTelemetryLog("PIPELINE", "Starting 5-layer execution pipeline for: '$goalText'")

        // Layer 1: Intent Contract Fast-Path
        val tier1Success = IntentContractBridge.tryIntentFastPath(service, goalText)
        if (tier1Success) {
            Log.i(TAG, "Layer 1 Intent Contract Fast-Path succeeded")
            service.broadcastTelemetryLog("PIPELINE", "Layer 1: Intent Contract Fast-Path succeeded")
            service.stateManager.completeTask()
            return true
        }

        // Layer 2: App Resolution & Launch
        val resolvedPackage = AppIndexer.resolveAppByQuery(service, goalText)
        if (resolvedPackage != null) {
            val launched = AppLauncher.launchApp(service, resolvedPackage)
            if (launched) {
                service.broadcastTelemetryLog("PIPELINE", "Layer 2: App resolved & launched package '$resolvedPackage'")
                try { Thread.sleep(800L) } catch (e: Exception) {}
            }
        }

        // Layer 3: Universal Node Crawl & 3-Level Tap / Inject
        val rootNode = service.getActiveWindowRoot()
        if (rootNode != null) {
            try {
                val localHandled = LocalHeuristicEngine.processLocalHeuristics(service, goalText, rootNode)
                if (localHandled) {
                    Log.i(TAG, "Layer 3 Universal Node Crawl & Local Heuristics succeeded")
                    service.broadcastTelemetryLog("PIPELINE", "Layer 3: Universal Node Crawl & Local Heuristic succeeded")
                    return true
                }
            } finally {
                rootNode.recycle()
            }
        }

        // Layer 4: Universal Scroll & Reveal Fallback
        val rootForScroll = service.getActiveWindowRoot()
        if (rootForScroll != null) {
            try {
                val scrolled = UniversalScrollEngine.scrollAndReveal(service, rootForScroll)
                if (scrolled) {
                    service.broadcastTelemetryLog("PIPELINE", "Layer 4: Scroll & Reveal executed. Re-crawling...")
                    val postScrollRoot = service.getActiveWindowRoot()
                    if (postScrollRoot != null) {
                        try {
                            val postScrollHandled = LocalHeuristicEngine.processLocalHeuristics(service, goalText, postScrollRoot)
                            if (postScrollHandled) {
                                return true
                            }
                        } finally {
                            postScrollRoot.recycle()
                        }
                    }
                }
            } finally {
                rootForScroll.recycle()
            }
        }

        // Layer 5: Generic Spatial Grid Anchor Tap
        Log.w(TAG, "Layers 1-4 unmapped. Triggering Layer 5 Spatial Grid Fallback...")
        service.broadcastTelemetryLog("PIPELINE", "Layer 5: Layers 1-4 unmapped. Triggering Spatial Grid Fallback")
        val gridDispatched = SpatialGrid.dispatchIntentAnchor(service, goalText)
        return gridDispatched
    }
}
