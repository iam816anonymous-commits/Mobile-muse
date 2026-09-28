package com.example.localagent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppResolver
import com.example.localagent.engine.DiagnosticRunner
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
                        service.stateManager.startTask(TaskGoal(id = goalId, description = goalText))
                        AppResolver.resolveAndLaunch(service, goalText)
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
}
