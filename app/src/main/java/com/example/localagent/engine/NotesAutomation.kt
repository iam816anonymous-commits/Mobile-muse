package com.example.localagent.engine

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.localagent.LocalAgentService

object NotesAutomation {

    private const val TAG = "NotesAutomation"

    fun createNote(service: LocalAgentService, rawGoal: String) {
        service.isProcessingGoal = true
        val noteText = rawGoal.lowercase()
            .removePrefix("note down ")
            .removePrefix("take note ")
            .removePrefix("note ")
            .removePrefix("create note ")
            .trim()

        Log.i(TAG, "Creating note with payload: '$noteText'")
        service.broadcastTelemetryLog("NOTES", "Note Payload: '$noteText'")

        try {
            // Tier 1: Intent.ACTION_SEND Fast-Path
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noteText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val notesPkg = AppIndexer.resolveAppByQuery(service, "note")
            if (notesPkg != null) {
                sendIntent.`package` = notesPkg
            }

            try {
                service.sendBroadcast(Intent("com.localagent.MINIMIZE_UI"))
                service.startActivity(sendIntent)
                service.broadcastTelemetryLog("NOTES", "Dispatched Intent.ACTION_SEND to $notesPkg")
                service.voiceSynthesizer?.speak("Note saved.")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Direct ACTION_SEND to notes package failed. Falling back to UI note creation...", e)
            }

            // Tier 2: Open Notes App & UI Automation
            val launched = if (notesPkg != null) AppLauncher.launchApp(service, notesPkg) else false
            if (launched) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val root = service.getActiveWindowRoot()
                    if (root != null) {
                        try {
                            // Tap add/new/create button
                            GenericUIOperator.findAndClickByKeywords(root, listOf("new", "add", "+", "create"), service)

                            // Inject text into editor
                            Handler(Looper.getMainLooper()).postDelayed({
                                val editRoot = service.getActiveWindowRoot()
                                if (editRoot != null) {
                                    try {
                                        GenericUIOperator.findInputAndType(editRoot, noteText)
                                        GenericUIOperator.confirmAction(editRoot, listOf("save", "done", "check", "v"), service)
                                        service.broadcastTelemetryLog("NOTES", "UI Note creation completed")
                                        service.voiceSynthesizer?.speak("Note created successfully.")
                                    } finally {
                                        editRoot.recycle()
                                    }
                                }
                            }, 800L)

                        } finally {
                            root.recycle()
                        }
                    }
                }, 1000L)
            }

        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                service.isProcessingGoal = false
            }, 3000L)
        }
    }
}
