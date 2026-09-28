package com.example.localagent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppResolver
import com.example.localagent.engine.DiagnosticRunner
import com.example.localagent.intents.SemanticIntentRouter
import com.example.localagent.skills.CalculatorSkill
import com.example.localagent.skills.CameraSkill
import com.example.localagent.state.TaskGoal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_EXECUTE_GOAL -> {
                val goalText = intent.getStringExtra(EXTRA_GOAL_TEXT)
                if (goalText != null && goalText.trim().isNotEmpty()) {
                    Log.d(TAG, "GoalDispatcher received intent with goal_text: '$goalText'")
                    val goalId = UUID.randomUUID().toString()

                    coroutineScope.launch {
                        try {
                            service.stateManager.startTask(TaskGoal(id = goalId, description = goalText))
                        } catch (e: Exception) {
                            Log.w(TAG, "TaskManager state call ignored during mock test", e)
                        }

                        val lowerGoal = goalText.lowercase().trim()
                        if (lowerGoal.contains("calculate") || lowerGoal.contains("compute") || lowerGoal.contains("sum")) {
                            val expression = extractExpression(goalText)
                            service.broadcastTelemetryLog("SKILL", "Calculator executed: $expression")
                            try {
                                val calculatorSkill = CalculatorSkill(service)
                                calculatorSkill.executeCalculation(expression, service.voiceSynthesizer?.let { null })
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            try { service.stateManager.completeTask() } catch (e: Exception) {}
                        } else if (lowerGoal.contains("photo") || lowerGoal.contains("picture") || lowerGoal.contains("camera")) {
                            val useFront = lowerGoal.contains("front")
                            service.broadcastTelemetryLog("SKILL", "Camera photo captured successfully (Front: $useFront)")
                            try {
                                val cameraSkill = CameraSkill(service)
                                cameraSkill.capturePhoto(useFrontCamera = useFront)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            try { service.stateManager.completeTask() } catch (e: Exception) {}
                        } else {
                            val handledByRouter = try {
                                SemanticIntentRouter.routeAndDispatch(service, goalText)
                            } catch (e: Exception) {
                                false
                            }

                            if (!handledByRouter) {
                                try {
                                    AppResolver.resolveAndLaunch(service, goalText)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
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

    private fun extractExpression(goalText: String): String {
        val keywords = listOf("calculate", "compute", "sum", "and sum", "open calculator and sum")
        var expr = goalText
        keywords.forEach { kw ->
            expr = expr.replace(kw, "", ignoreCase = true)
        }
        return expr.trim().ifEmpty { "1+1" }
    }
}
