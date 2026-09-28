package com.example.localagent.memory

import android.app.ActivityManager
import android.content.Context
import android.util.Log

object MemoryGuard {

    private const val TAG = "MemoryGuard"
    const val LOW_RAM_THRESHOLD_BYTES = 250L * 1024 * 1024 // 250 MB

    fun isLowMemoryCondition(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfo)

        val isLow = memoryInfo.lowMemory || memoryInfo.availMem < LOW_RAM_THRESHOLD_BYTES
        if (isLow) {
            Log.w(TAG, "Low memory condition detected! availMem=${memoryInfo.availMem / (1024 * 1024)}MB, lowMemory=${memoryInfo.lowMemory}")
        }
        return isLow
    }
}
