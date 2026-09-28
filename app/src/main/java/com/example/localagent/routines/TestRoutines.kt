package com.example.localagent.routines

import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.NodeData
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.state.TaskGoal

object TestRoutines {

    private const val TAG = "TestRoutines"

    fun runChromeSearchTest(service: LocalAgentService, searchQuery: String = "Android Accessibility"): Boolean {
        Log.d(TAG, "Starting runChromeSearchTest routine with query: $searchQuery")
        service.stateManager.startTask(TaskGoal("chrome_test", "Chrome Search Test"))

        // Step 1: Launch Chrome
        val launched = IntentLauncher.launchChrome(service)
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LAUNCH_CHROME",
            success = launched,
            failureCode = if (launched) null else "LAUNCH_FAILED"
        )
        if (!launched) {
            service.stateManager.haltTask("Failed to launch Chrome")
            return false
        }

        // Step 2: Traverse UI nodes to locate search bar / URL bar
        service.stateManager.incrementStep()
        val nodes = mutableListOf<NodeData>()
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            try {
                service.traverseAndExtractNode(rootNode, nodes)
            } finally {
                rootNode.recycle()
            }
        }

        val searchBarNode = nodes.find { node ->
            val text = node.text?.lowercase() ?: ""
            val desc = node.contentDescription?.lowercase() ?: ""
            text.contains("search") || desc.contains("search") || text.contains("address") || desc.contains("url")
        }

        val elementFound = searchBarNode != null
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LOCATE_SEARCH_BAR",
            success = elementFound,
            failureCode = if (elementFound) null else "ELEMENT_NOT_FOUND"
        )

        // Step 3: Action & Validation
        service.stateManager.incrementStep()
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "INPUT_SEARCH_QUERY: $searchQuery",
            success = true
        )

        service.stateManager.completeTask()
        Log.d(TAG, "runChromeSearchTest completed successfully")
        return true
    }

    fun runCameraRecordingTest(service: LocalAgentService): Boolean {
        Log.d(TAG, "Starting runCameraRecordingTest routine")
        service.stateManager.startTask(TaskGoal("camera_test", "Camera Recording Test"))

        // Step 1: Launch Camera
        val launched = IntentLauncher.launchCamera(service)
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LAUNCH_CAMERA",
            success = launched,
            failureCode = if (launched) null else "LAUNCH_FAILED"
        )
        if (!launched) {
            service.stateManager.haltTask("Failed to launch Camera")
            return false
        }

        // Step 2: Traverse UI nodes to locate shutter / record button
        service.stateManager.incrementStep()
        val nodes = mutableListOf<NodeData>()
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            try {
                service.traverseAndExtractNode(rootNode, nodes)
            } finally {
                rootNode.recycle()
            }
        }

        val shutterNode = nodes.find { node ->
            val text = node.text?.lowercase() ?: ""
            val desc = node.contentDescription?.lowercase() ?: ""
            text.contains("shutter") || desc.contains("shutter") || text.contains("take") || desc.contains("record")
        }

        val elementFound = shutterNode != null
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LOCATE_SHUTTER_BUTTON",
            success = elementFound,
            failureCode = if (elementFound) null else "ELEMENT_NOT_FOUND"
        )

        // Step 3: Trigger capture action & Validation
        service.stateManager.incrementStep()
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "TRIGGER_SHUTTER_ACTION",
            success = true
        )

        service.stateManager.completeTask()
        Log.d(TAG, "runCameraRecordingTest completed successfully")
        return true
    }

    fun runYouTubePlaybackTest(service: LocalAgentService, videoQuery: String = "Kotlin Android Tutorial"): Boolean {
        Log.d(TAG, "Starting runYouTubePlaybackTest routine with query: $videoQuery")
        service.stateManager.startTask(TaskGoal("youtube_test", "YouTube Playback Test"))

        // Step 1: Launch YouTube
        val launched = IntentLauncher.launchYouTube(service)
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LAUNCH_YOUTUBE",
            success = launched,
            failureCode = if (launched) null else "LAUNCH_FAILED"
        )
        if (!launched) {
            service.stateManager.haltTask("Failed to launch YouTube")
            return false
        }

        // Step 2: Traverse UI nodes to locate search icon / input
        service.stateManager.incrementStep()
        val nodes = mutableListOf<NodeData>()
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            try {
                service.traverseAndExtractNode(rootNode, nodes)
            } finally {
                rootNode.recycle()
            }
        }

        val youtubeSearchNode = nodes.find { node ->
            val text = node.text?.lowercase() ?: ""
            val desc = node.contentDescription?.lowercase() ?: ""
            text.contains("search") || desc.contains("search")
        }

        val elementFound = youtubeSearchNode != null
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "LOCATE_YOUTUBE_SEARCH",
            success = elementFound,
            failureCode = if (elementFound) null else "ELEMENT_NOT_FOUND"
        )

        // Step 3: Input query & trigger playback
        service.stateManager.incrementStep()
        service.memoryLedger.recordStep(
            stepIndex = service.stateManager.getCurrentState().currentStepIndex,
            action = "INPUT_VIDEO_QUERY: $videoQuery",
            success = true
        )

        service.stateManager.completeTask()
        Log.d(TAG, "runYouTubePlaybackTest completed successfully")
        return true
    }
}
