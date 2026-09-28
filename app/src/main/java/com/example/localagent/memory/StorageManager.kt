package com.example.localagent.memory

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object StorageManager {

    private const val TAG = "StorageManager"

    fun getStorageDirectory(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val agentDir = File(downloadDir, "LocalAgent")
        if (!agentDir.exists()) {
            agentDir.mkdirs()
        }
        return agentDir
    }

    fun getPersistentStorageDir(context: Context): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val agentDir = File(downloadDir, "LocalAgent")
        if (!agentDir.exists()) {
            agentDir.mkdirs()
        }

        // Automatic Migration from context.filesDir to /Download/LocalAgent/
        migrateLegacyFiles(context, agentDir)

        return agentDir
    }

    private fun migrateLegacyFiles(context: Context, targetDir: File) {
        val legacyFiles = listOf(
            "local_rules.json",
            "knowledge_ledger.json",
            "app_capabilities.json",
            "reflection_ledger.json",
            "memory_ledger.json"
        )

        legacyFiles.forEach { fileName ->
            val legacyFile = File(context.filesDir, fileName)
            val targetFile = File(targetDir, fileName)

            if (legacyFile.exists() && !targetFile.exists()) {
                try {
                    legacyFile.copyTo(targetFile, overwrite = true)
                    Log.d(TAG, "[STORAGE] Migrated legacy memory $fileName to Downloads")
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating $fileName to Downloads", e)
                }
            }
        }
    }
}
