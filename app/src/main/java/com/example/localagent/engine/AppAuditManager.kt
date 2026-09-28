package com.example.localagent.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.StorageManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object AppAuditManager {

    private const val TAG = "AppAuditManager"

    suspend fun runFullAppAudit(service: LocalAgentService) {
        service.broadcastTelemetryLog("AUDIT", "========================================")
        service.broadcastTelemetryLog("AUDIT", "STARTING FULL APP AUDIT LOOP")
        service.broadcastTelemetryLog("AUDIT", "========================================")

        val pm = service.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val totalApps = resolveInfos.size

        service.broadcastTelemetryLog("AUDIT", "Discovered $totalApps launcher applications.")

        val capabilitiesArray = JSONArray()

        for ((index, resolveInfo) in resolveInfos.withIndex()) {
            val pkgName = resolveInfo.activityInfo.packageName
            val appLabel = resolveInfo.loadLabel(pm).toString()
            val stepNumber = index + 1

            service.broadcastTelemetryLog("AUDIT", "[AUDIT] Auditing app $stepNumber/$totalApps: $appLabel (Waiting 3.5s)")
            Log.d(TAG, "Auditing app $stepNumber/$totalApps: $appLabel ($pkgName)")

            val launchIntent = pm.getLaunchIntentForPackage(pkgName)
            if (launchIntent == null) {
                service.broadcastTelemetryLog("AUDIT", " -> Launch: FAILED (No launch intent for $pkgName)")
                continue
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                service.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app $pkgName", e)
                service.broadcastTelemetryLog("AUDIT", " -> Launch: FAILED (${e.message})")
                continue
            }

            // Step B: Wait 3500ms to allow layout to fully render on 4GB RAM hardware
            delay(3500)

            // Step C: Inspect service.rootInActiveWindow to classify capability domain
            val rootNode = service.getActiveWindowRoot()
            val buttonLabels = mutableListOf<String>()
            if (rootNode != null) {
                try {
                    collectButtonLabels(rootNode, buttonLabels)
                } finally {
                    rootNode.recycle()
                }
            }

            val domain = classifyDomain(appLabel, pkgName, buttonLabels)

            val appJsonObject = JSONObject().apply {
                put("app_name", appLabel)
                put("package_name", pkgName)
                put("capability_domain", domain)
                put("sample_labels", JSONArray(buttonLabels.take(10)))
            }
            capabilitiesArray.put(appJsonObject)

            // Step D: Close app by dispatching GLOBAL_ACTION_HOME
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

            // Step E: Wait 1000ms before next app
            delay(1000)
        }

        // Flush to External Disk: /Download/LocalAgent/app_capabilities.json
        val storageDir = StorageManager.getStorageDirectory()
        val capabilitiesFile = File(storageDir, "app_capabilities.json")
        try {
            FileWriter(capabilitiesFile, false).use { writer ->
                writer.write(capabilitiesArray.toString(2))
            }
            val logMsg = "[AUDIT] Completed $totalApps apps. Saved to /Download/LocalAgent/app_capabilities.json."
            Log.i(TAG, logMsg)
            service.broadcastTelemetryLog("AUDIT", logMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write app capabilities to external disk", e)
            service.broadcastTelemetryLog("AUDIT", "[AUDIT] Error writing capabilities: ${e.message}")
        }

        // Return to MainActivity
        val activityIntent = Intent(service, com.example.localagent.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        service.startActivity(activityIntent)
    }

    private fun collectButtonLabels(node: AccessibilityNodeInfo, labels: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) labels.add(text)
        else if (!desc.isNullOrEmpty()) labels.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectButtonLabels(child, labels)
            } finally {
                child.recycle()
            }
        }
    }

    private fun classifyDomain(appName: String, pkgName: String, labels: List<String>): String {
        val combined = (appName + " " + pkgName + " " + labels.joinToString(" ")).lowercase()
        return when {
            combined.contains("note") || combined.contains("memo") || combined.contains("keep") || combined.contains("write") -> "Notes"
            combined.contains("music") || combined.contains("video") || combined.contains("media") || combined.contains("play") || combined.contains("player") -> "Media"
            combined.contains("calc") || combined.contains("alarm") || combined.contains("clock") || combined.contains("setting") || combined.contains("tool") -> "Utility"
            else -> "General"
        }
    }
}
