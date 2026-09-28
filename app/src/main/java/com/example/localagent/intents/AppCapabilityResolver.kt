package com.example.localagent.intents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class CapabilityDomain {
    DOMAIN_NOTES,
    DOMAIN_CLOCK,
    DOMAIN_VOICE_RECORDER,
    DOMAIN_LOCAL_VIDEO,
    DOMAIN_ONLINE_VIDEO,
    DOMAIN_GENERAL
}

data class AppCapability(
    val domain: CapabilityDomain,
    val packageName: String,
    val appName: String
)

class AppCapabilityResolver(private val context: Context) {

    private val storageFile = File(context.filesDir, "app_capabilities.json")

    fun scanAndMapCapabilities(): Map<CapabilityDomain, AppCapability> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

        val capabilityMap = mutableMapOf<CapabilityDomain, AppCapability>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val lowerPkg = pkg.lowercase()
            val lowerLabel = label.lowercase()

            when {
                lowerPkg.contains("keep") || lowerPkg.contains("note") || lowerPkg.contains("memo") || lowerLabel.contains("note") -> {
                    capabilityMap[CapabilityDomain.DOMAIN_NOTES] = AppCapability(CapabilityDomain.DOMAIN_NOTES, pkg, label)
                }
                lowerPkg.contains("clock") || lowerPkg.contains("alarm") || lowerLabel.contains("clock") -> {
                    capabilityMap[CapabilityDomain.DOMAIN_CLOCK] = AppCapability(CapabilityDomain.DOMAIN_CLOCK, pkg, label)
                }
                lowerPkg.contains("recorder") || lowerPkg.contains("sound") || lowerLabel.contains("recorder") -> {
                    capabilityMap[CapabilityDomain.DOMAIN_VOICE_RECORDER] = AppCapability(CapabilityDomain.DOMAIN_VOICE_RECORDER, pkg, label)
                }
                lowerPkg.contains("gallery") || lowerPkg.contains("vlc") || lowerPkg.contains("video") -> {
                    capabilityMap[CapabilityDomain.DOMAIN_LOCAL_VIDEO] = AppCapability(CapabilityDomain.DOMAIN_LOCAL_VIDEO, pkg, label)
                }
                lowerPkg.contains("youtube") -> {
                    capabilityMap[CapabilityDomain.DOMAIN_ONLINE_VIDEO] = AppCapability(CapabilityDomain.DOMAIN_ONLINE_VIDEO, pkg, label)
                }
            }
        }

        saveCapabilities(capabilityMap)
        return capabilityMap
    }

    private fun saveCapabilities(map: Map<CapabilityDomain, AppCapability>) {
        try {
            val jsonArray = JSONArray()
            map.forEach { (domain, cap) ->
                val obj = JSONObject().apply {
                    put("domain", domain.name)
                    put("packageName", cap.packageName)
                    put("appName", cap.appName)
                }
                jsonArray.put(obj)
            }
            storageFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
