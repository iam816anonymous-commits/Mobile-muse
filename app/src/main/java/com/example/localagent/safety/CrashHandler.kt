package com.example.localagent.safety

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.memory.StorageManager
import java.io.File

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Uncaught exception intercepted on thread ${thread.name}", throwable)
        try {
            val storageDir = StorageManager.getStorageDirectory()
            val crashFile = File(storageDir, "crash_dump.log")
            crashFile.writeText(
                "Timestamp: ${System.currentTimeMillis()}\n" +
                        "Thread: ${thread.name}\n" +
                        "Stack: ${Log.getStackTraceString(throwable)}\n"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash dump log", e)
        }

        // Reset execution locks
        try {
            AutonomousEngine.resetLocks()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting locks during crash handling", e)
        }

        // Clean restart of MainActivity
        try {
            val restartIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (restartIntent != null) {
                context.startActivity(restartIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting MainActivity during crash handling", e)
        }

        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
