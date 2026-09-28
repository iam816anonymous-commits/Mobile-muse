package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.engine.AutonomousEngine
import com.example.localagent.engine.DiagnosticRunner
import com.example.localagent.gestures.ActionExecutor
import com.example.localagent.gestures.GestureExecutor
import com.example.localagent.hud.FloatingHudManager
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.memory.KnowledgeLedger
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.memory.RuleLedger
import com.example.localagent.network.AiBridgeClient
import com.example.localagent.receiver.GoalDispatcher
import com.example.localagent.routines.TestRoutines
import com.example.localagent.safety.KillSwitchReceiver
import com.example.localagent.serializer.ScreenSerializer
import com.example.localagent.state.TaskStateManager
import com.example.localagent.voice.VoiceSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class NodeData(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val boundsInScreen: Rect,
    val hasActions: Boolean = false
)

open class LocalAgentService : AccessibilityService() {

    companion object {
        private const val TAG = "LocalAgentService"
        const val ACTION_GOAL_COMPLETED = "com.localagent.GOAL_COMPLETED"
        const val ACTION_TELEMETRY_LOG = "com.localagent.TELEMETRY_LOG"
        const val EXTRA_GOAL_TEXT = "goal_text"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_LOG_ENTRY = "log_entry"

        const val ACTION_TEST_APP_LAUNCH = "com.localagent.TEST_APP_LAUNCH"
        const val ACTION_TEST_NODE_DUMP = "com.localagent.TEST_NODE_DUMP"
        const val ACTION_TEST_COORDINATE_TAP = "com.localagent.TEST_COORDINATE_TAP"
        const val ACTION_TEST_TEXT_INJECTION = "com.localagent.TEST_TEXT_INJECTION"

        const val MAX_TRAVERSAL_DEPTH = 6
        private const val DOUBLE_PRESS_TIMEOUT_MS = 500L
    }

    val stateManager = TaskStateManager(maxStepsLimit = 15)
    lateinit var memoryLedger: MemoryLedger
    lateinit var ruleLedger: RuleLedger
    lateinit var knowledgeLedger: KnowledgeLedger
    var aiBridgeClient: AiBridgeClient = AiBridgeClient()
    lateinit var gestureExecutor: GestureExecutor
    lateinit var hudManager: FloatingHudManager
    var voiceSynthesizer: VoiceSynthesizer? = null
    private var killSwitchReceiver: KillSwitchReceiver? = null
    private var goalDispatcher: GoalDispatcher? = null
    private var diagnosticReceiver: BroadcastReceiver? = null
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var lastVolumeDownTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        memoryLedger = MemoryLedger(File(filesDir, "memory_ledger.json"))
        ruleLedger = RuleLedger(File(filesDir, "local_rules.json"))
        knowledgeLedger = KnowledgeLedger(File(filesDir, "knowledge_ledger.json"))
        gestureExecutor = GestureExecutor(this)
        voiceSynthesizer = VoiceSynthesizer(this)
        hudManager = FloatingHudManager(this) {
            haltAndResetAgent("Instant abort triggered via floating HUD tap")
        }

        stateManager.setListener { state ->
            hudManager.updateStatus(state.status)
        }

