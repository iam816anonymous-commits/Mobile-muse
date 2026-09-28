package com.example.localagent.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class KillSwitchReceiver(
    private val onKillTriggered: (() -> Unit)? = null
) : BroadcastReceiver() {

    companion object {
        const val ACTION_KILL_SWITCH = "com.example.localagent.ACTION_KILL_SWITCH"
        private const val TAG = "KillSwitchReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_KILL_SWITCH) {
            Log.w(TAG, "Kill Switch broadcast received! Halting agent execution immediately.")
            onKillTriggered?.invoke()
        }
    }
}
