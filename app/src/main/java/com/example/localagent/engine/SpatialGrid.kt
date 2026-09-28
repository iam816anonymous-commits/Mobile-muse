package com.example.localagent.engine

import android.graphics.PointF
import android.util.Log
import com.example.localagent.LocalAgentService

object SpatialGrid {

    private const val TAG = "SpatialGrid"

    // Normalized 18:9 (720x1440) UI UX Anchors
    val FAB_ADD = PointF(0.85f, 0.88f)
    val TOP_SEARCH = PointF(0.50f, 0.08f)
    val SUBMIT_ENTER = PointF(0.90f, 0.92f)
    val NAV_BACK = PointF(0.08f, 0.06f)
    val CENTER_ACTION = PointF(0.50f, 0.50f)

    fun dispatchAnchorTap(service: LocalAgentService, anchor: PointF, label: String): Boolean {
        val metrics = service.resources.displayMetrics
        val width = if (metrics.widthPixels <= 0) 720f else metrics.widthPixels.toFloat()
        val height = if (metrics.heightPixels <= 0) 1440f else metrics.heightPixels.toFloat()

        val x = width * anchor.x
        val y = height * anchor.y

        val dispatched = service.gestureExecutor.tap(x, y)
        Log.i(TAG, "Spatial Grid anchor '$label' tapped at ($x, $y) -> $dispatched")
        service.broadcastTelemetryLog("GRID", "Spatial Grid anchor '$label' tapped at ($x, $y) -> Dispatched: $dispatched")
        return dispatched
    }

    fun dispatchIntentAnchor(service: LocalAgentService, goalText: String): Boolean {
        val lowerGoal = goalText.lowercase()
        return when {
            lowerGoal.contains("add") || lowerGoal.contains("plus") || lowerGoal.contains("new") || lowerGoal.contains("create") -> {
                dispatchAnchorTap(service, FAB_ADD, "FAB_ADD")
            }
            lowerGoal.contains("search") || lowerGoal.contains("find") -> {
                dispatchAnchorTap(service, TOP_SEARCH, "TOP_SEARCH")
            }
            lowerGoal.contains("submit") || lowerGoal.contains("enter") || lowerGoal.contains("done") || lowerGoal.contains("send") -> {
                dispatchAnchorTap(service, SUBMIT_ENTER, "SUBMIT_ENTER")
            }
            else -> {
                dispatchAnchorTap(service, CENTER_ACTION, "CENTER_ACTION")
            }
        }
    }
}
