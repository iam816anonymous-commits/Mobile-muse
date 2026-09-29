package com.example.localagent.memory

import android.graphics.PointF

data class ActionNodeRule(
    val fingerprint: String,
    val goalIntent: String,
    val targetResourceId: String? = null,
    val targetText: String? = null,
    val targetRelativeCoord: PointF? = null,
    val actionType: String = "CLICK", // CLICK, TYPE, SWIPE, CONFIRM
    var successCount: Int = 1,
    var failureCount: Int = 0,
    var lastUpdatedMs: Long = System.currentTimeMillis()
)
