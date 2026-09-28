package com.example.localagent.engine

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.example.localagent.memory.StorageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

data class InstalledApp(val label: String, val packageName: String)

object AppIndexer {

    private const val TAG = "AppIndexer"
    private var cachedApps: List<InstalledApp>? = null

    fun getInstalledApps(context: Context, forceRefresh: Boolean = false): List<InstalledApp> {
        if (!forceRefresh && cachedApps != null) {
            return cachedApps!!
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = pm.queryIntentActivities(intent, 0)
        val apps = resolveList.map {
            InstalledApp(
                label = it.loadLabel(pm).toString().lowercase().trim(),
                packageName = it.activityInfo.packageName
            )
        }

        cachedApps = apps
        saveCapabilitiesToDisk(apps)
        return apps
    }

    fun resolveAppByQuery(context: Context, query: String): String? {
        val q = query.lowercase().trim()
        val apps = getInstalledApps(context)

        // Exact label match
        val exactMatch = apps.find { it.label == q }
        if (exactMatch != null) return exactMatch.packageName

        // Keyword fuzzy matching rules
        if (q.contains("calc")) {
            val calcApp = apps.find { it.label.contains("calc") || it.packageName.lowercase().contains("calc") }
            if (calcApp != null) return calcApp.packageName
        }

        if (q.contains("note") || q.contains("memo") || q.contains("keep")) {
            val noteApp = apps.find {
                it.label.contains("note") || it.label.contains("memo") || it.label.contains("keep") ||
                        it.packageName.lowercase().contains("note") || it.packageName.lowercase().contains("keep")
            }
            if (noteApp != null) return noteApp.packageName
        }

        if (q.contains("camera")) {
            val cameraApp = apps.find { it.label.contains("camera") || it.packageName.lowercase().contains("camera") }
            if (cameraApp != null) return cameraApp.packageName
        }

        if (q.contains("browser") || q.contains("chrome") || q.contains("web")) {
            val browserApp = apps.find { it.label.contains("chrome") || it.label.contains("browser") || it.packageName.lowercase().contains("chrome") }
            if (browserApp != null) return browserApp.packageName
        }

        if (q.contains("setting") || q.contains("settings")) {
            val settingsApp = apps.find { it.label.contains("setting") || it.packageName.lowercase().contains("setting") }
            if (settingsApp != null) return settingsApp.packageName
        }

        // Substring label match
        val subMatch = apps.find { it.label.contains(q) || q.contains(it.label) }
        if (subMatch != null) return subMatch.packageName

        return null
    }

    private fun saveCapabilitiesToDisk(apps: List<InstalledApp>) {
        try {
            val storageDir = StorageManager.getStorageDirectory()
            val file = File(storageDir, "app_capabilities.json")
            val jsonArray = JSONArray()
            apps.forEach { app ->
                val jsonObj = JSONObject().apply {
                    put("label", app.label)
                    put("package_name", app.packageName)
                }
                jsonArray.put(jsonObj)
            }
            FileWriter(file, false).use { writer ->
                writer.write(jsonArray.toString(2))
            }
            Log.d(TAG, "Saved ${apps.size} apps to /Download/LocalAgent/app_capabilities.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save app_capabilities.json to disk", e)
        }
    }
}
