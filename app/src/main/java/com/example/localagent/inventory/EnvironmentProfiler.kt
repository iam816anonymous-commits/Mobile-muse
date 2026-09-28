package com.example.localagent.inventory

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.localagent.LocalAgentService
import com.example.localagent.memory.MemoryGuard
import com.example.localagent.scraper.ContentScraper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AppCategory {
    NEWS,
    UTILITY,
    COMMUNICATION,
    GENERAL
}

data class EnvironmentProfile(
    val packageName: String,
    val appName: String,
    val category: AppCategory
)

class EnvironmentProfiler(private val context: Context) {

    private val storageFile = File(context.filesDir, "environment_catalog.json")

    fun scanAndCatalogApps(): List<EnvironmentProfile> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val profiles = mutableListOf<EnvironmentProfile>()
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val category = when {
                pkg.contains("chrome") || pkg.contains("news") || label.contains("News", ignoreCase = true) -> AppCategory.NEWS
                pkg.contains("clock") || pkg.contains("calculator") || pkg.contains("camera") || pkg.contains("calendar") -> AppCategory.UTILITY
                pkg.contains("whatsapp") || pkg.contains("mms") || pkg.contains("dialer") || pkg.contains("messaging") -> AppCategory.COMMUNICATION
                else -> AppCategory.GENERAL
            }
            profiles.add(EnvironmentProfile(pkg, label, category))
        }

        saveCatalog(profiles)
        return profiles
    }

    fun exploreAppWorkflow(service: LocalAgentService, packageName: String, onComplete: (String) -> Unit) {
        if (MemoryGuard.isLowMemoryCondition(context)) {
            service.broadcastTelemetryLog("SYS", "Low RAM detected during workflow exploration")
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            ContentScraper.monitorAndExtractResult(service) { extractedText ->
                val summary = if (extractedText.length > 200) extractedText.substring(0, 200) + "..." else extractedText
                service.broadcastTelemetryLog("PROFILER", "Workflow article summary: $summary")
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                onComplete(summary)
            }
        }
    }

    private fun saveCatalog(profiles: List<EnvironmentProfile>) {
        try {
            val jsonArray = JSONArray()
            profiles.forEach { profile ->
                val obj = JSONObject().apply {
                    put("packageName", profile.packageName)
                    put("appName", profile.appName)
                    put("category", profile.category.name)
                }
                jsonArray.put(obj)
            }
            storageFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
