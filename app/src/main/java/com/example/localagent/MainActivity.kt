package com.example.localagent

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.localagent.engine.DiagnosticRunner
import com.example.localagent.inventory.AppInventoryManager
import com.example.localagent.memory.KnowledgeLedger
import com.example.localagent.memory.RuleLedger
import com.example.localagent.memory.SelfReflectionEngine
import com.example.localagent.receiver.GoalDispatcher
import com.example.localagent.voice.VoiceCommandManager
import com.example.localagent.voice.VoiceEngine
import java.io.File

open class MainActivity : Activity() {

    private lateinit var tvAccessibilityBadge: TextView
    private lateinit var tvOverlayBadge: TextView
    private lateinit var tvBatteryBadge: TextView
    private lateinit var tvAudioBadge: TextView

    private lateinit var tvRamMetric: TextView
    private lateinit var tvKnowledgeMetric: TextView
    private lateinit var tvStatusMetric: TextView

    private lateinit var etGoalInput: EditText
    private lateinit var tvTerminalLog: TextView
    private lateinit var svTerminal: ScrollView

    private lateinit var llAdviceContainer: LinearLayout

    private var voiceEngine: VoiceEngine? = null
    private var reflectionEngine: SelfReflectionEngine? = null
    private val telemetryLogs = StringBuilder()
    private var telemetryReceiver: BroadcastReceiver? = null

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceEngine = VoiceEngine(this)
        reflectionEngine = SelfReflectionEngine(this)

