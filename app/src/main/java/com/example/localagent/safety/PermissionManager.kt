package com.example.localagent.safety

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.localagent.MainActivity

object PermissionManager {

    const val REQUEST_CODE_RUNTIME = 2001

    val GROUP_A_PERMISSIONS = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    fun checkAndRequestRuntimePermissions(activity: Activity): Boolean {
        val missing = GROUP_A_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQUEST_CODE_RUNTIME)
            return false
        }
        return true
    }

    fun checkGuidedOnboarding(activity: Activity): Boolean {
        if (!checkAndRequestRuntimePermissions(activity)) return false

        // Check 1: Accessibility Service
        if (!MainActivity.isAccessibilityServiceEnabled(activity, com.example.localagent.LocalAgentService::class.java)) {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return false
        }

        // Check 2: Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
            return false
        }

        // Check 3: Battery Optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(activity.packageName) == false) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}")))
                } catch (e: Exception) {
                    activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
                return false
            }
        }

        return true
    }
}
