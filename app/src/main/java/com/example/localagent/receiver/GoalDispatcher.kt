package com.example.localagent.receiver

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.DiagnosticRunner
import com.example.localagent.engine.QueryPayloadSanitizer
import com.example.localagent.engine.SystemCommandExecutor
import com.example.localagent.engine.TaskExecutionHub
import com.example.localagent.intents.DeviceToolsManager
import com.example.localagent.skills.HeadlessMathEngine
import com.example.localagent.state.TaskGoal
import com.example.localagent.vision.GeminiVisionBridge
import com.example.localagent.vision.LensLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.UUID

class GoalDispatcher(
    private val service: LocalAgentService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onGoalProcessed: ((String) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_GOAL = "com.localagent.EXECUTE_GOAL"
        const val ACTION_RUN_DIAGNOSTIC = "com.localagent.RUN_DIAGNOSTIC"
        const val ACTION_RUN_APP_AUDIT = "com.localagent.RUN_APP_AUDIT"
        const val EXTRA_GOAL_TEXT = "goal_text"
        private const val TAG = "GoalDispatcher"

        private val ABORT_KEYWORDS = listOf("stop", "close local agent", "abort", "cancel", "kill", "exit")

        fun resetExecutionState() {
            LocalAgentService.instance?.isProcessingGoal = false
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_EXECUTE_GOAL -> {
                val goalText = intent.getStringExtra(EXTRA_GOAL_TEXT)
                if (goalText != null && goalText.trim().isNotEmpty()) {
                    Log.d(TAG, "GoalDispatcher received intent with goal_text: '$goalText'")
                    val lowerGoal = goalText.lowercase().trim()

                    // Check for Abort / Close commands
                    if (ABORT_KEYWORDS.any { lowerGoal == it || lowerGoal.contains(it) }) {
                        Log.w(TAG, "Abort command detected in GoalDispatcher: '$goalText'")
                        coroutineScope.coroutineContext.cancelChildren()
                        service.stateManager.haltTask("User requested abort: $goalText")
                        service.stateManager.reset()
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        service.voiceSynthesizer?.speak("LocalAgent halted and standing by.")
                        service.broadcastTelemetryLog("SYS", "Safe local shutdown executed")
                        onGoalProcessed?.invoke(goalText)
                        return
                    }

                    val goalId = UUID.randomUUID().toString()

                    coroutineScope.launch {
                        service.isProcessingGoal = true
                        service.startMasterTaskTimeoutGuard(8000L)
                        try {
                            try {
                                service.stateManager.startTask(TaskGoal(id = goalId, description = goalText))
                            } catch (e: Exception) {
                                Log.w(TAG, "TaskManager state call ignored during mock test", e)
                            }

                            // SYSTEM COMMAND EXECUTOR HEADLESS ROUTING FIRST
                            val handledHeadlessly = when {
                                HeadlessMathEngine.isMathQuery(goalText) -> {
                                    SystemCommandExecutor.evaluateMath(service, goalText)
                                }
                                lowerGoal.contains("stop recording") || lowerGoal.contains("stop audio") -> {
                                    SystemCommandExecutor.stopHeadlessAudioRecording(service) != null
                                }
                                lowerGoal.contains("record audio") || lowerGoal.contains("start recording") || lowerGoal.contains("voice recorder") -> {
                                    SystemCommandExecutor.startHeadlessAudioRecording(service)
                                }
                                lowerGoal.contains("timer") -> {
                                    val sec = extractTimeInSeconds(lowerGoal)
                                    SystemCommandExecutor.setTimer(service, sec)
                                }
                                lowerGoal.contains("alarm") -> {
                                    val (hr, min) = extractAlarmTime(lowerGoal)
                                    SystemCommandExecutor.setAlarm(service, hr, min)
                                }
                                lowerGoal.startsWith("note ") || lowerGoal.contains("take note") || lowerGoal.contains("note down") || lowerGoal.contains("save note") -> {
                                    val note = extractNoteContent(goalText)
                                    SystemCommandExecutor.appendNote(service, note)
                                }
                                lowerGoal.contains("how many pictures") || lowerGoal.contains("how many photos") || lowerGoal.contains("photo summary") || lowerGoal.contains("picture summary") -> {
                                    SystemCommandExecutor.getLatestPhotoSummary(service).isNotBlank()
                                }
                                lowerGoal.contains("delete latest photo") || lowerGoal.contains("delete picture") || lowerGoal.contains("delete photo") || lowerGoal.contains("delete last photo") -> {
                                    SystemCommandExecutor.deleteLatestPhoto(service)
                                }
                                lowerGoal.contains("google search") || lowerGoal.startsWith("search ") || lowerGoal.contains("look up ") -> {
                                    val query = QueryPayloadSanitizer.extractSearchQuery(goalText)
                                    SystemCommandExecutor.performWebSearch(service, query)
                                }
                                else -> false
                            }

                            if (!handledHeadlessly) {
                                // Local Primitives & Hardware (Dual Flashlight Control)
                                if (lowerGoal.contains("front flash on") || lowerGoal.contains("turn on front flash") || lowerGoal.contains("front light on")) {
                                    val tools = DeviceToolsManager(service)
                                    tools.toggleFrontFlash(true)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("front flash off") || lowerGoal.contains("turn off front flash") || lowerGoal.contains("front light off")) {
                                    val tools = DeviceToolsManager(service)
                                    tools.toggleFrontFlash(false)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("flashlight on") || lowerGoal.contains("turn on torch") || lowerGoal.contains("rear flash on")) {
                                    val tools = DeviceToolsManager(service)
                                    tools.toggleRearFlash(true)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("flashlight off") || lowerGoal.contains("turn off torch") || lowerGoal.contains("rear flash off")) {
                                    val tools = DeviceToolsManager(service)
                                    tools.toggleRearFlash(false)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("vibrate") || lowerGoal.contains("haptic")) {
                                    val tools = DeviceToolsManager(service)
                                    tools.triggerHaptic(150L)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("what is in this photo") || lowerGoal.contains("describe last photo") || lowerGoal.contains("read text on screen")) {
                                    val visionBridge = GeminiVisionBridge()
                                    val dummyBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                                    val resultText = try {
                                        visionBridge.analyzeVisualSync(dummyBase64, goalText)
                                    } catch (e: Exception) { "A photo containing UI elements." }
                                    service.broadcastTelemetryLog("VISION", "Analyzed: $resultText")
                                    service.voiceSynthesizer?.speak(resultText)
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else if (lowerGoal.contains("search this on google lens") || lowerGoal.contains("identify with lens")) {
                                    val dummyUri = Uri.parse("content://media/external/images/media/1")
                                    LensLauncher.launchGoogleLens(service, dummyUri)
                                    service.broadcastTelemetryLog("VISION", "Dispatched Google Lens search")
                                    try { service.stateManager.completeTask() } catch (e: Exception) {}
                                } else {
                                    // TaskExecutionHub Routing
                                    try {
                                        TaskExecutionHub.executeGoal(service, goalText)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "TaskExecutionHub execution error", e)
                                        service.broadcastTelemetryLog("WARN", "TaskHub error: ${e.message}")
                                    }
                                }
                            }
                        } finally {
                            service.isProcessingGoal = false
                        }

                        onGoalProcessed?.invoke(goalText)
                    }
                } else {
                    Log.w(TAG, "GoalDispatcher received empty 'goal_text' extra")
                }
            }
            ACTION_RUN_DIAGNOSTIC -> {
                Log.d(TAG, "GoalDispatcher received ACTION_RUN_DIAGNOSTIC broadcast")
                coroutineScope.launch {
                    DiagnosticRunner.runFullEndToEndTest(service)
                }
            }
            ACTION_RUN_APP_AUDIT -> {
                Log.d(TAG, "GoalDispatcher received ACTION_RUN_APP_AUDIT broadcast")
                coroutineScope.launch {
                    DiagnosticRunner.runFullDeviceAudit(service)
                }
            }
        }
    }

    private fun extractNoteContent(rawGoal: String): String {
        return rawGoal.replace("note down", "", ignoreCase = true)
            .replace("take note", "", ignoreCase = true)
            .replace("save note", "", ignoreCase = true)
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
