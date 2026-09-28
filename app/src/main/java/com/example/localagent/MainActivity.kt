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
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.localagent.engine.DiagnosticRunner
import com.example.localagent.intents.AppCapabilityResolver
import com.example.localagent.intents.CapabilityDomain
import com.example.localagent.memory.KnowledgeLedger
import com.example.localagent.memory.MemoryRehydrationManager
import com.example.localagent.memory.RuleLedger
import com.example.localagent.memory.StorageManager
import com.example.localagent.receiver.GoalDispatcher
import com.example.localagent.safety.PermissionManager
import com.example.localagent.voice.VoiceEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class MainActivity : Activity() {

    // Tab Containers
    private lateinit var tabCommandView: LinearLayout
    private lateinit var tabMemoryView: LinearLayout
    private lateinit var tabSystemView: LinearLayout

    // Tab Buttons
    private lateinit var btnTabCommand: Button
    private lateinit var btnTabMemory: Button
    private lateinit var btnTabSystem: Button

    // Tab 1 UI
    private lateinit var tvStatusIndicator: TextView
    private lateinit var etGoalInput: EditText
    private lateinit var btnEngage: Button
    private lateinit var btnVoiceMic: Button
    private lateinit var tvCompactTerminalLog: TextView
    private lateinit var telemetryScrollView: ScrollView
    private lateinit var tvPermissionBanner: TextView

    // Tab 2 UI
    private lateinit var llMemoryCardsContainer: LinearLayout
    private lateinit var tvCapabilityLedgerCard: TextView

    // Tab 3 UI
    private lateinit var tvAccessibilityBadge: TextView
    private lateinit var tvOverlayBadge: TextView
    private lateinit var tvBatteryBadge: TextView
    private lateinit var tvAudioBadge: TextView
    private lateinit var tvStorageBadge: TextView
    private lateinit var tvRamGauge: TextView

    private var voiceEngine: VoiceEngine? = null
    private val telemetryLogs = StringBuilder()
    private var telemetryReceiver: BroadcastReceiver? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceEngine = VoiceEngine(this)

        val mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0E17"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0E17"))
            setPadding(24, 24, 24, 24)
        }

        // Header
        val headerText = TextView(this).apply {
            text = "JARVIS MISSION CONTROL"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00F0FF"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        // Top Tab Bar
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        btnTabCommand = Button(this).apply {
            text = "[COMMAND]"
            textSize = 11f
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#00F0FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener { switchTab(0) }
        }

        btnTabMemory = Button(this).apply {
            text = "[MEMORY]"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 2; marginEnd = 2 }
            setOnClickListener { switchTab(1) }
        }

        btnTabSystem = Button(this).apply {
            text = "[SYSTEM]"
            textSize = 11f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
            background = createBorderDrawable(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener { switchTab(2) }
        }

        tabBar.addView(btnTabCommand)
        tabBar.addView(btnTabMemory)
        tabBar.addView(btnTabSystem)

        // TAB 1: COMMAND DECK
        tabCommandView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        tvPermissionBanner = TextView(this).apply {
            text = "Critical Permissions Missing: Storage, Mic, or Camera denied. Tap to grant."
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF0055"))
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener {
                PermissionManager.checkAndRequestInitialPermissions(this@MainActivity)
            }
        }

        tvStatusIndicator = TextView(this).apply {
            text = "● STATUS: IDLE"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 16)
        }

        etGoalInput = EditText(this).apply {
            hint = "Enter goal (e.g., 'calculate 45 * 8' or 'Ask Gemini')..."
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            setPadding(20, 20, 20, 20)
        }

        val commandButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 16)
        }

        btnEngage = Button(this).apply {
            text = "[ENGAGE]"
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#00F0FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener {
                val goal = etGoalInput.text.toString().trim()
                if (goal.isNotEmpty()) {
                    sendBroadcast(Intent("com.localagent.EXECUTE_GOAL").putExtra("goal_text", goal))
                    appendLog("[ACT] Broadcasted ENGAGE: '$goal'")
                    voiceEngine?.speak("Engaging $goal")
                }
            }
        }

        btnVoiceMic = Button(this).apply {
            text = "🎤 [MIC]"
            setTextColor(Color.parseColor("#0A0E17"))
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#39FF14"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f).apply { marginStart = 4; marginEnd = 4 }
            setOnClickListener {
                appendLog("[VOICE] Listening...")
                voiceEngine?.startListening(
                    onResult = { transcribed ->
                        etGoalInput.setText(transcribed)
                        sendBroadcast(Intent("com.localagent.EXECUTE_GOAL").putExtra("goal_text", transcribed))
                        appendLog("[VOICE] Executing '$transcribed'")
                    },
                    onError = { err -> appendLog("[VOICE] Error: $err") }
                )
            }
        }

        val btnAbort = Button(this).apply {
            text = "[ABORT]"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable(Color.parseColor("#FF0055"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                sendBroadcast(Intent("com.example.localagent.ACTION_KILL_SWITCH"))
                appendLog("[ALERT] EMERGENCY ABORT DISPATCHED!")
                voiceEngine?.speak("Emergency abort executed")
            }
        }

        commandButtonRow.addView(btnEngage)
        commandButtonRow.addView(btnVoiceMic)
        commandButtonRow.addView(btnAbort)

        val terminalTitle = TextView(this).apply {
            text = "LIVE TACTICAL STREAM"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 8, 0, 4)
        }

        tvCompactTerminalLog = TextView(this).apply {
            text = "[PERM] Core Accessibility engine active\n[SYS] Command Deck Online.\n"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(16, 16, 16, 16)
        }

        val scrollPx = (220 * resources.displayMetrics.density).toInt()
        telemetryScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, scrollPx)
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#111625"))
            addView(tvCompactTerminalLog)
        }

        tabCommandView.addView(tvPermissionBanner)
        tabCommandView.addView(tvStatusIndicator)
        tabCommandView.addView(etGoalInput)
        tabCommandView.addView(commandButtonRow)
        tabCommandView.addView(terminalTitle)
        tabCommandView.addView(telemetryScrollView)

        // TAB 2: NEURAL MEMORY BANK
        tabMemoryView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        tvCapabilityLedgerCard = TextView(this).apply {
            text = "APP CAPABILITY & ROUTINE LEDGER\nLoading capabilities..."
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            setPadding(16, 16, 16, 16)
        }

        val memoryFooterSub = TextView(this).apply {
            text = "Storage: /Download/LocalAgent/ (Persists across uninstalls)"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 8, 0, 8)
        }

        val memoryButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        val btnExportMemory = Button(this).apply {
            text = "[Backup Memory]"
            textSize = 10f
            setTextColor(Color.parseColor("#39FF14"))
            background = createBorderDrawable(Color.parseColor("#16A34A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener {
                try {
                    val pDir = StorageManager.getPersistentStorageDir(this@MainActivity)
                    val rulesFile = File(pDir, "local_rules.json")
                    if (rulesFile.exists()) {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val backupFile = File(pDir, "rules_backup_$ts.json")
                        rulesFile.copyTo(backupFile, overwrite = true)
                        appendLog("[MEMORY] Backup created: ${backupFile.name}")
                    }
                } catch (e: Exception) {
                    appendLog("[MEMORY] Backup failed: ${e.message}")
                }
            }
        }

        val btnResyncMemory = Button(this).apply {
            text = "[Re-Sync From Downloads]"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#00F0FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 2; marginEnd = 2 }
            setOnClickListener {
                renderMemoryBankCards()
                appendLog("[MEMORY] Re-synced files from Downloads.")
            }
        }

        val btnPurgeMemory = Button(this).apply {
            text = "[Purge Ledger]"
            textSize = 10f
            setTextColor(Color.parseColor("#FF0055"))
            background = createBorderDrawable(Color.parseColor("#FF0055"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                val pDir = StorageManager.getPersistentStorageDir(this@MainActivity)
                File(pDir, "knowledge_ledger.json").delete()
                File(pDir, "local_rules.json").delete()
                appendLog("[MEMORY] Flushed neural memory ledgers.")
                renderMemoryBankCards()
            }
        }

        memoryButtonRow.addView(btnExportMemory)
        memoryButtonRow.addView(btnResyncMemory)
        memoryButtonRow.addView(btnPurgeMemory)

        llMemoryCardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }

        tabMemoryView.addView(tvCapabilityLedgerCard)
        tabMemoryView.addView(memoryFooterSub)
        tabMemoryView.addView(memoryButtonRow)
        tabMemoryView.addView(llMemoryCardsContainer)

        // TAB 3: SYSTEM & VITALS
        tabSystemView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        tvRamGauge = TextView(this).apply {
            text = "RAM BUDGET: Avail: --- MB / Total: 4 GB"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#39FF14"))
            setPadding(0, 0, 0, 12)
        }

        val permGridRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 8) }
        tvAccessibilityBadge = createBadgeView("ACCESSIBILITY: [REQUIRED]")
        val btnAccessSettings = Button(this).apply {
            text = "Grant Acc."
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            setOnClickListener { PermissionManager.openAccessibilitySettings(this@MainActivity) }
        }
        permGridRow1.addView(tvAccessibilityBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        permGridRow1.addView(btnAccessSettings)

        val permGridRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 8) }
        tvOverlayBadge = createBadgeView("OVERLAY: [REQUIRED]")
        val btnOverlaySettings = Button(this).apply {
            text = "Grant HUD"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            setOnClickListener { PermissionManager.requestOverlayPermission(this@MainActivity) }
        }
        permGridRow2.addView(tvOverlayBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        permGridRow2.addView(btnOverlaySettings)

        val permGridRow3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 8) }
        tvBatteryBadge = createBadgeView("BATTERY: [REQUIRED]")
        val btnBatterySettings = Button(this).apply {
            text = "Whitelist"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            setOnClickListener { PermissionManager.requestBatteryExemption(this@MainActivity) }
        }
        permGridRow3.addView(tvBatteryBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        permGridRow3.addView(btnBatterySettings)

        val permGridRow4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 8) }
        tvAudioBadge = createBadgeView("AUDIO: [REQUIRED]")
        val btnAudioSettings = Button(this).apply {
            text = "Grant Mic"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            setOnClickListener { PermissionManager.checkAndRequestInitialPermissions(this@MainActivity) }
        }
        permGridRow4.addView(tvAudioBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        permGridRow4.addView(btnAudioSettings)

        val permGridRow5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 8) }
        tvStorageBadge = createBadgeView("STORAGE: [REQUIRED]")
        val btnStorageSettings = Button(this).apply {
            text = "Grant Storage"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#334155"))
            setOnClickListener { PermissionManager.checkAndRequestInitialPermissions(this@MainActivity) }
        }
        permGridRow5.addView(tvStorageBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        permGridRow5.addView(btnStorageSettings)

        val hiosRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 16) }
        val tvHiosBadge = createBadgeView("HiOS FREEZE DEFENSE: [RECOMMENDED]").apply {
            background = createBadgeDrawable(Color.parseColor("#0284C7"))
        }
        val btnHiosSettings = Button(this).apply {
            text = "HiOS Whitelist"
            textSize = 10f
            setTextColor(Color.parseColor("#00F0FF"))
            background = createBorderDrawable(Color.parseColor("#0284C7"))
            setOnClickListener {
                try {
                    val intent = Intent().apply {
                        setClassName("com.transsion.phonemaster", "com.transsion.phonemaster.autostart.AutoStartManagementActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(settingsIntent)
                    } catch (ex: Exception) {
                        appendLog("[SYS] HiOS Manager intent unavailable")
                    }
                }
            }
        }
        hiosRow.addView(tvHiosBadge.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        hiosRow.addView(btnHiosSettings)

        val diagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 16) }
        val btnAppAudit = Button(this).apply {
            text = "[Run Full App Audit]"
            textSize = 10f
            setTextColor(Color.parseColor("#39FF14"))
            background = createBorderDrawable(Color.parseColor("#16A34A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 4 }
            setOnClickListener {
                sendBroadcast(Intent(GoalDispatcher.ACTION_RUN_APP_AUDIT))
                appendLog("[SYS] Triggered App Audit")
            }
        }
        val btnTestSwipe = Button(this).apply {
            text = "[Test Coordinate Swipe]"
            textSize = 10f
            setTextColor(Color.parseColor("#39FF14"))
            background = createBorderDrawable(Color.parseColor("#16A34A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 4 }
            setOnClickListener {
                sendBroadcast(Intent(LocalAgentService.ACTION_TEST_COORDINATE_TAP))
                appendLog("[SYS] Triggered Coordinate Swipe")
            }
        }
        diagRow.addView(btnAppAudit)
        diagRow.addView(btnTestSwipe)

        tabSystemView.addView(tvRamGauge)
        tabSystemView.addView(permGridRow1)
        tabSystemView.addView(permGridRow2)
        tabSystemView.addView(permGridRow3)
        tabSystemView.addView(permGridRow4)
        tabSystemView.addView(permGridRow5)
        tabSystemView.addView(hiosRow)
        tabSystemView.addView(diagRow)

        rootLayout.addView(headerText)
        rootLayout.addView(tabBar)
        rootLayout.addView(tabCommandView)
        rootLayout.addView(tabMemoryView)
        rootLayout.addView(tabSystemView)

        mainScrollView.addView(rootLayout)
        setContentView(mainScrollView)

        registerTelemetryReceiver()
        updateSystemStatus()

        // Trigger Guided Permission Onboarding Batch
        PermissionManager.checkGuidedOnboarding(this)
    }

    override fun onResume() {
        super.onResume()
        updateSystemStatus()
        renderMemoryBankCards()
        PermissionManager.checkGuidedOnboarding(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.REQUEST_CODE_RUNTIME) {
            updateSystemStatus()
            PermissionManager.checkGuidedOnboarding(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine?.shutdown()
        unregisterTelemetryReceiver()
    }

    private fun switchTab(tabIndex: Int) {
        tabCommandView.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        tabMemoryView.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        tabSystemView.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        btnTabCommand.background = if (tabIndex == 0) createButtonDrawable(Color.parseColor("#00F0FF")) else createBorderDrawable(Color.parseColor("#334155"))
        btnTabCommand.setTextColor(if (tabIndex == 0) Color.parseColor("#0A0E17") else Color.parseColor("#00F0FF"))

        btnTabMemory.background = if (tabIndex == 1) createButtonDrawable(Color.parseColor("#00F0FF")) else createBorderDrawable(Color.parseColor("#334155"))
        btnTabMemory.setTextColor(if (tabIndex == 1) Color.parseColor("#0A0E17") else Color.parseColor("#00F0FF"))

        btnTabSystem.background = if (tabIndex == 2) createButtonDrawable(Color.parseColor("#00F0FF")) else createBorderDrawable(Color.parseColor("#334155"))
        btnTabSystem.setTextColor(if (tabIndex == 2) Color.parseColor("#0A0E17") else Color.parseColor("#00F0FF"))

        if (tabIndex == 1) {
            renderMemoryBankCards()
        }
    }

    private fun renderMemoryBankCards() {
        llMemoryCardsContainer.removeAllViews()

        try {
            val capabilityResolver = AppCapabilityResolver(this)
            val capMap = capabilityResolver.scanAndMapCapabilities()

            val notesName = capMap[CapabilityDomain.DOMAIN_NOTES]?.appName ?: "Google Keep / Notes"
            val alarmName = capMap[CapabilityDomain.DOMAIN_CLOCK]?.appName ?: "System DeskClock"

            val pDir = StorageManager.getPersistentStorageDir(this)
            val ruleLedger = RuleLedger(File(pDir, "local_rules.json"))

            tvCapabilityLedgerCard.text = "APP CAPABILITY & ROUTINE LEDGER\n" +
                    "• Default Note App: $notesName\n" +
                    "• Default Alarm Engine: $alarmName\n" +
                    "• Default Media Players: [Online: YouTube / Offline: Gallery]\n" +
                    "• Total Rules Cached Offline: 300 Max Buffer"
        } catch (e: Exception) {
            tvCapabilityLedgerCard.text = "APP CAPABILITY & ROUTINE LEDGER\nCapabilities scanning..."
        }

        try {
            val pDir = StorageManager.getPersistentStorageDir(this)
            val kLedger = KnowledgeLedger(File(pDir, "knowledge_ledger.json"))
            val entries = kLedger.getEntries()

            if (entries.isEmpty()) {
                val emptyTv = TextView(this).apply {
                    text = "No neural memory entries cached."
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#64748B"))
                }
                llMemoryCardsContainer.addView(emptyTv)
                return
            }

            entries.take(10).forEach { entry ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = createBorderDrawable(Color.parseColor("#1E293B"))
                    setPadding(12, 12, 12, 12)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 0, 0, 8)
                    }
                }
                val tvApp = TextView(this).apply {
                    text = "APP: ${entry.targetApp}"
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#00F0FF"))
                }
                val tvGoal = TextView(this).apply {
                    text = "GOAL: ${entry.query}"
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.WHITE)
                }
                val tvAns = TextView(this).apply {
                    text = "ANSWER: ${entry.answer}"
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#39FF14"))
                }
                card.addView(tvApp)
                card.addView(tvGoal)
                card.addView(tvAns)
                llMemoryCardsContainer.addView(card)
            }
        } catch (e: Exception) {
            val errTv = TextView(this).apply {
                text = "Memory ledger uninitialized."
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#64748B"))
            }
            llMemoryCardsContainer.addView(errTv)
        }
    }

    private fun updateSystemStatus() {
        val isAccessEnabled = isAccessibilityServiceEnabled(this, LocalAgentService::class.java)
        val isInstanceBound = LocalAgentService.instance != null

        val hasAudio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        tvAudioBadge.text = if (hasAudio) "AUDIO: [GRANTED]" else "AUDIO: [REQUIRED]"
        tvAudioBadge.background = createBadgeDrawable(if (hasAudio) Color.parseColor("#16A34A") else Color.parseColor("#FF0055"))

        val hasStorage = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        tvStorageBadge.text = if (hasStorage) "STORAGE: [GRANTED]" else "STORAGE: [REQUIRED]"
        tvStorageBadge.background = createBadgeDrawable(if (hasStorage) Color.parseColor("#16A34A") else Color.parseColor("#FF0055"))

        val allRuntimeGranted = hasAudio && hasStorage
        btnEngage.isEnabled = allRuntimeGranted
        btnVoiceMic.isEnabled = allRuntimeGranted

        if (isAccessEnabled && !isInstanceBound) {
            tvPermissionBanner.text = "Accessibility link severed (Zombie State). Tap to reset link."
            tvPermissionBanner.setBackgroundColor(Color.parseColor("#EAB308")) // Yellow warning
            tvPermissionBanner.visibility = View.VISIBLE
            tvPermissionBanner.setOnClickListener {
                PermissionManager.openAccessibilitySettings(this@MainActivity)
            }
        } else if (!allRuntimeGranted) {
            tvPermissionBanner.text = "Critical Permissions Missing: Storage, Mic, or Camera denied. Tap to grant."
            tvPermissionBanner.setBackgroundColor(Color.parseColor("#FF0055"))
            tvPermissionBanner.visibility = View.VISIBLE
            tvPermissionBanner.setOnClickListener {
                PermissionManager.checkAndRequestInitialPermissions(this@MainActivity)
            }
        } else {
            tvPermissionBanner.visibility = View.GONE
        }

        tvAccessibilityBadge.text = if (isAccessEnabled && isInstanceBound) "ACCESSIBILITY: [GRANTED]" else "ACCESSIBILITY: [REQUIRED]"
        tvAccessibilityBadge.background = createBadgeDrawable(if (isAccessEnabled && isInstanceBound) Color.parseColor("#16A34A") else Color.parseColor("#FF0055"))

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        tvOverlayBadge.text = if (hasOverlay) "OVERLAY: [GRANTED]" else "OVERLAY: [REQUIRED]"
        tvOverlayBadge.background = createBadgeDrawable(if (hasOverlay) Color.parseColor("#16A34A") else Color.parseColor("#FF0055"))

        val isBatteryWhitelisted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else true
        tvBatteryBadge.text = if (isBatteryWhitelisted) "BATTERY: [GRANTED]" else "BATTERY: [REQUIRED]"
        tvBatteryBadge.background = createBadgeDrawable(if (isBatteryWhitelisted) Color.parseColor("#16A34A") else Color.parseColor("#FF0055"))

        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memoryInfo)
            val availMb = memoryInfo.availMem / (1024 * 1024)
            tvRamGauge.text = "RAM BUDGET: Avail: $availMb MB / Total: 4 GB"
        } catch (e: Exception) {
            tvRamGauge.text = "RAM BUDGET: Avail: --- MB"
        }
    }

    fun appendLog(logLine: String) {
        telemetryLogs.append(logLine).append("\n")
        runOnUiThread {
            tvCompactTerminalLog.text = telemetryLogs.toString()
            telemetryScrollView.post { telemetryScrollView.fullScroll(View.FOCUS_DOWN) }
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
                            val result = intent.getStringExtra(LocalAgentService.EXTRA_RESULT_DATA)
                            appendLog("[SCRAPE] Result: '$result'")
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
            try { unregisterReceiver(it) } catch (e: Exception) { e.printStackTrace() }
            telemetryReceiver = null
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

    private fun createBorderDrawable(strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(Color.parseColor("#1E293B"))
            setStroke(2, strokeColor)
        }
    }

    private fun createBadgeDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(color)
        }
    }

    private fun createButtonDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(bgColor)
        }
    }
}
