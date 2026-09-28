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

        if (q.contains("gallery") || q.contains("picture") || q.contains("photo") || q.contains("image") || q.contains("my picture")) {
            val photoApp = apps.find {
                it.label.contains("my picture") || it.label.contains("ai gallery") ||
                        it.label.contains("gallery") || it.label.contains("photo") ||
                        it.packageName.lowercase().contains("gallery") || it.packageName.lowercase().contains("photo")
            } ?: run {
                val intent = Intent(Intent.ACTION_VIEW).apply { setType("image/*") }
                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                resolveInfo?.activityInfo?.packageName?.let { pkg ->
                    apps.find { it.packageName == pkg }
                }
            }
            if (photoApp != null) {
                saveMediaViewerCapability(context, photoApp.packageName)
                return photoApp.packageName
            }
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

    private fun saveMediaViewerCapability(context: Context, packageName: String) {
        try {
            val storageDir = StorageManager.getStorageDirectory()
            val file = File(storageDir, "app_capabilities.json")
            val jsonArray = if (file.exists()) {
                try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
            } else JSONArray()

            val jsonObj = JSONObject().apply {
                put("media_viewer", packageName)
            }
            jsonArray.put(jsonObj)
            FileWriter(file, false).use { writer ->
                writer.write(jsonArray.toString(2))
            }
            Log.d(TAG, "Saved media_viewer capability ($packageName) to /Download/LocalAgent/app_capabilities.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media_viewer capability to disk", e)
        }
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
