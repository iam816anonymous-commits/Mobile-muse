package com.example.localagent.skills

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.localagent.LocalAgentService
import com.example.localagent.engine.AppIndexer
import com.example.localagent.engine.GenericUIOperator
import com.example.localagent.voice.VoiceSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CalculatorSkill {
    private const val TAG = "CalculatorSkill"

    fun evaluateMath(service: AccessibilityService, expression: String) {
        CoroutineScope(Dispatchers.Default).launch {
            val agentService = service as? LocalAgentService
            agentService?.isProcessingGoal = true
            try {
                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                }
                val resolvedActivity = service.packageManager.resolveActivity(calcIntent, 0)
                val calcPackage = resolvedActivity?.activityInfo?.packageName
                    ?: AppIndexer.resolveAppByQuery("calculator")
                    ?: "com.google.android.calculator"

                val launchIntent = service.packageManager.getLaunchIntentForPackage(calcPackage)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                if (launchIntent != null) {
                    service.startActivity(launchIntent)
                    delay(1200) // Wait for UI foreground stabilization
                }

                // Parse tokens cleanly
                val tokens = tokenizeExpression(expression)
                for (token in tokens) {
                    val root = service.rootInActiveWindow
                    var clicked = false
                    if (root != null) {
                        try {
                            val nodes = root.findAccessibilityNodeInfosByText(token)
                            for (node in nodes) {
                                try {
                                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                        clicked = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error clicking token node: $token", e)
                                } finally {
                                    try { node.recycle() } catch (e: Exception) {}
                                }
                            }
                        } finally {
                            try { root.recycle() } catch (e: Exception) {}
                        }
                    }
                    delay(120) // Critical debounce delay for MediaTek input buffer
                }

                // Tap '=' button
                delay(200)
                val root = service.rootInActiveWindow
                if (root != null) {
                    try {
                        root.findAccessibilityNodeInfosByText("=")?.firstOrNull()?.let {
                            try {
                                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error clicking equals node", e)
                            } finally {
                                try { it.recycle() } catch (e: Exception) {}
                            }
                        }
                    } finally {
                        try { root.recycle() } catch (e: Exception) {}
                    }
                }
                delay(400)

                // Extract numeric result
                val updatedRoot = service.rootInActiveWindow
                val resultText = if (updatedRoot != null) {
                    try {
                        val resultNodes = GenericUIOperator.harvestLeafText(updatedRoot) { text ->
                            text.matches(Regex("^[0-9,.]+$")) && text.length < 15
                        }
                        resultNodes.lastOrNull() ?: "Done"
                    } finally {
                        try { updatedRoot.recycle() } catch (e: Exception) {}
                    }
                } else {
                    "Done"
                }

                agentService?.voiceSynthesizer?.speak("The result is $resultText")
                agentService?.broadcastGoalCompleted(expression, "SUCCESS", resultText)
            } catch (e: Exception) {
                Log.e(TAG, "Calculator automation failed", e)
            } finally {
                agentService?.isProcessingGoal = false
            }
        }
    }

    fun executeCalculation(service: LocalAgentService, expression: String, voiceSynthesizer: VoiceSynthesizer? = null) {
        evaluateMath(service, expression)
    }

    private fun tokenizeExpression(expr: String): List<String> {
        val clean = expr.replace("calculate", "", ignoreCase = true)
            .replace("what is", "", ignoreCase = true)
            .replace(" ", "")
        val tokens = mutableListOf<String>()
        for (ch in clean) {
            tokens.add(ch.toString())
        }
        return tokens
    }
}