        val mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0E17"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0E17"))
            setPadding(32, 32, 32, 32)
        }

        // Header
        val headerText = TextView(this).apply {
            text = "JARVIS MISSION CONTROL :: LOCALAGENT"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00F0FF"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        // Jarvis Optimization Directives Card
        val adviceCardTitle = TextView(this).apply {
            text = "JARVIS OPTIMIZATION DIRECTIVES"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 8, 0, 4)
        }

        llAdviceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            setPadding(16, 16, 16, 16)
        }

        // Section A: System Clearance & Permission Manager
        val permTitle = TextView(this).apply {
            text = "SECTION A: SYSTEM CLEARANCE & PERMISSIONS"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 8, 0, 8)
        }

        val permGridRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        tvAccessibilityBadge = createBadgeView("ACCESSIBILITY: [REQUIRED]")
        val btnAccessSettings = Button(this).apply {
            text = "Grant Acc."
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val accessContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            addView(tvAccessibilityBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(btnAccessSettings)
        }

        tvOverlayBadge = createBadgeView("OVERLAY: [REQUIRED]")
        val btnOverlaySettings = Button(this).apply {
            text = "Grant HUD"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
        val overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            addView(tvOverlayBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(btnOverlaySettings)
        }

        permGridRow1.addView(accessContainer)
        permGridRow1.addView(overlayContainer)

        val permGridRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        tvBatteryBadge = createBadgeView("BATTERY: [REQUIRED]")
        val btnBatterySettings = Button(this).apply {
            text = "Whitelist"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        }
        val batteryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            addView(tvBatteryBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(btnBatterySettings)
        }

        tvAudioBadge = createBadgeView("AUDIO: [REQUIRED]")
        val btnAudioSettings = Button(this).apply {
            text = "Grant Mic"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_RECORD_AUDIO)
                }
            }
        }
        val audioContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            addView(tvAudioBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            addView(btnAudioSettings)
        }

        permGridRow2.addView(batteryContainer)
        permGridRow2.addView(audioContainer)

        // Section B: Hardware & Memory Vitals Bar
        val vitalsTitle = TextView(this).apply {
            text = "SECTION B: HARDWARE & MEMORY VITALS"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 8, 0, 8)
        }

        val vitalsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111625"))
            setPadding(16, 16, 16, 16)
        }

        tvRamMetric = TextView(this).apply {
            text = "RAM BUDGET\nAvail: --- MB"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvKnowledgeMetric = TextView(this).apply {
            text = "KNOWLEDGE CACHE\nCached: 0/200"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvStatusMetric = TextView(this).apply {
            text = "ACTIVE STATUS\nIDLE"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        vitalsRow.addView(tvRamMetric)
        vitalsRow.addView(tvKnowledgeMetric)
        vitalsRow.addView(tvStatusMetric)

        // Section C: Mission Engagement Deck
        val deckTitle = TextView(this).apply {
            text = "SECTION C: MISSION ENGAGEMENT DECK"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 16, 0, 8)
        }

        etGoalInput = EditText(this).apply {
            hint = "Enter goal (e.g., 'Ask Gemini about fusion reactors')"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            setPadding(24, 24, 24, 24)
        }

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 16)
        }

        val btnEngage = Button(this).apply {
            text = "[ENGAGE]"
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#00F0FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener {
                val goal = etGoalInput.text.toString().trim()
                if (goal.isNotEmpty()) {
                    val intent = Intent("com.localagent.EXECUTE_GOAL").apply {
                        putExtra("goal_text", goal)
                    }
                    sendBroadcast(intent)
                    appendLog("[ACT] Broadcasted com.localagent.EXECUTE_GOAL: '$goal'")
                    voiceEngine?.speak("Engaging goal: $goal")
                }
            }
        }

        val btnVoiceMic = Button(this).apply {
            text = "🎤 [VOICE INPUT]"
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#39FF14"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 4
                marginEnd = 4
            }
            setOnClickListener {
                appendLog("[VOICE] Starting push-to-talk speech recognition...")
                voiceEngine?.startListening(
                    onResult = { transcribed ->
                        etGoalInput.setText(transcribed)
                        appendLog("[VOICE] Transcribed: '$transcribed'")
                        val intent = Intent("com.localagent.EXECUTE_GOAL").apply {
                            putExtra("goal_text", transcribed)
                        }
                        sendBroadcast(intent)
                        voiceEngine?.speak("Engaging transcribed goal")
                    },
                    onError = { err ->
                        appendLog("[VOICE] Speech Error: $err")
                    }
                )
            }
        }

        val btnAbort = Button(this).apply {
            text = "[EMERGENCY ABORT]"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#FF0055"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply { marginStart = 4 }
            setOnClickListener {
                val intent = Intent("com.example.localagent.ACTION_KILL_SWITCH")
                sendBroadcast(intent)
                appendLog("[ALERT] EMERGENCY ABORT BROADCAST DISPATCHED!")
                voiceEngine?.speak("Emergency abort executed")
            }
        }

        buttonContainer.addView(btnEngage)
        buttonContainer.addView(btnVoiceMic)
        buttonContainer.addView(btnAbort)

        // Section D: Diagnostic & Audit Quick-Actions
        val diagTitle = TextView(this).apply {
            text = "SECTION D: DIAGNOSTIC & AUDIT QUICK-ACTIONS"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 8, 0, 8)
        }

        val diagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        val btnAppAudit = Button(this).apply {
            text = "[Run App Inventory Audit]"
            textSize = 11f
            setTextColor(Color.parseColor("#39FF14"))
            background = createBorderDrawable(Color.parseColor("#16A34A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener {
                sendBroadcast(Intent(GoalDispatcher.ACTION_RUN_APP_AUDIT))
                appendLog("[ACT] Triggered [Run App Inventory Audit]...")
            }
        }

        val btnTestSwipe = Button(this).apply {
            text = "[Test Coordinate Swipe]"
            textSize = 11f
            setTextColor(Color.parseColor("#39FF14"))
            background = createBorderDrawable(Color.parseColor("#16A34A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 2; marginEnd = 2 }
            setOnClickListener {
                sendBroadcast(Intent(LocalAgentService.ACTION_TEST_COORDINATE_TAP))
                appendLog("[ACT] Triggered [Test Coordinate Swipe]...")
            }
        }

        val btnClearCache = Button(this).apply {
            text = "[Clear Ledger Cache]"
            textSize = 11f
            setTextColor(Color.parseColor("#FF0055"))
            background = createBorderDrawable(Color.parseColor("#FF0055"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                try {
                    File(filesDir, "knowledge_ledger.json").delete()
                    File(filesDir, "local_rules.json").delete()
                    appendLog("[SYS] Flushed ledger caches and memory logs.")
                    updateDashboardStatus()
                } catch (e: Exception) {
                    appendLog("[SYS] Failed to clear ledger: ${e.message}")
                }
            }
        }

        diagRow.addView(btnAppAudit)
        diagRow.addView(btnTestSwipe)
        diagRow.addView(btnClearCache)

        // Section E: Real-Time Tactical Stream
        val terminalTitle = TextView(this).apply {
            text = "SECTION E: REAL-TIME TACTICAL STREAM"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 8, 0, 4)
        }

        tvTerminalLog = TextView(this).apply {
            text = "[PERM] Core Accessibility engine active\n[SYS] Mission Control dashboard initialized.\n"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setBackgroundColor(Color.parseColor("#111625"))
            setPadding(20, 20, 20, 20)
        }

        val terminalPx = (200 * resources.displayMetrics.density).toInt()
        svTerminal = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                terminalPx
            )
            addView(tvTerminalLog)
        }

        rootLayout.addView(headerText)
        rootLayout.addView(adviceCardTitle)
        rootLayout.addView(llAdviceContainer)
        rootLayout.addView(permTitle)
        rootLayout.addView(permGridRow1)
        rootLayout.addView(permGridRow2)
        rootLayout.addView(vitalsTitle)
        rootLayout.addView(vitalsRow)
        rootLayout.addView(deckTitle)
        rootLayout.addView(etGoalInput)
        rootLayout.addView(buttonContainer)
        rootLayout.addView(diagTitle)
        rootLayout.addView(diagRow)
        rootLayout.addView(terminalTitle)
        rootLayout.addView(svTerminal)

        mainScrollView.addView(rootLayout)
        setContentView(mainScrollView)

        registerTelemetryReceiver()
        updateOptimizationAdvice()
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
        updateOptimizationAdvice()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine?.shutdown()
        unregisterTelemetryReceiver()
    }

    private fun updateOptimizationAdvice() {
        llAdviceContainer.removeAllViews()
        val tips = reflectionEngine?.generateSystemAdvice() ?: emptyList()

        if (tips.isNotEmpty()) {
            val firstTip = tips.first()
            voiceEngine?.speak("System directive: ${firstTip.description}")
        }

        tips.forEach { tip ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 12)
            }
            val titleView = TextView(this).apply {
                text = "• ${tip.title}"
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#00F0FF"))
            }
            val descView = TextView(this).apply {
                text = tip.description
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#94A3B8"))
            }
            itemLayout.addView(titleView)
            itemLayout.addView(descView)

            if (tip.shortcutAction == "BATTERY_SETTINGS") {
                val btnFix = Button(this).apply {
                    text = "Fix Battery Settings"
                    textSize = 10f
                    setTextColor(Color.parseColor("#00F0FF"))
                    background = createBorderDrawable(Color.parseColor("#00F0FF"))
                    setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                }
                itemLayout.addView(btnFix)
            }

            llAdviceContainer.addView(itemLayout)
        }
    }

    private fun createBadgeView(defaultText: String): TextView {
        return TextView(this).apply {
            text = defaultText
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
        }
    }

    private fun updateDashboardStatus() {
        // Section A Badges
        val isAccessEnabled = isAccessibilityServiceEnabled(this, LocalAgentService::class.java)
        tvAccessibilityBadge.text = if (isAccessEnabled) "ACCESSIBILITY: [ENABLED]" else "ACCESSIBILITY: [REQUIRED]"
        tvAccessibilityBadge.background = createBadgeDrawable(
            if (isAccessEnabled) Color.parseColor("#16A34A") else Color.parseColor("#FF0055")
        )

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        tvOverlayBadge.text = if (hasOverlay) "OVERLAY: [ENABLED]" else "OVERLAY: [REQUIRED]"
        tvOverlayBadge.background = createBadgeDrawable(
            if (hasOverlay) Color.parseColor("#16A34A") else Color.parseColor("#FF0055")
        )

        val isBatteryWhitelisted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else {
            true
        }
        tvBatteryBadge.text = if (isBatteryWhitelisted) "BATTERY: [ENABLED]" else "BATTERY: [REQUIRED]"
        tvBatteryBadge.background = createBadgeDrawable(
            if (isBatteryWhitelisted) Color.parseColor("#16A34A") else Color.parseColor("#FF0055")
        )

        val hasAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        tvAudioBadge.text = if (hasAudio) "AUDIO: [ENABLED]" else "AUDIO: [REQUIRED]"
        tvAudioBadge.background = createBadgeDrawable(
            if (hasAudio) Color.parseColor("#16A34A") else Color.parseColor("#FF0055")
        )

        // Section B Hardware Vitals
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.getMemoryInfo(memoryInfo)
            val availMb = memoryInfo.availMem / (1024 * 1024)
            tvRamMetric.text = "RAM BUDGET\nAvail: $availMb MB"
            appendLog("[RAM] Free memory: $availMb MB (Safe)")
        } catch (e: Exception) {
            tvRamMetric.text = "RAM BUDGET\nAvail: --- MB"
        }

        try {
            val kLedger = KnowledgeLedger(File(filesDir, "knowledge_ledger.json"))
            val count = kLedger.getEntries().size
            tvKnowledgeMetric.text = "KNOWLEDGE CACHE\nCached: $count/200"
        } catch (e: Exception) {
            tvKnowledgeMetric.text = "KNOWLEDGE CACHE\nCached: 0/200"
        }

        tvStatusMetric.text = "ACTIVE STATUS\nIDLE"
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
                            val status = intent.getStringExtra(LocalAgentService.EXTRA_STATUS)
                            val result = intent.getStringExtra(LocalAgentService.EXTRA_RESULT_DATA)
                            appendLog("[SCRAPE] Extracted: '$result' [$status]")
                            if (status == "SUCCESS" && !result.isNull_or_blank()) {
                                voiceEngine?.speak("Extracted result: $result")
                            }
                        }
                        DiagnosticRunner.ACTION_AUDIT_COMPLETED -> {
                            appendLog("[AUDIT] Application audit complete.")
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(LocalAgentService.ACTION_TELEMETRY_LOG)
                addAction(LocalAgentService.ACTION_GOAL_COMPLETED)
                addAction(DiagnosticRunner.ACTION_AUDIT_COMPLETED)
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

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
