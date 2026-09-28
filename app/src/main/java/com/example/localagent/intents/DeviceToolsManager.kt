package com.example.localagent.intents

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService

class DeviceToolsManager(private val service: LocalAgentService) {

    companion object {
        private const val TAG = "DeviceToolsManager"
    }

    fun toggleFlashlight(enable: Boolean) {
        val cameraManager = service.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager != null) {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    val flashAvailable = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK && flashAvailable
                } ?: cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }

                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, enable)
                    service.broadcastTelemetryLog("TOOLS", "Hardware Flashlight set to: $enable")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "CameraManager setTorchMode failed, using Quick Settings fallback", e)
            }
        }

        // Quick Settings Fallback
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        Handler(Looper.getMainLooper()).postDelayed({
            val root = service.getActiveWindowRoot()
            if (root != null) {
                val flashNode = findFlashlightNode(root)
                if (flashNode != null) {
                    try {
                        service.performClickWithFallback(flashNode)
                        service.broadcastTelemetryLog("TOOLS", "Hardware Flashlight set to: $enable (Quick Settings)")
                    } finally {
                        flashNode.recycle()
                    }
                }
                root.recycle()
            }
        }, 500L)
    }

    fun triggerHaptic(durationMs: Long = 150L) {
        try {
            val vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
                service.broadcastTelemetryLog("TOOLS", "Triggered haptic vibration ($durationMs ms)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger haptic vibration", e)
        }
    }

    fun openAndToggleSetting(settingType: String) {
        val intent = when (settingType.lowercase()) {
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        service.startActivity(intent)
        service.broadcastTelemetryLog("TOOLS", "Navigated to settings: $settingType")

        Handler(Looper.getMainLooper()).postDelayed({
            val root = service.getActiveWindowRoot()
            if (root != null) {
                val switchNode = findSwitchNode(root)
                if (switchNode != null) {
                    try {
                        service.performClickWithFallback(switchNode)
                        service.broadcastTelemetryLog("TOOLS", "Toggled $settingType switch widget")
                    } finally {
                        switchNode.recycle()
                    }
                }
                root.recycle()
            }
        }, 800L)
    }

    private fun findFlashlightNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (desc.contains("flashlight") || desc.contains("torch") || text.contains("flashlight") || text.contains("torch")) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findFlashlightNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findSwitchNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val className = node.className?.toString() ?: ""
        if (className.contains("Switch") || node.isCheckable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findSwitchNode(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
