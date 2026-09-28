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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.gestures.ActionExecutor
import com.example.localagent.gestures.GestureExecutor
import com.example.localagent.intents.IntentLauncher
import com.example.localagent.memory.MemoryLedger
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
    }

    val stateManager = TaskStateManager(maxStepsLimit = 15)
    lateinit var memoryLedger: MemoryLedger
    var aiBridgeClient: AiBridgeClient = AiBridgeClient()
    lateinit var gestureExecutor: GestureExecutor
    private var killSwitchReceiver: KillSwitchReceiver? = null
    private val backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()
        memoryLedger = MemoryLedger(File(filesDir, "memory_ledger.json"))
        gestureExecutor = GestureExecutor(this)
        registerKillSwitch()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        serviceInfo = info
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

            val serializedScreen = ScreenSerializer.serializeScreen(extractedNodes)
            val currentGoalDesc = state.goal?.description ?: "Default Goal"

            // Check dynamic rule cache first for offline execution
            val cachedRule = memoryLedger.getCachedRule(serializedScreen)
            if (cachedRule != null) {
                Log.d(TAG, "Found cached rule for screen state. Executing offline: $cachedRule")
                memoryLedger.recordStep(
                    stepIndex = stateManager.getCurrentState().currentStepIndex,
                    action = "OFFLINE_RULE_ACTION: $cachedRule",
                    success = true
                )
            } else {
                // Query AI Bridge asynchronously if no cached rule exists
                aiBridgeClient.sendPayloadAsync(serializedScreen, currentGoalDesc) { result ->
                    result.onSuccess { aiResponse ->
                        Log.d(TAG, "AI Bridge response received: $aiResponse")
                        memoryLedger.cacheRule(serializedScreen, aiResponse)
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
