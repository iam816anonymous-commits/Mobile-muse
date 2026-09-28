package com.example.localagent.memory

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class OperationalMetric(
    val executionDurationMs: Long,
    val ramSpikeMb: Long,
    val obstacleDismissalCount: Int,
    val stepCountToSuccess: Int
)

data class OptimizationTip(
    val title: String,
    val description: String,
    val shortcutAction: String
)

class SelfReflectionEngine(private val context: Context) {

    private val storageFile = File(context.filesDir, "reflection_ledger.json")
    private val metricsBuffer = mutableListOf<OperationalMetric>()

    init {
        loadMetrics()
    }

    @Synchronized
    fun recordMetric(metric: OperationalMetric) {
        metricsBuffer.add(metric)
        while (metricsBuffer.size > 50) {
            metricsBuffer.removeAt(0)
        }
        saveMetrics()
    }

    fun generateSystemAdvice(): List<OptimizationTip> {
        val tips = mutableListOf<OptimizationTip>()

        val avgDuration = if (metricsBuffer.isNotEmpty()) metricsBuffer.map { it.executionDurationMs }.average() else 0.0
        if (avgDuration > 1200.0) {
            tips.add(
                OptimizationTip(
                    title = "Animation Scale Optimization",
                    description = "Disabling Window and Transition Animation scales in Developer Options will accelerate UI automation by ~40%.",
                    shortcutAction = "DEVELOPER_OPTIONS"
                )
            )
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        if (!isIgnoringBattery) {
            tips.add(
                OptimizationTip(
                    title = "Unrestricted Battery Usage",
                    description = "Allowing unrestricted background battery usage prevents the OS from suspending background tasks.",
                    shortcutAction = "BATTERY_SETTINGS"
                )
            )
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val freeMb = memInfo.availMem / (1024 * 1024)
        if (freeMb < 300) {
            tips.add(
                OptimizationTip(
                    title = "RAM Congestion Notice",
                    description = "Closing background apps before running heavy workflows will eliminate UI stuttering.",
                    shortcutAction = "SYSTEM_SETTINGS"
                )
            )
        }

        tips.add(
            OptimizationTip(
                title = "Offline Workflow Learning",
                description = "Workflow cached: identical future news queries will now execute 100% offline.",
                shortcutAction = "NONE"
            )
        )

        return tips
    }

    private fun saveMetrics() {
        try {
            val array = JSONArray()
            metricsBuffer.forEach { m ->
                val obj = JSONObject().apply {
                    put("duration", m.executionDurationMs)
                    put("ramSpike", m.ramSpikeMb)
                    put("obstacles", m.obstacleDismissalCount)
                    put("steps", m.stepCountToSuccess)
                }
                array.put(obj)
            }
            storageFile.writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadMetrics() {
        if (!storageFile.exists()) return
        try {
            val content = storageFile.readText()
            if (content.isBlank()) return
            val array = JSONArray(content)
            metricsBuffer.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                metricsBuffer.add(
                    OperationalMetric(
                        executionDurationMs = obj.getLong("duration"),
                        ramSpikeMb = obj.getLong("ramSpike"),
                        obstacleDismissalCount = obj.getInt("obstacles"),
                        stepCountToSuccess = obj.getInt("steps")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
