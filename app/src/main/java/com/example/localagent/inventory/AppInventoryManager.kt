package com.example.localagent.inventory

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppProfile(
    val appName: String,
    val packageName: String,
    var launchable: Boolean = false,
    var hasEditableInput: Boolean = false,
    var supportsScroll: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("appName", appName)
            put("packageName", packageName)
            put("launchable", launchable)
            put("hasEditableInput", hasEditableInput)
            put("supportsScroll", supportsScroll)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): AppProfile {
            return AppProfile(
                appName = json.getString("appName"),
                packageName = json.getString("packageName"),
                launchable = json.optBoolean("launchable", false),
                hasEditableInput = json.optBoolean("hasEditableInput", false),
                supportsScroll = json.optBoolean("supportsScroll", false)
            )
        }
    }
}

class AppInventoryManager(private val context: Context) {

    private val inventoryFile = File(context.filesDir, "app_inventory.json")
    private val profiles = mutableListOf<AppProfile>()

    init {
        loadInventory()
    }

    @Synchronized
    fun scanDeviceApps(): List<AppProfile> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        val myPackage = context.packageName

        for (resolveInfo in resolvedActivities) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == myPackage) continue

            val name = resolveInfo.loadLabel(pm)?.toString() ?: pkg
            val existing = profiles.find { it.packageName == pkg }
            if (existing == null) {
                profiles.add(AppProfile(appName = name, packageName = pkg, launchable = true))
            } else {
                existing.launchable = true
            }
        }

        saveInventory()
        return profiles.toList()
    }

    @Synchronized
    fun getProfiles(): List<AppProfile> = profiles.toList()

    @Synchronized
    fun updateProfile(profile: AppProfile) {
        val index = profiles.indexOfFirst { it.packageName == profile.packageName }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveInventory()
    }

    @Synchronized
    fun saveInventory() {
        try {
            val array = JSONArray()
            profiles.forEach { array.put(it.toJsonObject()) }
            inventoryFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun loadInventory() {
        if (!inventoryFile.exists()) return
        try {
            val content = inventoryFile.readText()
            if (content.isBlank()) return
            val array = JSONArray(content)
            profiles.clear()
            for (i in 0 until array.length()) {
                val jsonObject = array.getJSONObject(i)
                profiles.add(AppProfile.fromJsonObject(jsonObject))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
