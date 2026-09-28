package com.example.localagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.memory.MemoryLedger
import com.example.localagent.safety.KillSwitchReceiver
import com.example.localagent.state.TaskStateManager
import java.io.File

data class NodeData(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val boundsInScreen: Rect
)

class LocalAgentService : AccessibilityService() {

    companion object {
        private const val TAG = "LocalAgentService"
    }

    val stateManager = TaskStateManager(maxStepsLimit = 15)
    lateinit var memoryLedger: MemoryLedger
    private var killSwitchReceiver: KillSwitchReceiver? = null

    override fun onCreate() {
        super.onCreate()
        memoryLedger = MemoryLedger(File(filesDir, "memory_ledger.json"))
        registerKillSwitch()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterKillSwitch()
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
            memoryLedger.recordStep(
                stepIndex = stateManager.getCurrentState().currentStepIndex,
                action = "EXTRACT_NODES",
                success = true
            )
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

    fun traverseAndExtractNode(node: AccessibilityNodeInfo?, result: MutableList<NodeData>) {
        if (node == null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        result.add(
            NodeData(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                boundsInScreen = bounds
            )
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseAndExtractNode(child, result)
            } finally {
                child.recycle()
            }
        }
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
