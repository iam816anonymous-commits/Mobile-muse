package com.example.localagent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

open class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var tvAdbCommands: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        tvAdbCommands = findViewById(R.id.tvAdbCommands)

        btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        setupAdbInfoText()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    fun updateStatus() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, LocalAgentService::class.java)
        tvAccessibilityStatus.text = if (isAccessibilityEnabled) {
            "Accessibility Service: ENABLED"
        } else {
            "Accessibility Service: DISABLED"
        }

        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        tvOverlayStatus.text = if (hasOverlayPermission) {
            "Overlay Permission: GRANTED"
        } else {
            "Overlay Permission: MISSING"
        }
    }

    private fun setupAdbInfoText() {
        val infoText = StringBuilder().apply {
            append("OS: Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
            append("--- Quick ADB Test Commands ---\n\n")
            append("# Execute Gemini Goal:\n")
            append("adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text \"Ask Gemini about quantum computing\"\n\n")
            append("# Execute Chrome Search Goal:\n")
            append("adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text \"Search latest tech news on Chrome\"\n\n")
            append("# Emergency Kill Switch:\n")
            append("adb shell am broadcast -a com.example.localagent.ACTION_KILL_SWITCH\n")
        }.toString()

        tvAdbCommands.text = infoText
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
            val expectedComponentName = "${context.packageName}/${serviceClass.name}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val components = enabledServicesSetting.split(":")
            for (component in components) {
                val trimmed = component.trim()
                if (trimmed.equals(expectedComponentName, ignoreCase = true) ||
                    trimmed.equals("${context.packageName}/.${serviceClass.simpleName}", ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }
}
