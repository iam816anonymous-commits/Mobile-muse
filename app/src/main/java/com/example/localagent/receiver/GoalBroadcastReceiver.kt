package com.example.localagent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.localagent.LocalAgentService
import com.example.localagent.state.TaskGoal
import java.util.UUID

class GoalBroadcastReceiver(
    private val service: LocalAgentService,
    private val onGoalReceived: ((String) -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_GOAL = "com.localagent.EXECUTE_GOAL"
        const val EXTRA_GOAL_TEXT = "goal_text"
        private const val TAG = "GoalBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_EXECUTE_GOAL) {
            val goalText = intent.getStringExtra(EXTRA_GOAL_TEXT)
            if (goalText != null && goalText.trim().isNotEmpty()) {
                Log.d(TAG, "Goal broadcast received: '$goalText'")
                val goalId = UUID.randomUUID().toString()
                service.stateManager.startTask(TaskGoal(id = goalId, description = goalText))
                onGoalReceived?.invoke(goalText)
            } else {
                Log.w(TAG, "Goal broadcast received with empty 'goal_text' extra")
            }
        }
    }
}
