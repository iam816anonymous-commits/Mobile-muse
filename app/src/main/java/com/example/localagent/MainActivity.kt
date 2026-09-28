package com.example.localagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

open class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleText = TextView(this).apply {
            text = "LocalAgent Controller"
            textSize = 22f
        }

        val statusText = TextView(this).apply {
            text = "Target API: 26-28 (Android 8/9)\nStatus: Ready for ADB Goal Broadcasts"
            setPadding(0, 24, 0, 48)
        }

        val enableServiceBtn = Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(enableServiceBtn)

        setContentView(layout)
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
