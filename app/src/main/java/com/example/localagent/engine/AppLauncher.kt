package com.example.localagent.engine

import android.content.Context
import android.content.Intent
import android.util.Log

object AppLauncher {

    private const val TAG = "AppLauncher"

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            } ?: run {
                Log.w(TAG, "No launch intent available for package: $packageName")
                return false
            }

            // Broadcast to MainActivity to minimize itself so the new app takes the foreground
            val minimizeIntent = Intent("com.localagent.MINIMIZE_UI")
            context.sendBroadcast(minimizeIntent)

            context.startActivity(launchIntent)

            Log.i(TAG, "Successfully launched package in foreground: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app $packageName", e)
            false
        }
    }
}