        registerKillSwitch()
        registerGoalDispatcher()
        registerDiagnosticReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudManager.hide()
        voiceSynthesizer?.shutdown()
        unregisterKillSwitch()
        unregisterGoalDispatcher()
        unregisterDiagnosticReceiver()
        serviceScope.cancel()
        backgroundExecutor.shutdown()
    }

    fun broadcastTelemetryLog(typeTag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$typeTag] $timestamp - $message"
        val intent = Intent(ACTION_TELEMETRY_LOG).apply {
            putExtra(EXTRA_LOG_ENTRY, entry)
        }
        sendBroadcast(intent)
        Log.d(TAG, "Telemetry Log: $entry")
    }

    fun broadcastGoalCompleted(goalText: String, status: String, resultData: String) {
        val intent = Intent(ACTION_GOAL_COMPLETED).apply {
            putExtra(EXTRA_GOAL_TEXT, goalText)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_RESULT_DATA, resultData)
        }
        sendBroadcast(intent)
        broadcastTelemetryLog("EXTRACT", "Goal Completed [$status]: $resultData")
        voiceSynthesizer?.speak("Done. Extracted answer: $resultData")
        Log.d(TAG, "Broadcasted GOAL_COMPLETED: status=$status, result=$resultData")
    }

    // --- Self-Diagnostic Testing Panel Logic ---

    fun testAppLaunch() {
        broadcastTelemetryLog("DIAG", "Testing App Launch: Google Chrome...")
        val launched = IntentLauncher.launchChrome(this)
        val currentPkg = getActiveWindowRoot()?.packageName?.toString() ?: "unknown"
        broadcastTelemetryLog("DIAG", "App Launch result: launched=$launched, activePkg=$currentPkg")
    }

    fun testNodeDump() {
        broadcastTelemetryLog("DIAG", "Testing Node Dump: Collecting top 5 visible nodes...")
        val rootNode = getActiveWindowRoot()
        if (rootNode == null) {
            broadcastTelemetryLog("DIAG", "Node Dump failed: Active window root is null")
            return
        }

        try {
            val extractedNodes = mutableListOf<NodeData>()
            traverseAndExtractNode(rootNode, extractedNodes)
            val top5 = extractedNodes.take(5)
            broadcastTelemetryLog("DIAG", "Node Dump captured ${top5.size} nodes:")
            top5.forEachIndexed { idx, node ->
                val label = node.text ?: node.contentDescription ?: "<no label>"
                val boundsStr = "${node.boundsInScreen.left},${node.boundsInScreen.top}-${node.boundsInScreen.right},${node.boundsInScreen.bottom}"
                broadcastTelemetryLog("DIAG", " #$idx: [$label] (${node.className}) at [$boundsStr]")
            }
        } finally {
            rootNode.recycle()
        }
    }

    fun testCoordinateTap() {
        broadcastTelemetryLog("DIAG", "Testing Coordinate Tap at (500, 500)...")
        gestureExecutor.tap(500f, 500f, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                broadcastTelemetryLog("DIAG", "Coordinate Tap CONFIRMED at (500, 500)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                broadcastTelemetryLog("DIAG", "Coordinate Tap CANCELLED at (500, 500)")
            }
        })
    }

    fun testTextInjection() {
        broadcastTelemetryLog("DIAG", "Testing Text Injection into first editable field...")
        val rootNode = getActiveWindowRoot()
        if (rootNode == null) {
            broadcastTelemetryLog("DIAG", "Text Injection failed: Active window root is null")
            return
        }

        try {
            val editableNode = AutonomousEngine.findEditableNode(rootNode)
            if (editableNode != null) {
                try {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            "Diagnostic Sample Text"
                        )
                    }
                    val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    broadcastTelemetryLog("DIAG", "Text Injection performed: success=$success")
                } finally {
                    editableNode.recycle()
                }
            } else {
                broadcastTelemetryLog("DIAG", "Text Injection failed: No editable field found on current screen")
            }
        } finally {
            rootNode.recycle()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "onTrimMemory level: $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            Log.w(TAG, "Memory pressure detected. Flushing rule cache and clearing queues.")
            memoryLedger.clearRuleCache()
            broadcastTelemetryLog("SYS", "Memory pressure trim triggered")
            System.gc()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "LocalAgentService connected")
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        hudManager.show()
        broadcastTelemetryLog("SYS", "LocalAgentService connected and online")
    }

    public override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownTime <= DOUBLE_PRESS_TIMEOUT_MS) {
                Log.w(TAG, "Volume Down double-pressed within ${DOUBLE_PRESS_TIMEOUT_MS}ms. Instantly aborting agent.")
                haltAndResetAgent("Instant abort triggered via Volume Down double-press")
                lastVolumeDownTime = 0L
                return true
            } else {
                lastVolumeDownTime = currentTime
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val state = stateManager.getCurrentState()
        if (state.status != com.example.localagent.state.AgentStatus.RUNNING) {
            return
        }

        // Only process major state transitions (WINDOW_STATE_CHANGED or WINDOW_CONTENT_CHANGED)
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val goalText = state.goal?.description ?: return

        // Knowledge Ledger Fast Lookup before full execution
        val knownAnswer = knowledgeLedger.findAnswerForQuery(goalText)
        if (knownAnswer != null) {
            broadcastTelemetryLog("KNOWLEDGE", "Fast Local Query Match Found: '${knownAnswer.answer}'")
            broadcastGoalCompleted(goalText, "SUCCESS", knownAnswer.answer)
            stateManager.completeTask()
            return
        }

        AutonomousEngine.processCurrentScreen(this, goalText)
    }

    override fun onInterrupt() {
        Log.d(TAG, "LocalAgentService interrupted")
        stateManager.haltTask("Service interrupted")
        broadcastTelemetryLog("SYS", "Service interrupted")
    }

    fun traverseAndExtractNode(
        node: AccessibilityNodeInfo?,
        result: MutableList<NodeData>,
        currentDepth: Int = 0
    ) {
        if (node == null) return

        // Filter out nodes not visible to user or beyond traversal depth cap (6 levels)
        if (!node.isVisibleToUser || currentDepth >= MAX_TRAVERSAL_DEPTH) {
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val textStr = node.text?.toString()
        val descStr = node.contentDescription?.toString()
        val classStr = node.className?.toString()
        val hasActions = node.actionList.isNotEmpty() || node.isClickable

        result.add(
            NodeData(
                text = textStr,
                contentDescription = descStr,
                className = classStr,
                boundsInScreen = bounds,
                hasActions = hasActions
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseAndExtractNode(child, result, currentDepth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    fun performClickWithFallback(node: AccessibilityNodeInfo): Boolean {
        val canContinue = stateManager.incrementStep()
        if (!canContinue) {
            Log.w(TAG, "Circuit Breaker triggered in performClickWithFallback")
            haltAndResetAgent("CIRCUIT_BREAKER_STEP_LIMIT_EXCEEDED")
            return false
        }
        return ActionExecutor.performClickWithFallback(node, gestureExecutor)
    }

    fun waitForNodeOrTimeout(
        predicate: (AccessibilityNodeInfo) -> Boolean,
        timeoutMs: Long,
        callback: (AccessibilityNodeInfo?) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val pollInterval = 200L

        fun dispatchResult(result: AccessibilityNodeInfo?) {
            val mainLooper = try { Looper.getMainLooper() } catch (e: Exception) { null }
            val myLooper = try { Looper.myLooper() } catch (e: Exception) { null }

            if (mainLooper == null || myLooper == mainLooper) {
                callback(result)
            } else {
                mainHandler.post { callback(result) }
            }
        }

        fun poll() {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                dispatchResult(null)
                return
            }

            val root = getActiveWindowRoot()
            if (root != null) {
                val matched = findNodeMatching(root, predicate)
                if (matched != null) {
                    root.recycle()
                    dispatchResult(matched)
                    return
                }
                root.recycle()
            }

            backgroundExecutor.schedule({ poll() }, pollInterval, java.util.concurrent.TimeUnit.MILLISECONDS)
        }

        backgroundExecutor.execute { poll() }
    }

    open fun getActiveWindowRoot(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    private fun findNodeMatching(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser || depth >= MAX_TRAVERSAL_DEPTH) {
            return null
        }

        if (predicate(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val match = findNodeMatching(child, predicate, depth + 1)
                if (match != null) {
                    return match
                }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    fun launchChrome(): Boolean = IntentLauncher.launchChrome(this)
    fun launchYouTube(): Boolean = IntentLauncher.launchYouTube(this)
    fun launchCamera(): Boolean = IntentLauncher.launchCamera(this)

    fun runChromeSearchTest(query: String = "Android Accessibility"): Boolean {
        return TestRoutines.runChromeSearchTest(this, query)
    }

    fun runCameraRecordingTest(): Boolean {
        return TestRoutines.runCameraRecordingTest(this)
    }

    fun runYouTubePlaybackTest(query: String = "Kotlin Android Tutorial"): Boolean {
        return TestRoutines.runYouTubePlaybackTest(this, query)
    }

    private fun registerDiagnosticReceiver() {
        if (diagnosticReceiver == null) {
            diagnosticReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ACTION_TEST_APP_LAUNCH -> testAppLaunch()
                        ACTION_TEST_NODE_DUMP -> testNodeDump()
                        ACTION_TEST_COORDINATE_TAP -> testCoordinateTap()
                        ACTION_TEST_TEXT_INJECTION -> testTextInjection()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ACTION_TEST_APP_LAUNCH)
                addAction(ACTION_TEST_NODE_DUMP)
                addAction(ACTION_TEST_COORDINATE_TAP)
                addAction(ACTION_TEST_TEXT_INJECTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(diagnosticReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(diagnosticReceiver, filter)
            }
        }
    }

    private fun unregisterDiagnosticReceiver() {
        diagnosticReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering diagnostic receiver", e)
            }
            diagnosticReceiver = null
        }
    }

    private fun registerGoalDispatcher() {
        if (goalDispatcher == null) {
            goalDispatcher = GoalDispatcher(this, serviceScope)
            val filter = IntentFilter().apply {
                addAction(GoalDispatcher.ACTION_EXECUTE_GOAL)
                addAction(GoalDispatcher.ACTION_RUN_DIAGNOSTIC)
                addAction(GoalDispatcher.ACTION_RUN_APP_AUDIT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(goalDispatcher, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(goalDispatcher, filter)
            }
        }
    }

    private fun unregisterGoalDispatcher() {
        goalDispatcher?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering goal dispatcher", e)
            }
            goalDispatcher = null
        }
    }

    private fun registerKillSwitch() {
        if (killSwitchReceiver == null) {
            killSwitchReceiver = KillSwitchReceiver {
                haltAndResetAgent("Kill switch triggered via broadcast")
            }
            val filter = IntentFilter(KillSwitchReceiver.ACTION_KILL_SWITCH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(killSwitchReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(killSwitchReceiver, filter)
            }
        }
    }

    private fun unregisterKillSwitch() {
        killSwitchReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering kill switch receiver", e)
            }
            killSwitchReceiver = null
        }
    }

    fun haltAndResetAgent(reason: String) {
        Log.w(TAG, "Halting and resetting agent: $reason")
        val currentGoal = stateManager.getCurrentState().goal?.description ?: ""
        stateManager.haltTask(reason)
        memoryLedger.recordStep(
            stepIndex = stateManager.getCurrentState().currentStepIndex,
            action = "KILL_SWITCH",
            success = false,
            failureCode = reason
        )
        broadcastGoalCompleted(currentGoal, "FAILURE", reason)
        broadcastTelemetryLog("SYS", "ABORT EXECUTED: $reason")
        stateManager.reset()
    }
}
