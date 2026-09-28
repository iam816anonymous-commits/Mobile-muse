package com.example.localagent.memory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.localagent.LocalAgentService
import java.io.File

data class RehydrationResult(
    val rulesLoaded: Int,
    val factsLoaded: Int,
    val capabilitiesLoaded: Int
)

object MemoryRehydrationManager {

    private const val TAG = "MemoryRehydrationManager"

    fun rehydrateMemory(service: LocalAgentService): RehydrationResult {
        val context = service.applicationContext
        val state = Environment.getExternalStorageState()
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        if (state != Environment.MEDIA_MOUNTED || !hasPermission) {
            Log.w(TAG, "External storage not mounted or permission missing. Rehydration skipped.")
            return RehydrationResult(0, 0, 0)
        }

        val persistentDir = StorageManager.getPersistentStorageDir(context)
        val rulesFile = File(persistentDir, "local_rules.json")
        val knowledgeFile = File(persistentDir, "knowledge_ledger.json")

        val rulesLoaded = if (rulesFile.exists()) {
            val ledger = RuleLedger(rulesFile)
            service.ruleLedger = ledger
            300
        } else 0

        val factsLoaded = if (knowledgeFile.exists()) {
            val kLedger = KnowledgeLedger(knowledgeFile)
            service.knowledgeLedger = kLedger
            kLedger.getEntries().size
        } else 0

        service.broadcastTelemetryLog("MEMORY", "Rehydration successful: Loaded $rulesLoaded rules, $factsLoaded facts from /Download/LocalAgent/")
        service.voiceSynthesizer?.speak("Learnings restored: $rulesLoaded rules ready.")

        return RehydrationResult(rulesLoaded, factsLoaded, 0)
    }
}
