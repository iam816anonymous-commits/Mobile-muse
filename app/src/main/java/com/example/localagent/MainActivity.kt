package com.example.localagent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.localagent.memory.RuleLedger
import java.io.File

open class MainActivity : Activity() {

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvAccessibilityBadge: TextView
    private lateinit var tvOverlayBadge: TextView
    private lateinit var etGoalInput: EditText
    private lateinit var tvTerminalLog: TextView
    private lateinit var svTerminal: ScrollView
    private lateinit var tvMetricsBar: TextView

    private val telemetryLogs = StringBuilder()
    private var telemetryReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0E17"))
            setPadding(32, 32, 32, 32)
        }

        // Header
        val headerText = TextView(this).apply {
            text = "LOCALAGENT :: JARVIS TELEMETRY DECK"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00F0FF"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        // System Status Header Badges
        val badgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        tvStatusBadge = TextView(this).apply {
            text = "SYSTEM: STANDBY"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = createBadgeDrawable(Color.parseColor("#334155"))
            setPadding(20, 10, 20, 10)
        }

        tvAccessibilityBadge = TextView(this).apply {
            text = "ACCESSIBILITY: OFF"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = createBadgeDrawable(Color.parseColor("#475569"))
            setPadding(20, 10, 20, 10)
        }

        tvOverlayBadge = TextView(this).apply {
            text = "OVERLAY: OFF"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = createBadgeDrawable(Color.parseColor("#475569"))
            setPadding(20, 10, 20, 10)
        }

        badgeContainer.addView(tvStatusBadge)
        badgeContainer.addView(tvAccessibilityBadge)
        badgeContainer.addView(tvOverlayBadge)

        // Manual Engagement Deck
        val deckTitle = TextView(this).apply {
            text = "MANUAL TACTICAL ENGAGEMENT"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 8, 0, 8)
        }

        etGoalInput = EditText(this).apply {
            hint = "Enter goal (e.g., 'Ask Gemini how fusion reactors work')"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            setPadding(24, 24, 24, 24)
        }

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 24)
        }

        val btnEngage = Button(this).apply {
            text = "[ENGAGE GOAL]"
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#00F0FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener {
                val goal = etGoalInput.text.toString().trim()
                if (goal.isNotEmpty()) {
                    val intent = Intent("com.localagent.EXECUTE_GOAL").apply {
                        putExtra("goal_text", goal)
                    }
                    sendBroadcast(intent)
                    appendLog("[SYS] Broadcasted ENGAGE GOAL: '$goal'")
                }
            }
        }

        val btnAbort = Button(this).apply {
            text = "[ABORT ALL]"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#EF4444"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            setOnClickListener {
                val intent = Intent("com.example.localagent.ACTION_KILL_SWITCH")
                sendBroadcast(intent)
                appendLog("[SYS] EMERGENCY ABORT BROADCAST DISPATCHED!")
            }
        }

        buttonContainer.addView(btnEngage)
        buttonContainer.addView(btnAbort)

        // Settings Buttons
        val settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 24)
        }

        val btnSettings = Button(this).apply {
            text = "Accessibility Settings"
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnOverlay = Button(this).apply {
            text = "Overlay Settings"
            textSize = 12f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }

        settingsContainer.addView(btnSettings)
        settingsContainer.addView(btnOverlay)

        // Memory Metrics Bar
        tvMetricsBar = TextView(this).apply {
            text = "RULES LEDGER: 0 | MAX DEPTH: 7 | STATUS: READY"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 0, 0, 16)
        }

        // Live Tactical Terminal
        val terminalTitle = TextView(this).apply {
            text = "LIVE TACTICAL EVENT STREAM"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 8, 0, 8)
        }

        tvTerminalLog = TextView(this).apply {
            text = "[SYS] Tactical terminal initialized. Ready for operations.\n"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setBackgroundColor(Color.parseColor("#111625"))
            setPadding(20, 20, 20, 20)
        }

        svTerminal = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(tvTerminalLog)
        }

        rootLayout.addView(headerText)
        rootLayout.addView(badgeContainer)
        rootLayout.addView(deckTitle)
        rootLayout.addView(etGoalInput)
        rootLayout.addView(buttonContainer)
        rootLayout.addView(settingsContainer)
        rootLayout.addView(tvMetricsBar)
        rootLayout.addView(terminalTitle)
        rootLayout.addView(svTerminal)

        setContentView(rootLayout)
        registerTelemetryReceiver()
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterTelemetryReceiver()
    }

    private fun updateDashboardStatus() {
        val isAccessEnabled = isAccessibilityServiceEnabled(this, LocalAgentService::class.java)
        tvAccessibilityBadge.text = if (isAccessEnabled) "ACCESSIBILITY: ON" else "ACCESSIBILITY: OFF"
        tvAccessibilityBadge.background = createBadgeDrawable(
            if (isAccessEnabled) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
        )

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        tvOverlayBadge.text = if (hasOverlay) "OVERLAY: ON" else "OVERLAY: OFF"
        tvOverlayBadge.background = createBadgeDrawable(
            if (hasOverlay) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
        )

        // Read RuleLedger stats directly
        try {
            val ruleLedger = RuleLedger(File(filesDir, "local_rules.json"))
            tvMetricsBar.text = "RULES LEDGER: LOADED | MAX DEPTH: 7 | STATUS: ONLINE"
        } catch (e: Exception) {
            tvMetricsBar.text = "RULES LEDGER: 0 | MAX DEPTH: 7 | STATUS: STANDBY"
        }
    }

    fun appendLog(logLine: String) {
        telemetryLogs.append(logLine).append("\n")
        runOnUiThread {
            tvTerminalLog.text = telemetryLogs.toString()
            svTerminal.post { svTerminal.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun registerTelemetryReceiver() {
        if (telemetryReceiver == null) {
            telemetryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        LocalAgentService.ACTION_TELEMETRY_LOG -> {
                            val entry = intent.getStringExtra(LocalAgentService.EXTRA_LOG_ENTRY)
                            if (entry != null) appendLog(entry)
                        }
                        LocalAgentService.ACTION_GOAL_COMPLETED -> {
                            val goal = intent.getStringExtra(LocalAgentService.EXTRA_GOAL_TEXT)
                            val status = intent.getStringExtra(LocalAgentService.EXTRA_STATUS)
                            val result = intent.getStringExtra(LocalAgentService.EXTRA_RESULT_DATA)
                            appendLog("[EXTRACT] Goal Completed [$status]: $result")
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(LocalAgentService.ACTION_TELEMETRY_LOG)
                addAction(LocalAgentService.ACTION_GOAL_COMPLETED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(telemetryReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(telemetryReceiver, filter)
            }
        }
    }

    private fun unregisterTelemetryReceiver() {
        telemetryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            telemetryReceiver = null
        }
    }

    private fun createBadgeDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(color)
        }
    }

    private fun createBorderDrawable(strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(Color.parseColor("#1E293B"))
            setStroke(2, strokeColor)
        }
    }

    private fun createButtonDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(bgColor)
        }
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
