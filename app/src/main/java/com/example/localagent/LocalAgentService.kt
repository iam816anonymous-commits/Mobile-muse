package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentCallbacks2
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.gestures.ActionExecutor
import com.example.localagent.gestures.GestureExecutor
import com.example.localagent.hud.FloatingHudManager
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.memory.ActionRule
import com.example.localagent.memory.ActionType
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.memory.RuleLedger
import com.example.localagent.memory.ScreenHasher
import com.example.localagent.network.AiBridgeClient
import com.example.localagent.routines.TestRoutines
import com.example.localagent.safety.KillSwitchReceiver
import com.example.localagent.serializer.ScreenSerializer
import com.example.localagent.state.TaskStateManager
import java.io.File
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
        const val MAX_TRAVERSAL_DEPTH = 7
        private const val DOUBLE_PRESS_TIMEOUT_MS = 500L
    }

    val stateManager = TaskStateManager(maxStepsLimit = 15)
    lateinit var memoryLedger: MemoryLedger
    lateinit var ruleLedger: RuleLedger
    var aiBridgeClient: AiBridgeClient = AiBridgeClient()
    lateinit var gestureExecutor: GestureExecutor
    lateinit var hudManager: FloatingHudManager
    private var killSwitchReceiver: KillSwitchReceiver? = null
    private val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var lastVolumeDownTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        memoryLedger = MemoryLedger(File(filesDir, "memory_ledger.json"))
        ruleLedger = RuleLedger(File(filesDir, "local_rules.json"))
        gestureExecutor = GestureExecutor(this)
        hudManager = FloatingHudManager(this) {
            haltAndResetAgent("Instant abort triggered via floating HUD tap")
        }

        stateManager.setListener { state ->
            hudManager.updateStatus(state.status)
        }

        registerKillSwitch()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudManager.hide()
        unregisterKillSwitch()
        backgroundExecutor.shutdown()
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

        val canContinue = stateManager.incrementStep()
        if (!canContinue) {
            Log.w(TAG, "Circuit Breaker triggered in onAccessibilityEvent")
            memoryLedger.recordStep(
                stepIndex = state.currentStepIndex,
                action = "ACCESSIBILITY_EVENT_PROCESSING",
                success = false,
                failureCode = "CIRCUIT_BREAKER_STEP_LIMIT_EXCEEDED"
            )
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            val extractedNodes = mutableListOf<NodeData>()
            traverseAndExtractNode(rootNode, extractedNodes)
            Log.d(TAG, "Extracted ${extractedNodes.size} nodes from active window")

            val packageName = rootNode.packageName?.toString()
            val screenFingerprint = ScreenHasher.computeFingerprint(packageName, extractedNodes)
            val userGoal = state.goal?.description ?: "Default Goal"

            // 1. Check local rule graph for matching transition
            val localRule = ruleLedger.getActionRule(screenFingerprint, userGoal)
            if (localRule != null) {
                Log.d(TAG, "Local rule graph match found for fingerprint $screenFingerprint. Executing offline.")
                executeActionRule(localRule)
                memoryLedger.recordStep(
                    stepIndex = stateManager.getCurrentState().currentStepIndex,
                    action = "OFFLINE_RULE_GRAPH_ACTION: ${localRule.type}",
                    success = true
                )
            } else {
                // 2. Query AI Bridge if no local rule exists
                val serializedScreen = ScreenSerializer.serializeScreen(extractedNodes)
                aiBridgeClient.sendPayloadAsync(serializedScreen, userGoal) { result ->
                    result.onSuccess { aiResponse ->
                        Log.d(TAG, "AI Bridge response received: $aiResponse")
                        val newRule = ActionRule(type = ActionType.CLICK, textPayload = aiResponse)
                        ruleLedger.addTransition(screenFingerprint, userGoal, newRule)
                        memoryLedger.recordStep(
                            stepIndex = stateManager.getCurrentState().currentStepIndex,
                            action = "AI_BRIDGE_ACTION",
                            success = true
                        )
                    }.onFailure { error ->
                        Log.e(TAG, "AI Bridge request failed", error)
                        memoryLedger.recordStep(
                            stepIndex = stateManager.getCurrentState().currentStepIndex,
                            action = "AI_BRIDGE_ACTION",
                            success = false,
                            failureCode = error.message
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error traversing node tree", e)
            memoryLedger.recordStep(
                stepIndex = stateManager.getCurrentState().currentStepIndex,
                action = "EXTRACT_NODES",
                success = false,
                failureCode = e.javaClass.simpleName
            )
        } finally {
            rootNode.recycle()
        }
    }

    private fun executeActionRule(rule: ActionRule) {
        when (rule.type) {
            ActionType.CLICK -> {
                rule.targetBounds?.let { bounds ->
                    gestureExecutor.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                }
            }
            ActionType.SWIPE -> {
                rule.targetBounds?.let { bounds ->
                    gestureExecutor.swipe(
                        bounds.centerX().toFloat(),
                        bounds.bottom.toFloat(),
                        bounds.centerX().toFloat(),
                        bounds.top.toFloat()
                    )
                }
            }
            ActionType.INPUT -> {
                Log.d(TAG, "Executing INPUT rule payload: ${rule.textPayload}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "LocalAgentService interrupted")
        stateManager.haltTask("Service interrupted")
    }

    fun traverseAndExtractNode(
        node: AccessibilityNodeInfo?,
        result: MutableList<NodeData>,
        currentDepth: Int = 0
    ) {
        if (node == null) return

        // Filter out nodes not visible to user or beyond traversal depth cap (7 levels)
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

    private fun registerKillSwitch() {
        if (killSwitchReceiver == null) {
            killSwitchReceiver = KillSwitchReceiver {
                haltAndResetAgent("Kill switch triggered via broadcast")
            }
            val filter = IntentFilter(KillSwitchReceiver.ACTION_KILL_SWITCH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(killSwitchReceiver, filter, RECEIVER_NOT_EXPORTED)
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
        stateManager.haltTask(reason)
        memoryLedger.recordStep(
            stepIndex = stateManager.getCurrentState().currentStepIndex,
            action = "KILL_SWITCH",
            success = false,
            failureCode = reason
        )
        stateManager.reset()
    }
}
